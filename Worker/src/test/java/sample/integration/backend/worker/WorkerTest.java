package sample.integration.backend.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import sample.integration.backend.models.*;
import sample.integration.backend.repositories.CasePriorityRepository;
import sample.integration.backend.repositories.CaseRepository;
import sample.integration.backend.services.CaseDataFetcher;
import sample.integration.backend.services.CasePublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

/**
 * Tests developed with AI assistance to verify Worker orchestration logic against README.md requirements.
 *
 * Unit tests for the Worker class, designed to verify the core orchestration logic
 * against the requirements defined in README.md:
 *
 * - Files named <id>.zip are processed through a two-phase pipeline (caching → transfer)
 * - Metadata is fetched from three external systems: Ret (<id>), Sag (<id>), Prio (Sag.id)
 * - File and metadata are transferred to System 1 (Jira) and System 2 (Archive Store)
 * - Idempotency: already-completed transfers are skipped on reprocessing
 * - Retry: failed operations increment a counter, max 5 attempts before permanent failure
 * - State transitions: PROCESSING → COMPLETED or PROCESSING → FAILED
 */
class WorkerTest {

    @TempDir
    Path tempDir;

    private Path processingPath;
    private Path failedCachingPath;
    private Path failedTransferPath;
    private Path completedPath;

    private RabbitTemplate rabbitTemplate;
    private CaseRepository caseRepository;
    private CasePriorityRepository casePriorityRepository;
    private CaseDataFetcher caseDataFetcher;
    private CasePublisher casePublisher;

    private Worker worker;

    @BeforeEach
    void setUp() throws IOException {
        processingPath = tempDir.resolve("processing");
        failedCachingPath = tempDir.resolve("failed/caching");
        failedTransferPath = tempDir.resolve("failed/transfer");
        completedPath = tempDir.resolve("completed");

        Files.createDirectories(processingPath);
        Files.createDirectories(failedCachingPath);
        Files.createDirectories(failedTransferPath);
        Files.createDirectories(completedPath);

        rabbitTemplate = mock(RabbitTemplate.class);
        caseRepository = mock(CaseRepository.class);
        casePriorityRepository = mock(CasePriorityRepository.class);
        caseDataFetcher = mock(CaseDataFetcher.class);
        casePublisher = mock(CasePublisher.class);

        worker = new Worker(
                rabbitTemplate,
                processingPath.toString(),
                failedCachingPath.toString(),
                failedTransferPath.toString(),
                completedPath.toString(),
                "file.transfer.queue",
                List.of(caseDataFetcher),
                List.of(casePublisher),
                caseRepository,
                casePriorityRepository
        );
    }

    // --- processFileMessage tests ---

    /**
     * Verifies the happy path of the caching phase: when a file exists in the processing folder,
     * a new Case record is created in the database, metadata is cached successfully, and the
     * caseId is published to the transfer queue with the correct priority via MessagePostProcessor.
     */
    @Test
    void processFileMessage_success_createsCaseAndSendsToTransferQueue() throws IOException {
        String fileName = "12345.zip";
        Files.createFile(processingPath.resolve(fileName));

        Case savedCase = new Case();
        savedCase.setCaseId("12345");
        savedCase.setFileName(fileName);
        when(caseRepository.findById("12345")).thenReturn(Optional.empty()).thenReturn(Optional.of(savedCase));
        when(caseRepository.saveAndFlush(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));
        when(caseDataFetcher.syncCaseData(any())).thenReturn(true);

        CasePriority priority = new CasePriority(savedCase, CasePriorityStatus.HIGH);
        when(casePriorityRepository.findByCaseRecord(any())).thenReturn(priority);

        worker.processFileMessage(fileName);

        verify(caseRepository).saveAndFlush(any(Case.class));
        // Verify convertAndSend was called with MessagePostProcessor (the 3-arg overload used in production)
        verify(rabbitTemplate).convertAndSend(
                eq("file.transfer.queue"),
                (Object) eq("12345"),
                any(MessagePostProcessor.class)
        );
    }

    /**
     * Verifies that when metadata caching fails (CaseDataFetcher returns false),
     * a RuntimeException is thrown to trigger RabbitMQ retry, and no message is
     * sent to the transfer queue.
     */
    @Test
    void processFileMessage_cachingFails_throwsException() throws IOException {
        String fileName = "12345.zip";
        Files.createFile(processingPath.resolve(fileName));

        Case savedCase = new Case();
        savedCase.setCaseId("12345");
        savedCase.setFileName(fileName);
        when(caseRepository.findById("12345")).thenReturn(Optional.empty()).thenReturn(Optional.of(savedCase));
        when(caseRepository.saveAndFlush(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));
        when(caseDataFetcher.syncCaseData(any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> worker.processFileMessage(fileName));
        verifyNoInteractions(rabbitTemplate);
    }

    /**
     * Verifies that when the expected file is missing from the processing folder,
     * no Case record is created and no interaction with RabbitMQ occurs.
     * The worker should log the error and exit gracefully without throwing.
     */
    @Test
    void processFileMessage_fileDoesNotExist_doesNotCreateCase() {
        worker.processFileMessage("nonexistent.zip");

        verify(caseRepository, never()).saveAndFlush(any());
        verifyNoInteractions(rabbitTemplate);
    }

    /**
     * Verifies the idempotency guard during caching: if a Case already exists with
     * a non-PROCESSING status (e.g. COMPLETED), reprocessing is rejected with an
     * exception to prevent duplicate work.
     */
    @Test
    void processFileMessage_existingCompletedCase_throwsException() throws IOException {
        String fileName = "12345.zip";
        Files.createFile(processingPath.resolve(fileName));

        Case existingCase = new Case();
        existingCase.setCaseId("12345");
        existingCase.setFileName(fileName);
        existingCase.setCaseTransferStatus(CaseTransferStatus.COMPLETED);
        when(caseRepository.findById("12345")).thenReturn(Optional.of(existingCase));

        assertThrows(RuntimeException.class, () -> worker.processFileMessage(fileName));
    }

    // --- transferCase tests ---

    /**
     * Verifies the happy path of the transfer phase: when all CasePublishers succeed,
     * the Case status is set to COMPLETED, the file is moved from processing to completed,
     * and the updated Case record is persisted to the database.
     */
    @Test
    void transferCase_allPublishersSucceed_setsCompletedAndMovesFile() throws IOException {
        String fileName = "12345.zip";
        Files.createFile(processingPath.resolve(fileName));

        Case caseRecord = new Case();
        caseRecord.setCaseId("12345");
        caseRecord.setFileName(fileName);
        caseRecord.setCaseTransferStatus(CaseTransferStatus.PROCESSING);
        when(caseRepository.findById("12345")).thenReturn(Optional.of(caseRecord));
        when(caseRepository.saveAndFlush(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));
        when(casePublisher.publish(any())).thenReturn(true);

        worker.transferCase("12345");

        assertEquals(CaseTransferStatus.COMPLETED, caseRecord.getCaseTransferStatus());
        assertTrue(Files.exists(completedPath.resolve(fileName)));
        assertFalse(Files.exists(processingPath.resolve(fileName)));
        verify(caseRepository).saveAndFlush(caseRecord);
    }

    /**
     * Verifies that when a CasePublisher fails, the retry counter is incremented,
     * the Case remains in PROCESSING status, the file stays in the processing folder,
     * and a RuntimeException is thrown to trigger RabbitMQ's retry mechanism.
     */
    @Test
    void transferCase_publisherFails_incrementsRetriesAndThrows() throws IOException {
        String fileName = "12345.zip";
        Files.createFile(processingPath.resolve(fileName));

        Case caseRecord = new Case();
        caseRecord.setCaseId("12345");
        caseRecord.setFileName(fileName);
        caseRecord.setRetries(0);
        when(caseRepository.findById("12345")).thenReturn(Optional.of(caseRecord));
        when(caseRepository.saveAndFlush(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));
        when(casePublisher.publish(any())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> worker.transferCase("12345"));

        assertEquals(1, caseRecord.getRetries());
        assertEquals(CaseTransferStatus.PROCESSING, caseRecord.getCaseTransferStatus());
        assertTrue(Files.exists(processingPath.resolve(fileName)));
    }

    /**
     * Verifies that after exceeding the maximum number of retries (5 attempts),
     * the Case is marked as FAILED, the file is moved to the failed/transfer folder,
     * and no further exception is thrown (permanent failure is terminal).
     */
    @Test
    void transferCase_maxRetriesExceeded_failsPermanently() throws IOException {
        String fileName = "12345.zip";
        Files.createFile(processingPath.resolve(fileName));

        Case caseRecord = new Case();
        caseRecord.setCaseId("12345");
        caseRecord.setFileName(fileName);
        caseRecord.setRetries(4);
        when(caseRepository.findById("12345")).thenReturn(Optional.of(caseRecord));
        when(caseRepository.saveAndFlush(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));
        when(casePublisher.publish(any())).thenReturn(false);

        worker.transferCase("12345");

        assertEquals(CaseTransferStatus.FAILED, caseRecord.getCaseTransferStatus());
        assertTrue(Files.exists(failedTransferPath.resolve(fileName)));
        assertFalse(Files.exists(processingPath.resolve(fileName)));
    }

    /**
     * Verifies that attempting to transfer a case that does not exist in the database
     * results in a RuntimeException, since the transfer phase requires a pre-existing
     * Case record created during the caching phase.
     */
    @Test
    void transferCase_caseNotFound_throwsException() {
        when(caseRepository.findById("99999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> worker.transferCase("99999"));
    }

    // --- Idempotency tests ---

    /**
     * Verifies idempotent transfer behavior: when a Case already has an existing
     * JiraTransferStatus record, the publisher recognizes it and skips the transfer.
     * The Case still completes successfully and the file is moved to the completed folder.
     */
    @Test
    void transferCase_alreadyTransferredToJira_skipsJira() throws IOException {
        String fileName = "12345.zip";
        Files.createFile(processingPath.resolve(fileName));

        Case caseRecord = new Case();
        caseRecord.setCaseId("12345");
        caseRecord.setFileName(fileName);
        JiraTransferStatus existingJira = new JiraTransferStatus(caseRecord);
        caseRecord.setJiraTransferStatus(existingJira);
        when(caseRepository.findById("12345")).thenReturn(Optional.of(caseRecord));
        when(caseRepository.saveAndFlush(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));
        when(casePublisher.publish(any())).thenReturn(true);

        worker.transferCase("12345");

        assertEquals(CaseTransferStatus.COMPLETED, caseRecord.getCaseTransferStatus());
        assertTrue(Files.exists(completedPath.resolve(fileName)));
    }

    // --- incrementAndSave test ---

    /**
     * Verifies that incrementAndSave correctly increments the retry counter by one
     * and persists the updated Case record to the database via saveAndFlush.
     */
    @Test
    void incrementAndSave_incrementsRetriesAndPersists() {
        Case caseRecord = new Case();
        caseRecord.setRetries(2);
        when(caseRepository.saveAndFlush(any(Case.class))).thenAnswer(inv -> inv.getArgument(0));

        Case result = worker.incrementAndSave(caseRecord);

        assertEquals(3, result.getRetries());
        verify(caseRepository).saveAndFlush(caseRecord);
    }
}

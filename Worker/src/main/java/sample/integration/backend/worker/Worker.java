package sample.integration.backend.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sample.integration.backend.models.*;
import sample.integration.backend.repositories.CasePriorityRepository;
import sample.integration.backend.repositories.CaseRepository;
import sample.integration.backend.services.CaseDataFetcher;
import sample.integration.backend.services.CasePublisher;

import java.io.IOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Component
public class Worker {
    private static final Logger log = LoggerFactory.getLogger(Worker.class);
    private final Path processingPath;
    private final Path failedCachingPath;
    private final Path failedTransferPath;
    private final Path completedPath;

    private final String transferQueue;

    private final RabbitTemplate rabbitTemplate;

    private final List<CaseDataFetcher> caseDataFetchers;
    private final List<CasePublisher> casePublishers;

    private final CaseRepository caseRepository;
    private final CasePriorityRepository casePriorityRepository;

    public Worker(
            RabbitTemplate rabbitTemplate,
            @Value("${worker.location.processing}") String processingLocation,
            @Value("${worker.location.failed.caching}") String failedCachingPath,
            @Value("${worker.location.failed.transfer}") String failedTransferPath,
            @Value("${worker.location.completed}") String completedPath,
            @Value("${worker.queue.transfer}") String transferQueue,
            List<CaseDataFetcher> caseDataFetchers,
            List<CasePublisher> casePublishers,
            CaseRepository caseRepository,
            CasePriorityRepository casePriorityRepository
    ) {
        this.processingPath = Paths.get(processingLocation);
        this.failedCachingPath = Paths.get(failedCachingPath);
        this.failedTransferPath = Paths.get(failedTransferPath);
        this.completedPath = Paths.get(completedPath);

        this.transferQueue = transferQueue;

        this.rabbitTemplate = rabbitTemplate;

        this.caseDataFetchers = caseDataFetchers;
        this.casePublishers = casePublishers;

        this.caseRepository = caseRepository;
        this.casePriorityRepository = casePriorityRepository;
    }

    @RabbitListener(queues = "${worker.queue.caching}", concurrency = "${worker.rabbitmq.threads.caching}")
    public void processFileMessage(String fileName) {
        log.info("Started caching process for file: {}", fileName);

        if (Files.exists(processingPath.resolve(fileName))) {
            Case caseRecord = initCase(fileName);
            boolean caseDataCachingComplete = cacheCaseData(caseRecord);

            if (caseDataCachingComplete) {
                String caseId = getCaseIdFromFileName(fileName);
                CasePriority casePriority = casePriorityRepository.findByCaseRecord(caseRecord);
                int casePriorityValue = casePriority.getPriorityStatus().getValue();

                log.info("Sending message to broker with case id {} and priority value {}", caseId, casePriorityValue);
                rabbitTemplate.convertAndSend(
                        transferQueue,
                        caseId,
                        message -> {
                            message.getMessageProperties().setPriority(casePriorityValue);
                            return message;
                        }
                );
            } else {
                throw new RuntimeException("Case data caching failed for case id " + caseRecord.getCaseId());
            }
        } else {
            log.error("File {} does not exist in {}", fileName, processingPath);
        }
    }

    @RabbitListener(queues = "${worker.queue.transfer}", concurrency = "${worker.rabbitmq.threads.transfer}")
    public void transferFileMessage(String fileName) {
        String caseId = getCaseIdFromFileName(fileName);
        log.info("Started transfer process for case id: {}", caseId);

        Optional<Case> caseRecordOptional = caseRepository.findById(caseId);
        if (caseRecordOptional.isPresent()) {
            Case caseRecord = caseRecordOptional.get();
            log.info("Found case with id {}, retries {}, and priority {}", caseId, caseRecord.getRetries(), caseRecord.getCasePriority().getPriorityStatus());

            if (Files.exists(processingPath.resolve(caseRecord.getFileName()))) {
                transferCase(caseId);
            } else {
                log.error("File for case id {} does not exist in {}, but case cache has been created", caseId, processingPath);
                caseTransferFailedPermanently(caseRecord);
            }
        } else {
            log.error("Case id {} was not found in the database", caseId);
            caseTransferFailedPermanently(fileName);
        }
        /*
        //If you want to see that data is loaded based on priority enable this delay
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        */

    }

    public void transferCase(String caseId) {
        Optional<Case> caseRecordOptional = caseRepository.findById(caseId);
        if (caseRecordOptional.isPresent()) {
            Case caseRecord = caseRecordOptional.get();
            boolean transfersComplete = true;
            for (CasePublisher casePublisher : casePublishers) {
                transfersComplete = transfersComplete & casePublisher.publish(caseRecord);
            }
            if (transfersComplete) {
                caseRecord.setCaseTransferStatus(CaseTransferStatus.COMPLETED);
                if (caseTransferCompleted(caseRecord.getFileName())) {
                    caseRepository.saveAndFlush(caseRecord);
                    log.info("Case id {} completed successfully", caseId);
                } else {
                    log.error("Case id {} failed to move file {} to completed folder", caseId, caseRecord.getFileName());
                    transfersComplete = false;
                }
            }
            if (!transfersComplete) {
                log.warn("Transfer not complete for case id {}, current retry {}", caseId, caseRecord.getRetries());

                incrementAndSave(caseRecord);

                if (caseRecord.getRetries() > 4) {
                    log.error("Case id {} failed permanently after max retries", caseId);
                    caseTransferFailedPermanently(caseRecord);
                } else {
                    log.info("Case id {} failed transfer, retrying...", caseId);
                    throw new RuntimeException("Transfer failed for caseId: " + caseRecord.getCaseId() + ", retries: " + caseRecord.getRetries());
                }
            }
        } else {
            throw new RuntimeException("Failed to load case with id " + caseId);
        }
    }

    public Case incrementAndSave(Case caseRecord) {
        caseRecord.incrementRetries();
        return caseRepository.saveAndFlush(caseRecord);
    }

    private String getCaseIdFromFileName(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex == -1) ? fileName : fileName.substring(0, lastDotIndex);
    }

    private Case initCase(String fileName) {
        String caseId = getCaseIdFromFileName(fileName);
        Case caseRecord;
        Optional<Case> caseRecordOptional = caseRepository.findById(caseId);
        if (caseRecordOptional.isPresent()) {
            caseRecord = caseRecordOptional.get();
            if (caseRecord.getCaseTransferStatus() != CaseTransferStatus.PROCESSING) {
                throw new RuntimeException("Case is " + caseRecord.getCaseTransferStatus() + ", so no changes can occur to it.");
            }
        } else {
            caseRecord = new Case();
            caseRecord.setCaseId(caseId);
            caseRecord.setFileName(fileName);
            caseRecord = caseRepository.saveAndFlush(caseRecord);
        }
        return caseRecord;
    }

    private boolean cacheCaseData(Case caseRecord) {
        boolean caseDataCachingComplete = true;
        if (caseRecord == null) {
            log.error("Case record is null when trying to call case service");
            caseDataCachingComplete = false;
        }
        for (CaseDataFetcher caseDataFetcher : caseDataFetchers) {
            caseDataCachingComplete = caseDataCachingComplete & caseDataFetcher.syncCaseData(caseRecord);
        }
        if (!caseDataCachingComplete) {
            log.warn("Caching not complete for case id {}, current retry {}", caseRecord.getCaseId(), caseRecord.getRetries());

            incrementAndSave(caseRecord);

            if (caseRecord.getRetries() > 4) {
                log.error("Case id {} caching failed permanently after max retries", caseRecord.getCaseId());
                caseDataCachingFailedPermanently(caseRecord);
            } else {
                log.info("Case id {} caching failed, retrying...", caseRecord.getCaseId());
                throw new RuntimeException("Data caching failed for caseId: " + caseRecord.getCaseId() + ", retries: " + caseRecord.getRetries());
            }
        }
        return caseDataCachingComplete;
    }

    private Case caseDataCachingFailedPermanently(Case caseRecord) {
        log.info("Moving file for case id {} to failed caching folder", caseRecord.getCaseId());

        Path source = processingPath.resolve(caseRecord.getFileName());
        Path target = failedCachingPath.resolve(caseRecord.getFileName());
        try {
            log.info("Moving file from {} to {}", source, target);
            Files.move(source, target);
            caseRecord.setCaseTransferStatus(CaseTransferStatus.FAILED);
            return caseRepository.saveAndFlush(caseRecord);
        } catch (IOException e) {
            log.error("Failed moving file from {} to {}", source, target);
        }
        return null;
    }

    private void caseTransferFailedPermanently(Case caseRecord) {
        if (caseTransferFailedPermanently(caseRecord.getFileName())) {
            caseRecord.setCaseTransferStatus(CaseTransferStatus.FAILED);
            caseRepository.saveAndFlush(caseRecord);
        }
    }

    private boolean caseTransferFailedPermanently(String fileName) {
        Path source = processingPath.resolve(fileName);
        Path target = failedTransferPath.resolve(fileName);
        try {
            log.info("Moving file from {} to {}", source, target);
            Files.move(source, target);
            return true;
        } catch (IOException e) {
            log.error("Failed moving file from {} to {}", source, target);
            return false;
        }
    }

    private boolean caseTransferCompleted(String fileName) {
        Path source = processingPath.resolve(fileName);
        Path target = completedPath.resolve(fileName);
        if (!Files.exists(source) && Files.exists(target)) {
            return true;
        }

        try {
            log.info("Moving file from {} to {}", source, target);
            Files.move(source, target);
            return true;
        } catch (IOException e) {
            log.error("Failed moving file from {} to {}", source, target);
            return false;
        }
    }
}
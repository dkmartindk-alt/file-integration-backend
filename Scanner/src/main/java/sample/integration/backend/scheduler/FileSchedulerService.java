package sample.integration.backend.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.stream.Stream;

@Component
public class FileSchedulerService {
    private final Path inputPath;
    private final Path processingPath;
    private final Path failedPath;
    private static final Logger log = LoggerFactory.getLogger(FileSchedulerService.class);

    private final RabbitTemplate rabbitTemplate;
    private final String queuename;

    public FileSchedulerService(
            @Value("${filescanner.location.input}") String input,
            @Value("${filescanner.location.processing}") String processing,
            @Value("${filescanner.location.failed}") String failed,
            @Value("${filescanner.queue.name}") String queuename,
            RabbitTemplate rabbitTemplate) {
        this.inputPath = Paths.get(input);
        this.processingPath = Paths.get(processing);
        this.failedPath = Paths.get(failed);
        this.queuename = queuename;
        this.rabbitTemplate = rabbitTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        log.info("FileSchedulerService starting..");
        processFiles();
        log.info("FileSchedulerService ended");

    }

    @Scheduled(cron = "${filescanner.cron.expression}")
    public void processScheduledFiles() {
        // Din forretningslogikkens kerne her
        log.info("Cron job running..");
        processFiles();
        log.info("Cron job ended");
    }

    private void processFiles(){
        Path[] filesArray;
        HashMap<Path, Long> fileSizes = new HashMap<>();

        try (Stream<Path> stream = Files.list(this.inputPath)) {
            // 1. Brug Path[]::new
            // 2. Luk streamen automatisk via try-with-resources
            filesArray = stream.toArray(Path[]::new);

            for(Path file : filesArray){
                fileSizes.put(file, Files.size(file));
            }

            Thread.sleep(500);

            for(Path file : filesArray){
                if(fileSizes.get(file) == Files.size(file)){
                    //We waited some time and we know now the file isnt in transfer
                    try {
                        Path targetProcessingPath = processingPath.resolve(file.getFileName());
                        log.info("Moving from {} to {}  with size {}", file, targetProcessingPath, fileSizes.get(file));
                        Files.move(file, targetProcessingPath);
                        String message = file.getFileName().toString();
                        rabbitTemplate.convertAndSend(this.queuename, message);

                        log.info("File moved and message pushed to RabbitMQ: {}", file.getFileName());
                    } catch (FileAlreadyExistsException e) {
                        //We add a time stamp, since multiple files with the same filename could be added.
                        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

                        String originalFileName = file.getFileName().toString();
                        String uniqueFileName = originalFileName + "." + timestamp;

                        Path targetFailedPath = failedPath.resolve(uniqueFileName);

                        Files.move(file, targetFailedPath);
                        log.error("File has already been transfered - Moved {} to {}", file, targetFailedPath);
                    }
                }
                else{
                    log.info("File is being written to disk, ignoring " + file);
                }
            }

        } catch (IOException | InterruptedException e) {
            log.info("Fejl ved læsning af mappe: " + e.getMessage());
        }
    }
}

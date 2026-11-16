package sample.integration.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.CountDownLatch;

/**
 * This application scans a folder defined in application.properties.
 * Files in the folder, are when registreret in the Queue moved to the intransit folder
 * Each file name is unique forever - If a filename is found that has been used in the past it is deleted
 * */

@SpringBootApplication
@EnableScheduling
public class ScannerApplication {

	public static void main(String[] args) throws InterruptedException {
		SpringApplication.run(ScannerApplication.class, args);
		//We dont want the app to end.
		CountDownLatch latch = new CountDownLatch(1);
		latch.await();
	}

}

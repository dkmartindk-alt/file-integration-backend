package sample.integration.backend;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BackendApplication {
	@Value("${worker.queue.transfer}")
	private String workerQueueTransfer;

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public Queue transferQueue() {
		return QueueBuilder.durable(workerQueueTransfer)
				.withArgument("x-max-priority", 10)
				.build();
	}

}

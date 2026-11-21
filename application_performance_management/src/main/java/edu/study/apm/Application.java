package edu.study.apm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    ThreadPoolTaskExecutor platformExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("work-platform-");
        return executor;
    }

    @Bean(destroyMethod = "close")
    SimpleAsyncTaskExecutor virtualExecutor() {
        var executor = new SimpleAsyncTaskExecutor("work-virtual-");
        executor.setVirtualThreads(true);
        executor.setTaskTerminationTimeout(5_000);
        return executor;
    }
}

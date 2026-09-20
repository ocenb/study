package com.example.demo;

import com.example.demo.service.ThreadService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner runThreadDemo(ThreadService threadService) {
        return args -> {
            System.out.println("\n>>> Запуск демонстрации многопоточности при старте приложения...");
            threadService.executeThreadsDemo();
            System.out.println(">>> Демонстрация успешно завершена.\n");
        };
    }
}

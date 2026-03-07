package com.church.qt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class ChurchQtApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChurchQtApplication.class, args);
    }
}
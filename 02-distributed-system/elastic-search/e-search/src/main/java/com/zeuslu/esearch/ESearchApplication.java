package com.zeuslu.esearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class ESearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(ESearchApplication.class, args);
    }
}

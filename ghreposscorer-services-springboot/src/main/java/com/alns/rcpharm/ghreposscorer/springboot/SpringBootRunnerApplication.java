package com.alns.rcpharm.ghreposscorer.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableCaching
@EnableScheduling
public class SpringBootRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootRunnerApplication.class, args);
    }
}

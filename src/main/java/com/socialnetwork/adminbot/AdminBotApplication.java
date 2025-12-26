package com.socialnetwork.adminbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class AdminBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminBotApplication.class, args);
    }

}

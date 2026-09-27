package com.example.backendb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class BackendBApplication {

    private static final Logger log = LoggerFactory.getLogger(BackendBApplication.class);

    @Value("${spring.application.name}")
    private String appName;

    @Value("${app.env}")
    private String appEnv;

    @Value("${server.port}")
    private String serverPort;

    public static void main(String[] args) {
        SpringApplication.run(BackendBApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartup() {
        log.info("Starting {} (env={}, port={})", appName, appEnv, serverPort);
    }
}

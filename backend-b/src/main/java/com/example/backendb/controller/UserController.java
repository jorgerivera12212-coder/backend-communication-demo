package com.example.backendb.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Value("${spring.application.name}")
    private String serviceName;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "ok",
            "service", serviceName
        );
    }

    @GetMapping("/user")
    public Map<String, Object> getUser() {
        log.info("GET /user");
        return Map.of(
            "id", 1,
            "name", "John Doe",
            "email", "john@example.com"
        );
    }
}

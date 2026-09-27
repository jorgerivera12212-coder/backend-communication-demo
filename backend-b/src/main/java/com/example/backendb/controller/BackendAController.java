package com.example.backendb.controller;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Comunicación en sentido inverso: backend-b llama a backend-a.
 * Solo consulta /health de backend-a, que no vuelve a llamar a backend-b (sin bucles).
 */
@RestController
public class BackendAController {

    private static final Logger log = LoggerFactory.getLogger(BackendAController.class);

    private final String backendAUrl;
    private final RestClient restClient;

    public BackendAController(
        @Value("${backend-a.url}") String backendAUrl,
        @Value("${backend-a.timeout-seconds}") double timeoutSeconds
    ) {
        Duration timeout = Duration.ofMillis((long) (timeoutSeconds * 1000));

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.backendAUrl = backendAUrl;
        this.restClient = RestClient.builder()
            .baseUrl(backendAUrl)
            .requestFactory(requestFactory)
            .build();
    }

    @GetMapping("/backend-a-status")
    public Map<String, Object> backendAStatus() {
        log.info("GET /backend-a-status -> calling {}/health", backendAUrl);

        try {
            Map<String, Object> backendAHealth = restClient.get()
                .uri("/health")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            // 200 sin cuerpo: body() devuelve null (y Map.of no admite nulos)
            if (backendAHealth == null) {
                log.error("Error communicating with backend-a: empty response body");
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Error communicating with backend-a"
                );
            }

            return Map.of(
                "message", "Status retrieved from backend-a",
                "backendA", backendAHealth
            );

        } catch (RestClientException e) {
            log.error("Error communicating with backend-a: {}", e.getMessage());
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Error communicating with backend-a"
            );
        }
    }
}

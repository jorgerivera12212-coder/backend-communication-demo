package com.example.backendb.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpServer;

/**
 * No necesita backend-a levantado: se sustituye por un servidor HTTP falso del propio JDK.
 */
@WebMvcTest(BackendAController.class)
class BackendAControllerTest {

    private static volatile int fakeStatus = 200;

    private static final HttpServer fakeBackendA = startFakeBackendA();

    private static HttpServer startFakeBackendA() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/health", exchange -> {
                byte[] body = "{\"status\":\"ok\",\"service\":\"backend-a\"}"
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(fakeStatus, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void backendAProperties(DynamicPropertyRegistry registry) {
        registry.add("backend-a.url",
            () -> "http://localhost:" + fakeBackendA.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeBackendA() {
        fakeBackendA.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetFakeStatus() {
        fakeStatus = 200;
    }

    @Test
    void returnsHealthFromBackendA() throws Exception {
        mockMvc.perform(get("/backend-a-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Status retrieved from backend-a"))
            .andExpect(jsonPath("$.backendA.status").value("ok"))
            .andExpect(jsonPath("$.backendA.service").value("backend-a"));
    }

    @Test
    void returns502WhenBackendAFails() throws Exception {
        fakeStatus = 500;

        mockMvc.perform(get("/backend-a-status"))
            .andExpect(status().isBadGateway());
    }
}

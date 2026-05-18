package com.myplans.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void givenInvalidEmail_whenRegister_thenReturn400BadRequest() {
        String jsonBody = """
                {
                  "email": "correo-malo-sin-arroba",
                  "password": "123"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().get("message"));
    }

    @Test
    public void givenValidNewUser_whenRegister_thenReturn201Created() {
        String jsonBody = """
                {
                  "email": "test.integration@myplans.com",
                  "password": "PasswordFuerte123!",
                  "nombreCompleto": "Test Integration"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", request, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Usuario registrado exitosamente", response.getBody().get("message"));
    }

    @Test
    public void givenDuplicateEmail_whenRegister_thenReturn409WithMessage() {
        String jsonBody = """
                {
                  "email": "duplicado@myplans.com",
                  "password": "PasswordFuerte123!",
                  "nombreCompleto": "Usuario Duplicado"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<Map> first = restTemplate.postForEntity("/api/auth/register", request, Map.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        ResponseEntity<Map> second = restTemplate.postForEntity("/api/auth/register", request, Map.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        String message = (String) second.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("correo"),
                "El mensaje debe explicar que el correo está duplicado, pero fue: " + message);
    }

    @Test
    public void givenWeakPassword_whenRegister_thenReturn400WithSpecificMessage() {
        String jsonBody = """
                {
                  "email": "weakpass@myplans.com",
                  "password": "todominus",
                  "nombreCompleto": "Usuario Test"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", request, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("mayúscula")
                        || message.toLowerCase().contains("contraseña"),
                "El mensaje debe ser específico sobre la regla incumplida, pero fue: " + message);
    }

    @Test
    public void givenNoToken_whenAccessProtectedEndpoint_thenReturn401WithMessage() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me",
                org.springframework.http.HttpMethod.GET,
                request,
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody().get("message"));
    }

    @Test
    public void givenInvalidToken_whenAccessProtectedEndpoint_thenReturn401WithSessionExpiredMessage() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("token-falso-no-firmado");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me",
                org.springframework.http.HttpMethod.GET,
                request,
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("token")
                        || message.toLowerCase().contains("sesión")
                        || message.toLowerCase().contains("iniciar sesión"),
                "El mensaje debe indicar problema de token/sesión, pero fue: " + message);
    }

    @Test
    public void whenLogout_thenReturn200() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/logout", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Sesión cerrada correctamente", response.getBody().get("message"));
    }
}

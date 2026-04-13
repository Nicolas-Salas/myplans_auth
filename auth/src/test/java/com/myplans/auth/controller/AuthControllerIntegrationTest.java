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
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Levantamos el servidor REAL en un puerto aleatorio para pruebas exactas a producción
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev") // Mantenemos H2 en memoria
public class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void givenInvalidEmail_whenRegister_thenReturn400BadRequest() {
        // Arrange: Preparamos JSON intencionalmente defectuoso
        String jsonBody = """
                {
                  "email": "correo-malo-sin-arroba",
                  "password": "123"
                }
                """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        // Act: Hacemos la petición HTTP POST real
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", request, Map.class);

        // Assert: Validamos las defensas (Debe ser 400 Bad Request)
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().get("message"));
    }

    @Test
    public void givenValidNewUser_whenRegister_thenReturn201Created() {
        // Arrange: Preparamos un usuario válido
        String jsonBody = """
                {
                  "email": "test.integration@myplans.com",
                  "password": "PasswordFuerte123!"
                }
                """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        // Act: Hacemos la petición HTTP POST real
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", request, Map.class);

        // Assert: Validamos el éxito (Debe ser 201 Created)
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Usuario registrado exitosamente", response.getBody().get("message"));
    }
}
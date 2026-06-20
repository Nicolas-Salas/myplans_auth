package com.myplans.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AdminControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;

    @BeforeEach
    public void setUp() {
        String loginJson = """
                {
                  "email": "admin@myplans.com",
                  "password": "PasswordSegura123!"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(loginJson, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login", request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            adminToken = (String) response.getBody().get("token");
        }
    }

    @Test
    public void givenAdminToken_whenCreateUser_thenReturn201Created() {
        String newUserJson = """
                {
                  "email": "nuevo.auditor.mina@myplans.com",
                  "password": "PasswordFuerte123!",
                  "nombreCompleto": "Auditor de Mina",
                  "roles": ["ROLE_AUDITOR"]
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        HttpEntity<String> request = new HttpEntity<>(newUserJson, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/admin/users", request, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
    }

    @Test
    public void givenAdminToken_whenUpdateUserWithBody_thenReturn200OK() {
        String updateJson = """
                {
                  "email": "admin.actualizado@myplans.com"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        HttpEntity<String> request = new HttpEntity<>(updateJson, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/admin/users/1",
                HttpMethod.PUT,
                request,
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Usuario actualizado correctamente", response.getBody().get("message"));
    }

    @Test
    public void givenAdminToken_whenUpdateUserWithEmptyBody_thenReturn400WithMessage() {
        String emptyJson = "{}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        HttpEntity<String> request = new HttpEntity<>(emptyJson, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/admin/users/1",
                HttpMethod.PUT,
                request,
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("campo"),
                "El mensaje debe indicar qué campos se pueden enviar, pero fue: " + message);
    }

    @Test
    public void givenAdminToken_whenCreateUserWithDuplicateEmail_thenReturn409() {
        String duplicateJson = """
                {
                  "email": "admin@myplans.com",
                  "password": "PasswordFuerte123!",
                  "nombreCompleto": "Otro Admin",
                  "roles": ["ROLE_AUDITOR"]
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        HttpEntity<String> request = new HttpEntity<>(duplicateJson, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/admin/users", request, Map.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody().get("message"));
    }
}

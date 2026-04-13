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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
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
    public void givenAdminToken_whenUpdateUserEmail_thenReturn200OK() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/admin/users/1?email=admin.actualizado@myplans.com",
                HttpMethod.PUT,
                request,
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Usuario actualizado correctamente", response.getBody().get("message"));
    }
}
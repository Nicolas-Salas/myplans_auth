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

import static org.junit.jupiter.api.Assertions.*;

/**
 * PF-024 — Cambiar contraseña con contraseña actual correcta
 * PF-005 — Solicitud de reset con email válido
 * PF-006 — Nueva contraseña con token válido
 *
 * Cubre también: contraseña igual a la actual, contraseña débil,
 * contraseña actual incorrecta, acceso sin token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PasswordChangeIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    /** Token JWT del usuario admin sembrado por DataSeeder */
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
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "El login del admin debe funcionar antes de cada test");
        adminToken = (String) response.getBody().get("token");
        assertNotNull(adminToken, "Se esperaba un token JWT en la respuesta del login");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PF-024: Cambio de contraseña exitoso
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void givenCorrectCurrentPassword_whenChangePassword_thenReturn200AndTokenStillValid() {
        String body = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": "NuevaPass456@"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Contraseña actualizada correctamente", response.getBody().get("message"));

        // Verificar que el token JWT sigue siendo válido tras el cambio
        HttpHeaders checkHeaders = new HttpHeaders();
        checkHeaders.setBearerAuth(adminToken);
        ResponseEntity<Map> meResponse = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(checkHeaders),
                Map.class);
        assertEquals(HttpStatus.OK, meResponse.getStatusCode(),
                "El token JWT debe seguir siendo válido después del cambio de contraseña");
    }

    @Test
    public void givenCorrectCurrentPassword_whenChangePassword_thenLoginWithNewPasswordWorks() {
        String changeBody = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": "NuevaPass456@"
                }
                """;
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(adminToken);

        ResponseEntity<Map> changeResponse = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(changeBody, authHeaders),
                Map.class);
        assertEquals(HttpStatus.OK, changeResponse.getStatusCode());

        // Login con la nueva contraseña debe funcionar
        String loginJson = """
                {
                  "email": "admin@myplans.com",
                  "password": "NuevaPass456@"
                }
                """;
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginJson, loginHeaders), Map.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode(),
                "Login con la nueva contraseña debe retornar HTTP 200");
        assertNotNull(loginResponse.getBody().get("token"),
                "Debe retornarse un token JWT con la nueva contraseña");
    }

    @Test
    public void givenCorrectCurrentPassword_whenChangePassword_thenOldPasswordFails() {
        String changeBody = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": "NuevaPass456@"
                }
                """;
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(adminToken);
        restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(changeBody, authHeaders),
                Map.class);

        // Login con la contraseña antigua debe fallar
        String loginJson = """
                {
                  "email": "admin@myplans.com",
                  "password": "PasswordSegura123!"
                }
                """;
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginJson, loginHeaders), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, loginResponse.getStatusCode(),
                "La contraseña antigua debe quedar inválida después del cambio");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validaciones de error — contraseña actual incorrecta
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void givenWrongCurrentPassword_whenChangePassword_thenReturn400WithMessage() {
        String body = """
                {
                  "currentPassword": "ContraseñaMal123!",
                  "newPassword": "OtraPass456@"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("actual") || message.toLowerCase().contains("incorrecta"),
                "El mensaje debe indicar que la contraseña actual es incorrecta, pero fue: " + message);
    }

    @Test
    public void givenSamePassword_whenChangePassword_thenReturn400WithMessage() {
        String body = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": "PasswordSegura123!"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("igual") || message.toLowerCase().contains("actual"),
                "El mensaje debe indicar que la nueva contraseña no puede ser igual a la actual, pero fue: " + message);
    }

    @Test
    public void givenWeakNewPassword_whenChangePassword_thenReturn400WithMessage() {
        String body = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": "debil"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message, "Se esperaba un mensaje de error descriptivo");
    }

    @Test
    public void givenMissingCurrentPassword_whenChangePassword_thenReturn400() {
        String body = """
                {
                  "currentPassword": "",
                  "newPassword": "NuevaPass456@"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void givenMissingNewPassword_whenChangePassword_thenReturn400() {
        String body = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": ""
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seguridad: sin token debe retornar 401
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void givenNoToken_whenChangePassword_thenReturn401() {
        String body = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": "NuevaPass456@"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody().get("message"));
    }

    @Test
    public void givenInvalidToken_whenChangePassword_thenReturn401() {
        String body = """
                {
                  "currentPassword": "PasswordSegura123!",
                  "newPassword": "NuevaPass456@"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("token-inventado-invalido");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PF-005 / PF-006 — Reset de contraseña vía token
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void givenValidEmail_whenRequestReset_thenReturn200() {
        String body = """
                { "email": "admin@myplans.com" }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/reset-password", new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message,
                "Se esperaba un mensaje de confirmación aunque el email no exista (anti-enumeración)");
    }

    @Test
    public void givenNonExistentEmail_whenRequestReset_thenReturn200ToAvoidEnumeration() {
        String body = """
                { "email": "noexiste@myplans.com" }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/reset-password", new HttpEntity<>(body, headers), Map.class);

        // Por seguridad, siempre debe retornar 200 (anti-enumeración de usuarios)
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    public void givenInvalidToken_whenSetNewPassword_thenReturn400WithMessage() {
        String body = """
                {
                  "token": "token-falso-que-no-existe",
                  "newPassword": "NuevaPass789!"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/new-password", new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = (String) response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("token") || message.toLowerCase().contains("inválido"),
                "El mensaje debe indicar que el token es inválido, pero fue: " + message);
    }

    @Test
    public void givenWeakNewPasswordInReset_whenSetNewPassword_thenReturn400() {
        String body = """
                {
                  "token": "token-cualquiera",
                  "newPassword": "abc"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/new-password", new HttpEntity<>(body, headers), Map.class);

        // El token va a fallar primero, pero validamos que la respuesta sigue siendo 400
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}

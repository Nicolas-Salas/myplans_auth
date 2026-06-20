package com.myplans.auth.controller;

import com.myplans.auth.entity.PasswordResetToken;
import com.myplans.auth.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PF-006 — Flujo completo de reset de contraseña con token real.
 *
 * Como no hay SMTP, el token se extrae directamente del repositorio H2
 * para simular el flujo que un usuario haría con el enlace del email.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PasswordResetFlowTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> login(String email, String password) {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        return restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(body, jsonHeaders()), Map.class);
    }

    private ResponseEntity<Map> solicitarReset(String email) {
        String body = String.format("{\"email\":\"%s\"}", email);
        return restTemplate.postForEntity(
                "/api/auth/reset-password", new HttpEntity<>(body, jsonHeaders()), Map.class);
    }

    private ResponseEntity<Map> usarToken(String token, String nuevaPassword) {
        String body = String.format(
                "{\"token\":\"%s\",\"newPassword\":\"%s\"}", token, nuevaPassword);
        return restTemplate.postForEntity(
                "/api/auth/new-password", new HttpEntity<>(body, jsonHeaders()), Map.class);
    }

    @Test
    void givenValidEmail_whenRequestReset_thenTokenGeneratedInDb() {
        ResponseEntity<Map> resetResp = solicitarReset("admin@myplans.com");
        assertEquals(HttpStatus.OK, resetResp.getStatusCode());

        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertFalse(tokens.isEmpty(),
                "Debe haberse generado al menos un token de reset en la BD");
        PasswordResetToken token = tokens.get(0);
        assertNotNull(token.getToken(), "El token no debe ser nulo");
        assertFalse(token.isExpired(), "El token recién generado no debe estar expirado");
        assertEquals("admin@myplans.com", token.getUser().getEmail());
    }

    @Test
    void givenValidToken_whenUseIt_thenReturn200AndPasswordUpdated() {
        solicitarReset("admin@myplans.com");

        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertFalse(tokens.isEmpty(), "Debe haber un token en BD");
        String resetToken = tokens.get(0).getToken();

        ResponseEntity<Map> useResp = usarToken(resetToken, "NuevaPassReset999!");
        assertEquals(HttpStatus.OK, useResp.getStatusCode(),
                "El uso del token válido debe retornar 200");
        assertEquals("Contraseña actualizada exitosamente",
                useResp.getBody().get("message"));
    }

    @Test
    void givenValidToken_whenUsed_thenLoginWithNewPasswordWorks() {
        solicitarReset("admin@myplans.com");
        String resetToken = tokenRepository.findAll().get(0).getToken();

        ResponseEntity<Map> useResp = usarToken(resetToken, "NuevaPassReset999!");
        assertEquals(HttpStatus.OK, useResp.getStatusCode());

        ResponseEntity<Map> loginNew = login("admin@myplans.com", "NuevaPassReset999!");
        assertEquals(HttpStatus.OK, loginNew.getStatusCode(),
                "El login con la nueva contraseña debe funcionar");
        assertNotNull(loginNew.getBody().get("token"),
                "Debe retornarse un token JWT con la nueva contraseña");
    }

    @Test
    void givenValidToken_whenUsed_thenOldPasswordFails() {
        solicitarReset("admin@myplans.com");
        String resetToken = tokenRepository.findAll().get(0).getToken();
        usarToken(resetToken, "NuevaPassReset999!");

        ResponseEntity<Map> loginOld = login("admin@myplans.com", "PasswordSegura123!");
        assertEquals(HttpStatus.UNAUTHORIZED, loginOld.getStatusCode(),
                "La contraseña antigua debe quedar inválida tras el reset");
    }

    @Test
    void givenValidToken_whenUsedTwice_thenSecondUseReturn400() {
        solicitarReset("admin@myplans.com");
        String resetToken = tokenRepository.findAll().get(0).getToken();

        ResponseEntity<Map> first = usarToken(resetToken, "PrimeraPass999!");
        assertEquals(HttpStatus.OK, first.getStatusCode());

        ResponseEntity<Map> second = usarToken(resetToken, "SegundaPass888!");
        assertEquals(HttpStatus.BAD_REQUEST, second.getStatusCode(),
                "Un token de reset solo puede usarse una vez");
        String msg = (String) second.getBody().get("message");
        assertTrue(msg.toLowerCase().contains("token") || msg.toLowerCase().contains("inválido"),
                "El mensaje debe indicar que el token es inválido, fue: " + msg);
    }

    @Disabled("En H2/test el email service lanza excepción → TX rollback → token no se invalida. Requiere SMTP real o mock de emailService.")
    @Test
    void givenResetDone_whenSecondResetRequested_thenPreviousTokenInvalidated() {
        solicitarReset("admin@myplans.com");
        String primeraToken = tokenRepository.findAll().get(0).getToken();

        solicitarReset("admin@myplans.com");
        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertEquals(1, tokens.size(),
                "Debe haber exactamente 1 token activo (el anterior es eliminado al pedir uno nuevo)");

        ResponseEntity<Map> useViejo = usarToken(primeraToken, "NuevaPass999!");
        assertEquals(HttpStatus.BAD_REQUEST, useViejo.getStatusCode(),
                "El token anterior debe quedar invalidado");
    }

    @Test
    void givenWeakNewPassword_whenUseResetToken_thenReturn400() {
        solicitarReset("admin@myplans.com");
        String resetToken = tokenRepository.findAll().get(0).getToken();

        ResponseEntity<Map> resp = usarToken(resetToken, "weak");
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
                "La nueva contraseña debe cumplir las reglas de complejidad");
    }
}

package com.myplans.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PF-021 — Admin activa/desactiva usuario (toggle-status).
 *
 * Flujo:
 *  1. Admin crea un usuario nuevo (activo por defecto).
 *  2. Admin desactiva el usuario → PATCH /api/admin/users/{id}/toggle-status → 200.
 *  3. Usuario intenta hacer login → 403 "cuenta debe ser activada".
 *  4. Admin reactiva → 200.
 *  5. Usuario hace login exitosamente → 200.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminToggleStatusTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        String loginJson = """
                {
                  "email": "admin@myplans.com",
                  "password": "PasswordSegura123!"
                }
                """;
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginJson, h), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode(), "Login de admin debe funcionar");
        adminToken = (String) resp.getBody().get("token");
        assertNotNull(adminToken);
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(adminToken);
        return h;
    }

    /** Crea un usuario y retorna su id. */
    private Long crearUsuario(String email, String password) {
        String body = String.format("""
                {
                  "email": "%s",
                  "password": "%s",
                  "nombreCompleto": "Usuario Test Toggle",
                  "roles": ["ROLE_USER"]
                }
                """, email, password);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/admin/users", new HttpEntity<>(body, adminHeaders()), Map.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode(),
                "El admin debe poder crear el usuario de prueba");
        return ((Number) resp.getBody().get("id")).longValue();
    }

    private ResponseEntity<Map> hacerLogin(String email, String password) {
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(body, h), Map.class);
    }

    private ResponseEntity<Map> toggleStatus(Long idUsuario) {
        return restTemplate.exchange(
                "/api/admin/users/" + idUsuario + "/toggle-status",
                HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders()),
                Map.class);
    }

    @Test
    void givenActiveUser_whenToggleOff_thenReturn200() {
        Long id = crearUsuario("toggle1@test.com", "PasswordFuerte123!");

        ResponseEntity<Map> resp = toggleStatus(id);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String msg = (String) resp.getBody().get("message");
        assertNotNull(msg);
        assertTrue(msg.toLowerCase().contains("estado") || msg.toLowerCase().contains("usuario"),
                "El mensaje debe confirmar el cambio de estado, fue: " + msg);
    }

    @Test
    void givenActiveUser_whenToggleOff_thenLoginReturns403() {
        Long id = crearUsuario("toggle2@test.com", "PasswordFuerte123!");

        ResponseEntity<Map> toggle = toggleStatus(id);
        assertEquals(HttpStatus.OK, toggle.getStatusCode());

        ResponseEntity<Map> loginResp = hacerLogin("toggle2@test.com", "PasswordFuerte123!");
        assertEquals(HttpStatus.FORBIDDEN, loginResp.getStatusCode(),
                "Un usuario desactivado no debe poder hacer login");
        String msg = (String) loginResp.getBody().get("message");
        assertNotNull(msg);
        assertTrue(
                msg.toLowerCase().contains("activad") || msg.toLowerCase().contains("administrador"),
                "El mensaje debe indicar que la cuenta está desactivada, fue: " + msg);
    }

    @Test
    void givenDeactivatedUser_whenToggleBackOn_thenLoginSucceeds() {
        Long id = crearUsuario("toggle3@test.com", "PasswordFuerte123!");

        toggleStatus(id);

        ResponseEntity<Map> loginFail = hacerLogin("toggle3@test.com", "PasswordFuerte123!");
        assertEquals(HttpStatus.FORBIDDEN, loginFail.getStatusCode());

        ResponseEntity<Map> reactivar = toggleStatus(id);
        assertEquals(HttpStatus.OK, reactivar.getStatusCode());

        ResponseEntity<Map> loginOk = hacerLogin("toggle3@test.com", "PasswordFuerte123!");
        assertEquals(HttpStatus.OK, loginOk.getStatusCode(),
                "Tras reactivar, el usuario debe poder hacer login");
        assertNotNull(loginOk.getBody().get("token"),
                "El login exitoso debe retornar un token JWT");
    }

    @Test
    void givenDoubleToogle_whenToggleOff_thenToggleOn_thenLoginSucceeds() {
        Long id = crearUsuario("toggle4@test.com", "PasswordFuerte123!");
        toggleStatus(id);
        toggleStatus(id);

        ResponseEntity<Map> login = hacerLogin("toggle4@test.com", "PasswordFuerte123!");
        assertEquals(HttpStatus.OK, login.getStatusCode(),
                "Doble toggle debe dejar al usuario activo nuevamente");
    }

    @Test
    void givenNoToken_whenToggleStatus_thenReturn401() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/users/1/toggle-status",
                HttpMethod.PATCH,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void givenRoleUser_whenToggleStatus_thenReturn403() {
        Long id = crearUsuario("target@test.com", "PasswordFuerte123!");

        Long userId = crearUsuario("attacker@test.com", "PasswordFuerte123!");
        ResponseEntity<Map> userLogin = hacerLogin("attacker@test.com", "PasswordFuerte123!");
        assertEquals(HttpStatus.OK, userLogin.getStatusCode());
        String userToken = (String) userLogin.getBody().get("token");

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(userToken);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/admin/users/" + id + "/toggle-status",
                HttpMethod.PATCH,
                new HttpEntity<>(h),
                Map.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "ROLE_USER no puede cambiar el estado de otros usuarios");
    }

    @Test
    void givenUserNotFound_whenToggleStatus_thenReturn404() {
        ResponseEntity<Map> resp = toggleStatus(99999L);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}

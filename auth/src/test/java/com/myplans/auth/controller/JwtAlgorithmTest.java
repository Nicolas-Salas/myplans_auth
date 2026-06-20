package com.myplans.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PS-014 — El JWT generado por Auth usa el algoritmo HS384.
 *
 * Verifica decodificando el header Base64 del token sin validar firma —
 * solo para inspeccionar el campo "alg".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class JwtAlgorithmTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private String loginYObtenerToken() {
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
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String token = (String) resp.getBody().get("token");
        assertNotNull(token, "El login debe retornar un token");
        return token;
    }

    /** Decodifica el header del JWT (primer segmento Base64url) sin verificar firma. */
    private String decodificarHeaderJwt(String token) {
        String headerB64 = token.split("\\.")[0];
        String padded = headerB64.replace('-', '+').replace('_', '/');
        switch (padded.length() % 4) {
            case 2: padded += "=="; break;
            case 3: padded += "=";  break;
        }
        return new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8);
    }

    @Test
    void givenLoginSuccessful_whenDecodeJwtHeader_thenAlgorithmIsHS384() {
        String token  = loginYObtenerToken();
        String header = decodificarHeaderJwt(token);

        assertTrue(header.contains("HS384"),
                "El JWT debe usar el algoritmo HS384, header decodificado: " + header);
    }

    @Test
    void givenLoginSuccessful_whenDecodeJwtHeader_thenHeaderIsValidJson() {
        String token  = loginYObtenerToken();
        String header = decodificarHeaderJwt(token);

        assertTrue(header.startsWith("{") && header.endsWith("}"),
                "El header JWT debe ser JSON válido, header decodificado: " + header);
        assertTrue(header.contains("alg"),
                "El header JWT debe contener el campo 'alg', header decodificado: " + header);
    }

    @Test
    void givenLoginSuccessful_whenDecodeJwtHeader_thenNotHS256() {
        String token  = loginYObtenerToken();
        String header = decodificarHeaderJwt(token);

        assertFalse(header.contains("HS256"),
                "El JWT NO debe usar HS256 (algoritmo más débil que HS384), header: " + header);
    }

    @Test
    void givenJwtStructure_whenSplit_thenHasThreeParts() {
        String token = loginYObtenerToken();
        String[] parts = token.split("\\.");

        assertEquals(3, parts.length,
                "Un JWT válido debe tener 3 partes separadas por punto (header.payload.signature)");
        assertTrue(parts[0].length() > 0, "El header no debe estar vacío");
        assertTrue(parts[1].length() > 0, "El payload no debe estar vacío");
        assertTrue(parts[2].length() > 0, "La firma no debe estar vacía");
    }

    @Test
    void givenJwtPayload_whenDecoded_thenContainsEmailAndRoles() {
        String token   = loginYObtenerToken();
        String[] parts = token.split("\\.");
        String payloadB64 = parts[1].replace('-', '+').replace('_', '/');
        switch (payloadB64.length() % 4) {
            case 2: payloadB64 += "=="; break;
            case 3: payloadB64 += "=";  break;
        }
        String payload = new String(Base64.getDecoder().decode(payloadB64), StandardCharsets.UTF_8);

        assertTrue(payload.contains("admin@myplans.com"),
                "El payload debe contener el email del usuario");
        assertTrue(payload.contains("ROLE_ADMIN") || payload.contains("roles"),
                "El payload debe contener información de roles");
    }
}

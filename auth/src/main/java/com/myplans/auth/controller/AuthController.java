package com.myplans.auth.controller;

import com.myplans.auth.dto.AuthResponseDTO;
import com.myplans.auth.dto.LoginRequestDTO;
import com.myplans.auth.dto.UserRegisterDTO;
import com.myplans.auth.service.AuthService;
import com.myplans.auth.dto.PasswordResetRequestDTO;
import com.myplans.auth.dto.NewPasswordDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
// --- ANOTACIÓN DE SWAGGER PARA LA CLASE ---
@Tag(name = "Autenticación", description = "Endpoints públicos para el registro, inicio de sesión y recuperación de credenciales. No requieren token JWT.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Registrar nuevo usuario", description = "Crea una nueva cuenta de usuario en el sistema. Asigna el rol básico (ROLE_USER) por defecto si no se especifica otro.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación en los datos enviados"),
        @ApiResponse(responseCode = "409", description = "Conflicto: El correo ya está registrado")
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerUser(
            @Parameter(description = "Datos requeridos para el registro") @Valid @RequestBody UserRegisterDTO registerDTO) {
        try {
            authService.registerUser(registerDTO);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Usuario registrado exitosamente");
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
        }
    }

    @Operation(summary = "Iniciar sesión", description = "Autentica a un usuario mediante correo y contraseña. Retorna un Token JWT válido para acceder a las rutas protegidas.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Autenticación exitosa, retorna el token JWT"),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas (Unauthorized)"),
        @ApiResponse(responseCode = "403", description = "Cuenta pendiente de activación (Forbidden)"),
        @ApiResponse(responseCode = "400", description = "Cuerpo de la petición mal formado")
    })
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(
            @Parameter(description = "Credenciales de acceso") @Valid @RequestBody LoginRequestDTO loginRequest) {
        try {
            AuthResponseDTO authResponse = authService.authenticateUser(loginRequest);
            return ResponseEntity.ok(authResponse);
            
        } catch (DisabledException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Cuenta pendiente de activación");
            response.put("message", "Tu cuenta ha sido registrada con éxito, pero debe ser activada por un administrador para poder ingresar.");
            response.put("status", 403);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            
        } catch (BadCredentialsException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Credenciales inválidas");
            response.put("message", "El correo o la contraseña son incorrectos.");
            response.put("status", 401);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @Operation(summary = "Solicitar recuperación de contraseña", description = "Genera un token de un solo uso (TTL 15 min) y simula el envío de un correo electrónico con el enlace de recuperación. Por seguridad, siempre retorna un 200 OK para evitar enumeración de usuarios.")
    @ApiResponse(responseCode = "200", description = "Solicitud procesada correctamente")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> requestReset(
            @Parameter(description = "Correo electrónico del usuario") @Valid @RequestBody PasswordResetRequestDTO requestDTO) {
        try {
            authService.requestPasswordReset(requestDTO.getEmail());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Si el correo existe, se ha enviado un enlace de recuperación.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Si el correo existe, se ha enviado un enlace de recuperación.");
            return ResponseEntity.ok(response);
        }
    }

    @Operation(summary = "Establecer nueva contraseña", description = "Recibe un token válido de recuperación y una nueva contraseña. Encripta la nueva contraseña y destruye el token para prevenir su reutilización.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Token inválido, expirado o contraseña no cumple con los requisitos")
    })
    @PostMapping("/new-password")
    public ResponseEntity<Map<String, String>> saveNewPassword(
            @Parameter(description = "Token de seguridad y nueva contraseña") @Valid @RequestBody NewPasswordDTO requestDTO) {
        try {
            authService.resetPassword(requestDTO.getToken(), requestDTO.getNewPassword());
            Map<String, String> response = new HashMap<>();
            response.put("message", "Contraseña actualizada exitosamente");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }
    }
}
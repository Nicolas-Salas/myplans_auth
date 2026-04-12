package com.myplans.auth.controller;

import com.myplans.auth.dto.AuthResponseDTO;
import com.myplans.auth.dto.LoginRequestDTO;
import com.myplans.auth.dto.UserRegisterDTO;
import com.myplans.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.myplans.auth.dto.PasswordResetRequestDTO;
import com.myplans.auth.dto.NewPasswordDTO;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerUser(@Valid @RequestBody UserRegisterDTO registerDTO) {
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

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> authenticateUser(@Valid @RequestBody LoginRequestDTO loginRequest) {
        AuthResponseDTO authResponse = authService.authenticateUser(loginRequest);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> requestReset(@Valid @RequestBody PasswordResetRequestDTO requestDTO) {
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

    @PostMapping("/new-password")
    public ResponseEntity<Map<String, String>> saveNewPassword(@Valid @RequestBody NewPasswordDTO requestDTO) {
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
package com.myplans.auth.controller;

import com.myplans.auth.dto.UserRegisterDTO;
import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.User;
import com.myplans.auth.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // --- ENDPOINTS DE USUARIOS ---
    
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/users/{id}/toggle-status")
    public ResponseEntity<Map<String, String>> toggleUserStatus(@PathVariable Long id) {
        userService.toggleUserStatus(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Estado del usuario actualizado");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/roles/{roleName}")
    public ResponseEntity<Map<String, String>> assignRole(@PathVariable Long userId, @PathVariable String roleName) {
        userService.assignRoleToUser(userId, roleName);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rol asignado correctamente");
        return ResponseEntity.ok(response);
    }

    // --- ENDPOINTS DE ROLES ---

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(userService.getAllRoles());
    }

    @PostMapping("/roles")
    public ResponseEntity<Map<String, String>> createRole(@RequestParam String roleName) {
        try {
            userService.createRole(roleName);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Rol creado exitosamente");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // Crear usuario
    @PostMapping("/users")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserRegisterDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.adminCreateUser(dto));
    }

    // Editar información básica
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> updateEmail(@PathVariable Long id, @RequestParam String email) {
        userService.updateUserEmail(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Usuario actualizado correctamente");
        return ResponseEntity.ok(response);
    }

    // Quitar un permiso específico
    @DeleteMapping("/users/{userId}/roles/{roleName}")
    public ResponseEntity<Map<String, String>> revokeRole(@PathVariable Long userId, @PathVariable String roleName) {
        userService.revokeRoleFromUser(userId, roleName);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rol " + roleName + " revocado al usuario " + userId);
        return ResponseEntity.ok(response);
    }
}
package com.myplans.auth.controller;

import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.User;
import com.myplans.auth.service.UserService;
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
}
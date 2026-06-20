package com.myplans.auth.controller;

import com.myplans.auth.dto.AdminUpdateDTO;
import com.myplans.auth.dto.UserRegisterDTO;
import com.myplans.auth.dto.PermissionRequestDTO;
import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.User;
import com.myplans.auth.entity.Modulo;
import com.myplans.auth.entity.Acceso;
import com.myplans.auth.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Administración (IAM)", description = "Endpoints protegidos para la gestión de ciclo de vida de usuarios, roles y privilegios. Requiere Token JWT con ROLE_ADMIN.")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // --- ENDPOINTS DE USUARIOS ---

    @Operation(summary = "Listar todos los usuarios")
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Mapa id→nombre de todos los usuarios", description = "Accesible a AUDITOR y ADMIN para resolver IDs en vistas de auditoría. Incluye usuarios inactivos para preservar historial.")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @GetMapping("/users/nombres")
    public ResponseEntity<List<Map<String, Object>>> getUserNombres() {
        return ResponseEntity.ok(
            userService.getAllUsers().stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("nombre", u.getNombreCompleto() != null ? u.getNombreCompleto() : "Usuario " + u.getId());
                    return m;
                })
                .toList()
        );
    }

    @Operation(summary = "Activar/Desactivar Usuario (Soft Delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del usuario actualizado con éxito"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @PatchMapping("/users/{id}/toggle-status")
    public ResponseEntity<Map<String, String>> toggleUserStatus(
            @Parameter(description = "ID único del usuario") @PathVariable Long id) {
        userService.toggleUserStatus(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Estado del usuario actualizado");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Asignar permiso (Rol) a usuario")
    @PostMapping("/users/{userId}/roles/{roleName}")
    public ResponseEntity<Map<String, String>> assignRole(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @Parameter(description = "Nombre del rol (ej. ROLE_AUDITOR)") @PathVariable String roleName) {
        userService.assignRoleToUser(userId, roleName);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rol asignado correctamente");
        return ResponseEntity.ok(response);
    }

    // --- ENDPOINTS DE ROLES ---

    @Operation(summary = "Listar todos los roles")
    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(userService.getAllRoles());
    }

    @Operation(summary = "Crear nuevo Rol")
    @PostMapping("/roles")
    public ResponseEntity<Map<String, String>> createRole(
            @Parameter(description = "Nombre del nuevo rol") @RequestParam String roleName) {
        userService.createRole(roleName);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rol creado exitosamente");
        return ResponseEntity.ok(response);
    }

    // --- ENDPOINTS DE APROVISIONAMIENTO Y EDICIÓN ---

    @Operation(summary = "Aprovisionamiento Directo (Crear Usuario)")
    @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente")
    @PostMapping("/users")
    public ResponseEntity<User> createUser(
            @Parameter(description = "Datos del nuevo usuario y sus roles") @Valid @RequestBody UserRegisterDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.adminCreateUser(dto));
    }

    @Operation(summary = "Editar datos de usuario",
        description = "Actualiza los campos editables de un usuario: email, nombreCompleto, " +
                      "rut, telefono y/o password. Todos los campos son opcionales — pero " +
                      "se debe enviar al menos uno. Si no, el endpoint responde con un " +
                      "mensaje indicando qué campos puede enviar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o body vacío"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "409", description = "Correo o RUT ya en uso")
    })
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> updateUser(
            @Parameter(description = "ID del usuario a modificar") @PathVariable Long id,
            @Valid @RequestBody AdminUpdateDTO dto) {
        userService.updateUser(id, dto);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Usuario actualizado correctamente");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Revocar permiso (Degradar Rol)")
    @DeleteMapping("/users/{userId}/roles/{roleName}")
    public ResponseEntity<Map<String, String>> revokeRole(
            @Parameter(description = "ID del usuario") @PathVariable Long userId,
            @Parameter(description = "Rol a remover (ej. ROLE_ADMIN)") @PathVariable String roleName) {
        userService.revokeRoleFromUser(userId, roleName);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rol " + roleName + " revocado al usuario " + userId);
        return ResponseEntity.ok(response);
    }

    // --- ENDPOINTS MATRIZ DE PERMISOS ---

    @Operation(summary = "Listar todos los módulos")
    @GetMapping("/modules")
    public ResponseEntity<List<Modulo>> getAllModulos() {
        return ResponseEntity.ok(userService.getAllModulos());
    }

    @Operation(summary = "Listar todos los tipos de acceso")
    @GetMapping("/access-types")
    public ResponseEntity<List<Acceso>> getAllAccesos() {
        return ResponseEntity.ok(userService.getAllAccesos());
    }

    @Operation(summary = "Otorgar Permiso a Rol")
    @PostMapping("/roles/{idRol}/permissions")
    public ResponseEntity<Map<String, String>> grantPermission(
            @Parameter(description = "ID del Rol al que se le dará el permiso") @PathVariable Long idRol,
            @RequestBody PermissionRequestDTO dto) {
        userService.grantPermissionToRole(idRol, dto.getIdModulo(), dto.getIdAcceso());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Permiso otorgado exitosamente al rol");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Revocar Permiso a Rol")
    @DeleteMapping("/roles/{idRol}/permissions")
    public ResponseEntity<Map<String, String>> revokePermission(
            @Parameter(description = "ID del Rol al que se le quitará el permiso") @PathVariable Long idRol,
            @RequestBody PermissionRequestDTO dto) {
        userService.revokePermissionFromRole(idRol, dto.getIdModulo(), dto.getIdAcceso());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Permiso revocado exitosamente del rol");
        return ResponseEntity.ok(response);
    }
}

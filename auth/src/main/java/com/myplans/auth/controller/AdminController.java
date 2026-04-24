package com.myplans.auth.controller;

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
    
    @Operation(summary = "Listar todos los usuarios", description = "Retorna una lista completa de todos los operadores, auditores y administradores registrados en el sistema.")
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Activar/Desactivar Usuario (Soft Delete)", description = "Alterna el estado 'isActive' de un usuario. Si está activo lo desactiva (baneo temporal/despido) y viceversa.")
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

    @Operation(summary = "Asignar permiso (Rol) a usuario", description = "Añade un nuevo rol a la lista de privilegios del usuario especificado.")
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

    @Operation(summary = "Listar todos los roles", description = "Obtiene el catálogo de roles disponibles para asignar en el sistema.")
    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getAllRoles() {
        return ResponseEntity.ok(userService.getAllRoles());
    }

    @Operation(summary = "Crear nuevo Rol", description = "Crea un nuevo nivel de acceso. Automáticamente añade el prefijo 'ROLE_' si el administrador no lo incluye.")
    @PostMapping("/roles")
    public ResponseEntity<Map<String, String>> createRole(
            @Parameter(description = "Nombre del nuevo rol") @RequestParam String roleName) {
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

    // --- ENDPOINTS DE APROVISIONAMIENTO Y EDICIÓN ---

    @Operation(summary = "Aprovisionamiento Directo (Crear Usuario)", description = "Permite a un administrador dar de alta a un empleado directamente, saltando el auto-registro público.")
    @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente")
    @PostMapping("/users")
    public ResponseEntity<User> createUser(
            @Parameter(description = "Datos del nuevo usuario y sus roles") @Valid @RequestBody UserRegisterDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.adminCreateUser(dto));
    }

    @Operation(summary = "Editar correo de usuario", description = "Actualiza la dirección de correo electrónico de un operador existente. Valida que el nuevo correo no esté en uso.")
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> updateEmail(
            @Parameter(description = "ID del usuario a modificar") @PathVariable Long id, 
            @Parameter(description = "Nuevo correo electrónico") @RequestParam String email) {
        userService.updateUserEmail(id, email);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Usuario actualizado correctamente");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Revocar permiso (Degradar Rol)", description = "Quita un rol específico a un usuario, aplicando el principio de mínimo privilegio.")
    @DeleteMapping("/users/{userId}/roles/{roleName}")
    public ResponseEntity<Map<String, String>> revokeRole(
            @Parameter(description = "ID del usuario") @PathVariable Long userId, 
            @Parameter(description = "Rol a remover (ej. ROLE_ADMIN)") @PathVariable String roleName) {
        userService.revokeRoleFromUser(userId, roleName);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Rol " + roleName + " revocado al usuario " + userId);
        return ResponseEntity.ok(response);
    }

    // --- ENDPOINTS MATRIZ DE PERMISOS  ---

    @Operation(summary = "Listar todos los módulos", description = "Obtiene el catálogo de módulos (ej: Planos, Usuarios) para renderizar las filas de la matriz de permisos.")
    @GetMapping("/modules")
    public ResponseEntity<List<Modulo>> getAllModulos() {
        return ResponseEntity.ok(userService.getAllModulos());
    }

    @Operation(summary = "Listar todos los tipos de acceso", description = "Obtiene los tipos de acceso (LEER, CREAR, EDITAR, ELIMINAR) para renderizar las columnas de la matriz de permisos.")
    @GetMapping("/access-types")
    public ResponseEntity<List<Acceso>> getAllAccesos() {
        return ResponseEntity.ok(userService.getAllAccesos());
    }

    @Operation(summary = "Otorgar Permiso a Rol", description = "Asocia un nivel de acceso específico dentro de un módulo a un rol (Inserta en ROL_MODULO).")
    @PostMapping("/roles/{idRol}/permissions")
    public ResponseEntity<Map<String, String>> grantPermission(
            @Parameter(description = "ID del Rol al que se le dará el permiso") @PathVariable Long idRol, 
            @RequestBody PermissionRequestDTO dto) {
        userService.grantPermissionToRole(idRol, dto.getIdModulo(), dto.getIdAcceso());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Permiso otorgado exitosamente al rol");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Revocar Permiso a Rol", description = "Quita un nivel de acceso específico dentro de un módulo a un rol (Elimina de ROL_MODULO).")
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
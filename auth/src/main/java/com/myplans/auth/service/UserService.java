package com.myplans.auth.service;

import com.myplans.auth.dto.UserRegisterDTO;
import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.User;
import com.myplans.auth.repository.RoleRepository;
import com.myplans.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, 
                    RoleRepository roleRepository, 
                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // --- CRUD USUARIOS ---
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
    }

    // --- CRUD ROLES ---
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional
    public void createRole(String roleName) {
        String formattedRoleName = roleName.toUpperCase();
        if (!formattedRoleName.startsWith("ROLE_")) {
            formattedRoleName = "ROLE_" + formattedRoleName;
        }
        if (roleRepository.findByName(formattedRoleName).isPresent()) {
            throw new RuntimeException("El rol ya existe");
        }
        roleRepository.save(new Role(null, formattedRoleName));
    }

    // --- ASIGNACIÓN DE ROLES (RBAC) ---
    @Transactional
    public void assignRoleToUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        user.getRoles().add(role);
        userRepository.save(user);
    }

    // --- CREACIÓN DIRECTA POR ADMIN ---
    @Transactional
    public User adminCreateUser(UserRegisterDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("El correo ya está registrado");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setIsActive(true);

        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            dto.getRoles().forEach(roleName -> {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleName));
                user.getRoles().add(role);
            });
        } else {
            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Error: Rol base no encontrado"));
            user.getRoles().add(userRole);
        }

        return userRepository.save(user);
    }

    // --- REVOCAR PERMISOS ---
    @Transactional
    public void revokeRoleFromUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("El rol especificado no existe"));

        if (user.getRoles().contains(role)) {
            user.getRoles().remove(role);
            userRepository.save(user);
        }
    }

    // --- EDICIÓN DE USUARIO ---
    @Transactional
    public void updateUserEmail(Long userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        if (!user.getEmail().equals(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new RuntimeException("El nuevo correo ya está en uso");
        }
        
        user.setEmail(newEmail);
        userRepository.save(user);
    }
}
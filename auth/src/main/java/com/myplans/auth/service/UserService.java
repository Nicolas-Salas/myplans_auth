package com.myplans.auth.service;

import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.User;
import com.myplans.auth.repository.RoleRepository;
import com.myplans.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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
}
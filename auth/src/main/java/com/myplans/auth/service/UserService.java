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

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional
    public void createRole(String roleName) {
        String formattedRoleName = roleName.toUpperCase();
        if (!formattedRoleName.startsWith("ROLE_")) {
            formattedRoleName = "ROLE_" + formattedRoleName;
        }
        if (roleRepository.findByNombre(formattedRoleName).isPresent()) {
            throw new RuntimeException("El rol ya existe");
        }
        roleRepository.save(new Role(null, formattedRoleName));
    }

    @Transactional
    public void assignRoleToUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Role role = roleRepository.findByNombre(roleName)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        user.setRole(role);
        userRepository.save(user);
    }

    @Transactional
    public User adminCreateUser(UserRegisterDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("El correo ya está registrado");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setNombreCompleto(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setIsActive(true);

        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            String roleName = dto.getRoles().iterator().next();
            Role role = roleRepository.findByNombre(roleName)
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado: " + roleName));
            user.setRole(role);
        } else {
            Role userRole = roleRepository.findByNombre("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Error: Rol base no encontrado"));
            user.setRole(userRole);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void revokeRoleFromUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Role userRole = roleRepository.findByNombre("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Error: Rol base no encontrado"));
        
        user.setRole(userRole);
        userRepository.save(user);
    }

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
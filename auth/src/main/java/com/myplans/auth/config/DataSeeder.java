package com.myplans.auth.config;

import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.User;
import com.myplans.auth.repository.RoleRepository;
import com.myplans.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_ADMIN")));
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_USER")));
        Role auditorRole = roleRepository.findByName("ROLE_AUDITOR")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_AUDITOR")));

        if (!userRepository.existsByEmail("admin@myplans.com")) {
            User admin = new User();
            admin.setEmail("admin@myplans.com");
            admin.setPassword(passwordEncoder.encode("PasswordSegura123!")); 
            admin.getRoles().add(adminRole);
            
            userRepository.save(admin);
            System.out.println("✅ SEMILLA: Administrador maestro creado con éxito (admin@myplans.com)");
        }
    }
}
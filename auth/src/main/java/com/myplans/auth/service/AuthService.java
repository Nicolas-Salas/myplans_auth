package com.myplans.auth.service;

import com.myplans.auth.dto.AdminUpdateDTO;
import com.myplans.auth.dto.AuthResponseDTO;
import com.myplans.auth.dto.ChangePasswordDTO;
import com.myplans.auth.dto.LoginRequestDTO;
import com.myplans.auth.dto.ModulePermissionDTO;
import com.myplans.auth.dto.UserRegisterDTO;
import com.myplans.auth.entity.Modulo;
import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.RoleModulo;
import com.myplans.auth.entity.User;
import com.myplans.auth.entity.PasswordResetToken;
import com.myplans.auth.repository.PasswordResetTokenRepository;
import com.myplans.auth.repository.RoleRepository;
import com.myplans.auth.repository.UserRepository;
import com.myplans.auth.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void registerUser(UserRegisterDTO registerDTO) {
        if (userRepository.existsByEmail(registerDTO.getEmail())) {
            throw new RuntimeException("El correo ingresado ya se encuentra registrado");
        }

        if (registerDTO.getRut() != null && !registerDTO.getRut().isBlank()) {
            if (userRepository.existsByRut(registerDTO.getRut())) {
                throw new RuntimeException("El RUT ingresado ya se encuentra registrado");
            }
        }

        User user = new User();
        user.setEmail(registerDTO.getEmail().toLowerCase());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNombreCompleto(registerDTO.getNombreCompleto());
        user.setRut(registerDTO.getRut());
        user.setTelefono(registerDTO.getTelefono());
        user.setIsActive(false);

        Role assignedRole;
        if (registerDTO.getRoles() == null || registerDTO.getRoles().isEmpty()) {
            assignedRole = roleRepository.findByNombre("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Error: Rol base no encontrado."));
        } else {
            String roleName = registerDTO.getRoles().iterator().next();
            assignedRole = roleRepository.findByNombre(roleName)
                    .orElseThrow(() -> new RuntimeException("Error: Role " + roleName + " no encontrado."));
        }

        user.setRole(assignedRole);
        userRepository.save(user);
    }

    public AuthResponseDTO authenticateUser(LoginRequestDTO loginRequest) {
        String email = loginRequest.getEmail().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String jwtToken = jwtUtil.generateToken(userDetails);

        List<ModulePermissionDTO> permisosFrontend = new ArrayList<>();

        if (user.getRole() != null && user.getRole().getPermisos() != null) {
            Map<Modulo, List<RoleModulo>> agrupadosPorModulo = user.getRole().getPermisos().stream()
                    .collect(Collectors.groupingBy(RoleModulo::getModulo));

            agrupadosPorModulo.forEach((modulo, rolesModulos) -> {
                List<String> acciones = rolesModulos.stream()
                        .map(rm -> rm.getAcceso().getNombre())
                        .collect(Collectors.toList());

                permisosFrontend.add(new ModulePermissionDTO(
                        modulo.getNombre(),
                        modulo.getRutaFrontend(),
                        acciones));
            });
        }

        return new AuthResponseDTO(
                jwtToken,
                "Bearer",
                user.getEmail(),
                user.getNombreCompleto(),
                user.getRole() != null ? user.getRole().getNombre() : "SIN_ROL",
                permisosFrontend);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        tokenRepository.deleteByUser_Id(user.getId());
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token inválido o no encontrado"));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new RuntimeException("El token ha expirado");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.delete(resetToken);
    }

    public User getMe(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Transactional
    public void updateMe(String email, AdminUpdateDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (dto.getNombreCompleto() != null && !dto.getNombreCompleto().isBlank()) {
            user.setNombreCompleto(dto.getNombreCompleto());
        }

        if (dto.getTelefono() != null) {
            user.setTelefono(dto.getTelefono());
        }

        userRepository.save(user);
    }

    @Transactional
    public void changeMyPassword(String email, ChangePasswordDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("La contraseña actual es incorrecta");
        }

        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 8) {
            throw new RuntimeException("La nueva contraseña debe tener al menos 8 caracteres");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }
}
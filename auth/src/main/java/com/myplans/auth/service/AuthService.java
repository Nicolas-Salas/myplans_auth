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
import com.myplans.auth.exception.BusinessException;
import com.myplans.auth.exception.EmailAlreadyExistsException;
import com.myplans.auth.exception.InvalidPasswordException;
import com.myplans.auth.exception.NoFieldsToUpdateException;
import com.myplans.auth.exception.ResourceNotFoundException;
import com.myplans.auth.exception.RutAlreadyExistsException;
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
        if (userRepository.existsByEmail(registerDTO.getEmail().toLowerCase())) {
            throw new EmailAlreadyExistsException(
                    "El correo ingresado ya se encuentra registrado");
        }

        if (registerDTO.getRut() != null && !registerDTO.getRut().isBlank()) {
            if (userRepository.existsByRut(registerDTO.getRut())) {
                throw new RutAlreadyExistsException(
                        "El RUT ingresado ya se encuentra registrado");
            }
        }
        PasswordPolicy.validate(registerDTO.getPassword());

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
                    .orElseThrow(() -> new ResourceNotFoundException("Rol base no encontrado"));
        } else {
            String roleName = registerDTO.getRoles().iterator().next();
            assignedRole = roleRepository.findByNombre(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Rol no encontrado: " + roleName));
        }

        user.setRole(assignedRole);
        userRepository.save(user);
    }

    public AuthResponseDTO authenticateUser(LoginRequestDTO loginRequest) {
        String email = loginRequest.getEmail().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

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
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        tokenRepository.deleteByUser_Id(user.getId());
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(token, user, LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Token inválido o no encontrado"));

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new BusinessException("El token ha expirado. Solicita uno nuevo");
        }

        PasswordPolicy.validate(newPassword);

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.delete(resetToken);
    }

    public User getMe(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Transactional
    public void updateMe(String email, AdminUpdateDTO dto) {
        if (dto == null || dto.isEmpty()) {
            throw new NoFieldsToUpdateException(
                    "Debes enviar al menos un campo para actualizar: nombreCompleto o telefono");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

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
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (dto.getCurrentPassword() == null || dto.getCurrentPassword().isBlank()) {
            throw new InvalidPasswordException("Debes ingresar tu contraseña actual");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().isBlank()) {
            throw new InvalidPasswordException("Debes ingresar la nueva contraseña");
        }

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("La contraseña actual es incorrecta");
        }

        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new InvalidPasswordException(
                    "La nueva contraseña no puede ser igual a la actual");
        }
        PasswordPolicy.validate(dto.getNewPassword());

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }
}

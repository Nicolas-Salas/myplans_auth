package com.myplans.auth.service;

import com.myplans.auth.dto.AdminUpdateDTO;
import com.myplans.auth.dto.UserRegisterDTO;
import com.myplans.auth.entity.Acceso;
import com.myplans.auth.entity.Modulo;
import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.RoleModulo;
import com.myplans.auth.entity.User;
import com.myplans.auth.exception.BusinessException;
import com.myplans.auth.exception.EmailAlreadyExistsException;
import com.myplans.auth.exception.NoFieldsToUpdateException;
import com.myplans.auth.exception.ResourceNotFoundException;
import com.myplans.auth.exception.RutAlreadyExistsException;
import com.myplans.auth.repository.AccesoRepository;
import com.myplans.auth.repository.ModuloRepository;
import com.myplans.auth.repository.RoleModuloRepository;
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
    private final ModuloRepository moduloRepository;
    private final AccesoRepository accesoRepository;
    private final RoleModuloRepository roleModuloRepository;

    public UserService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            ModuloRepository moduloRepository,
            AccesoRepository accesoRepository,
            RoleModuloRepository roleModuloRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.moduloRepository = moduloRepository;
        this.accesoRepository = accesoRepository;
        this.roleModuloRepository = roleModuloRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional
    public void createRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new BusinessException("Debes ingresar el nombre del rol");
        }
        String formattedRoleName = roleName.toUpperCase();
        if (!formattedRoleName.startsWith("ROLE_")) {
            formattedRoleName = "ROLE_" + formattedRoleName;
        }
        if (roleRepository.findByNombre(formattedRoleName).isPresent()) {
            throw new BusinessException("El rol '" + formattedRoleName + "' ya existe");
        }
        roleRepository.save(new Role(null, formattedRoleName));
    }

    @Transactional
    public void assignRoleToUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Role role = roleRepository.findByNombre(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado: " + roleName));
        user.setRole(role);
        userRepository.save(user);
    }

    @Transactional
    public User adminCreateUser(UserRegisterDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail().toLowerCase())) {
            throw new EmailAlreadyExistsException(
                    "El correo ingresado ya se encuentra registrado");
        }

        if (dto.getRut() != null && !dto.getRut().isBlank()) {
            if (userRepository.existsByRut(dto.getRut())) {
                throw new RutAlreadyExistsException(
                        "El RUT ingresado ya se encuentra registrado");
            }
        }

        PasswordPolicy.validate(dto.getPassword());

        User user = new User();
        user.setEmail(dto.getEmail().toLowerCase());
        user.setNombreCompleto(dto.getNombreCompleto());
        user.setRut(dto.getRut());
        user.setTelefono(dto.getTelefono());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setIsActive(true);

        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            String roleName = dto.getRoles().iterator().next();
            Role role = roleRepository.findByNombre(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Rol no encontrado: " + roleName));
            user.setRole(role);
        } else {
            Role userRole = roleRepository.findByNombre("ROLE_USER")
                    .orElseThrow(() -> new ResourceNotFoundException("Rol base no encontrado"));
            user.setRole(userRole);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void revokeRoleFromUser(Long userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Role userRole = roleRepository.findByNombre("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Rol base no encontrado"));
        user.setRole(userRole);
        userRepository.save(user);
    }

    @Transactional
    public void updateUserEmail(Long userId, String newEmail) {
        if (newEmail == null || newEmail.isBlank()) {
            throw new NoFieldsToUpdateException("Debes enviar el nuevo correo");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        String normalizedEmail = newEmail.toLowerCase();
        if (!user.getEmail().equals(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException("El nuevo correo ya está en uso");
        }
        user.setEmail(normalizedEmail);
        userRepository.save(user);
    }

    @Transactional
    public void updateUser(Long userId, AdminUpdateDTO dto) {
        if (dto == null || dto.isEmpty()) {
            throw new NoFieldsToUpdateException(
                    "Debes enviar al menos un campo para actualizar: " +
                    "email, nombreCompleto, rut, telefono o password");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            String normalizedEmail = dto.getEmail().toLowerCase();
            if (!user.getEmail().equals(normalizedEmail) && userRepository.existsByEmail(normalizedEmail)) {
                throw new EmailAlreadyExistsException("El nuevo correo ya está en uso");
            }
            user.setEmail(normalizedEmail);
        }

        if (dto.getNombreCompleto() != null && !dto.getNombreCompleto().isBlank()) {
            user.setNombreCompleto(dto.getNombreCompleto());
        }

        if (dto.getRut() != null && !dto.getRut().isBlank()) {
            boolean rutEnUso = userRepository.findAll().stream()
                    .anyMatch(u -> !u.getId().equals(userId) && dto.getRut().equals(u.getRut()));
            if (rutEnUso) {
                throw new RutAlreadyExistsException("El RUT ya está registrado");
            }
            user.setRut(dto.getRut());
        }

        if (dto.getTelefono() != null) {
            user.setTelefono(dto.getTelefono());
        }

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            // Si el admin envía nueva contraseña, debe cumplir la política.
            PasswordPolicy.validate(dto.getPassword());
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        userRepository.save(user);
    }

    @Transactional
    public void grantPermissionToRole(Long idRol, Long idModulo, Long idAcceso) {
        Role role = roleRepository.findById(idRol)
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));
        Modulo modulo = moduloRepository.findById(idModulo)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo no encontrado"));
        Acceso acceso = accesoRepository.findById(idAcceso)
                .orElseThrow(() -> new ResourceNotFoundException("Acceso no encontrado"));

        RoleModulo.RoleModuloId idCompuesto = new RoleModulo.RoleModuloId();
        idCompuesto.setIdRol(idRol);
        idCompuesto.setIdModulo(idModulo);
        idCompuesto.setIdAcceso(idAcceso);

        if (!roleModuloRepository.existsById(idCompuesto)) {
            RoleModulo roleModulo = new RoleModulo();
            roleModulo.setId(idCompuesto);
            roleModulo.setRol(role);
            roleModulo.setModulo(modulo);
            roleModulo.setAcceso(acceso);
            roleModuloRepository.save(roleModulo);
        }
    }

    @Transactional
    public void revokePermissionFromRole(Long idRol, Long idModulo, Long idAcceso) {
        RoleModulo.RoleModuloId idCompuesto = new RoleModulo.RoleModuloId();
        idCompuesto.setIdRol(idRol);
        idCompuesto.setIdModulo(idModulo);
        idCompuesto.setIdAcceso(idAcceso);

        if (roleModuloRepository.existsById(idCompuesto)) {
            roleModuloRepository.deleteById(idCompuesto);
        }
    }

    public List<Modulo> getAllModulos() {
        return moduloRepository.findAll();
    }

    public List<Acceso> getAllAccesos() {
        return accesoRepository.findAll();
    }
}

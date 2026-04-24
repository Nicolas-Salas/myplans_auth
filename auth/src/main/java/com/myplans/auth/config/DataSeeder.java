package com.myplans.auth.config;

import com.myplans.auth.entity.Role;
import com.myplans.auth.entity.User;
import com.myplans.auth.entity.Modulo;
import com.myplans.auth.entity.Acceso;
import com.myplans.auth.entity.RoleModulo;
import com.myplans.auth.repository.RoleRepository;
import com.myplans.auth.repository.UserRepository;
import com.myplans.auth.repository.ModuloRepository;
import com.myplans.auth.repository.AccesoRepository;
import com.myplans.auth.repository.RoleModuloRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModuloRepository moduloRepository;
    private final AccesoRepository accesoRepository;
    private final RoleModuloRepository roleModuloRepository;

    public DataSeeder(UserRepository userRepository, 
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

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Role adminRole = roleRepository.findByNombre("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_ADMIN")));
        Role userRole = roleRepository.findByNombre("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_USER")));
        Role auditorRole = roleRepository.findByNombre("ROLE_AUDITOR")
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_AUDITOR")));

        Modulo moduloPlanos = moduloRepository.findAll().stream()
                .filter(m -> m.getNombre().equals("Gestión de Planos"))
                .findFirst()
                .orElseGet(() -> {
                    Modulo m = new Modulo();
                    m.setNombre("Gestión de Planos");
                    m.setDescripcion("Módulo principal");
                    m.setRutaFrontend("/planos");
                    return moduloRepository.save(m);
                });

        Acceso accesoCrear = accesoRepository.findAll().stream()
                .filter(a -> a.getNombre().equals("CREAR"))
                .findFirst()
                .orElseGet(() -> {
                    Acceso a = new Acceso();
                    a.setNombre("CREAR");
                    a.setDescripcion("Permite crear registros");
                    return accesoRepository.save(a);
                });

        Acceso accesoEliminar = accesoRepository.findAll().stream()
                .filter(a -> a.getNombre().equals("ELIMINAR"))
                .findFirst()
                .orElseGet(() -> {
                    Acceso a = new Acceso();
                    a.setNombre("ELIMINAR");
                    a.setDescripcion("Permite borrar registros");
                    return accesoRepository.save(a);
                });

        asignarPermiso(adminRole, moduloPlanos, accesoCrear);

        if (!userRepository.existsByEmail("admin@myplans.com")) {
            User admin = new User();
            admin.setEmail("admin@myplans.com");
            admin.setNombreCompleto("Administrador Maestro");
            admin.setPassword(passwordEncoder.encode("PasswordSegura123!")); 
            admin.setRole(adminRole);
            
            userRepository.save(admin);
            System.out.println("✅ SEMILLA: Administrador maestro creado con éxito (admin@myplans.com)");
        }
    }

    private void asignarPermiso(Role rol, Modulo modulo, Acceso acceso) {
        RoleModulo.RoleModuloId id = new RoleModulo.RoleModuloId();
        id.setIdRol(rol.getId());
        id.setIdModulo(modulo.getId());
        id.setIdAcceso(acceso.getId());

        if (!roleModuloRepository.existsById(id)) {
            RoleModulo rm = new RoleModulo();
            rm.setId(id);
            rm.setRol(rol);
            rm.setModulo(modulo);
            rm.setAcceso(acceso);
            roleModuloRepository.save(rm);
        }
    }
}
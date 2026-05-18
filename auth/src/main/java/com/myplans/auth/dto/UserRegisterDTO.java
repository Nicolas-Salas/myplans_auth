package com.myplans.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserRegisterDTO {
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    // nombre_completo es NOT NULL en la entidad User: si no lo validamos
    // aquí, la petición llega hasta Hibernate y truena con un 500 por
    // DataIntegrityViolationException. Mejor responder 400 desde @Valid.
    @NotBlank(message = "El nombre completo es obligatorio")
    private String nombreCompleto;

    private String rut;
    private String telefono;

    private Set<String> roles;
}

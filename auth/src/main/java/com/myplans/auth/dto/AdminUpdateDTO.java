package com.myplans.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUpdateDTO {

    @Email(message = "El email debe tener un formato válido")
    private String email;

    private String nombreCompleto;
    private String rut;
    private String telefono;
    private String password;

    public boolean isEmpty() {
        return isBlank(email)
                && isBlank(nombreCompleto)
                && isBlank(rut)
                && isBlank(telefono)
                && isBlank(password);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

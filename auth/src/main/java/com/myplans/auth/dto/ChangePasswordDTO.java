package com.myplans.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordDTO {

    @NotBlank(message = "Debes ingresar tu contraseña actual")
    private String currentPassword;

    @NotBlank(message = "Debes ingresar la nueva contraseña")
    @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
    private String newPassword;
}

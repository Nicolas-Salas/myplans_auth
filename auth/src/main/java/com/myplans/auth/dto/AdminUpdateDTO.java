package com.myplans.auth.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUpdateDTO {
    private String email;
    private String nombreCompleto;
    private String rut;
    private String telefono;
    private String password;
}

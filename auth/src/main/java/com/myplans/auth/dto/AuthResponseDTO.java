package com.myplans.auth.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private String type;
    private String email;
    private String nombreCompleto;
    private String rol;
    private List<ModulePermissionDTO> permisos;
}
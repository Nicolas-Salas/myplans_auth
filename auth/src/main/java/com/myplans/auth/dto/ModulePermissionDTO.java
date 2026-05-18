package com.myplans.auth.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ModulePermissionDTO {
    private String modulo;
    private String rutaFrontend;
    private List<String> accesos;
}
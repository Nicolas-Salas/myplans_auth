package com.myplans.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "MODULO")
public class Modulo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modulo")
    private Long id;
    private String nombre;
    private String descripcion;
    @Column(name = "ruta_frontend")
    private String rutaFrontend;
}
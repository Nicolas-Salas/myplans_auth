package com.myplans.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "ROL_MODULO")
public class RoleModulo {

    @EmbeddedId
    private RoleModuloId id;

    @JsonIgnore
    @ManyToOne @MapsId("idRol") @JoinColumn(name = "id_rol")
    private Role rol;

    @ManyToOne @MapsId("idModulo") @JoinColumn(name = "id_modulo")
    private Modulo modulo;

    @ManyToOne @MapsId("idAcceso") @JoinColumn(name = "id_acceso")
    private Acceso acceso;

    @Embeddable
    @Getter
    @Setter
    public static class RoleModuloId implements Serializable {
        @Column(name = "id_rol") private Long idRol;
        @Column(name = "id_modulo") private Long idModulo;
        @Column(name = "id_acceso") private Long idAcceso;
    }
}
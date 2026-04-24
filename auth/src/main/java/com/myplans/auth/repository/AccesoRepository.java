package com.myplans.auth.repository;

import com.myplans.auth.entity.Acceso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccesoRepository extends JpaRepository<Acceso, Long> {
}
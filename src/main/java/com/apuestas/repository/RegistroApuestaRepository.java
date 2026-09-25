package com.apuestas.repository;

import com.apuestas.entity.RegistroApuesta;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RegistroApuestaRepository implements PanacheRepository<RegistroApuesta> {
}

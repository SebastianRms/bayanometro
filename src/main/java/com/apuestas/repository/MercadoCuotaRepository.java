package com.apuestas.repository;

import com.apuestas.entity.MercadoCuota;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MercadoCuotaRepository implements PanacheRepository<MercadoCuota> {
}

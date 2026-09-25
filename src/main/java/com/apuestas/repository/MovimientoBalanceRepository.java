package com.apuestas.repository;

import com.apuestas.entity.MovimientoBalance;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MovimientoBalanceRepository implements PanacheRepository<MovimientoBalance> {
}

package com.apuestas.repository;

import com.apuestas.entity.BalanceActual;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BalanceActualRepository implements PanacheRepository<BalanceActual> {
}

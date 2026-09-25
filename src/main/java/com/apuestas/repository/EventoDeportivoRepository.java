package com.apuestas.repository;

import com.apuestas.entity.EventoDeportivo;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EventoDeportivoRepository implements PanacheRepository<EventoDeportivo> {
}

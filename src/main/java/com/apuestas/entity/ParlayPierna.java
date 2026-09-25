package com.apuestas.entity;

import com.apuestas.entity.enums.EstadoApuesta;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "parlay_piernas")
public class ParlayPierna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apuesta_id", nullable = false)
    public RegistroApuesta apuesta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id")
    public EventoDeportivo evento;

    @Column(name = "seleccion", nullable = false, length = 100)
    public String seleccion;

    @Column(name = "cuota", nullable = false, precision = 8, scale = 2)
    public BigDecimal cuota;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 20)
    public EstadoApuesta resultado = EstadoApuesta.PENDIENTE;
}

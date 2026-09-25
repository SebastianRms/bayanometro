package com.apuestas.entity;

import com.apuestas.entity.enums.TipoMercado;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "mercados_cuotas", indexes = {
    @Index(name = "idx_cuotas_evento", columnList = "evento_id"),
    @Index(name = "idx_cuotas_es_valor", columnList = "es_valor")
})
public class MercadoCuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    public EventoDeportivo evento;

    @Column(name = "casa_apuesta", nullable = false, length = 50)
    public String casaApuesta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_mercado", nullable = false, length = 50)
    public TipoMercado tipoMercado;

    @Column(nullable = false, length = 50)
    public String seleccion;

    @Column(nullable = false, precision = 8, scale = 2)
    public BigDecimal cuota;

    @Column(name = "probabilidad_estimada", nullable = false, precision = 6, scale = 4)
    public BigDecimal probabilidadEstimada;

    @Column(nullable = false, precision = 8, scale = 4)
    public BigDecimal ev;

    @Column(name = "es_valor", nullable = false)
    public Boolean esValor = false;

    @CreationTimestamp
    @Column(name = "actualizado_en")
    public ZonedDateTime actualizadoEn;
}

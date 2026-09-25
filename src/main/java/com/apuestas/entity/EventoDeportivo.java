package com.apuestas.entity;

import com.apuestas.entity.enums.Deporte;
import com.apuestas.entity.enums.EstadoPartido;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.Map;

@Entity
@Table(name = "eventos_deportivos", indexes = {
    @Index(name = "idx_eventos_fecha_deporte", columnList = "fecha_evento, deporte"),
    @Index(name = "idx_eventos_estado", columnList = "estado")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"deporte", "fixture_externo_id"})
})
public class EventoDeportivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "fixture_externo_id", nullable = false, length = 50)
    public String fixtureExternoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public Deporte deporte;

    @Column(name = "liga_id", nullable = false)
    public Long ligaId;

    @Column(nullable = false, length = 100)
    public String liga;

    @Column(nullable = false)
    public Integer temporada;

    @Column(length = 50)
    public String pais;

    @Column(name = "equipo_local_id", nullable = false)
    public Long equipoLocalId;

    @Column(name = "equipo_local", nullable = false, length = 120)
    public String equipoLocal;

    @Column(name = "equipo_visita_id", nullable = false)
    public Long equipoVisitaId;

    @Column(name = "equipo_visita", nullable = false, length = 120)
    public String equipoVisita;

    @Column(length = 120)
    public String arbitro;

    @Column(name = "fecha_evento", nullable = false)
    public ZonedDateTime fechaEvento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public EstadoPartido estado = EstadoPartido.NS;

    @Column(name = "marcador_local")
    public Integer marcadorLocal;

    @Column(name = "marcador_visita")
    public Integer marcadorVisita;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalles_especificos", columnDefinition = "jsonb")
    public Map<String, Object> detallesEspecificos;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    public ZonedDateTime creadoEn;

}

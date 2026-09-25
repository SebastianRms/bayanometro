package com.apuestas.entity;

import com.apuestas.entity.enums.Deporte;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "ligas", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"deporte", "liga_id"})
})
public class Liga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "deporte", nullable = false, length = 30)
    public Deporte deporte;

    @Column(name = "liga_id", nullable = false)
    public Long ligaId;

    @Column(name = "nombre", nullable = false, length = 150)
    public String nombre;

    @Column(name = "pais", length = 80)
    public String pais;

    @Column(name = "activa", nullable = false)
    public Boolean activa = true;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    public ZonedDateTime actualizadoEn;
}

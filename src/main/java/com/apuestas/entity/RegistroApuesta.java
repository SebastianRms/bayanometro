package com.apuestas.entity;

import com.apuestas.entity.enums.EstadoApuesta;
import com.apuestas.entity.enums.TipoJugada;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "registro_apuestas", indexes = {
    @Index(name = "idx_apuestas_usuario_estado", columnList = "usuario_telegram_id, resultado")
})
public class RegistroApuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "usuario_telegram_id", nullable = false)
    public Long usuarioTelegramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_jugada", nullable = false, length = 20)
    public TipoJugada tipoJugada;

    @Column(nullable = false, length = 30)
    public String deporte;

    @Column(length = 100)
    public String liga;

    @Column(length = 250)
    public String partido;

    @Column(length = 100)
    public String pick;

    @Column(nullable = false, precision = 8, scale = 2)
    public BigDecimal momio;

    @Column(name = "monto_apostado", nullable = false, precision = 12, scale = 2)
    public BigDecimal montoApostado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public EstadoApuesta resultado = EstadoApuesta.PENDIENTE;

    @Column(name = "ganancia_neta", precision = 12, scale = 2)
    public BigDecimal gananciaNeta = BigDecimal.ZERO;

    @Column(name = "balance_resultante", nullable = false, precision = 12, scale = 2)
    public BigDecimal balanceResultante;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    public ZonedDateTime creadoEn;
}

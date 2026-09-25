package com.apuestas.entity;

import com.apuestas.entity.enums.TipoMovimiento;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "movimientos_balance", indexes = {
    @Index(name = "idx_mov_usuario_fecha", columnList = "usuario_telegram_id, fecha")
})
public class MovimientoBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "usuario_telegram_id", nullable = false)
    public Long usuarioTelegramId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false, length = 20)
    public TipoMovimiento tipoMovimiento;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal monto;

    @Column(name = "saldo_previo", nullable = false, precision = 12, scale = 2)
    public BigDecimal saldoPrevio;

    @Column(name = "saldo_posterior", nullable = false, precision = 12, scale = 2)
    public BigDecimal saldoPosterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apuesta_id")
    public RegistroApuesta apuesta;

    @Column(columnDefinition = "TEXT")
    public String descripcion;

    @CreationTimestamp
    @Column(updatable = false)
    public ZonedDateTime fecha;
}

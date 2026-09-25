package com.apuestas.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "balance_actual", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"usuario_telegram_id"})
})
public class BalanceActual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "usuario_telegram_id", nullable = false)
    public Long usuarioTelegramId;

    @Version
    public Integer version;

    @Column(name = "saldo_actual", nullable = false, precision = 12, scale = 2)
    public BigDecimal saldoActual = new BigDecimal("150.00");

    @Column(name = "total_apostado_historico", nullable = false, precision = 12, scale = 2)
    public BigDecimal totalApostadoHistorico = BigDecimal.ZERO;

    @Column(name = "total_cobrado_historico", nullable = false, precision = 12, scale = 2)
    public BigDecimal totalCobradoHistorico = BigDecimal.ZERO;

    @Column(name = "total_retirado_historico", nullable = false, precision = 12, scale = 2)
    public BigDecimal totalRetiradoHistorico = BigDecimal.ZERO;

    @Column(name = "apuestas_ganadas", nullable = false)
    public Integer apuestasGanadas = 0;

    @Column(name = "apuestas_perdidas", nullable = false)
    public Integer apuestasPerdidas = 0;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    public ZonedDateTime actualizadoEn;
}

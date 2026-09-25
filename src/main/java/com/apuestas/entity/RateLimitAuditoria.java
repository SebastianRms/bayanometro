package com.apuestas.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "rate_limit_auditoria", indexes = {
    @Index(name = "idx_rate_usuario_ts", columnList = "usuario_telegram_id, timestamp")
})
public class RateLimitAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "usuario_telegram_id", nullable = false)
    public Long usuarioTelegramId;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false)
    public ZonedDateTime timestamp;
}

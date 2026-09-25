package com.apuestas.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "telegram_updates")
public class TelegramUpdate {

    @Id
    @Column(name = "update_id")
    public Long updateId;

    @CreationTimestamp
    @Column(name = "procesado_en", nullable = false)
    public ZonedDateTime procesadoEn;
}

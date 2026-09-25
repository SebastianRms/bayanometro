package com.apuestas.resource;

import com.apuestas.entity.RateLimitAuditoria;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Helper transaccional para el TelegramWebhookResource.
 * Separado en bean propio para que los métodos @Transactional pasen
 * por el interceptor CDI de Quarkus (self-invocation no funciona en CDI).
 */
@ApplicationScoped
public class TelegramWebhookTxHelper {

    @Inject
    EntityManager em;

    /**
     * Registra el update_id en la tabla de deduplicación.
     * @return true si es nuevo (insertado), false si ya existía (duplicado).
     */
    @Transactional
    public boolean registrarUpdate(Long updateId) {
        int inserted = em.createNativeQuery(
                "INSERT INTO telegram_updates(update_id) VALUES (?) ON CONFLICT DO NOTHING")
            .setParameter(1, updateId)
            .executeUpdate();
        return inserted > 0;
    }

    /**
     * Persiste la entrada de auditoría de rate-limit.
     * TX corta, independiente del AI call.
     */
    @Transactional
    public void registrarAuditoria(Long userId) {
        RateLimitAuditoria auditoria = new RateLimitAuditoria();
        auditoria.usuarioTelegramId = userId;
        em.persist(auditoria);
    }
}

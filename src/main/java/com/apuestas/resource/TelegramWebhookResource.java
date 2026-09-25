package com.apuestas.resource;

import com.apuestas.ai.BettingAiAssistant;
import com.apuestas.exception.RateLimitExcedidoException;
import com.apuestas.exception.UsuarioNoAutorizadoException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/webhook/telegram")
public class TelegramWebhookResource {

    private static final Logger LOG = Logger.getLogger(TelegramWebhookResource.class);

    @Inject
    BettingAiAssistant aiAssistant;

    @Inject
    TelegramWebhookTxHelper txHelper;

    @ConfigProperty(name = "bayanometro.telegram.allowed-users")
    List<Long> allowedUsers;

    @ConfigProperty(name = "bayanometro.telegram.webhook-secret")
    String secretToken;

    @ConfigProperty(name = "bayanometro.telegram.rate-limit-per-window")
    int rateLimit;

    @ConfigProperty(name = "bayanometro.telegram.window-hours")
    int windowHours;

    private Cache<Long, AtomicInteger> rateLimitCache;

    @PostConstruct
    void init() {
        rateLimitCache = Caffeine.newBuilder()
            .expireAfterWrite(windowHours, TimeUnit.HOURS)
            .build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response recibirMensaje(Map<String, Object> payload, @HeaderParam("X-Telegram-Bot-Api-Secret-Token") String tokenHeader) {

        if (!secretToken.equals(tokenHeader)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // Deduplicación y auditoría en TX separada y corta (sin AI call dentro)
        Long updateId = extraerUpdateId(payload);
        if (updateId != null) {
            boolean esNuevo = txHelper.registrarUpdate(updateId);
            if (!esNuevo) {
                return Response.ok().build(); // duplicado — ignorar
            }
        }

        Long userId = extraerUserId(payload);
        if (userId == null) return Response.ok().build();

        if (!allowedUsers.contains(userId)) {
            throw new UsuarioNoAutorizadoException(userId);
        }

        AtomicInteger counter = rateLimitCache.get(userId, k -> new AtomicInteger(0));
        if (counter != null && counter.incrementAndGet() > rateLimit) {
            throw new RateLimitExcedidoException(LocalDateTime.now().plusHours(windowHours));
        }

        // Auditoría en TX propia, corta
        txHelper.registrarAuditoria(userId);

        String texto = extraerTexto(payload);
        if (texto == null || texto.isBlank()) return Response.ok().build();

        Long chatId = extraerChatId(payload);

        // La llamada al AI corre SIN transacción JTA abierta para evitar
        // que TransactionReaper cancele la TX mientras Gemini procesa (>60s)
        try {
            String respuestaAi = aiAssistant.chat(userId, texto);
            if (chatId != null && respuestaAi != null) {
                return Response.ok(Map.of(
                    "method", "sendMessage",
                    "chat_id", chatId,
                    "text", respuestaAi
                )).build();
            }
            return Response.ok().build();
        } catch (Exception e) {
            LOG.errorf(e, "Error al procesar mensaje con IA para usuario: %d", userId);
            if (chatId != null) {
                return Response.ok(Map.of(
                    "method", "sendMessage",
                    "chat_id", chatId,
                    "text", "⚠️ Error al procesar tu solicitud: " + e.getMessage()
                )).build();
            }
            return Response.serverError().entity("Error procesando IA: " + e.getMessage()).build();
        }
    }

    private Long extraerUpdateId(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            Object id = payload.get("update_id");
            if (id != null) return Long.valueOf(id.toString());
        } catch (RuntimeException e) {
            LOG.tracef(e, "Error al extraer update_id del payload");
        }
        return null;
    }

    private Long extraerChatId(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            Object msgObj = payload.get("message");
            if (msgObj instanceof Map<?, ?> msgMap) {
                Object chatObj = msgMap.get("chat");
                if (chatObj instanceof Map<?, ?> chatMap && chatMap.get("id") != null) {
                    return Long.valueOf(chatMap.get("id").toString());
                }
            }
        } catch (RuntimeException e) {
            LOG.tracef(e, "Error al extraer chat_id del payload");
        }
        return null;
    }

    private Long extraerUserId(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            Object msgObj = payload.get("message");
            if (msgObj instanceof Map<?, ?> msgMap) {
                Object fromObj = msgMap.get("from");
                if (fromObj instanceof Map<?, ?> fromMap && fromMap.get("id") != null) {
                    return Long.valueOf(fromMap.get("id").toString());
                }
            }
        } catch (RuntimeException e) {
            LOG.tracef(e, "Error al extraer user_id del payload");
        }
        return null;
    }

    private String extraerTexto(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            Object msgObj = payload.get("message");
            if (msgObj instanceof Map<?, ?> msgMap && msgMap.get("text") != null) {
                return msgMap.get("text").toString();
            }
        } catch (RuntimeException e) {
            LOG.tracef(e, "Error al extraer texto del payload");
        }
        return null;
    }
}

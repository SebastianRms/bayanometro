package com.apuestas.exception;

import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;

/**
 * Mapeador global de excepciones para estandarizar las respuestas de error en formato JSON.
 */
public class GlobalExceptionMapper {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @ServerExceptionMapper
    public Response mapBayanometroException(BayanometroException ex) {
        LOG.warnf("Excepción de negocio controlada [%s]: %s", ex.getCodigoError(), ex.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(
                        "error", ex.getCodigoError(),
                        "mensaje", ex.getMessage()
                ))
                .build();
    }

    @ServerExceptionMapper
    public Response mapUsuarioNoAutorizadoException(UsuarioNoAutorizadoException ex) {
        LOG.warnf("Intento de acceso no autorizado: %s", ex.getMessage());
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of(
                        "error", ex.getCodigoError(),
                        "mensaje", ex.getMessage()
                ))
                .build();
    }

    @ServerExceptionMapper
    public Response mapRateLimitExcedidoException(RateLimitExcedidoException ex) {
        LOG.warnf("Rate limit excedido: %s", ex.getMessage());
        return Response.status(429) // Too Many Requests
                .entity(Map.of(
                        "error", ex.getCodigoError(),
                        "mensaje", ex.getMessage()
                ))
                .build();
    }

    @ServerExceptionMapper
    public Response mapFallback(Throwable ex) {
        LOG.errorf(ex, "Error interno no controlado en el servidor: %s", ex.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of(
                        "error", "ERROR_INTERNO",
                        "mensaje", "Ocurrió un error inesperado al procesar la solicitud."
                ))
                .build();
    }
}

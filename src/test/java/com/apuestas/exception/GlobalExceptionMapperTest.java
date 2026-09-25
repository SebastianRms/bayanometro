package com.apuestas.exception;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionMapperTest {

    private GlobalExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GlobalExceptionMapper();
    }

    @Test
    @DisplayName("Debe mapear BayanometroException a HTTP 400 con código de error y mensaje")
    void testMapBayanometroException() {
        SaldoInsuficienteException ex = new SaldoInsuficienteException(new BigDecimal("10.00"), new BigDecimal("50.00"));

        Response response = mapper.mapBayanometroException(ex);

        assertNotNull(response);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("FIN-001", entity.get("error"));
        assertNotNull(entity.get("mensaje"));
    }

    @Test
    @DisplayName("Debe mapear UsuarioNoAutorizadoException a HTTP 403")
    void testMapUsuarioNoAutorizadoException() {
        UsuarioNoAutorizadoException ex = new UsuarioNoAutorizadoException(999888777L);

        Response response = mapper.mapUsuarioNoAutorizadoException(ex);

        assertNotNull(response);
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("SEC-001", entity.get("error"));
    }

    @Test
    @DisplayName("Debe mapear RateLimitExcedidoException a HTTP 429")
    void testMapRateLimitExcedidoException() {
        RateLimitExcedidoException ex = new RateLimitExcedidoException(LocalDateTime.now().plusHours(1));

        Response response = mapper.mapRateLimitExcedidoException(ex);

        assertNotNull(response);
        assertEquals(429, response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("SEC-002", entity.get("error"));
    }

    @Test
    @DisplayName("Debe mapear errores no controlados a HTTP 500 sin exponer detalles sensibles")
    void testMapFallback() {
        NullPointerException ex = new NullPointerException("NPE inesperado en base de datos");

        Response response = mapper.mapFallback(ex);

        assertNotNull(response);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertEquals("ERROR_INTERNO", entity.get("error"));
        assertEquals("Ocurrió un error inesperado al procesar la solicitud.", entity.get("mensaje"));
    }
}

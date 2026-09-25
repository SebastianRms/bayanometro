package com.apuestas.resource;

import com.apuestas.service.BankrollService;
import com.apuestas.service.BetEngineMathService;
import com.apuestas.service.IngestionBatchService;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BankrollAdminResourceTest {

    private BankrollAdminResource adminResource;
    private BankrollService bankrollService;
    private BetEngineMathService betEngine;
    private IngestionBatchService ingestionService;

    @BeforeEach
    void setUp() {
        adminResource = new BankrollAdminResource();
        bankrollService = Mockito.mock(BankrollService.class);
        betEngine = Mockito.mock(BetEngineMathService.class);
        ingestionService = Mockito.mock(IngestionBatchService.class);

        adminResource.bankrollService = bankrollService;
        adminResource.betEngine = betEngine;
        adminResource.ingestionService = ingestionService;
        adminResource.secretToken = "secreto-admin-test";
    }

    @Test
    @DisplayName("Debe rechazar la llamada si el token administrativo es incorrecto")
    void testTokenInvalidoLanzaExcepcion() {
        assertThrows(NotAuthorizedException.class, () -> 
            adminResource.conciliar(12345L, "token-falso")
        );
    }

    @Test
    @DisplayName("Debe retornar HTTP 400 Bad Request si no se proporciona el userId")
    void testUserIdNuloRetornaBadRequest() {
        Response response = adminResource.conciliar(null, "secreto-admin-test");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    @DisplayName("Debe procesar la reconciliación exitosamente cuando el token y userId son válidos")
    void testReconciliacionExitosa() {
        Long userId = 8464022602L;
        Mockito.when(bankrollService.conciliarResultadosYActualizarBank(userId))
                .thenReturn(Map.of("boletosCerrados", 2, "totalAcreditado", 50.0));

        Response response = adminResource.conciliar(userId, "secreto-admin-test");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        Mockito.verify(bankrollService, Mockito.times(1)).conciliarResultadosYActualizarBank(userId);
    }
}

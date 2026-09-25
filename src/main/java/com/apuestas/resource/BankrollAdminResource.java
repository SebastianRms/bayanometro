package com.apuestas.resource;

import com.apuestas.service.BankrollService;
import com.apuestas.service.BetEngineMathService;
import com.apuestas.service.IngestionBatchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Endpoints administrativos y de diagnóstico para el motor de apuestas y reconciliación de bankroll.
 * Protegidos mediante token secreto administrativo.
 */
@Path("/api")
public class BankrollAdminResource {

    @Inject
    BankrollService bankrollService;

    @Inject
    IngestionBatchService ingestionService;

    @Inject
    BetEngineMathService betEngine;

    @ConfigProperty(name = "bayanometro.telegram.webhook-secret", defaultValue = "")
    String secretToken;

    @GET
    @Path("/reconciliar")
    @Produces(MediaType.APPLICATION_JSON)
    public Response conciliar(
            @QueryParam("userId") Long userId,
            @HeaderParam("X-Admin-Token") String adminToken) {

        validarAccesoAdmin(adminToken);

        if (userId == null || userId <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "PARAMETRO_REQUERIDO", "mensaje", "El parámetro 'userId' es obligatorio."))
                    .build();
        }

        Map<String, Object> resultado = bankrollService.conciliarResultadosYActualizarBank(userId);
        return Response.ok(resultado).build();
    }

    @GET
    @Path("/picks-debug")
    @Produces(MediaType.APPLICATION_JSON)
    public Response testPicks(
            @QueryParam("userId") Long userId,
            @QueryParam("deporte") String deporte,
            @HeaderParam("X-Admin-Token") String adminToken) {

        validarAccesoAdmin(adminToken);

        if (userId == null || userId <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "PARAMETRO_REQUERIDO", "mensaje", "El parámetro 'userId' es obligatorio."))
                    .build();
        }

        List<Map<String, Object>> picks = betEngine.obtenerMejoresPicksDelDia(userId, deporte);
        return Response.ok(picks).build();
    }

    private void validarAccesoAdmin(String adminToken) {
        if (secretToken != null && !secretToken.isBlank() && !secretToken.equals(adminToken)) {
            throw new jakarta.ws.rs.NotAuthorizedException("Token administrativo no proporcionado o inválido.");
        }
    }
}

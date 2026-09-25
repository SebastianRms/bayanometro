package com.apuestas.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "odds-api")
public interface TheOddsApiClient {

    /**
     * Obtiene la lista de deportes activos.
     */
    @GET
    @Path("/sports")
    List<Map<String, Object>> getDeportesActivos(@QueryParam("apiKey") String apiKey);

    /**
     * Obtiene cuotas en vivo de un deporte específico en formato decimal (ej. Pinnacle, Bet365).
     *
     * @param sportKey   Identificador del deporte/liga (ej. "soccer_epl", "soccer_mexico_ligamx", "basketball_nba")
     * @param apiKey     API key de The Odds API
     * @param regions    Regiones de casas de apuestas (ej. "us,eu")
     * @param markets    Mercados a consultar (ej. "h2h", "totals", "spreads")
     * @param oddsFormat Formato de cuota ("decimal")
     */
    @GET
    @Path("/sports/{sport}/odds")
    List<Map<String, Object>> getCuotasDeporte(
        @PathParam("sport") String sportKey,
        @QueryParam("apiKey") String apiKey,
        @QueryParam("regions") String regions,
        @QueryParam("markets") String markets,
        @QueryParam("oddsFormat") String oddsFormat
    );

    /**
     * Obtiene marcadores y partidos completados recientes (para liquidación de apuestas).
     */
    @GET
    @Path("/sports/{sport}/scores")
    List<Map<String, Object>> getResultadosDeporte(
        @PathParam("sport") String sportKey,
        @QueryParam("apiKey") String apiKey,
        @QueryParam("daysFrom") Integer daysFrom
    );
}

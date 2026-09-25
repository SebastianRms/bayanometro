package com.apuestas.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

@RegisterRestClient(configKey = "api-sports-football")
@ClientHeaderParam(name = "x-apisports-key", value = "${bayanometro.api-sports.key}")
public interface ApiFootballRestClient {

    @GET
    @Path("/fixtures")
    Map<String, Object> getFixtures(
        @QueryParam("date") String date,
        @QueryParam("status") String status,
        @QueryParam("timezone") String timezone
    );

    /** Últimos N partidos de un equipo (forma reciente) */
    @GET
    @Path("/fixtures")
    Map<String, Object> getUltimosPartidosEquipo(
        @QueryParam("team") Long teamId,
        @QueryParam("last") int last
    );

    /** Últimos N enfrentamientos directos entre dos equipos */
    @GET
    @Path("/fixtures/headtohead")
    Map<String, Object> getH2H(
        @QueryParam("h2h") String h2hIds,   // formato: "teamId1-teamId2"
        @QueryParam("last") int last
    );

    @GET
    @Path("/odds")
    Map<String, Object> getOdds(
        @QueryParam("date") String date,
        @QueryParam("page") int page
    );
}

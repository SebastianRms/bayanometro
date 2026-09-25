package com.apuestas.service.impl;

import com.apuestas.client.ApiFootballRestClient;
import com.apuestas.service.EstadisticasService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación del análisis estadístico de forma y H2H para partidos de fútbol.
 * Consume la API-Sports free plan (100 req/day) con un límite conservador por llamada.
 */
@ApplicationScoped
public class EstadisticasServiceImpl implements EstadisticasService {

    private static final Logger LOG = Logger.getLogger(EstadisticasServiceImpl.class);

    @Inject
    @RestClient
    ApiFootballRestClient footballClient;

    @Override
    public Map<String, Object> analizarPartido(Long equipoLocalId, Long equipoVisitaId) {
        Map<String, Object> resultado = new LinkedHashMap<>();

        try {
            // --- Forma del equipo local (últimos 10 partidos) ---
            Map<String, Object> formaLocal = analizarForma(equipoLocalId, "Local");
            resultado.put("formaLocal", formaLocal);
        } catch (Exception e) {
            LOG.warn("No se pudo obtener forma del equipo local " + equipoLocalId + ": " + e.getMessage());
            resultado.put("formaLocal", Map.of("error", "No disponible"));
        }

        try {
            // --- Forma del equipo visitante (últimos 10 partidos) ---
            Map<String, Object> formaVisita = analizarForma(equipoVisitaId, "Visita");
            resultado.put("formaVisita", formaVisita);
        } catch (Exception e) {
            LOG.warn("No se pudo obtener forma del equipo visitante " + equipoVisitaId + ": " + e.getMessage());
            resultado.put("formaVisita", Map.of("error", "No disponible"));
        }

        try {
            // --- H2H últimos 5 enfrentamientos ---
            Map<String, Object> h2h = analizarH2H(equipoLocalId, equipoVisitaId);
            resultado.put("h2h", h2h);
        } catch (Exception e) {
            LOG.warn("No se pudo obtener H2H: " + e.getMessage());
            resultado.put("h2h", Map.of("error", "No disponible"));
        }

        // --- Resumen ejecutivo para el LLM ---
        resultado.put("resumen", generarResumen(resultado, equipoLocalId, equipoVisitaId));

        return resultado;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> analizarForma(Long teamId, String rol) {
        Map<String, Object> response = footballClient.getUltimosPartidosEquipo(teamId, 10);
        List<Map<String, Object>> fixtures = (List<Map<String, Object>>) response.get("response");

        if (fixtures == null || fixtures.isEmpty()) {
            return Map.of("equipo", teamId, "partidos", 0, "nota", "Sin datos de forma reciente");
        }

        int jugados = 0, ganados = 0, empatados = 0, perdidos = 0;
        int golesFavor = 0, golesContra = 0;
        List<String> resultados = new ArrayList<>();
        StringBuilder racha = new StringBuilder();

        for (Map<String, Object> item : fixtures) {
            try {
                Map<String, Object> teams  = (Map<String, Object>) item.get("teams");
                Map<String, Object> goals  = (Map<String, Object>) item.get("goals");
                Map<String, Object> score  = (Map<String, Object>) item.get("score");

                Map<String, Object> home = (Map<String, Object>) teams.get("home");
                Map<String, Object> away = (Map<String, Object>) teams.get("away");

                boolean esLocal = teamId.equals(toLong(home.get("id")));
                Object golesHome = goals.get("home");
                Object golesAway = goals.get("away");

                if (golesHome == null || golesAway == null) continue; // partido sin marcador (pendiente)

                int gH = toInt(golesHome);
                int gA = toInt(golesAway);
                int gF = esLocal ? gH : gA;
                int gC = esLocal ? gA : gH;

                golesFavor  += gF;
                golesContra += gC;
                jugados++;

                String rival = esLocal
                    ? (String) away.get("name")
                    : (String) home.get("name");
                String ubicacion = esLocal ? "L" : "V";

                if (gF > gC)       { ganados++;  resultados.add("G"); racha.append("W"); }
                else if (gF == gC) { empatados++; resultados.add("E"); racha.append("D"); }
                else               { perdidos++;  resultados.add("P"); racha.append("L"); }

                resultados.set(resultados.size() - 1,
                    ubicacion + " " + gF + "-" + gC + " vs " + rival);

            } catch (Exception ex) {
                LOG.debug("Error parseando partido de forma: " + ex.getMessage());
            }
        }

        if (jugados == 0) return Map.of("equipo", teamId, "partidos", 0, "nota", "Sin partidos completados recientes");

        double promedioGF = (double) golesFavor  / jugados;
        double promedioGC = (double) golesContra / jugados;
        String rachaReciente = racha.length() >= 5
            ? racha.substring(racha.length() - 5)   // últimos 5 resultados
            : racha.toString();

        Map<String, Object> forma = new LinkedHashMap<>();
        forma.put("rol", rol);
        forma.put("partidosAnalizados", jugados);
        forma.put("ganados", ganados);
        forma.put("empatados", empatados);
        forma.put("perdidos", perdidos);
        forma.put("pctVictorias", String.format("%.0f%%", (double) ganados / jugados * 100));
        forma.put("promedioGolesFavor", String.format("%.2f", promedioGF));
        forma.put("promedioGolesContra", String.format("%.2f", promedioGC));
        forma.put("rachaReciente5", rachaReciente);           // Ej: "WWDLW"
        forma.put("ultimosResultados", resultados);

        return forma;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> analizarH2H(Long localId, Long visitaId) {
        String h2hKey = localId + "-" + visitaId;
        Map<String, Object> response = footballClient.getH2H(h2hKey, 5);
        List<Map<String, Object>> fixtures = (List<Map<String, Object>>) response.get("response");

        if (fixtures == null || fixtures.isEmpty()) {
            return Map.of("partidos", 0, "nota", "Sin historial H2H disponible");
        }

        int total = 0, victoriasLocal = 0, empates = 0, victoriasVisita = 0;
        List<String> partidos = new ArrayList<>();

        for (Map<String, Object> item : fixtures) {
            try {
                Map<String, Object> teams = (Map<String, Object>) item.get("teams");
                Map<String, Object> goals = (Map<String, Object>) item.get("goals");
                Map<String, Object> fixture = (Map<String, Object>) item.get("fixture");

                Map<String, Object> home = (Map<String, Object>) teams.get("home");
                Map<String, Object> away = (Map<String, Object>) teams.get("away");

                Object golesHome = goals.get("home");
                Object golesAway = goals.get("away");
                if (golesHome == null || golesAway == null) continue;

                int gH = toInt(golesHome);
                int gA = toInt(golesAway);

                String nombreHome = (String) home.get("name");
                String nombreAway = (String) away.get("name");
                String fecha = fixture != null ? String.valueOf(fixture.get("date")).substring(0, 10) : "?";

                total++;
                // Determinamos quién es el "local original" vs el equipo que pedimos
                boolean homeEsNuestroLocal = localId.equals(toLong(home.get("id")));

                if (gH > gA) {
                    if (homeEsNuestroLocal) victoriasLocal++; else victoriasVisita++;
                } else if (gH < gA) {
                    if (homeEsNuestroLocal) victoriasVisita++; else victoriasLocal++;
                } else {
                    empates++;
                }

                partidos.add(fecha + " " + nombreHome + " " + gH + "-" + gA + " " + nombreAway);

            } catch (Exception ex) {
                LOG.debug("Error parseando H2H: " + ex.getMessage());
            }
        }

        Map<String, Object> h2h = new LinkedHashMap<>();
        h2h.put("enfrentamientosAnalizados", total);
        h2h.put("victoriasLocal", victoriasLocal);
        h2h.put("empates", empates);
        h2h.put("victoriasVisita", victoriasVisita);
        h2h.put("historial", partidos);

        return h2h;
    }

    private String generarResumen(Map<String, Object> data, Long localId, Long visitaId) {
        StringBuilder sb = new StringBuilder();

        @SuppressWarnings("unchecked")
        Map<String, Object> fL = (Map<String, Object>) data.get("formaLocal");
        @SuppressWarnings("unchecked")
        Map<String, Object> fV = (Map<String, Object>) data.get("formaVisita");
        @SuppressWarnings("unchecked")
        Map<String, Object> h2h = (Map<String, Object>) data.get("h2h");

        if (fL != null && fL.containsKey("pctVictorias")) {
            sb.append("LOCAL: ").append(fL.get("pctVictorias")).append(" victorias en últimos ")
              .append(fL.get("partidosAnalizados")).append(" partidos | Racha: ").append(fL.get("rachaReciente5"))
              .append(" | xG aprox ").append(fL.get("promedioGolesFavor")).append("/").append(fL.get("promedioGolesContra")).append(". ");
        }
        if (fV != null && fV.containsKey("pctVictorias")) {
            sb.append("VISITA: ").append(fV.get("pctVictorias")).append(" victorias en últimos ")
              .append(fV.get("partidosAnalizados")).append(" partidos | Racha: ").append(fV.get("rachaReciente5"))
              .append(" | xG aprox ").append(fV.get("promedioGolesFavor")).append("/").append(fV.get("promedioGolesContra")).append(". ");
        }
        if (h2h != null && h2h.containsKey("enfrentamientosAnalizados")) {
            int total = toInt(h2h.get("enfrentamientosAnalizados"));
            if (total > 0) {
                sb.append("H2H (").append(total).append(" partidos): ")
                  .append(h2h.get("victoriasLocal")).append("V-")
                  .append(h2h.get("empates")).append("E-")
                  .append(h2h.get("victoriasVisita")).append("D para el local.");
            }
        }

        return sb.length() > 0 ? sb.toString() : "Análisis estadístico no disponible.";
    }

    private int toInt(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }

    private Long toLong(Object o) {
        if (o instanceof Long l) return l;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }
}

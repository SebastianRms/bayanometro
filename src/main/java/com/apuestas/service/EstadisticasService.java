package com.apuestas.service;

import java.util.Map;

/**
 * Analiza forma reciente (últimos 10 partidos) y H2H (últimos 5 enfrentamientos)
 * de los equipos de un evento deportivo, para enriquecer el análisis de picks.
 */
public interface EstadisticasService {

    /**
     * Retorna el análisis completo de un partido: forma local, forma visitante y H2H.
     *
     * @param equipoLocalId  ID externo del equipo local
     * @param equipoVisitaId ID externo del equipo visitante
     * @return Mapa con claves: formaLocal, formaVisita, h2h, resumen
     */
    Map<String, Object> analizarPartido(Long equipoLocalId, Long equipoVisitaId);
}

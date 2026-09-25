package com.apuestas.service.impl;

import com.apuestas.entity.BalanceActual;
import com.apuestas.entity.EventoDeportivo;
import com.apuestas.entity.MercadoCuota;
import com.apuestas.entity.RegistroApuesta;
import com.apuestas.entity.enums.Deporte;
import com.apuestas.entity.enums.EstadoApuesta;
import com.apuestas.entity.enums.EstadoPartido;
import com.apuestas.entity.enums.TipoMercado;
import com.apuestas.repository.EventoDeportivoRepository;
import com.apuestas.repository.MercadoCuotaRepository;
import com.apuestas.repository.RegistroApuestaRepository;
import com.apuestas.service.BankrollService;
import com.apuestas.service.BetEngineMathService;
import com.apuestas.service.EstadisticasService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BetEngineMathServiceImpl implements BetEngineMathService {

    @Inject
    MercadoCuotaRepository cuotaRepo;

    @Inject
    EventoDeportivoRepository eventoRepo;

    @Inject
    BankrollService bankrollService;

    @Inject
    EstadisticasService estadisticasService;

    @Inject
    RegistroApuestaRepository apuestaRepo;

    @Override
    public List<Map<String, Object>> obtenerMejoresPicksDelDia(Long userId, String deporteOpcional) {
        BalanceActual balance = bankrollService.obtenerResumenActual(userId);
        BigDecimal saldoOperativo = balance.saldoActual.multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP);
        // Stake por pick individual: 10% del operativo, con piso de $10 MXN si el saldo lo permite
        BigDecimal stakeDirecta = saldoOperativo.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        if (stakeDirecta.compareTo(new BigDecimal("10.00")) < 0 && balance.saldoActual.compareTo(new BigDecimal("10.00")) >= 0) {
            stakeDirecta = new BigDecimal("10.00");
        }
        // Stake para el parlay combinado: 5% del operativo, con piso de $10 MXN si el saldo lo permite
        BigDecimal stakeParlay = saldoOperativo.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        if (stakeParlay.compareTo(new BigDecimal("10.00")) < 0 && balance.saldoActual.compareTo(new BigDecimal("10.00")) >= 0) {
            stakeParlay = new BigDecimal("10.00");
        }

        String depLower = (deporteOpcional != null ? deporteOpcional.toLowerCase().replace("_", " ").replace("-", " ") : "");
        boolean esLigaMx = depLower.contains("liga mx") || depLower.contains("ligamx") || depLower.contains("mexic");
        boolean esFutbol = depLower.contains("futbol") || depLower.contains("soccer");
        boolean esNfl = depLower.contains("nfl") || depLower.contains("american");
        boolean esBaseball = depLower.contains("base") || depLower.contains("mlb");

        ZonedDateTime ahora = ZonedDateTime.now();
        ZonedDateTime limiteFuturo;
        int maxPicks = 4;

        String hqlValor;
        String hqlExtra;
        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("estado", EstadoPartido.NS);
        queryParams.put("ahora", ahora);
        // Filtro de Probabilidad Mínima: Solo picks con probabilidad estimada >= 45% (favoritos y líneas principales de alta probabilidad)
        queryParams.put("minProb", new BigDecimal("0.45"));

        if (esLigaMx) {
            limiteFuturo = ahora.plusDays(7);
            maxPicks = 10; // Mostrar todas las recomendaciones para la Liga MX
            queryParams.put("limite", limiteFuturo);
            queryParams.put("filtroLiga", "%liga mx%");
            hqlValor = "esValor = true and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and lower(evento.liga) like :filtroLiga and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
            hqlExtra = "esValor = false and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and lower(evento.liga) like :filtroLiga and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
        } else if (esNfl) {
            limiteFuturo = ahora.plusDays(6);
            queryParams.put("limite", limiteFuturo);
            queryParams.put("deporte", Deporte.NFL);
            hqlValor = "esValor = true and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and evento.deporte = :deporte and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
            hqlExtra = "esValor = false and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and evento.deporte = :deporte and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
        } else if (esBaseball) {
            limiteFuturo = ahora.plusDays(3);
            queryParams.put("limite", limiteFuturo);
            queryParams.put("deporte", Deporte.BASEBALL);
            hqlValor = "esValor = true and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and evento.deporte = :deporte and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
            hqlExtra = "esValor = false and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and evento.deporte = :deporte and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
        } else if (esFutbol) {
            limiteFuturo = ahora.plusDays(5);
            queryParams.put("limite", limiteFuturo);
            queryParams.put("deporte", Deporte.FUTBOL);
            hqlValor = "esValor = true and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and evento.deporte = :deporte and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
            hqlExtra = "esValor = false and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and evento.deporte = :deporte and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
        } else {
            limiteFuturo = ahora.plusHours(72);
            queryParams.put("limite", limiteFuturo);
            hqlValor = "esValor = true and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
            hqlExtra = "esValor = false and evento.estado = :estado and evento.fechaEvento > :ahora and evento.fechaEvento <= :limite and probabilidadEstimada >= :minProb order by probabilidadEstimada desc, ev desc";
        }

        // Obtener apuestas pendientes del usuario para no sugerir lo que ya jugó
        List<RegistroApuesta> apuestasPendientes = (userId != null && apuestaRepo != null)
                ? apuestaRepo.find("usuarioTelegramId = ?1 and resultado = ?2", userId, EstadoApuesta.PENDIENTE).list()
                : Collections.emptyList();

        // 1. Traer pool con EV+ y descartar partidos ya apostados
        List<MercadoCuota> poolValor = cuotaRepo.find(hqlValor, queryParams).page(0, 30).list();

        // Deduplicar: solo 1 selección por eventoId (la de mayor EV)
        Map<Long, MercadoCuota> mejorPorEvento = new java.util.LinkedHashMap<>();
        for (MercadoCuota mc : poolValor) {
            if (yaFueApostado(mc.evento, apuestasPendientes)) {
                continue; // DESCARTAR si el usuario ya tiene este partido o selección en juego
            }
            mejorPorEvento.putIfAbsent(mc.evento.id, mc);
        }
        List<MercadoCuota> cuotasValor = new ArrayList<>(mejorPorEvento.values());

        // 2. Si quedan menos picks distintos de los solicitados, rellenar desde pool general
        if (cuotasValor.size() < maxPicks) {
            List<MercadoCuota> poolExtra = cuotaRepo.find(hqlExtra, queryParams).page(0, 30).list();
            for (MercadoCuota mc : poolExtra) {
                if (yaFueApostado(mc.evento, apuestasPendientes)) {
                    continue;
                }
                if (!mejorPorEvento.containsKey(mc.evento.id)) {
                    cuotasValor.add(mc);
                    mejorPorEvento.put(mc.evento.id, mc);
                }
                if (cuotasValor.size() >= maxPicks) break;
            }
        }

        // Limitar al máximo determinado
        if (cuotasValor.size() > maxPicks) cuotasValor = cuotasValor.subList(0, maxPicks);

        if (!cuotasValor.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>();

            // 0. Resumen de Bankroll real directo en la tool para evitar alucinaciones
            Map<String, Object> resumenBankroll = new LinkedHashMap<>();
            resumenBankroll.put("tipo", "RESUMEN_BANKROLL");
            resumenBankroll.put("saldoActualMXN", balance.saldoActual);
            resumenBankroll.put("saldoOperativoMXN", saldoOperativo);
            resumenBankroll.put("reservaColchonMXN", balance.saldoActual.subtract(saldoOperativo).setScale(2, RoundingMode.HALF_UP));
            resumenBankroll.put("totalApostadoHistoricoMXN", balance.totalApostadoHistorico);
            result.add(resumenBankroll);

            BigDecimal momioCombinadoParlay = BigDecimal.ONE;
            List<String> legasParlay = new ArrayList<>();
            // Rastrear eventos ya en el parlay para evitar duplicados
            java.util.Set<Long> eventosEnParlay = new java.util.HashSet<>();

            int numPick = 1;
            for (MercadoCuota mc : cuotasValor) {
                Map<String, Object> pick = new LinkedHashMap<>();
                pick.put("tipo", "DIRECTA");
                pick.put("numeroPick", numPick++);
                pick.put("eventoId", mc.evento.id);
                pick.put("partido", mc.evento.equipoLocal + " vs " + mc.evento.equipoVisita);
                pick.put("liga", mc.evento.liga);
                
                String seleccionTexto;
                String equipoOSeleccion;
                if (mc.tipoMercado == TipoMercado.OVER_UNDER) {
                    seleccionTexto = mc.seleccion + " (" + mc.evento.equipoLocal + " vs " + mc.evento.equipoVisita + ")";
                    equipoOSeleccion = mc.seleccion;
                } else {
                    String nombreEquipo = "Home".equalsIgnoreCase(mc.seleccion) ? mc.evento.equipoLocal
                            : ("Away".equalsIgnoreCase(mc.seleccion) ? mc.evento.equipoVisita : "Empate");
                    seleccionTexto = mc.seleccion + " (" + nombreEquipo + ")";
                    equipoOSeleccion = nombreEquipo;
                }

                pick.put("seleccion", seleccionTexto);
                pick.put("equipoRecomendado", equipoOSeleccion);
                pick.put("tipoMercado", mc.tipoMercado != null ? mc.tipoMercado.name() : "MONEYLINE");
                pick.put("casaApuesta", mc.casaApuesta);
                pick.put("momio", mc.cuota);
                pick.put("momioAmericano", convertirDecimalAAmericano(mc.cuota));
                pick.put("ev", mc.ev);
                pick.put("esValor", mc.esValor);
                pick.put("probabilidadFair", mc.probabilidadEstimada);

                // Cuota mínima aceptable para mantener EV positivo (Breakeven = 1 / P_fair)
                BigDecimal pFair = mc.probabilidadEstimada;
                BigDecimal cuotaMinima = BigDecimal.ZERO;
                String momioAmericanoMin = "N/A";
                if (pFair != null && pFair.compareTo(BigDecimal.ZERO) > 0) {
                    cuotaMinima = BigDecimal.ONE.divide(pFair, 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.CEILING);
                    momioAmericanoMin = convertirDecimalAAmericano(cuotaMinima);
                }
                pick.put("cuotaMinimaAceptable", cuotaMinima);
                pick.put("momioAmericanoMinimo", momioAmericanoMin);
                pick.put("reglaMomioMinimo", "Momio mínimo aceptable: " + cuotaMinima + " (" + momioAmericanoMin + "). Si tu casa paga menos, el EV es negativo.");
                
                java.time.ZonedDateTime fechaCST = mc.evento.fechaEvento.withZoneSameInstant(java.time.ZoneId.of("America/Mexico_City"));
                pick.put("horario", fechaCST.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (z)")));
                pick.put("stakeRecomendadoMXN", stakeDirecta);

                BigDecimal retornoBruto = stakeDirecta.multiply(mc.cuota).setScale(2, RoundingMode.HALF_UP);
                pick.put("retornoBrutoMXN", retornoBruto);
                pick.put("gananciaNeta", retornoBruto.subtract(stakeDirecta).setScale(2, RoundingMode.HALF_UP));

                // Análisis estadístico
                try {
                    Map<String, Object> stats = estadisticasService.analizarPartido(
                        mc.evento.equipoLocalId, mc.evento.equipoVisitaId);
                    pick.put("analisisEstadistico", stats);
                } catch (Exception statsEx) {
                    pick.put("analisisEstadistico", Map.of("nota", "Stats no disponibles: " + statsEx.getMessage()));
                }

                result.add(pick);

                // Parlay: agregar si es de eventos distintos
                if (!eventosEnParlay.contains(mc.evento.id) && legasParlay.size() < 4) {
                    momioCombinadoParlay = momioCombinadoParlay.multiply(mc.cuota).setScale(2, RoundingMode.HALF_UP);
                    String legStr = mc.tipoMercado == TipoMercado.OVER_UNDER
                            ? mc.evento.equipoLocal + " vs " + mc.evento.equipoVisita + " → " + mc.seleccion + " @" + mc.cuota + " [" + mc.casaApuesta + "]"
                            : mc.evento.equipoLocal + " vs " + mc.evento.equipoVisita + " → " + equipoOSeleccion + " @" + mc.cuota + " (Mín: " + cuotaMinima + ") [" + mc.casaApuesta + "]";
                    legasParlay.add(legStr);
                    eventosEnParlay.add(mc.evento.id);
                }
            }

            // Agregar parlay sugerido si hay 2 o más eventos
            if (legasParlay.size() >= 2) {
                Map<String, Object> parlay = new LinkedHashMap<>();
                parlay.put("tipo", "PARLAY_SUGERIDO");
                parlay.put("piernas", legasParlay);
                parlay.put("momioCombinadoAprox", momioCombinadoParlay);
                parlay.put("stakeRecomendadoMXN", stakeParlay);
                BigDecimal retornoParlay = stakeParlay.multiply(momioCombinadoParlay).setScale(2, RoundingMode.HALF_UP);
                parlay.put("retornoBrutoMXN", retornoParlay);
                parlay.put("gananciaNeta", retornoParlay.subtract(stakeParlay).setScale(2, RoundingMode.HALF_UP));
                parlay.put("advertencia", "Parlay compuesto de " + legasParlay.size() + " partidos distintos.");
                result.add(parlay);
            }

            return result;
        }

        // 3. Fallback: próximos partidos sin cuotas aún
        List<EventoDeportivo> proximosPartidos = eventoRepo
                .find("estado = ?1 order by fechaEvento asc", EstadoPartido.NS)
                .page(0, 4)
                .list();

        if (!proximosPartidos.isEmpty()) {
            List<Map<String, Object>> fixtures = new ArrayList<>();
            for (EventoDeportivo ev : proximosPartidos) {
                Map<String, Object> fix = new LinkedHashMap<>();
                fix.put("tipo", "SIN_CUOTAS");
                fix.put("eventoId", ev.id);
                fix.put("partido", ev.equipoLocal + " vs " + ev.equipoVisita);
                fix.put("liga", ev.liga);
                fix.put("horario", ev.fechaEvento.toString());
                fix.put("mensaje", "Cuotas aún no registradas. Puedes ingresar momio manual.");
                fixtures.add(fix);
            }
            return fixtures;
        }

        return Collections.emptyList();
    }

    private boolean yaFueApostado(EventoDeportivo evento, List<RegistroApuesta> apuestasPendientes) {
        if (evento == null || apuestasPendientes == null || apuestasPendientes.isEmpty()) return false;
        String local = evento.equipoLocal != null ? evento.equipoLocal.toLowerCase() : "";
        String visita = evento.equipoVisita != null ? evento.equipoVisita.toLowerCase() : "";
        String localKey = extraerPalabraClave(local);
        String visitaKey = extraerPalabraClave(visita);

        for (RegistroApuesta ap : apuestasPendientes) {
            String pStr = ap.partido != null ? ap.partido.toLowerCase() : "";
            String pickStr = ap.pick != null ? ap.pick.toLowerCase() : "";
            String combined = pStr + " " + pickStr;

            // Coincidencia por ID de evento si venía en el pick o partido
            if (combined.contains("id " + evento.id) || combined.contains("#" + evento.id) || combined.contains(" " + evento.id + " ")) {
                return true;
            }

            // Coincidencia por palabras clave de equipos
            if (!localKey.isEmpty() && combined.contains(localKey)) return true;
            if (!visitaKey.isEmpty() && combined.contains(visitaKey)) return true;
            if (!local.isEmpty() && combined.contains(local)) return true;
            if (!visita.isEmpty() && combined.contains(visita)) return true;
        }
        return false;
    }

    private String extraerPalabraClave(String nombre) {
        if (nombre == null) return "";
        String s = nombre.toLowerCase().trim();
        if (s.contains("white sox")) return "white sox";
        if (s.contains("red sox")) return "red sox";
        if (s.contains("blue jays")) return "blue jays";
        String[] partes = s.split("\\s+");
        return partes[partes.length - 1];
    }

    public static String convertirDecimalAAmericano(BigDecimal cuota) {
        if (cuota == null) return "N/A";
        double d = cuota.doubleValue();
        if (d >= 2.0) {
            int am = (int) Math.round((d - 1.0) * 100.0);
            return "+" + am;
        } else if (d > 1.0) {
            int am = (int) Math.round(100.0 / (d - 1.0));
            return "-" + am;
        }
        return "N/A";
    }
}

package com.apuestas.service.impl;

import com.apuestas.client.TheOddsApiClient;
import com.apuestas.entity.EventoDeportivo;
import com.apuestas.entity.MercadoCuota;
import com.apuestas.entity.enums.Deporte;
import com.apuestas.entity.enums.EstadoPartido;
import com.apuestas.entity.enums.TipoMercado;
import com.apuestas.repository.EventoDeportivoRepository;
import com.apuestas.repository.MercadoCuotaRepository;
import com.apuestas.service.IngestionBatchService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class IngestionBatchServiceImpl implements IngestionBatchService {

    private static final Logger LOG = Logger.getLogger(IngestionBatchServiceImpl.class);

    @Inject
    @RestClient
    TheOddsApiClient oddsApiClient;

    @Inject
    EventoDeportivoRepository eventoRepo;

    @Inject
    MercadoCuotaRepository cuotaRepo;

    @Inject
    com.apuestas.service.BankrollService bankrollService;

    @Inject
    com.apuestas.repository.BalanceActualRepository balanceRepo;

    @ConfigProperty(name = "bayanometro.odds-api.key", defaultValue = "")
    String oddsApiKey;

    @ConfigProperty(name = "bayanometro.odds-api.sports", defaultValue = "soccer_epl,soccer_spain_la_liga,soccer_mexico_ligamx,soccer_uefa_champs_league,basketball_nba,americanfootball_nfl,baseball_mlb")
    String sportsConfig;

    @ConfigProperty(name = "bayanometro.batch.timezone", defaultValue = "America/Mexico_City")
    String timezone;

    /**
     * Executor dedicado para llamadas HTTP bloqueantes a The Odds API.
     * Evita bloquear el event loop de Vert.x durante resolución DNS o TCP lento.
     */
    private final ExecutorService httpExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "odds-api-http-worker");
        t.setDaemon(true);
        return t;
    });

    @Override
    @Scheduled(cron = "{bayanometro.batch.morning.cron}")
    public void ejecutarBatchMatutino() {
        LOG.info("Iniciando Turno 1 (Batch Matutino) con timezone " + ZoneId.of(timezone));
        ejecutarDescargaManualHoy();
    }

    @Override
    @Scheduled(cron = "{bayanometro.batch.afternoon.cron}")
    public void ejecutarRefrescoTarde() {
        LOG.info("Iniciando Turno 2 (Refresco Tarde) con timezone " + ZoneId.of(timezone));
        ejecutarDescargaManualHoy();
    }

    @Override
    @Scheduled(cron = "{bayanometro.batch.night.cron}")
    public void ejecutarConciliacionNocturna() {
        LOG.info("Iniciando Turno 3 (Conciliacion Nocturna) con timezone " + ZoneId.of(timezone));
        try {
            List<com.apuestas.entity.BalanceActual> balances = balanceRepo.listAll();
            for (com.apuestas.entity.BalanceActual b : balances) {
                if (b.usuarioTelegramId != null) {
                    LOG.info("Conciliando boletos para usuario: " + b.usuarioTelegramId);
                    bankrollService.conciliarResultadosYActualizarBank(b.usuarioTelegramId);
                }
            }
        } catch (Exception ex) {
            LOG.error("Error en Turno 3 conciliacion: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional
    public String ejecutarDescargaManualHoy() {
        if (oddsApiKey == null || oddsApiKey.isBlank() || "tu_api_key_aqui".equalsIgnoreCase(oddsApiKey)) {
            LOG.warn("THE_ODDS_API_KEY no configurada.");
            return "⚠️ No se ha configurado la variable THE_ODDS_API_KEY en tu entorno.\n"
                 + "Obtén una clave gratuita (500 consultas/mes) en https://the-odds-api.com y colócala en tu archivo .env como THE_ODDS_API_KEY=tu_clave para activar momios y partidos en tiempo real.";
        }

        String fechaHoy = LocalDate.now(ZoneId.of(timezone)).toString();
        LOG.info("Iniciando descarga de cuotas y partidos en vivo desde The Odds API para fecha: " + fechaHoy);

        int partidosGuardados = 0;
        int cuotasGuardadas = 0;
        String[] sports = sportsConfig.split(",");

        for (String sportKey : sports) {
            sportKey = sportKey.trim();
            if (sportKey.isEmpty()) continue;

            try {
                LOG.info("Consultando The Odds API para deporte: " + sportKey);

                // Ejecutar llamada HTTP en hilo dedicado para no bloquear el event loop de Vert.x
                final String sportKeyFinal = sportKey;
                Future<List<Map<String, Object>>> futureEvents = httpExecutor.submit(() ->
                    oddsApiClient.getCuotasDeporte(sportKeyFinal, oddsApiKey, "us,eu", "h2h,totals", "decimal")
                );
                List<Map<String, Object>> events = futureEvents.get(30, TimeUnit.SECONDS);

                if (events == null || events.isEmpty()) {
                    LOG.info("Sin eventos activos para deporte: " + sportKey);
                    continue;
                }

                for (Map<String, Object> eventMap : events) {
                    try {
                        String eventId = String.valueOf(eventMap.get("id"));
                        String sportKeyFromEvent = (String) eventMap.get("sport_key");
                        String actualSportKey = sportKeyFromEvent != null ? sportKeyFromEvent : sportKey;
                        Deporte deporteEnum = clasificarDeporte(actualSportKey);

                        String sportTitle = (String) eventMap.get("sport_title");
                        String homeTeam = (String) eventMap.get("home_team");
                        String awayTeam = (String) eventMap.get("away_team");
                        String commenceTimeStr = (String) eventMap.get("commence_time");
                        ZonedDateTime commenceTime = ZonedDateTime.parse(commenceTimeStr);

                        // Buscar o crear el evento deportivo
                        EventoDeportivo evento = eventoRepo.find("deporte = ?1 and fixtureExternoId = ?2", deporteEnum, eventId).firstResult();
                        if (evento == null) {
                            evento = new EventoDeportivo();
                            evento.deporte = deporteEnum;
                            evento.fixtureExternoId = eventId;
                        }

                        evento.ligaId = Math.abs((long) actualSportKey.hashCode());
                        evento.liga = sportTitle != null ? sportTitle : actualSportKey;
                        evento.temporada = commenceTime.getYear();
                        evento.equipoLocalId = Math.abs((long) homeTeam.hashCode());
                        evento.equipoLocal = homeTeam;
                        evento.equipoVisitaId = Math.abs((long) awayTeam.hashCode());
                        evento.equipoVisita = awayTeam;
                        evento.fechaEvento = commenceTime;
                        evento.estado = EstadoPartido.NS;

                        eventoRepo.persist(evento);
                        partidosGuardados++;

                        // Procesar casas de apuestas con enfoque multi-bookmaker cuantitativo (Sharp vs Soft)
                        List<Map<String, Object>> bookmakers = (List<Map<String, Object>>) eventMap.get("bookmakers");
                        if (bookmakers == null || bookmakers.isEmpty()) continue;

                        // 1. Extraer mercados H2H y TOTALS (Over/Under)
                        Map<String, Map<String, BigDecimal>> cuotasPorBookmaker = new LinkedHashMap<>();
                        Map<String, BigDecimal> overroundPorBookmaker = new LinkedHashMap<>();
                        TipoMercado tipoMercadoEvento = TipoMercado.MONEYLINE;
                        String pinnacleBookmakerTitle = null;

                        // Estructura Totals: punto (ej: "2.5") -> { bookmakerTitle -> { "Over" -> cuota, "Under" -> cuota } }
                        Map<String, Map<String, Map<String, BigDecimal>>> totalsPorPunto = new LinkedHashMap<>();
                        Map<String, String> pinnaclePorPunto = new LinkedHashMap<>();

                        for (Map<String, Object> bm : bookmakers) {
                            String bmTitle = (String) bm.get("title");
                            String bmKey = String.valueOf(bm.get("key")).toLowerCase();

                            List<Map<String, Object>> markets = (List<Map<String, Object>>) bm.get("markets");
                            if (markets == null || markets.isEmpty()) continue;

                            for (Map<String, Object> market : markets) {
                                String marketKey = (String) market.get("key");
                                if ("h2h".equalsIgnoreCase(marketKey)) {
                                    List<Map<String, Object>> outcomes = (List<Map<String, Object>>) market.get("outcomes");
                                    if (outcomes == null || outcomes.size() < 2) continue;

                                    TipoMercado tipoMercado = outcomes.size() == 3 ? TipoMercado.ONE_X_TWO : TipoMercado.MONEYLINE;
                                    tipoMercadoEvento = tipoMercado;

                                    Map<String, BigDecimal> mapaCuotas = new LinkedHashMap<>();
                                    BigDecimal sumaProbImp = BigDecimal.ZERO;
                                    boolean cuotasValidas = true;

                                    for (Map<String, Object> out : outcomes) {
                                        BigDecimal cuotaVal;
                                        try {
                                            cuotaVal = new BigDecimal(out.get("price").toString());
                                        } catch (Exception ex) {
                                            cuotasValidas = false;
                                            break;
                                        }
                                        if (cuotaVal.compareTo(new BigDecimal("1.01")) < 0) {
                                            cuotasValidas = false;
                                            break;
                                        }

                                        String outcomeName = (String) out.get("name");
                                        String seleccion;
                                        if (outcomeName.equalsIgnoreCase(homeTeam)) {
                                            seleccion = "Home";
                                        } else if (outcomeName.equalsIgnoreCase(awayTeam)) {
                                            seleccion = "Away";
                                        } else {
                                            seleccion = "Draw";
                                        }

                                        mapaCuotas.put(seleccion, cuotaVal);
                                        BigDecimal probImp = BigDecimal.ONE.divide(cuotaVal, 6, RoundingMode.HALF_UP);
                                        sumaProbImp = sumaProbImp.add(probImp);
                                    }

                                    // Validar que el mercado esté completo y sin datos corruptos (overround realista entre 0.98 y 1.25)
                                    if (cuotasValidas && mapaCuotas.size() >= (tipoMercado == TipoMercado.ONE_X_TWO ? 3 : 2)
                                            && sumaProbImp.compareTo(new BigDecimal("0.98")) >= 0
                                            && sumaProbImp.compareTo(new BigDecimal("1.25")) <= 0) {
                                        cuotasPorBookmaker.put(bmTitle, mapaCuotas);
                                        overroundPorBookmaker.put(bmTitle, sumaProbImp);
                                        if (bmKey.contains("pinnacle")) {
                                            pinnacleBookmakerTitle = bmTitle;
                                        }
                                    }
                                } else if ("totals".equalsIgnoreCase(marketKey)) {
                                    List<Map<String, Object>> outcomes = (List<Map<String, Object>>) market.get("outcomes");
                                    if (outcomes != null && outcomes.size() >= 2) {
                                        BigDecimal overPrice = null;
                                        BigDecimal underPrice = null;
                                        String pointKey = null;

                                        for (Map<String, Object> out : outcomes) {
                                            String name = (String) out.get("name");
                                            Object pt = out.get("point");
                                            if (pt != null) pointKey = String.valueOf(pt);
                                            try {
                                                BigDecimal pr = new BigDecimal(out.get("price").toString());
                                                if ("Over".equalsIgnoreCase(name)) overPrice = pr;
                                                else if ("Under".equalsIgnoreCase(name)) underPrice = pr;
                                            } catch (Exception ignored) {}
                                        }

                                        if (pointKey != null && overPrice != null && underPrice != null
                                                && overPrice.compareTo(new BigDecimal("1.01")) >= 0 && underPrice.compareTo(new BigDecimal("1.01")) >= 0) {
                                            BigDecimal sumTotals = BigDecimal.ONE.divide(overPrice, 6, RoundingMode.HALF_UP)
                                                    .add(BigDecimal.ONE.divide(underPrice, 6, RoundingMode.HALF_UP));
                                            if (sumTotals.compareTo(new BigDecimal("0.98")) >= 0 && sumTotals.compareTo(new BigDecimal("1.25")) <= 0) {
                                                totalsPorPunto.putIfAbsent(pointKey, new LinkedHashMap<>());
                                                Map<String, BigDecimal> bmTotals = new LinkedHashMap<>();
                                                bmTotals.put("Over", overPrice);
                                                bmTotals.put("Under", underPrice);
                                                totalsPorPunto.get(pointKey).put(bmTitle, bmTotals);
                                                if (bmKey.contains("pinnacle")) {
                                                    pinnaclePorPunto.put(pointKey, bmTitle);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Determinar la Probabilidad Justa (P_fair) para H2H
                        if (!cuotasPorBookmaker.isEmpty()) {
                            Map<String, BigDecimal> probFairMap = new LinkedHashMap<>();
                            List<String> seleccionesPosibles = tipoMercadoEvento == TipoMercado.ONE_X_TWO
                                    ? List.of("Home", "Away", "Draw")
                                    : List.of("Home", "Away");

                            if (pinnacleBookmakerTitle != null) {
                                Map<String, BigDecimal> pinCuotas = cuotasPorBookmaker.get(pinnacleBookmakerTitle);
                                BigDecimal pinOverround = overroundPorBookmaker.get(pinnacleBookmakerTitle);
                                for (String sel : seleccionesPosibles) {
                                    BigDecimal c = pinCuotas.get(sel);
                                    if (c != null && c.compareTo(BigDecimal.ZERO) > 0) {
                                        BigDecimal probImp = BigDecimal.ONE.divide(c, 6, RoundingMode.HALF_UP);
                                        BigDecimal pFair = probImp.divide(pinOverround, 4, RoundingMode.HALF_UP);
                                        probFairMap.put(sel, pFair);
                                    }
                                }
                            } else {
                                for (String sel : seleccionesPosibles) {
                                    BigDecimal sumaProbFairConsenso = BigDecimal.ZERO;
                                    int casasConEstaSeleccion = 0;
                                    for (Map.Entry<String, Map<String, BigDecimal>> entry : cuotasPorBookmaker.entrySet()) {
                                        BigDecimal c = entry.getValue().get(sel);
                                        BigDecimal ov = overroundPorBookmaker.get(entry.getKey());
                                        if (c != null && ov != null && c.compareTo(BigDecimal.ZERO) > 0 && ov.compareTo(BigDecimal.ZERO) > 0) {
                                            BigDecimal probImp = BigDecimal.ONE.divide(c, 6, RoundingMode.HALF_UP);
                                            BigDecimal pFairCasa = probImp.divide(ov, 6, RoundingMode.HALF_UP);
                                            sumaProbFairConsenso = sumaProbFairConsenso.add(pFairCasa);
                                            casasConEstaSeleccion++;
                                        }
                                    }
                                    if (casasConEstaSeleccion > 0) {
                                        BigDecimal pFairFinal = sumaProbFairConsenso.divide(new BigDecimal(casasConEstaSeleccion), 4, RoundingMode.HALF_UP);
                                        probFairMap.put(sel, pFairFinal);
                                    }
                                }
                            }

                            // Persistir H2H
                            for (Map.Entry<String, Map<String, BigDecimal>> entry : cuotasPorBookmaker.entrySet()) {
                                String bookmakerTitle = entry.getKey();
                                Map<String, BigDecimal> mapaCuotas = entry.getValue();

                                for (Map.Entry<String, BigDecimal> selEntry : mapaCuotas.entrySet()) {
                                    String seleccion = selEntry.getKey();
                                    BigDecimal cuota = selEntry.getValue();
                                    BigDecimal probFair = probFairMap.get(seleccion);
                                    if (probFair == null || probFair.compareTo(BigDecimal.ZERO) <= 0) continue;

                                    BigDecimal evCalc = probFair.multiply(cuota).subtract(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
                                    boolean enRango = cuota.compareTo(new BigDecimal("1.25")) >= 0 && cuota.compareTo(new BigDecimal("3.50")) <= 0;
                                    boolean esValor = enRango && evCalc.compareTo(BigDecimal.ZERO) > 0;

                                    MercadoCuota mc = cuotaRepo.find("evento.id = ?1 and tipoMercado = ?2 and seleccion = ?3 and casaApuesta = ?4",
                                            evento.id, tipoMercadoEvento, seleccion, bookmakerTitle).firstResult();
                                    if (mc == null) {
                                        mc = new MercadoCuota();
                                        mc.evento = evento;
                                        mc.casaApuesta = bookmakerTitle;
                                        mc.tipoMercado = tipoMercadoEvento;
                                        mc.seleccion = seleccion;
                                    }

                                    mc.cuota = cuota;
                                    mc.probabilidadEstimada = probFair;
                                    mc.ev = evCalc;
                                    mc.esValor = esValor;

                                    cuotaRepo.persist(mc);
                                    cuotasGuardadas++;
                                }
                            }
                        }

                        // 3. Evaluar Totals (Over / Under)
                        for (Map.Entry<String, Map<String, Map<String, BigDecimal>>> pEntry : totalsPorPunto.entrySet()) {
                            String pointKey = pEntry.getKey();
                            Map<String, Map<String, BigDecimal>> bmsForPoint = pEntry.getValue();
                            if (bmsForPoint.isEmpty()) continue;

                            BigDecimal pFairOver = null;
                            BigDecimal pFairUnder = null;

                            String pinBm = pinnaclePorPunto.get(pointKey);
                            if (pinBm != null && bmsForPoint.containsKey(pinBm)) {
                                Map<String, BigDecimal> pinO = bmsForPoint.get(pinBm);
                                BigDecimal ov = pinO.get("Over");
                                BigDecimal un = pinO.get("Under");
                                BigDecimal overround = BigDecimal.ONE.divide(ov, 6, RoundingMode.HALF_UP)
                                        .add(BigDecimal.ONE.divide(un, 6, RoundingMode.HALF_UP));
                                pFairOver = BigDecimal.ONE.divide(ov, 6, RoundingMode.HALF_UP).divide(overround, 4, RoundingMode.HALF_UP);
                                pFairUnder = BigDecimal.ONE.divide(un, 6, RoundingMode.HALF_UP).divide(overround, 4, RoundingMode.HALF_UP);
                            } else {
                                BigDecimal sumFairOver = BigDecimal.ZERO;
                                BigDecimal sumFairUnder = BigDecimal.ZERO;
                                int count = 0;
                                for (Map<String, BigDecimal> pair : bmsForPoint.values()) {
                                    BigDecimal ov = pair.get("Over");
                                    BigDecimal un = pair.get("Under");
                                    BigDecimal overround = BigDecimal.ONE.divide(ov, 6, RoundingMode.HALF_UP)
                                            .add(BigDecimal.ONE.divide(un, 6, RoundingMode.HALF_UP));
                                    sumFairOver = sumFairOver.add(BigDecimal.ONE.divide(ov, 6, RoundingMode.HALF_UP).divide(overround, 4, RoundingMode.HALF_UP));
                                    sumFairUnder = sumFairUnder.add(BigDecimal.ONE.divide(un, 6, RoundingMode.HALF_UP).divide(overround, 4, RoundingMode.HALF_UP));
                                    count++;
                                }
                                if (count > 0) {
                                    pFairOver = sumFairOver.divide(new BigDecimal(count), 4, RoundingMode.HALF_UP);
                                    pFairUnder = sumFairUnder.divide(new BigDecimal(count), 4, RoundingMode.HALF_UP);
                                }
                            }

                            if (pFairOver == null || pFairUnder == null) continue;

                            for (Map.Entry<String, Map<String, BigDecimal>> bmEntry : bmsForPoint.entrySet()) {
                                String bmTitle = bmEntry.getKey();
                                Map<String, BigDecimal> pair = bmEntry.getValue();

                                for (String selType : List.of("Over", "Under")) {
                                    BigDecimal cuota = pair.get(selType);
                                    BigDecimal pFair = "Over".equals(selType) ? pFairOver : pFairUnder;
                                    if (cuota == null || pFair == null || pFair.compareTo(BigDecimal.ZERO) <= 0) continue;

                                    BigDecimal evCalc = pFair.multiply(cuota).subtract(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
                                    boolean enRango = cuota.compareTo(new BigDecimal("1.25")) >= 0 && cuota.compareTo(new BigDecimal("3.50")) <= 0;
                                    boolean esValor = enRango && evCalc.compareTo(BigDecimal.ZERO) > 0;

                                    String selNombre = selType + " " + pointKey;
                                    MercadoCuota mc = cuotaRepo.find("evento.id = ?1 and tipoMercado = ?2 and seleccion = ?3 and casaApuesta = ?4",
                                            evento.id, TipoMercado.OVER_UNDER, selNombre, bmTitle).firstResult();
                                    if (mc == null) {
                                        mc = new MercadoCuota();
                                        mc.evento = evento;
                                        mc.casaApuesta = bmTitle;
                                        mc.tipoMercado = TipoMercado.OVER_UNDER;
                                        mc.seleccion = selNombre;
                                    }

                                    mc.cuota = cuota;
                                    mc.probabilidadEstimada = pFair;
                                    mc.ev = evCalc;
                                    mc.esValor = esValor;

                                    cuotaRepo.persist(mc);
                                    cuotasGuardadas++;
                                }
                            }
                        }

                    } catch (Exception ex) {
                        LOG.warn("Error parseando evento deportivo individual: " + ex.getMessage());
                    }
                }

            } catch (Exception sportEx) {
                LOG.error("Error al consultar The Odds API para " + sportKey + ": " + sportEx.getMessage());
            }
        }

        return String.format("✅ Ingesta The Odds API completada. Se procesaron %d eventos y se registraron %d cuotas en vivo en la base de datos.",
                partidosGuardados, cuotasGuardadas);
    }

    private Deporte clasificarDeporte(String sportKey) {
        if (sportKey.startsWith("soccer")) return Deporte.FUTBOL;
        if (sportKey.startsWith("basketball")) return Deporte.BASKETBALL;
        if (sportKey.startsWith("baseball")) return Deporte.BASEBALL;
        if (sportKey.startsWith("americanfootball")) return Deporte.NFL;
        return Deporte.FUTBOL;
    }

    private Map<String, Object> seleccionarBookmaker(List<Map<String, Object>> bookmakers) {
        for (Map<String, Object> bm : bookmakers) {
            String key = String.valueOf(bm.get("key")).toLowerCase();
            if (key.contains("pinnacle")) return bm;
        }
        for (Map<String, Object> bm : bookmakers) {
            String key = String.valueOf(bm.get("key")).toLowerCase();
            if (key.contains("bet365")) return bm;
        }
        return bookmakers.get(0);
    }
}

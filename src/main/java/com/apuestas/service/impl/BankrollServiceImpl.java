package com.apuestas.service.impl;

import com.apuestas.client.TheOddsApiClient;
import com.apuestas.entity.BalanceActual;
import com.apuestas.entity.EventoDeportivo;
import com.apuestas.entity.MercadoCuota;
import com.apuestas.entity.MovimientoBalance;
import com.apuestas.entity.RegistroApuesta;
import com.apuestas.entity.enums.EstadoApuesta;
import com.apuestas.entity.enums.TipoJugada;
import com.apuestas.entity.enums.TipoMovimiento;
import com.apuestas.exception.SaldoInsuficienteException;
import com.apuestas.repository.BalanceActualRepository;
import com.apuestas.repository.EventoDeportivoRepository;
import com.apuestas.repository.MercadoCuotaRepository;
import com.apuestas.repository.MovimientoBalanceRepository;
import com.apuestas.repository.RegistroApuestaRepository;
import com.apuestas.service.BankrollService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

@ApplicationScoped
public class BankrollServiceImpl implements BankrollService {

    private static final Logger LOG = Logger.getLogger(BankrollServiceImpl.class);

    @Inject
    BalanceActualRepository balanceRepo;

    @Inject
    MovimientoBalanceRepository movimientoRepo;

    @Inject
    RegistroApuestaRepository apuestaRepo;

    @Inject
    EventoDeportivoRepository eventoRepo;

    @Inject
    MercadoCuotaRepository cuotaRepo;

    @Inject
    @RestClient
    TheOddsApiClient oddsApiClient;

    @ConfigProperty(name = "bayanometro.odds-api.key", defaultValue = "")
    String oddsApiKey;

    @ConfigProperty(name = "bayanometro.odds-api.sports", defaultValue = "soccer_epl,soccer_spain_la_liga,soccer_mexico_ligamx,soccer_uefa_champs_league,basketball_nba,americanfootball_nfl,baseball_mlb")
    String sportsConfig;

    private final Map<String, List<Map<String, Object>>> scoresCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, java.time.Instant> scoresCacheExpiry = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    @Transactional
    @Retry(maxRetries = 3, retryOn = OptimisticLockException.class)
    public String registrarApuestaDirecta(Long userId, Long eventoId, String seleccion, BigDecimal cuotaReal, BigDecimal monto) {
        BalanceActual balance = obtenerOInicializarBalance(userId);
        if (balance.saldoActual.compareTo(monto) < 0) {
            throw new SaldoInsuficienteException(balance.saldoActual, monto);
        }

        BigDecimal saldoPrevio = balance.saldoActual;
        balance.saldoActual = balance.saldoActual.subtract(monto);
        balance.totalApostadoHistorico = balance.totalApostadoHistorico.add(monto);

        EventoDeportivo ev = null;
        if (eventoId != null) {
            ev = eventoRepo.findById(eventoId);
        }
        if (ev == null && seleccion != null && !seleccion.isBlank()) {
            String trimmed = seleccion.trim();
            // Buscar si es un ID numérico directo (ej. "504" o "#504")
            String soloDigitos = trimmed.replaceAll("[^0-9]", "");
            if (!soloDigitos.isEmpty() && trimmed.length() <= 8 && !trimmed.toLowerCase().contains("cardinals") && !trimmed.toLowerCase().contains("packers")) {
                try {
                    Long posId = Long.parseLong(soloDigitos);
                    ev = eventoRepo.findById(posId);
                } catch (Exception ignored) {}
            }
            if (ev == null) {
                String cleanSel = trimmed.toLowerCase()
                        .replace("away", "")
                        .replace("home", "")
                        .replace("draw", "")
                        .replace("win", "")
                        .replace("vs", "")
                        .replace("pick", "")
                        .replace("#", "")
                        .trim();
                if (!cleanSel.isEmpty()) {
                    String term = "%" + cleanSel + "%";
                    ev = eventoRepo.find("(lower(equipoLocal) like ?1 or lower(equipoVisita) like ?1 or lower(liga) like ?1) and fechaEvento > ?2 order by fechaEvento asc",
                            term, ZonedDateTime.now().minusHours(12)).firstResult();
                }
            }
        }

        RegistroApuesta apuesta = new RegistroApuesta();
        apuesta.usuarioTelegramId = userId;
        apuesta.tipoJugada = TipoJugada.DIRECTA;
        if (ev != null) {
            apuesta.deporte = ev.deporte != null ? ev.deporte.name() : "GENERAL";
            apuesta.liga = ev.liga;
            apuesta.partido = ev.equipoLocal + " vs " + ev.equipoVisita;
            apuesta.pick = seleccion;
        } else {
            apuesta.deporte = "GENERAL";
            apuesta.partido = seleccion;
            apuesta.pick = seleccion;
        }
        apuesta.momio = cuotaReal;
        apuesta.montoApostado = monto;
        apuesta.balanceResultante = balance.saldoActual;
        apuestaRepo.persist(apuesta);

        MovimientoBalance mov = new MovimientoBalance();
        mov.usuarioTelegramId = userId;
        mov.tipoMovimiento = TipoMovimiento.APUESTA;
        mov.monto = monto;
        mov.saldoPrevio = saldoPrevio;
        mov.saldoPosterior = balance.saldoActual;
        mov.apuesta = apuesta;
        mov.descripcion = "Apuesta directa: " + (apuesta.partido != null ? apuesta.partido + " - " : "") + seleccion + " @" + cuotaReal;
        
        movimientoRepo.persist(mov);
        balanceRepo.persist(balance);
        
        String evaluacionValor = "";
        if (ev != null && cuotaReal != null) {
            try {
                List<MercadoCuota> cuotas = cuotaRepo.find("evento.id = ?1", ev.id).list();
                MercadoCuota mcMatch = null;
                String selLower = seleccion.toLowerCase();
                for (MercadoCuota mc : cuotas) {
                    if (selLower.contains(mc.seleccion.toLowerCase()) ||
                        (ev.equipoLocal != null && selLower.contains(ev.equipoLocal.toLowerCase()) && "Home".equalsIgnoreCase(mc.seleccion)) ||
                        (ev.equipoVisita != null && selLower.contains(ev.equipoVisita.toLowerCase()) && "Away".equalsIgnoreCase(mc.seleccion))) {
                        mcMatch = mc;
                        break;
                    }
                }
                if (mcMatch == null && !cuotas.isEmpty()) {
                    mcMatch = cuotas.get(0);
                }
                if (mcMatch != null && mcMatch.probabilidadEstimada != null && mcMatch.probabilidadEstimada.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pFair = mcMatch.probabilidadEstimada;
                    BigDecimal cuotaMin = BigDecimal.ONE.divide(pFair, 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.CEILING);
                    String amMin = BetEngineMathServiceImpl.convertirDecimalAAmericano(cuotaMin);
                    BigDecimal evReal = cuotaReal.multiply(pFair).subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                    if (evReal.compareTo(BigDecimal.ZERO) >= 0) {
                        evaluacionValor = String.format("\n📈 EV Real con tu momio (%s): +%s%% (Momio mínimo recomendado: %s [%s] - ¡Excelente entrada de valor!)", cuotaReal, evReal, cuotaMin, amMin);
                    } else {
                        evaluacionValor = String.format("\n📉 EV Real con tu momio (%s): %s%% (Ojo: el momio mínimo para ventaja matemática era %s [%s])", cuotaReal, evReal, cuotaMin, amMin);
                    }
                }
            } catch (Exception ex) {
                // Silencioso si falla la comparación
            }
        }

        String descEvento = ev != null ? (ev.equipoLocal + " vs " + ev.equipoVisita + " [" + seleccion + "]") : seleccion;
        return String.format("✅ Apuesta registrada exitosamente (Boleto #%d): %s a cuota %s por $%s MXN. Saldo anterior: $%s MXN | Nuevo saldo: $%s MXN.%s",
                apuesta.id, descEvento, cuotaReal, monto, saldoPrevio, balance.saldoActual, evaluacionValor);
    }

    @Override
    @Transactional
    @Retry(maxRetries = 3, retryOn = OptimisticLockException.class)
    public String registrarParlay(Long userId, List<Map<String, Object>> piernas, BigDecimal monto) {
        BalanceActual balance = obtenerOInicializarBalance(userId);
        if (balance.saldoActual.compareTo(monto) < 0) {
            throw new SaldoInsuficienteException(balance.saldoActual, monto);
        }

        BigDecimal saldoPrevio = balance.saldoActual;
        balance.saldoActual = balance.saldoActual.subtract(monto);
        balance.totalApostadoHistorico = balance.totalApostadoHistorico.add(monto);

        RegistroApuesta apuesta = new RegistroApuesta();
        apuesta.usuarioTelegramId = userId;
        apuesta.tipoJugada = TipoJugada.PARLAY;
        apuesta.deporte = "MIXTO";
        apuesta.momio = BigDecimal.valueOf(2.0); // Dummy for now
        apuesta.montoApostado = monto;
        apuesta.balanceResultante = balance.saldoActual;
        apuestaRepo.persist(apuesta);

        MovimientoBalance mov = new MovimientoBalance();
        mov.usuarioTelegramId = userId;
        mov.tipoMovimiento = TipoMovimiento.APUESTA;
        mov.monto = monto;
        mov.saldoPrevio = saldoPrevio;
        mov.saldoPosterior = balance.saldoActual;
        mov.apuesta = apuesta;
        mov.descripcion = "Apuesta parlay";
        
        movimientoRepo.persist(mov);
        balanceRepo.persist(balance);
        
        return "Parlay registrado. Nuevo saldo: $" + balance.saldoActual;
    }

    @Override
    @Transactional
    @Retry(maxRetries = 3, retryOn = OptimisticLockException.class)
    public String procesarRetiro(Long userId, BigDecimal monto, String concepto) {
        BalanceActual balance = obtenerOInicializarBalance(userId);
        if (balance.saldoActual.compareTo(monto) < 0) {
            throw new SaldoInsuficienteException(balance.saldoActual, monto);
        }

        BigDecimal saldoPrevio = balance.saldoActual;
        balance.saldoActual = balance.saldoActual.subtract(monto);
        balance.totalRetiradoHistorico = balance.totalRetiradoHistorico.add(monto);
        
        MovimientoBalance mov = new MovimientoBalance();
        mov.usuarioTelegramId = userId;
        mov.tipoMovimiento = TipoMovimiento.RETIRO;
        mov.monto = monto;
        mov.saldoPrevio = saldoPrevio;
        mov.saldoPosterior = balance.saldoActual;
        mov.descripcion = concepto;
        
        movimientoRepo.persist(mov);
        balanceRepo.persist(balance);
        
        return "Retiro procesado. Nuevo saldo: $" + balance.saldoActual;
    }

    @Override
    @Transactional
    @Retry(maxRetries = 3, retryOn = OptimisticLockException.class)
    public String registrarDeposito(BigDecimal monto, String concepto) {
        return "Not Implemented";
    }

    @Override
    @Transactional
    public BalanceActual obtenerResumenActual(Long userId) {
        return obtenerOInicializarBalance(userId);
    }

    @Override
    public List<Map<String, Object>> obtenerHistorialApuestas(Long userId, int limite) {
        List<RegistroApuesta> apuestas = apuestaRepo
                .find("usuarioTelegramId = ?1 order by creadoEn desc", userId)
                .page(0, limite > 0 ? limite : 10)
                .list();

        List<Map<String, Object>> lista = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (z)").withZone(ZoneId.of("America/Mexico_City"));

        for (RegistroApuesta a : apuestas) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ticketId", a.id);
            map.put("tipoJugada", a.tipoJugada != null ? a.tipoJugada.name() : "DIRECTA");
            map.put("deporte", a.deporte);
            map.put("liga", a.liga);
            map.put("partido", a.partido);
            map.put("pick", a.pick);
            map.put("momio", a.momio);
            map.put("montoApostadoMXN", a.montoApostado);
            map.put("resultado", a.resultado != null ? a.resultado.name() : "PENDIENTE");
            BigDecimal retPotencial = a.montoApostado.multiply(a.momio).setScale(2, RoundingMode.HALF_UP);
            map.put("retornoPotencialMXN", retPotencial);
            map.put("gananciaPotencialMXN", retPotencial.subtract(a.montoApostado).setScale(2, RoundingMode.HALF_UP));
            map.put("balanceResultanteMXN", a.balanceResultante);
            if (a.creadoEn != null) {
                map.put("fechaRegistro", fmt.format(a.creadoEn));
            }
            lista.add(map);
        }
        return lista;
    }

    private BalanceActual obtenerOInicializarBalance(Long userId) {
        Optional<BalanceActual> balOpt = balanceRepo.find("usuarioTelegramId", userId).firstResultOptional();
        if (balOpt.isEmpty()) {
            BalanceActual b = new BalanceActual();
            b.usuarioTelegramId = userId;
            balanceRepo.persist(b);
            return b;
        }
        return balOpt.get();
    }

    @Override
    @Transactional
    public String liquidarApuestaManual(Long userId, Long boletoId, String resultadoStr) {
        RegistroApuesta apuesta = apuestaRepo.findById(boletoId);
        if (apuesta == null) {
            return "Boleto #" + boletoId + " no encontrado en el sistema.";
        }
        if (!apuesta.usuarioTelegramId.equals(userId)) {
            return "El Boleto #" + boletoId + " no pertenece a tu usuario.";
        }
        if (apuesta.resultado != EstadoApuesta.PENDIENTE) {
            return String.format("El Boleto #%d ya fue resuelto anteriormente como %s.", boletoId, apuesta.resultado);
        }

        EstadoApuesta estado;
        String resUpper = resultadoStr.toUpperCase().trim();
        if (resUpper.contains("GANA") || resUpper.contains("WIN") || resUpper.contains("COBRA")) {
            estado = EstadoApuesta.GANADA;
        } else if (resUpper.contains("PERD") || resUpper.contains("LOSS") || resUpper.contains("FALL")) {
            estado = EstadoApuesta.PERDIDA;
        } else if (resUpper.contains("CANC") || resUpper.contains("VOID") || resUpper.contains("PUSH") || resUpper.contains("EMPATE")) {
            estado = EstadoApuesta.CANCELADA;
        } else {
            return "Resultado no reconocido ('" + resultadoStr + "'). Usa: GANADA, PERDIDA o CANCELADA.";
        }

        BalanceActual balance = obtenerOInicializarBalance(userId);
        return liquidarApuestaInternal(apuesta, balance, estado, "Resolución manual por el usuario");
    }

    private String liquidarApuestaInternal(RegistroApuesta apuesta, BalanceActual balance, EstadoApuesta nuevoEstado, String motivo) {
        BigDecimal saldoPrevio = balance.saldoActual;

        if (nuevoEstado == EstadoApuesta.GANADA) {
            BigDecimal montoCobrado = apuesta.montoApostado.multiply(apuesta.momio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal gananciaNeta = montoCobrado.subtract(apuesta.montoApostado).setScale(2, RoundingMode.HALF_UP);

            balance.saldoActual = balance.saldoActual.add(montoCobrado);
            balance.totalCobradoHistorico = balance.totalCobradoHistorico.add(montoCobrado);
            balance.apuestasGanadas = balance.apuestasGanadas + 1;

            apuesta.resultado = EstadoApuesta.GANADA;
            apuesta.gananciaNeta = gananciaNeta;
            apuesta.balanceResultante = balance.saldoActual;

            MovimientoBalance mov = new MovimientoBalance();
            mov.usuarioTelegramId = apuesta.usuarioTelegramId;
            mov.tipoMovimiento = TipoMovimiento.PREMIO;
            mov.monto = montoCobrado;
            mov.saldoPrevio = saldoPrevio;
            mov.saldoPosterior = balance.saldoActual;
            mov.apuesta = apuesta;
            mov.descripcion = "Premio cobrado: Boleto #" + apuesta.id + " (" + apuesta.partido + ") [" + apuesta.pick + " @" + apuesta.momio + "]. " + motivo;

            movimientoRepo.persist(mov);
            balanceRepo.persist(balance);
            apuestaRepo.persist(apuesta);

            return String.format("🟢 Boleto #%d GANADO: Cobro $%s MXN (Ganancia neta: +$%s MXN). Nuevo saldo: $%s MXN",
                    apuesta.id, montoCobrado, gananciaNeta, balance.saldoActual);

        } else if (nuevoEstado == EstadoApuesta.PERDIDA) {
            balance.apuestasPerdidas = balance.apuestasPerdidas + 1;

            apuesta.resultado = EstadoApuesta.PERDIDA;
            apuesta.gananciaNeta = apuesta.montoApostado.negate();
            apuesta.balanceResultante = balance.saldoActual;

            balanceRepo.persist(balance);
            apuestaRepo.persist(apuesta);

            return String.format("🔴 Boleto #%d PERDIDO: -$%s MXN. Saldo actual: $%s MXN",
                    apuesta.id, apuesta.montoApostado, balance.saldoActual);

        } else if (nuevoEstado == EstadoApuesta.CANCELADA) {
            balance.saldoActual = balance.saldoActual.add(apuesta.montoApostado);

            apuesta.resultado = EstadoApuesta.CANCELADA;
            apuesta.gananciaNeta = BigDecimal.ZERO;
            apuesta.balanceResultante = balance.saldoActual;

            MovimientoBalance mov = new MovimientoBalance();
            mov.usuarioTelegramId = apuesta.usuarioTelegramId;
            mov.tipoMovimiento = TipoMovimiento.REEMBOLSO;
            mov.monto = apuesta.montoApostado;
            mov.saldoPrevio = saldoPrevio;
            mov.saldoPosterior = balance.saldoActual;
            mov.apuesta = apuesta;
            mov.descripcion = "Reembolso por cancelacion: Boleto #" + apuesta.id;

            movimientoRepo.persist(mov);
            balanceRepo.persist(balance);
            apuestaRepo.persist(apuesta);

            return String.format("⚪ Boleto #%d CANCELADO: Reembolsado $%s MXN. Nuevo saldo: $%s MXN",
                    apuesta.id, apuesta.montoApostado, balance.saldoActual);
        }

        return "Estado no reconocido para boleto #" + apuesta.id;
    }

    @Override
    @Transactional
    public Map<String, Object> conciliarResultadosYActualizarBank(Long userId) {
        BalanceActual balance = obtenerOInicializarBalance(userId);
        BigDecimal saldoInicial = balance.saldoActual;

        List<RegistroApuesta> pendientes = apuestaRepo
                .find("usuarioTelegramId = ?1 and resultado = ?2 order by id asc", userId, EstadoApuesta.PENDIENTE)
                .list();

        if (pendientes.isEmpty()) {
            return Map.of(
                "tipo", "RESUMEN_CONCILIACION",
                "mensaje", "No tienes boletos pendientes de resolución en este momento.",
                "saldoActualMXN", balance.saldoActual,
                "apuestasPendientes", 0
            );
        }

        // Descargar partidos terminados con marcadores desde The Odds API de forma inteligente
        List<Map<String, Object>> partidosCompletados = Collections.synchronizedList(new ArrayList<>());
        if (oddsApiKey != null && !oddsApiKey.isBlank() && !"tu_api_key_aqui".equalsIgnoreCase(oddsApiKey)) {
            java.util.Set<String> deportesRequeridos = determinarDeportesNecesarios(pendientes);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            java.time.Instant ahora = java.time.Instant.now();

            for (String sportKey : deportesRequeridos) {
                java.time.Instant expiry = scoresCacheExpiry.get(sportKey);
                if (expiry != null && ahora.isBefore(expiry) && scoresCache.containsKey(sportKey)) {
                    LOG.info("Reutilizando scores en cache para deporte: " + sportKey);
                    partidosCompletados.addAll(scoresCache.get(sportKey));
                    continue;
                }

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        LOG.info("Consultando scores oficiales para deporte: " + sportKey);
                        List<Map<String, Object>> scores = oddsApiClient.getResultadosDeporte(sportKey, oddsApiKey, 3);
                        List<Map<String, Object>> completadosDeporte = new ArrayList<>();
                        if (scores != null) {
                            for (Map<String, Object> evMap : scores) {
                                Boolean completed = (Boolean) evMap.get("completed");
                                if (Boolean.TRUE.equals(completed)) {
                                    completadosDeporte.add(evMap);
                                }
                            }
                        }
                        scoresCache.put(sportKey, completadosDeporte);
                        scoresCacheExpiry.put(sportKey, ahora.plus(15, java.time.temporal.ChronoUnit.MINUTES));
                        partidosCompletados.addAll(completadosDeporte);
                    } catch (Exception ex) {
                        LOG.warn("Error consultando scores de " + sportKey + ": " + ex.getMessage());
                    }
                }));
            }
            if (!futures.isEmpty()) {
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    LOG.warn("Timeout o interrupción consultando scores The Odds API: " + e.getMessage());
                }
            }
        }

        List<Map<String, Object>> boletosCerrados = new ArrayList<>();
        List<Map<String, Object>> boletosAunPendientes = new ArrayList<>();
        BigDecimal totalCobradoSesion = BigDecimal.ZERO;

        for (RegistroApuesta ap : pendientes) {
            Map<String, Object> comp = encontrarMejorPartidoCompletado(ap, partidosCompletados);
            if (comp != null) {
                String home = (String) comp.get("home_team");
                String away = (String) comp.get("away_team");
                List<Map<String, Object>> scoresList = (List<Map<String, Object>>) comp.get("scores");

                if (home != null && away != null && scoresList != null && scoresList.size() >= 2) {
                    int sHome = -1, sAway = -1;
                    for (Map<String, Object> sc : scoresList) {
                        String name = (String) sc.get("name");
                        try {
                            int scoreVal = Integer.parseInt(String.valueOf(sc.get("score")));
                            if (home.equalsIgnoreCase(name)) sHome = scoreVal;
                            else if (away.equalsIgnoreCase(name)) sAway = scoreVal;
                        } catch (Exception ignored) {}
                    }

                    if (sHome >= 0 && sAway >= 0) {
                        String ganador = sHome > sAway ? home : (sAway > sHome ? away : "Empate");
                        String perdedor = sHome > sAway ? away : (sAway > sHome ? home : "Empate");

                        boolean gano = determinoGanador(ap, home, away, ganador, perdedor, sHome, sAway);
                        EstadoApuesta estadoFinal = gano ? EstadoApuesta.GANADA : EstadoApuesta.PERDIDA;
                        String motivo = String.format("Marcador final: %s %d - %d %s (Ganador: %s, Total: %d)", home, sHome, sAway, away, ganador, (sHome + sAway));

                        liquidarApuestaInternal(ap, balance, estadoFinal, motivo);

                        Map<String, Object> infoBoleto = new LinkedHashMap<>();
                        infoBoleto.put("boletoId", ap.id);
                        infoBoleto.put("partido", home + " vs " + away);
                        infoBoleto.put("pick", ap.pick);
                        infoBoleto.put("momio", ap.momio);
                        infoBoleto.put("montoApostadoMXN", ap.montoApostado);
                        infoBoleto.put("resultado", estadoFinal.name());
                        infoBoleto.put("marcadorFinal", String.format("%s %d - %d %s", home, sHome, sAway, away));

                        if (estadoFinal == EstadoApuesta.GANADA) {
                            BigDecimal cobro = ap.montoApostado.multiply(ap.momio).setScale(2, RoundingMode.HALF_UP);
                            totalCobradoSesion = totalCobradoSesion.add(cobro);
                            infoBoleto.put("montoCobradoMXN", cobro);
                            infoBoleto.put("gananciaNetaMXN", cobro.subtract(ap.montoApostado).setScale(2, RoundingMode.HALF_UP));
                        } else {
                            infoBoleto.put("montoCobradoMXN", BigDecimal.ZERO);
                            infoBoleto.put("gananciaNetaMXN", ap.montoApostado.negate());
                        }
                        boletosCerrados.add(infoBoleto);
                        continue;
                    }
                }
            }

            Map<String, Object> pendInfo = new LinkedHashMap<>();
            pendInfo.put("boletoId", ap.id);
            pendInfo.put("partido", ap.partido);
            pendInfo.put("pick", ap.pick);
            pendInfo.put("momio", ap.momio);
            pendInfo.put("montoApostadoMXN", ap.montoApostado);
            pendInfo.put("estado", "PENDIENTE (en juego o por jugarse)");
            boletosAunPendientes.add(pendInfo);
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("tipo", "RESUMEN_CONCILIACION");
        respuesta.put("boletosCerrados", boletosCerrados);
        respuesta.put("cantidadCerrados", boletosCerrados.size());
        respuesta.put("boletosAunPendientes", boletosAunPendientes);
        respuesta.put("cantidadPendientes", boletosAunPendientes.size());
        respuesta.put("saldoAnteriorMXN", saldoInicial);
        respuesta.put("totalCobradoEnEstaConciliacionMXN", totalCobradoSesion);
        respuesta.put("nuevoSaldoDisponibleMXN", balance.saldoActual);
        respuesta.put("totalApostadoHistoricoMXN", balance.totalApostadoHistorico);
        respuesta.put("totalCobradoHistoricoMXN", balance.totalCobradoHistorico);
        respuesta.put("apuestasGanadasTotal", balance.apuestasGanadas);
        respuesta.put("apuestasPerdidasTotal", balance.apuestasPerdidas);
        return respuesta;
    }

    private Map<String, Object> encontrarMejorPartidoCompletado(RegistroApuesta ap, List<Map<String, Object>> partidosCompletados) {
        String pStr = (ap.partido != null ? ap.partido : "").toLowerCase();
        String pickStr = (ap.pick != null ? ap.pick : "").toLowerCase();

        Long eventoId = null;
        if (pickStr.contains("id ") || pStr.contains("id ")) {
            try {
                String raw = pickStr.contains("id ") ? pickStr : pStr;
                String dig = raw.replaceAll("[^0-9]", "");
                if (!dig.isEmpty()) {
                    eventoId = Long.parseLong(dig);
                }
            } catch (Exception ignored) {}
        }

        EventoDeportivo ev = eventoId != null ? eventoRepo.findById(eventoId) : null;
        ZonedDateTime fechaRef = (ev != null && ev.fechaEvento != null) ? ev.fechaEvento : ap.creadoEn;

        Map<String, Object> mejorCoincidencia = null;
        long menorDiferenciaSegundos = Long.MAX_VALUE;

        for (Map<String, Object> comp : partidosCompletados) {
            String home = (String) comp.get("home_team");
            String away = (String) comp.get("away_team");
            if (home == null || away == null) continue;

            boolean coincideEquipos = false;
            if (ev != null) {
                coincideEquipos = coincideTextoEquipos(ev.equipoLocal, ev.equipoVisita, home, away);
            } else {
                coincideEquipos = coincideTextoEquipos(pStr, home, away) || coincideTextoEquipos(pickStr, home, away);
            }

            if (!coincideEquipos) continue;

            // Verificar commence_time para asociar al partido correcto de la serie
            String commenceTimeStr = (String) comp.get("commence_time");
            if (commenceTimeStr != null && fechaRef != null) {
                try {
                    ZonedDateTime gameTime = ZonedDateTime.parse(commenceTimeStr);

                    // Si el juego comenzó más de 6 horas antes de que se registrara la apuesta o del evento programado, ignorar
                    if (gameTime.isBefore(fechaRef.minusHours(6))) {
                        continue;
                    }

                    // Si el evento en BD está fechado para el futuro (+12h), no conciliar con juegos pasados
                    if (ev != null && ev.fechaEvento != null && gameTime.isBefore(ev.fechaEvento.minusHours(6))) {
                        continue;
                    }

                    long diff = Math.abs(java.time.Duration.between(fechaRef, gameTime).getSeconds());
                    if (diff < menorDiferenciaSegundos) {
                        menorDiferenciaSegundos = diff;
                        mejorCoincidencia = comp;
                    }
                } catch (Exception e) {
                    if (mejorCoincidencia == null) mejorCoincidencia = comp;
                }
            } else if (mejorCoincidencia == null) {
                mejorCoincidencia = comp;
            }
        }

        return mejorCoincidencia;
    }

    private boolean coincideTextoEquipos(String texto, String home, String away) {
        if (texto == null || home == null || away == null) return false;
        String t = texto.toLowerCase();
        String h = home.toLowerCase();
        String a = away.toLowerCase();

        String hKeyword = extraerPalabraClaveEquipo(h);
        String aKeyword = extraerPalabraClaveEquipo(a);

        if (!hKeyword.isEmpty() && t.contains(hKeyword) && !aKeyword.isEmpty() && t.contains(aKeyword)) return true;
        if (!hKeyword.isEmpty() && t.contains(hKeyword)) return true;
        if (!aKeyword.isEmpty() && t.contains(aKeyword)) return true;
        if (t.contains(h) || t.contains(a)) return true;
        return false;
    }

    private boolean coincideTextoEquipos(String local, String visita, String home, String away) {
        if (local == null || visita == null || home == null || away == null) return false;
        String lKey = extraerPalabraClaveEquipo(local);
        String vKey = extraerPalabraClaveEquipo(visita);
        String hKey = extraerPalabraClaveEquipo(home);
        String aKey = extraerPalabraClaveEquipo(away);
        return (!lKey.isEmpty() && !vKey.isEmpty()) && ((lKey.equals(hKey) && vKey.equals(aKey)) || (lKey.equals(aKey) && vKey.equals(hKey)));
    }

    private String extraerPalabraClaveEquipo(String nombre) {
        if (nombre == null) return "";
        String s = nombre.toLowerCase().trim();
        if (s.contains("white sox")) return "white sox";
        if (s.contains("red sox")) return "red sox";
        if (s.contains("blue jays")) return "blue jays";
        String[] partes = s.split("\\s+");
        return partes[partes.length - 1];
    }

    private boolean determinoGanador(RegistroApuesta ap, String home, String away, String ganador, String perdedor, int sHome, int sAway) {
        String pickStr = (ap.pick != null ? ap.pick : "").toLowerCase().trim();
        String pStr = (ap.partido != null ? ap.partido : "").toLowerCase().trim();

        // 0. Soporte para Totales (Over / Under)
        int totalMarcador = sHome + sAway;
        if (pickStr.contains("over") || pickStr.contains("más de") || pickStr.contains("mas de") || pickStr.startsWith("más") || pickStr.startsWith("mas")) {
            Double linea = extraerNumeroLinea(pickStr);
            if (linea != null) {
                return totalMarcador > linea;
            }
        } else if (pickStr.contains("under") || pickStr.contains("menos de") || pickStr.startsWith("menos")) {
            Double linea = extraerNumeroLinea(pickStr);
            if (linea != null) {
                return totalMarcador < linea;
            }
        }

        // 1. Si era por ID numérico (ej. ID 370 o ID 369)
        if (pickStr.contains("id ") || pStr.contains("id ")) {
            try {
                String raw = pickStr.contains("id ") ? pickStr : pStr;
                String dig = raw.replaceAll("[^0-9]", "");
                if (!dig.isEmpty()) {
                    EventoDeportivo ev = eventoRepo.findById(Long.parseLong(dig));
                    if (ev != null) {
                        List<MercadoCuota> cuotas = cuotaRepo.find("evento.id = ?1 and esValor = true", ev.id).list();
                        String sel = !cuotas.isEmpty() ? cuotas.get(0).seleccion : "";
                        String apostado = "Home".equalsIgnoreCase(sel) ? ev.equipoLocal : ev.equipoVisita;
                        String keyGanador = extraerPalabraClaveEquipo(ganador);
                        String keyApostado = extraerPalabraClaveEquipo(apostado);
                        return keyGanador.equalsIgnoreCase(keyApostado)
                                || ganador.toLowerCase().contains(keyApostado)
                                || apostado.toLowerCase().contains(keyGanador);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Si el pick es texto directo de equipo (ej. "Cardinals", "STL Cardinals", "MIL Brewers")
        String keyGanador = extraerPalabraClaveEquipo(ganador);
        String keyPerdedor = extraerPalabraClaveEquipo(perdedor);

        // Primero verificar ap.pick
        if (!pickStr.isEmpty() && !pickStr.startsWith("pick ")) {
            if (!keyGanador.isEmpty() && pickStr.contains(keyGanador)) return true;
            if (!keyPerdedor.isEmpty() && pickStr.contains(keyPerdedor)) return false;
            if (ganador.toLowerCase().contains(pickStr)) return true;
            if (perdedor.toLowerCase().contains(pickStr)) return false;
        }

        // Si ap.partido solo menciona a un equipo (sin "vs" ni "-")
        if (!pStr.contains(" vs ") && !pStr.contains(" - ")) {
            if (!keyGanador.isEmpty() && pStr.contains(keyGanador)) return true;
            if (!keyPerdedor.isEmpty() && pStr.contains(keyPerdedor)) return false;
        }

        return false;
    }

    private Double extraerNumeroLinea(String str) {
        if (str == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)").matcher(str);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private java.util.Set<String> determinarDeportesNecesarios(List<RegistroApuesta> pendientes) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (RegistroApuesta ap : pendientes) {
            String dep = (ap.deporte != null ? ap.deporte : "").toLowerCase();
            String liga = (ap.liga != null ? ap.liga : "").toLowerCase();
            String part = (ap.partido != null ? ap.partido : "").toLowerCase();
            String pick = (ap.pick != null ? ap.pick : "").toLowerCase();
            String combined = (dep + " " + liga + " " + part + " " + pick).toLowerCase();

            if (combined.contains("mlb") || combined.contains("base") || combined.contains("red sox") || combined.contains("yankees") || combined.contains("brewers") || combined.contains("cubs") || combined.contains("athletics") || combined.contains("astros")) {
                keys.add("baseball_mlb");
            }
            if (combined.contains("nfl") || combined.contains("americanfootball") || combined.contains("packers") || combined.contains("falcons") || combined.contains("jaguars") || combined.contains("patriots") || combined.contains("giants") || combined.contains("titans") || combined.contains("bengals") || combined.contains("steelers")) {
                keys.add("americanfootball_nfl");
            }
            if (combined.contains("liga mx") || combined.contains("ligamx") || combined.contains("mexic") || combined.contains("chivas") || combined.contains("guadalajara") || combined.contains("tigres") || combined.contains("puebla") || combined.contains("tijuana") || combined.contains("atlas") || combined.contains("queretaro")) {
                keys.add("soccer_mexico_ligamx");
            }
            if (combined.contains("epl") || combined.contains("premier") || combined.contains("arsenal") || combined.contains("chelsea") || combined.contains("liverpool") || combined.contains("city")) {
                keys.add("soccer_epl");
            }
            if (combined.contains("la liga") || combined.contains("laliga") || combined.contains("madrid") || combined.contains("barcelona")) {
                keys.add("soccer_spain_la_liga");
            }
            if (combined.contains("nations") || combined.contains("andorra") || combined.contains("malta")) {
                keys.add("soccer_uefa_nations_league");
            }
            if (combined.contains("champions") || combined.contains("uefa_champs")) {
                keys.add("soccer_uefa_champs_league");
            }
            if (combined.contains("nba") || combined.contains("basketball")) {
                keys.add("basketball_nba");
            }
            if (combined.contains("wnba") || combined.contains("connecticut sun") || combined.contains("toronto tempo")) {
                keys.add("basketball_wnba");
            }
            if (combined.contains("nhl") || combined.contains("hockey") || combined.contains("lightning") || combined.contains("panthers") || combined.contains("sabres") || combined.contains("red wings") || combined.contains("tappara") || combined.contains("lukko")) {
                keys.add("icehockey_nhl");
            }
        }
        if (keys.isEmpty()) {
            keys.add("baseball_mlb");
            keys.add("soccer_mexico_ligamx");
            keys.add("americanfootball_nfl");
        }
        return keys;
    }
}

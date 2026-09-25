package com.apuestas.ai;

import com.apuestas.repository.EventoDeportivoRepository;
import com.apuestas.service.BankrollService;
import com.apuestas.service.BetEngineMathService;
import com.apuestas.service.EstadisticasService;
import com.apuestas.service.IngestionBatchService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BettingTools {

    private static final Logger LOG = Logger.getLogger(BettingTools.class);

    @Inject 
    BetEngineMathService betEngine;

    @Inject 
    BankrollService bankrollService;

    @Inject
    IngestionBatchService ingestionService;

    @Inject
    EstadisticasService estadisticasService;

    @Inject
    EventoDeportivoRepository eventoRepo;

    @Tool("Descarga o actualiza los partidos y momios del dia desde API-Sports (ejecuta el batch matutino)")
    public String ejecutarDescargaPartidosHoy(@ToolMemoryId Long userId) {
        return ingestionService.ejecutarDescargaManualHoy();
    }

    @Tool("Analiza en detalle un partido especifico: forma ultimos 10 partidos por equipo y H2H ultimos 5 enfrentamientos. Llamar cuando el usuario pregunte por estadisticas, forma o historial de un partido concreto.")
    public Object analizarPartidoOnDemand(
            @ToolMemoryId Long userId,
            Long eventoId) {
        com.apuestas.entity.EventoDeportivo ev = eventoRepo.findById(eventoId);
        if (ev == null) return Map.of("error", "Evento " + eventoId + " no encontrado en la base de datos.");
        return estadisticasService.analizarPartido(ev.equipoLocalId, ev.equipoVisitaId);
    }

    @Tool("Consulta picks de valor del dia. El parametro deporteOpcional puede ser 'LIGA_MX' (para todos los partidos y cuotas de Liga MX mexicana), 'FUTBOL', 'BASEBALL', 'NFL' o null para todos los deportes.")
    public List<Map<String, Object>> obtenerPicksDeValor(
            @ToolMemoryId Long userId,
            String deporteOpcional) {
        return betEngine.obtenerMejoresPicksDelDia(userId, deporteOpcional);
    }

    @Tool("Registra una apuesta directa en el bankroll del usuario, descontando el monto del saldo inmediatamente. " +
          "Invocar SIEMPRE que el usuario diga que apostó, metió dinero o confirme una jugada (ej. 'le metí 10 pesos a Cardinals momio +115', 'apuesta 20 al pick 1', 'le metí 15 al Over 2.5 de Cruz Azul'). " +
          "NUNCA pidas un ID de evento al usuario. Si el usuario da momio americano (+115, -110), conviértelo a decimal (2.15, 1.91). " +
          "Parámetros: seleccion (nombre del equipo, selección Over/Under o partido apostado), cuotaReal (momio decimal), monto (importe en MXN apostado).")
    @Transactional
    public String confirmarApuestaDirecta(
            @ToolMemoryId Long userId,
            String seleccion,
            BigDecimal cuotaReal,
            BigDecimal monto) {
        Long eventoId = null;
        String seleccionFinal = seleccion;
        // Si el usuario dijo "pick 1", "pick 2", etc., resolverlo con los picks del día o de Liga MX
        if (seleccion != null && seleccion.toLowerCase().contains("pick")) {
            try {
                String numStr = seleccion.replaceAll("[^0-9]", "");
                if (!numStr.isEmpty()) {
                    int numPick = Integer.parseInt(numStr);
                    List<Map<String, Object>> picks = betEngine.obtenerMejoresPicksDelDia(userId, null);
                    boolean encontrado = false;
                    for (Map<String, Object> p : picks) {
                        if ("DIRECTA".equals(p.get("tipo")) && Integer.valueOf(numPick).equals(p.get("numeroPick"))) {
                            eventoId = (Long) p.get("eventoId");
                            seleccionFinal = (String) p.get("equipoRecomendado");
                            encontrado = true;
                            break;
                        }
                    }
                    if (!encontrado) {
                        List<Map<String, Object>> picksLigaMx = betEngine.obtenerMejoresPicksDelDia(userId, "LIGA_MX");
                        for (Map<String, Object> p : picksLigaMx) {
                            if ("DIRECTA".equals(p.get("tipo")) && Integer.valueOf(numPick).equals(p.get("numeroPick"))) {
                                eventoId = (Long) p.get("eventoId");
                                seleccionFinal = (String) p.get("equipoRecomendado");
                                break;
                            }
                        }
                    }
                }
            } catch (RuntimeException e) {
                LOG.tracef(e, "No se pudo interpretar el número de pick desde la selección: %s", seleccion);
            }
        }
        return bankrollService.registrarApuestaDirecta(userId, eventoId, seleccionFinal, cuotaReal, monto);
    }

    @Tool("Registra un parlay confirmado con 2 o mas piernas")
    @Transactional
    public String confirmarParlay(
            @ToolMemoryId Long userId,
            List<Map<String, Object>> piernas,
            BigDecimal monto) {
        return bankrollService.registrarParlay(userId, piernas, monto);
    }

    @Tool("Registra un retiro de utilidades")
    @Transactional
    public String registrarRetiro(
            @ToolMemoryId Long userId,
            BigDecimal monto,
            String concepto) {
        return bankrollService.procesarRetiro(userId, monto, concepto);
    }

    @Tool("Consulta saldo, ROI y estadisticas del usuario")
    public Object consultarBalance(@ToolMemoryId Long userId) {
        return bankrollService.obtenerResumenActual(userId);
    }

    @Tool("Consulta las apuestas registradas del usuario (boletos activos, pendientes y recientes) con sus IDs de ticket, selección, momio, monto apostado y estado.")
    public List<Map<String, Object>> consultarHistorialApuestas(@ToolMemoryId Long userId) {
        return bankrollService.obtenerHistorialApuestas(userId, 10);
    }

    @Tool("Verifica resultados oficiales de partidos terminados con The Odds API, cierra los boletos correspondientes (GANADA/PERDIDA) y actualiza el saldo del bankroll con los premios cobrados. " +
          "Invocar SIEMPRE que el usuario diga 'actualiza las apuestas', 'cierra las que hayan terminado', 'checa resultados', 'concilia los boletos', 'trae resultados', 'termina las apuestas necesarias', o 'compara resultados y actualiza el bank'.")
    public Map<String, Object> conciliarResultadosYActualizarBank(@ToolMemoryId Long userId) {
        return bankrollService.conciliarResultadosYActualizarBank(userId);
    }

    @Tool("Declara manualmente el resultado de un boleto especifico (GANADA, PERDIDA o CANCELADA) y actualiza el saldo del bankroll. " +
          "Invocar cuando el usuario indique explícitamente el resultado de un boleto (ej. 'el boleto 8 fue perdida', 'marca el boleto 5 como ganada', 'cierra el boleto 1 como cobrado').")
    public String declararResultadoBoleto(
            @ToolMemoryId Long userId,
            Long boletoId,
            String resultado) {
        return bankrollService.liquidarApuestaManual(userId, boletoId, resultado);
    }
}

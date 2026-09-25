package com.apuestas.service;

import java.util.List;
import java.util.Map;

public interface BetEngineMathService {
    List<Map<String, Object>> obtenerMejoresPicksDelDia(Long userId, String deporteOpcional);
}

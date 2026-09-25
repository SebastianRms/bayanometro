-- 1. Catálogo de ligas (cargado una vez en deploy)
CREATE TABLE ligas (
    id BIGSERIAL PRIMARY KEY,
    deporte VARCHAR(30) NOT NULL,
    liga_id BIGINT NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    pais VARCHAR(80),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(deporte, liga_id)
);

-- 2. Eventos deportivos
CREATE TABLE eventos_deportivos (
    id BIGSERIAL PRIMARY KEY,
    fixture_externo_id VARCHAR(50) NOT NULL,
    deporte VARCHAR(30) NOT NULL,
    liga_id BIGINT NOT NULL,
    liga VARCHAR(100) NOT NULL,
    temporada INT NOT NULL,
    pais VARCHAR(50),
    equipo_local_id BIGINT NOT NULL,
    equipo_local VARCHAR(120) NOT NULL,
    equipo_visita_id BIGINT NOT NULL,
    equipo_visita VARCHAR(120) NOT NULL,
    arbitro VARCHAR(120),
    fecha_evento TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'NS',
    marcador_local INT,
    marcador_visita INT,
    detalles_especificos JSONB DEFAULT '{}'::jsonb,
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(deporte, fixture_externo_id)
);
CREATE INDEX idx_eventos_fecha_deporte ON eventos_deportivos (fecha_evento, deporte);
CREATE INDEX idx_eventos_estado ON eventos_deportivos (estado);
CREATE INDEX idx_eventos_equipos ON eventos_deportivos (equipo_local_id, equipo_visita_id);
CREATE INDEX idx_eventos_liga_temporada ON eventos_deportivos (liga_id, temporada);
CREATE INDEX idx_eventos_detalles_gin ON eventos_deportivos USING gin (detalles_especificos);

-- 3. Mercados / cuotas
CREATE TABLE mercados_cuotas (
    id BIGSERIAL PRIMARY KEY,
    evento_id BIGINT NOT NULL REFERENCES eventos_deportivos(id) ON DELETE CASCADE,
    casa_apuesta VARCHAR(50) NOT NULL,
    tipo_mercado VARCHAR(50) NOT NULL,
    seleccion VARCHAR(50) NOT NULL,
    cuota NUMERIC(8,2) NOT NULL CHECK (cuota >= 1.01),
    probabilidad_estimada NUMERIC(6,4) NOT NULL CHECK (probabilidad_estimada BETWEEN 0 AND 1),
    ev NUMERIC(8,4) NOT NULL,
    es_valor BOOLEAN NOT NULL DEFAULT FALSE,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(evento_id, casa_apuesta, tipo_mercado, seleccion)
);
CREATE INDEX idx_cuotas_evento ON mercados_cuotas (evento_id);
CREATE INDEX idx_cuotas_es_valor ON mercados_cuotas (es_valor) WHERE es_valor = TRUE;

-- 4. Apuestas (boleto principal). Una fila = un boleto.
CREATE TABLE registro_apuestas (
    id BIGSERIAL PRIMARY KEY,
    usuario_telegram_id BIGINT NOT NULL,
    tipo_jugada VARCHAR(20) NOT NULL,       -- DIRECTA, PARLAY
    deporte VARCHAR(30) NOT NULL,           -- para DIRECTA; para PARLAY = MIXTO
    liga VARCHAR(100),
    partido VARCHAR(250),
    pick VARCHAR(100),
    momio NUMERIC(8,2) NOT NULL CHECK (momio >= 1.01),
    monto_apostado NUMERIC(12,2) NOT NULL CHECK (monto_apostado > 0),
    resultado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    ganancia_neta NUMERIC(12,2) DEFAULT 0.00,
    balance_resultante NUMERIC(12,2) NOT NULL,
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_apuestas_usuario_estado ON registro_apuestas (usuario_telegram_id, resultado);

-- 5. Piernas del boleto (1 fila para DIRECTA, N para PARLAY)
CREATE TABLE parlay_piernas (
    id BIGSERIAL PRIMARY KEY,
    apuesta_id BIGINT NOT NULL REFERENCES registro_apuestas(id) ON DELETE CASCADE,
    evento_id BIGINT REFERENCES eventos_deportivos(id),
    seleccion VARCHAR(100) NOT NULL,
    cuota NUMERIC(8,2) NOT NULL CHECK (cuota >= 1.01),
    resultado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
);
CREATE INDEX idx_piernas_apuesta ON parlay_piernas (apuesta_id);
CREATE INDEX idx_piernas_evento ON parlay_piernas (evento_id);

-- 6. Movimientos de balance (auditoria)
CREATE TABLE movimientos_balance (
    id BIGSERIAL PRIMARY KEY,
    usuario_telegram_id BIGINT NOT NULL,
    tipo_movimiento VARCHAR(20) NOT NULL,
    monto NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    saldo_previo NUMERIC(12,2) NOT NULL,
    saldo_posterior NUMERIC(12,2) NOT NULL,
    apuesta_id BIGINT REFERENCES registro_apuestas(id),
    descripcion TEXT,
    fecha TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mov_usuario_fecha ON movimientos_balance (usuario_telegram_id, fecha);

-- 7. Balance por usuario
CREATE TABLE balance_actual (
    id BIGSERIAL PRIMARY KEY,
    usuario_telegram_id BIGINT NOT NULL UNIQUE,
    version INT NOT NULL DEFAULT 0,
    saldo_actual NUMERIC(12,2) NOT NULL DEFAULT 150.00 CHECK (saldo_actual >= 0),
    total_apostado_historico NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_cobrado_historico NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total_retirado_historico NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    apuestas_ganadas INT NOT NULL DEFAULT 0,
    apuestas_perdidas INT NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 8. Rate limiting (auditoria)
CREATE TABLE rate_limit_auditoria (
    id BIGSERIAL PRIMARY KEY,
    usuario_telegram_id BIGINT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rate_usuario_ts ON rate_limit_auditoria (usuario_telegram_id, timestamp);

-- 9. Idempotencia Telegram
CREATE TABLE telegram_updates (
    update_id BIGINT PRIMARY KEY,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

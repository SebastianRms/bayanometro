#!/bin/sh
# register-webhook.sh
# Monitorea el contenedor del túnel Cloudflare y registra el webhook en Telegram
# cada vez que la URL cambia. Cuenta con watchdog activo de auto-recuperacion si Cloudflare se desconecta.

BOT_TOKEN="${TELEGRAM_BOT_TOKEN}"
WEBHOOK_SECRET="${TELEGRAM_WEBHOOK_SECRET}"
TUNNEL_CONTAINER="bayanometro-tunnel"
CLOUDFLARE_ANYCAST_IP="104.16.230.132"

echo "[webhook-updater] Iniciando monitor de URL de tunel Cloudflare con auto-recuperacion..."

LAST_URL=""
FAIL_COUNT=0

while true; do
  # Verificar si el tunel reporta error "Tunnel not found" en sus ultimos logs
  if docker logs --tail 10 "$TUNNEL_CONTAINER" 2>&1 | grep -q "Tunnel not found"; then
    echo "[webhook-updater] ⚠️ Alerta: Cloudflare reporto 'Tunnel not found'. Reiniciando tunel..."
    docker restart "$TUNNEL_CONTAINER" >/dev/null 2>&1
    LAST_URL=""
    sleep 8
  fi

  # Obtener la ultima URL publica asignada
  TUNNEL_URL=$(docker logs "$TUNNEL_CONTAINER" 2>&1 \
    | grep -oE 'https://[a-z0-9]+-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+\.trycloudflare\.com' \
    | grep -v 'api\.trycloudflare' \
    | tail -1)

  if [ -z "$TUNNEL_URL" ]; then
    echo "[webhook-updater] Tunel aun sin URL publica, reintentando en 5s..."
    sleep 5
    continue
  fi

  # Si la URL cambio, registrarla en Telegram
  if [ "$TUNNEL_URL" != "$LAST_URL" ]; then
    echo "[webhook-updater] URL nueva detectada: $TUNNEL_URL"

    WEBHOOK_URL="${TUNNEL_URL}/webhook/telegram"
    RESPONSE=$(wget -qO- \
      "https://api.telegram.org/bot${BOT_TOKEN}/setWebhook?url=${WEBHOOK_URL}&secret_token=${WEBHOOK_SECRET}")

    echo "[webhook-updater] Respuesta Telegram: $RESPONSE"

    if echo "$RESPONSE" | grep -q '"ok":true'; then
      echo "[webhook-updater] ✅ Webhook registrado exitosamente: $WEBHOOK_URL"
      LAST_URL="$TUNNEL_URL"
      FAIL_COUNT=0
    else
      echo "[webhook-updater] ❌ Error al registrar webhook, reintentando en 10s..."
      sleep 10
      continue
    fi
  fi

  sleep 10
done

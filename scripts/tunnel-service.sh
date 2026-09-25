#!/bin/sh
# tunnel-service.sh
# Mantiene un túnel SSH permanente hacia localhost.run apuntando a app:8080,
# registra automáticamente la URL pública asignada en Telegram
# e incluye un watchdog activo para detectar si app:8080 se reinicia y refrescar la conexión.

apk add --no-cache openssh-client curl >/dev/null 2>&1

while true; do
  echo "[tunnel-service] Conectando a localhost.run..."
  rm -f /tmp/tunnel.log
  
  ssh -o StrictHostKeyChecking=no \
      -o ServerAliveInterval=15 \
      -o ServerAliveCountMax=3 \
      -o ExitOnForwardFailure=yes \
      -R 80:app:8080 nokey@localhost.run > /tmp/tunnel.log 2>&1 &
  SSH_PID=$!

  # Esperar a que se publique la URL asignada
  URL=""
  for i in $(seq 1 30); do
    if [ -f /tmp/tunnel.log ]; then
      URL=$(grep -oE 'https://[a-z0-9]+\.lhr\.life' /tmp/tunnel.log | head -1)
      if [ -n "$URL" ]; then
        echo "[tunnel-service] URL detectada: $URL"
        echo "[tunnel-service] Registrando webhook en Telegram..."
        RESP=$(curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook?url=${URL}/webhook/telegram&secret_token=${TELEGRAM_WEBHOOK_SECRET}")
        echo "[tunnel-service] Respuesta Telegram: $RESP"
        break
      fi
    fi
    sleep 1
  done

  # Loop de vigilancia activo: monitorea tanto el proceso SSH como la salud del backend
  FAILURES=0
  while kill -0 $SSH_PID 2>/dev/null; do
    if curl -s -m 3 http://app:8080/q/openapi >/dev/null; then
      FAILURES=0
    else
      FAILURES=$((FAILURES + 1))
      echo "[tunnel-service] Alerta: app:8080 no responde (fallo $FAILURES/3)"
      if [ "$FAILURES" -ge 3 ]; then
        echo "[tunnel-service] Backend se reinicio o cambio de IP interna. Reiniciando SSH..."
        kill -9 $SSH_PID 2>/dev/null
        break
      fi
    fi
    sleep 5
  done

  wait $SSH_PID 2>/dev/null
  echo "[tunnel-service] Reconectando en 3 segundos..."
  sleep 3
done

@echo off
echo ==============================================
echo   Iniciando Bayanometro (Docker en WSL)
echo ==============================================
wsl -e bash -c "cd /mnt/c/Users/Public/devPublic/proyectos/java/bayanometro && docker compose up -d"
echo.
echo Estado actual de los servicios:
wsl docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo.
echo ==============================================
echo   Bot y Webhook de Telegram listos!
echo ==============================================
pause

@echo off
echo ==============================================
echo   Estado de Contenedores Bayanometro
echo ==============================================
wsl docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo.
echo Para ver logs en vivo ejecuta:
echo   wsl docker logs -f bayanometro-api
echo ==============================================
pause

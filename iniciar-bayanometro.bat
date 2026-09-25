@echo off
title BayanitoBot - Servidor en Vivo
echo ========================================================
echo   Iniciando Bayanometro en Vivo (Modo Terminal)
echo   Presiona Ctrl + C para detenerlo cuando ya no lo uses
echo ========================================================
echo.
wsl -d Ubuntu-22.04 -- docker compose -f /mnt/c/Users/Public/devPublic/proyectos/java/bayanometro/docker-compose.yml up
pause

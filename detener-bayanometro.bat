@echo off
title BayanitoBot - Detener
echo Deteniendo todos los contenedores y liberando RAM...
wsl -d Ubuntu-22.04 -- docker compose -f /mnt/c/Users/Public/devPublic/proyectos/java/bayanometro/docker-compose.yml down
wsl --shutdown
echo Listo. Toda la RAM ha sido liberada.
pause

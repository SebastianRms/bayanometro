@echo off
echo ==============================================
echo   Deteniendo Bayanometro (Docker en WSL)
echo ==============================================
wsl -e bash -c "cd /mnt/c/Users/Public/devPublic/proyectos/java/bayanometro && docker compose down"
echo.
echo Servicios detenidos correctamente.
echo ==============================================
pause

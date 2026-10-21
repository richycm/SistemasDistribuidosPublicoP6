@echo off
REM Practica 6 - Compila y empaqueta target\p6-servicios-web.jar (incluye Jersey, Grizzly y Jackson).
REM Usa "mvn" del PATH; si no existe, el Maven que trae Apache NetBeans.
cd /d "%~dp0"
where mvn >nul 2>nul
if %errorlevel%==0 (
    call mvn -B package
) else (
    call "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" -B package
)
pause

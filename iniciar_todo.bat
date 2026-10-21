@echo off
REM ==========================================================================
REM  Practica 6 - Servicios Web | Arranca el sistema completo
REM  Orden: 3 cocinas REST -> coordinador REST -> cliente Java -> cliente web
REM  Requiere haber ejecutado compilar.bat (target\p6-servicios-web.jar).
REM ==========================================================================
cd /d "%~dp0"
set JAR=target\p6-servicios-web.jar
set PKG=com.mycompany.p6sistemasdistribuidos
if not exist %JAR% (
    echo No existe %JAR%. Ejecuta primero compilar.bat
    pause
    exit /b 1
)

start "Cocina Norte  :6001" java -cp %JAR% %PKG%.CocinaNorte
start "Cocina Centro :6002" java -cp %JAR% %PKG%.CocinaCentro
start "Cocina Sur    :6003" java -cp %JAR% %PKG%.CocinaSur
ping -n 3 127.0.0.1 >nul

start "Coordinador   :5000" java -cp %JAR% %PKG%.CoordinadorDespacho
ping -n 4 127.0.0.1 >nul

start "Cliente Java" java -cp %JAR% %PKG%.ClienteComensalGUI
start "" http://127.0.0.1:5000/web/

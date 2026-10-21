@echo off
REM ==========================================================================
REM  Practica 6: Servicios Web RESTful (Python + Flask)
REM  Alumno: Ricardo Carmona Martinez
REM  Levanta las 3 cocinas REST, el Coordinador y abre el Frontend Web.
REM ==========================================================================
cd /d "%~dp0"

start "Cocina Norte  :6001" python cocina.py 6001 "Cocina Norte"
start "Cocina Centro :6002" python cocina.py 6002 "Cocina Centro"
start "Cocina Sur    :6003" python cocina.py 6003 "Cocina Sur"
timeout /t 2 /nobreak >nul

start "Coordinador   :5000" python coordinador.py
timeout /t 2 /nobreak >nul

start "" http://127.0.0.1:5000/

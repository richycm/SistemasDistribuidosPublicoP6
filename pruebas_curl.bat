@echo off
REM ==========================================================================
REM  Practica 6 - Servicios Web | Pruebas de la API REST con curl
REM  Alumno: Ricardo Carmona Martinez
REM
REM  Requisitos: las 3 cocinas y el coordinador recien iniciados
REM  (iniciar_todo.bat). Los folios 1..5 asumen que la Cocina Norte no ha
REM  recibido pedidos todavia.
REM ==========================================================================
chcp 65001 >nul
set C=http://127.0.0.1:5000/api
set N=http://127.0.0.1:6001/api
set FMT=-s -w "\n   => HTTP %%{http_code}\n\n"

echo ===== 1. GET /api/menu (coordinador) =====
curl %FMT% %C%/menu

echo ===== 2. GET /api/cocinas (coordinador) =====
curl %FMT% %C%/cocinas

echo ===== 3. POST /api/despachos (balanceo por menor carga) =====
curl %FMT% -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Ana\"}" %C%/despachos

echo ===== 4. POST /api/pedidos x3 en Cocina Norte (ocupan sus 3 estaciones) =====
curl -s -i -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Ana\",\"idPlatillo\":2}" %N%/pedidos
echo.
echo.
curl %FMT% -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Beto\",\"idPlatillo\":2}" %N%/pedidos
curl %FMT% -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Carla\",\"idPlatillo\":5}" %N%/pedidos

echo ===== 5. POST /api/pedidos: el 4o ya no tiene estacion y queda EN_FILA =====
curl %FMT% -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Luis\",\"idPlatillo\":1,\"notas\":\"sin cebolla\"}" %N%/pedidos

echo ===== 6. GET /api/pedidos/4 =====
curl %FMT% %N%/pedidos/4

echo ===== 7. PUT /api/pedidos/4 (cambia platillo y notas mientras sigue en fila) =====
curl %FMT% -X PUT -H "Content-Type: application/json" -d "{\"idPlatillo\":3,\"notas\":\"extra salsa\"}" %N%/pedidos/4

echo ===== 8. POST + DELETE /api/pedidos/5 (cancelar un pedido en fila) =====
curl %FMT% -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Mara\",\"idPlatillo\":6}" %N%/pedidos
curl %FMT% -X DELETE %N%/pedidos/5

echo ===== 9. GET /api/pedidos?estado=EN_FILA =====
curl %FMT% "%N%/pedidos?estado=EN_FILA"

echo ===== 10. ERROR 409: PUT a un pedido que ya se esta cocinando =====
curl %FMT% -X PUT -H "Content-Type: application/json" -d "{\"notas\":\"bien cocido\"}" %N%/pedidos/1

echo ===== 11. ERROR 409: DELETE a un pedido ya cancelado =====
curl %FMT% -X DELETE %N%/pedidos/5

echo ===== 12. ERROR 404: folio inexistente =====
curl %FMT% %N%/pedidos/999

echo ===== 13. ERROR 404: ruta inexistente =====
curl %FMT% %N%/platillos

echo ===== 14. ERROR 400: platillo inexistente =====
curl %FMT% -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Ana\",\"idPlatillo\":99}" %N%/pedidos

echo ===== 15. ERROR 400: JSON mal formado =====
curl %FMT% -X POST -H "Content-Type: application/json" -d "{\"comensal\":\"Ana\",\"idPlatillo\":" %N%/pedidos

echo ===== 16. ERROR 405: verbo no permitido =====
curl %FMT% -X DELETE %N%/menu

echo ===== 17. GET /api/carga de la Cocina Norte =====
curl %FMT% %N%/carga

echo Esperando 15 s a que terminen los platillos...
ping -n 16 127.0.0.1 >nul

echo ===== 18. GET /api/pedidos?estado=LISTO =====
curl %FMT% "%N%/pedidos?estado=LISTO"

pause

"""
Práctica 6: Servicios Web RESTful — Nodo Trabajador (Servidor de Cocina)
Alumno: Ricardo Carmona Martínez | Materia: Sistemas Distribuidos
Uso: python cocina.py [puerto] [nombre]
"""
import sys, time, threading
from collections import deque
from datetime import datetime
from flask import Flask, request, jsonify
import logging

logging.getLogger("werkzeug").setLevel(logging.ERROR)
app = Flask(__name__)

PUERTO = int(sys.argv[1]) if len(sys.argv) > 1 else 6001
NOMBRE = sys.argv[2] if len(sys.argv) > 2 else "Cocina Norte"
TOTAL_ESTACIONES = 3

MENU = {
    1: {"id": 1, "nombre": "Tacos al Pastor (orden)", "segundos": 5, "precio": 75.0},
    2: {"id": 2, "nombre": "Hamburguesa Clásica",     "segundos": 7, "precio": 115.0},
    3: {"id": 3, "nombre": "Pizza Individual",        "segundos": 6, "precio": 125.0},
    4: {"id": 4, "nombre": "Ensalada César",          "segundos": 4, "precio": 85.0},
    5: {"id": 5, "nombre": "Alitas BBQ (12 pzs)",     "segundos": 6, "precio": 135.0},
    6: {"id": 6, "nombre": "Corte Ribeye",            "segundos": 8, "precio": 220.0},
}

semaforo = threading.Semaphore(TOTAL_ESTACIONES)
lock = threading.Lock()
cond_cola = threading.Condition(lock)
cola_fifo = deque()
pedidos = {}
estaciones = {i: None for i in range(1, TOTAL_ESTACIONES + 1)}
folio_seq = 0
atendidos = 0
ventas_totales = 0.0

def log(tag, msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] [{NOMBRE}] [{tag}] {msg}", flush=True)

def cocinar(pedido, num_est):
    global atendidos, ventas_totales
    try:
        total = pedido["segundosPreparacion"]
        for s in range(1, total + 1):
            time.sleep(1)
            with lock:
                pedido["segundosTranscurridos"] = s
        with lock:
            pedido["estado"] = "LISTO"
            pedido["horaListo"] = datetime.now().strftime("%H:%M:%S")
            estaciones[num_est] = None
            atendidos += 1
            ventas_totales += pedido["precio"]
        log("LISTO", f"Folio #{pedido['folio']} ({pedido['platillo']}) terminado en Estación #{num_est} -> ${pedido['precio']:.2f}")
    finally:
        semaforo.release()

def despachador_fifo():
    while True:
        with cond_cola:
            while not cola_fifo:
                cond_cola.wait()
            folio = cola_fifo[0]
        semaforo.acquire()
        with lock:
            if cola_fifo and cola_fifo[0] == folio:
                cola_fifo.popleft()
            p = pedidos.get(folio)
            if not p or p["estado"] != "EN_FILA":
                semaforo.release()
                continue
            num_est = next(k for k, v in estaciones.items() if v is None)
            p["estado"] = "PREPARANDO"
            p["estacion"] = num_est
            estaciones[num_est] = p
        log("PREPARANDO", f"Estación #{num_est} cocina {p['platillo']} (Folio #{folio}) para [{p['comensal']}]")
        threading.Thread(target=cocinar, args=(p, num_est), daemon=True).start()

@app.after_request
def agregar_cors(resp):
    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    resp.headers["Access-Control-Allow-Headers"] = "Content-Type, X-Sondeo"
    if not request.headers.get("X-Sondeo") and request.method != "OPTIONS":
        log("HTTP", f"{request.method:6s} {request.path} -> {resp.status}")
    return resp

@app.get("/api/menu")
def obtener_menu():
    return jsonify(list(MENU.values())), 200

@app.get("/api/carga")
def obtener_carga():
    with lock:
        en_fila = sum(1 for f in cola_fifo if pedidos[f]["estado"] == "EN_FILA")
        prep = sum(1 for v in estaciones.values() if v is not None)
        est_info = [
            {"estacion": k, "libre": v is None,
             "folio": v["folio"] if v else None, "comensal": v["comensal"] if v else None,
             "platillo": v["platillo"] if v else None,
             "progreso": f"{v['segundosTranscurridos']}/{v['segundosPreparacion']}s" if v else "Libre"}
            for k, v in estaciones.items()
        ]
        cola_info = [
            {"folio": f, "comensal": pedidos[f]["comensal"], "platillo": pedidos[f]["platillo"], "notas": pedidos[f]["notas"]}
            for f in cola_fifo if pedidos[f]["estado"] == "EN_FILA"
        ]
        return jsonify({
            "nombre": NOMBRE, "puerto": PUERTO, "totalEstaciones": TOTAL_ESTACIONES,
            "estacionesLibres": TOTAL_ESTACIONES - prep, "preparando": prep, "enFila": en_fila,
            "cargaActual": prep + en_fila, "atendidos": atendidos, "ventasTotales": ventas_totales,
            "estaciones": est_info, "cola": cola_info
        }), 200

@app.get("/api/pedidos")
def listar_pedidos():
    with lock:
        return jsonify(list(pedidos.values())), 200

@app.get("/api/pedidos/<int:folio>")
def consultar_pedido(folio):
    with lock:
        p = pedidos.get(folio)
        if not p:
            return jsonify({"codigo": 404, "error": "Not Found", "mensaje": f"No existe el folio {folio}"}), 404
        return jsonify(dict(p)), 200

@app.post("/api/pedidos")
def crear_pedido():
    global folio_seq
    datos = request.get_json(silent=True)
    if not isinstance(datos, dict):
        return jsonify({"codigo": 400, "error": "Bad Request", "mensaje": "JSON inválido o vacío"}), 400
    comensal = str(datos.get("comensal", "")).strip()
    platillo = MENU.get(datos.get("idPlatillo"))
    if not comensal or not platillo:
        return jsonify({"codigo": 400, "error": "Bad Request", "mensaje": "Comensal o idPlatillo inválido"}), 400
    with cond_cola:
        folio_seq += 1
        folio = folio_seq
        p = {
            "folio": folio, "cocina": NOMBRE, "comensal": comensal,
            "idPlatillo": platillo["id"], "platillo": platillo["nombre"],
            "precio": platillo["precio"], "notas": str(datos.get("notas", "")).strip(),
            "estado": "EN_FILA", "estacion": 0, "segundosTranscurridos": 0,
            "segundosPreparacion": platillo["segundos"],
            "horaRegistro": datetime.now().strftime("%H:%M:%S"), "horaListo": None
        }
        pedidos[folio] = p
        cola_fifo.append(folio)
        cond_cola.notify_all()
    log("EN_FILA", f"Folio #{folio} registrado: {platillo['nombre']} para [{comensal}]")
    resp = jsonify(p)
    resp.status_code = 202
    resp.headers["Location"] = f"http://127.0.0.1:{PUERTO}/api/pedidos/{folio}"
    return resp

@app.put("/api/pedidos/<int:folio>")
def modificar_pedido(folio):
    datos = request.get_json(silent=True) or {}
    with lock:
        p = pedidos.get(folio)
        if not p:
            return jsonify({"codigo": 404, "error": "Not Found", "mensaje": f"No existe el folio {folio}"}), 404
        if p["estado"] != "EN_FILA":
            return jsonify({"codigo": 409, "error": "Conflict", "mensaje": f"Folio {folio} ya está en estado {p['estado']}"}), 409
        if "idPlatillo" in datos and datos["idPlatillo"] is not None:
            plat = MENU.get(datos["idPlatillo"])
            if not plat:
                return jsonify({"codigo": 400, "error": "Bad Request", "mensaje": "idPlatillo inexistente"}), 400
            p.update({"idPlatillo": plat["id"], "platillo": plat["nombre"], "precio": plat["precio"], "segundosPreparacion": plat["segundos"]})
        if "notas" in datos and datos["notas"] is not None:
            p["notas"] = str(datos["notas"]).strip()
        log("MODIFICADO", f"Folio #{folio} actualizado -> {p['platillo']} (notas='{p['notas']}')")
        return jsonify(dict(p)), 200

@app.delete("/api/pedidos/<int:folio>")
def cancelar_pedido(folio):
    with cond_cola:
        p = pedidos.get(folio)
        if not p:
            return jsonify({"codigo": 404, "error": "Not Found", "mensaje": f"No existe el folio {folio}"}), 404
        if p["estado"] != "EN_FILA":
            return jsonify({"codigo": 409, "error": "Conflict", "mensaje": f"No se puede cancelar en estado {p['estado']}"}), 409
        p["estado"] = "CANCELADO"
        if folio in cola_fifo:
            cola_fifo.remove(folio)
        log("CANCELADO", f"Folio #{folio} de [{p['comensal']}] cancelado antes de entrar a estación")
        return jsonify(dict(p)), 200

@app.errorhandler(404)
def error_404(_e):
    return jsonify({"codigo": 404, "error": "Not Found", "mensaje": f"Ruta inexistente: {request.path}"}), 404

@app.errorhandler(405)
def error_405(_e):
    return jsonify({"codigo": 405, "error": "Method Not Allowed", "mensaje": f"Verbo {request.method} no permitido en {request.path}"}), 405

if __name__ == "__main__":
    threading.Thread(target=despachador_fifo, daemon=True).start()
    log("INICIO", f"Servicio REST activo en http://127.0.0.1:{PUERTO}/api ({TOTAL_ESTACIONES} estaciones | Semáforo FIFO)")
    app.run(host="0.0.0.0", port=PUERTO, threaded=True)

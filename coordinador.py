"""
Práctica 6: Servicios Web RESTful — Coordinador de Despacho y Servidor Web
Alumno: Ricardo Carmona Martínez | Materia: Sistemas Distribuidos
Uso: python coordinador.py
"""
import os, time, threading
from datetime import datetime
from flask import Flask, request, jsonify, send_from_directory
import requests, logging

logging.getLogger("werkzeug").setLevel(logging.ERROR)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
app = Flask(__name__, static_folder=os.path.join(BASE_DIR, "web"))

PUERTO = 5000
COCINAS = [
    {"nombre": "Cocina Norte",  "puerto": 6001, "url": "http://127.0.0.1:6001/api", "online": False},
    {"nombre": "Cocina Centro", "puerto": 6002, "url": "http://127.0.0.1:6002/api", "online": False},
    {"nombre": "Cocina Sur",    "puerto": 6003, "url": "http://127.0.0.1:6003/api", "online": False},
]
MENU = [
    {"id": 1, "nombre": "Tacos al Pastor (orden)", "segundos": 5, "precio": 75.0},
    {"id": 2, "nombre": "Hamburguesa Clásica",     "segundos": 7, "precio": 115.0},
    {"id": 3, "nombre": "Pizza Individual",        "segundos": 6, "precio": 125.0},
    {"id": 4, "nombre": "Ensalada César",          "segundos": 4, "precio": 85.0},
    {"id": 5, "nombre": "Alitas BBQ (12 pzs)",     "segundos": 6, "precio": 135.0},
    {"id": 6, "nombre": "Corte Ribeye",            "segundos": 8, "precio": 220.0},
]

lock = threading.Lock()
en_transito = {c["nombre"]: 0 for c in COCINAS}
rr_idx = 0

def log(tag, msg):
    print(f"[{datetime.now().strftime('%H:%M:%S')}] [Coordinador] [{tag}] {msg}", flush=True)

def sondear_cocina(c):
    try:
        r = requests.get(f"{c['url']}/carga", headers={"X-Sondeo": "1"}, timeout=0.6)
        if r.status_code == 200:
            d = r.json()
            if not c["online"]:
                c["online"] = True
                log("RECUPERACION", f"{c['nombre']} ({c['url']}) respondió 200 OK -> ONLINE")
            return {
                "nombre": c["nombre"], "url": c["url"], "puerto": c["puerto"], "online": True,
                "carga": d["cargaActual"], "enFila": d["enFila"], "preparando": d["preparando"],
                "estacionesLibres": d["estacionesLibres"], "totalEstaciones": d["totalEstaciones"],
                "atendidos": d["atendidos"], "ventas": d["ventasTotales"],
                "estaciones": d.get("estaciones", []), "cola": d.get("cola", [])
            }
    except Exception:
        pass
    if c["online"]:
        c["online"] = False
        log("FALLO", f"{c['nombre']} ({c['url']}) sin respuesta -> OFFLINE")
    return {
        "nombre": c["nombre"], "url": c["url"], "puerto": c["puerto"], "online": False,
        "carga": -1, "enFila": 0, "preparando": 0, "estacionesLibres": 0,
        "totalEstaciones": 3, "atendidos": 0, "ventas": 0.0, "estaciones": [], "cola": []
    }

@app.after_request
def agregar_cors(resp):
    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    resp.headers["Access-Control-Allow-Headers"] = "Content-Type, X-Sondeo"
    if not request.headers.get("X-Sondeo") and request.path.startswith("/api/"):
        log("HTTP", f"{request.method:6s} {request.path} -> {resp.status}")
    return resp

@app.get("/")
@app.get("/web/")
@app.get("/web/index.html")
def servir_front():
    return send_from_directory(app.static_folder, "index.html")

@app.get("/api/menu")
def obtener_menu():
    return jsonify(MENU), 200

@app.get("/api/cocinas")
def obtener_cocinas():
    return jsonify([sondear_cocina(c) for c in COCINAS]), 200

@app.post("/api/despachos")
def despachar():
    global rr_idx
    datos = request.get_json(silent=True) or {}
    comensal = str(datos.get("comensal", "")).strip()
    if not comensal:
        return jsonify({"codigo": 400, "error": "Bad Request", "mensaje": "Falta el campo comensal"}), 400
    estados = [sondear_cocina(c) for c in COCINAS]
    activas = [e for e in estados if e["online"]]
    if not activas:
        log("SIN_SERVICIO", f"Solicitud de [{comensal}] rechazada: 0 cocinas activas (503)")
        return jsonify({"codigo": 503, "error": "Service Unavailable", "mensaje": "No hay cocinas disponibles"}), 503
    with lock:
        n = len(activas)
        ordenadas = [activas[(rr_idx + i) % n] for i in range(n)]
        rr_idx = (rr_idx + 1) % n
        elegida = min(ordenadas, key=lambda x: x["carga"] + en_transito.get(x["nombre"], 0))
        carga_ef = elegida["carga"] + en_transito.get(elegida["nombre"], 0)
        en_transito[elegida["nombre"]] += 1
        threading.Timer(0.8, lambda nom=elegida["nombre"]: liberar_reserva(nom)).start()
    log("BALANCEO", f"Comensal [{comensal}] -> {elegida['nombre']} ({elegida['url']}) | Carga efectiva: {carga_ef}")
    return jsonify({"comensal": comensal, "cocina": elegida["nombre"], "url": elegida["url"], "carga": carga_ef}), 200

def liberar_reserva(nombre):
    with lock:
        en_transito[nombre] = max(0, en_transito[nombre] - 1)

if __name__ == "__main__":
    log("INICIO", f"Coordinador REST y Frontend Web activos en http://127.0.0.1:{PUERTO}/")
    for c in COCINAS:
        sondear_cocina(c)
    app.run(host="0.0.0.0", port=PUERTO, threaded=True)

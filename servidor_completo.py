"""
Servidor Unificado para Render.com y Despliegue en la Nube
Levanta las 3 cocinas como procesos independientes (6001, 6002, 6003)
y el Coordinador en el puerto asignado por la nube ($PORT o 5000).
"""
import os, sys, time, subprocess

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

if __name__ == "__main__":
    print("=== Iniciando Restaurante Distribuido en la Nube (Render) ===", flush=True)

    # 1. Levantar las 3 cocinas como procesos independientes en segundo plano
    p1 = subprocess.Popen([sys.executable, os.path.join(BASE_DIR, "cocina.py"), "6001", "Cocina Norte"])
    p2 = subprocess.Popen([sys.executable, os.path.join(BASE_DIR, "cocina.py"), "6002", "Cocina Centro"])
    p3 = subprocess.Popen([sys.executable, os.path.join(BASE_DIR, "cocina.py"), "6003", "Cocina Sur"])

    time.sleep(1.5)

    # 2. Levantar Coordinador en el puerto asignado por Render ($PORT)
    import coordinador
    puerto_render = int(os.environ.get("PORT", 5000))
    print(f"Coordinador listo en puerto {puerto_render}. Frontend Web disponible.", flush=True)

    for c in coordinador.COCINAS:
        coordinador.sondear_cocina(c)

    try:
        coordinador.app.run(host="0.0.0.0", port=puerto_render, threaded=True)
    finally:
        p1.terminate()
        p2.terminate()
        p3.terminate()

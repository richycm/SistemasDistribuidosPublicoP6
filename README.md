# Práctica 6: Servicios Web RESTful — Restaurante Distribuido (Python + Flask)

**Alumno:** Ricardo Carmona Martínez  
**Materia:** Sistemas Distribuidos  

---

## Arquitectura del Sistema

El sistema evoluciona el restaurante distribuido de la Práctica 4 reemplazando los sockets TCP y comandos de texto por **Servicios Web RESTful sobre HTTP y JSON** implementados en **Python (Flask)** y consumidos desde un **Frontend Web interactivo**:

1. **Nodos Cocina (`cocina.py` — Puertos `6001`, `6002` y `6003`)**:
   - Cada nodo posee **3 estaciones de cocción** controladas por un semáforo contador (`threading.Semaphore(3)`), exclusión mutua (`threading.Lock`) y una **cola FIFO** (`collections.deque` + `threading.Condition`).
   - Expone los verbos HTTP:
     - `GET /api/menu`: Catálogo de platillos (`200 OK`).
     - `GET /api/carga`: Estado de las 3 estaciones, cola FIFO, carga activa y ventas (`200 OK`).
     - `GET /api/pedidos` y `GET /api/pedidos/<folio>`: Consulta de estado (`200 OK` / `404 Not Found`).
     - `POST /api/pedidos`: Crea un pedido de forma asíncrona en estado `EN_FILA` (`202 Accepted` + cabecera `Location`).
     - `PUT /api/pedidos/<folio>`: Modifica platillo y/o notas mientras sigue `EN_FILA` (`200 OK` / `409 Conflict`).
     - `DELETE /api/pedidos/<folio>`: Cancela el pedido antes de pasar a cocción (`200 OK` / `409 Conflict`).

2. **Coordinador de Despacho (`coordinador.py` — Puerto `5000`)**:
   - Sondea `GET /api/carga` de las tres cocinas con detección de caídas (`ONLINE` / `OFFLINE`) y reconexión automática.
   - `POST /api/despachos`: Asigna al comensal la cocina activa con **menor carga efectiva** (`200 OK` o `503 Service Unavailable` si todas están caídas).
   - Sirve el Frontend Web en `http://127.0.0.1:5000/`.

3. **Frontend Web (`web/index.html`)**:
   - Panel visual en tiempo real que muestra las 3 cocinas con sus 3 estaciones de cocción, cola FIFO, envío de pedidos individuales o en ráfaga, modificación (`PUT`), cancelación (`DELETE`) y bitácora HTTP.

---

## Estructura de Archivos

```text
Codigo/
├── cocina.py          # Servicio REST del nodo trabajador (Semáforo + Cola FIFO)
├── coordinador.py     # Coordinador REST (Balanceo + Tolerancia a fallos + Servidor Web)
├── web/
│   └── index.html     # Frontend Web interactivo (HTML5 + CSS + Fetch API)
├── iniciar.bat        # Script para levantar las 3 cocinas, el coordinador y el navegador
├── requirements.txt   # Dependencias de Python (flask, requests)
└── README.md
```

---

## Ejecución Rápida

1. Instalar dependencias:
   ```bash
   pip install -r requirements.txt
   ```
2. Ejecutar todo el sistema en un clic:
   ```cmd
   iniciar.bat
   ```
   O manualmente en terminales separadas:
   ```bash
   python cocina.py 6001 "Cocina Norte"
   python cocina.py 6002 "Cocina Centro"
   python cocina.py 6003 "Cocina Sur"
   python coordinador.py
   ```
3. Abrir en el navegador: **http://127.0.0.1:5000/**

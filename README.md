# Práctica 6: Servicios Web RESTful — Restaurante Distribuido

**Sistemas Distribuidos · ESCOM-IPN · Grupo 7CM1**
Alumno: **Ricardo Carmona Martínez**

Práctica 6 de Sistemas Distribuidos. Implementación de un servicio web RESTful con **JAX-RS (Jersey 3)**, servidor HTTP embebido **Grizzly** y **JSON (Jackson)**. Toma como base la Práctica 4 (despacho de pedidos con *sockets* TCP): las mismas tres cocinas y el coordinador ahora se comunican por HTTP, y cualquier cliente que hable HTTP puede usarlos (cliente Java, página web en JavaScript o `curl`).

---

## Estructura del repositorio

```
Cliente/                  Clientes que consumen la API
  index.html              Cliente web (HTML + JavaScript fetch); lo sirve el coordinador en /web/
  com/.../ClienteComensal.java      Logica HTTP del comensal (java.net.http.HttpClient)
  com/.../ClienteComensalGUI.java   Interfaz Swing: pedidos concurrentes, rafagas, PUT y DELETE
src/main/java/            Servidores REST
  com/.../CocinaResource.java       Recurso JAX-RS de cada cocina (@GET @POST @PUT @DELETE)
  com/.../Cocina.java               Semaforo de estaciones, fila FIFO y despachador
  com/.../ServidorCocina.java       Levanta Grizzly + Jersey en el puerto de la cocina
  com/.../CocinaNorte|Centro|Sur.java  Lanzadores (puertos 6001, 6002, 6003)
  com/.../CoordinadorResource.java  Recurso JAX-RS del coordinador
  com/.../CoordinadorDespacho.java  Servidor del coordinador (5000), balanceo y tolerancia a fallos
  com/.../FiltroCors.java, FiltroBitacoraHttp.java, MapeadorErrores.java, ApiError.java
  com/.../Pedido.java, SolicitudPedido.java, Menu.java, CocinaGUI.java, CoordinadorGUI.java
pom.xml                   Proyecto Maven (Jersey, Grizzly, Jackson)
compilar.bat / iniciar_todo.bat / pruebas_curl.bat
```

## Arquitectura

```
                                   POST /api/despachos          GET /api/carga (sondeo)
  Cliente Java (Swing) ───┐       ┌───────────────────────┐ ───────────────────────► Cocina Norte  :6001
  Cliente Web (fetch)  ───┼─────► │ Coordinador  :5000    │ ───────────────────────► Cocina Centro :6002
  curl / Postman       ───┘       │ (también sirve /web/) │ ───────────────────────► Cocina Sur    :6003
                                  └───────────────────────┘
          │  1) El comensal pide cocina al coordinador → {"cocina": "...", "url": "http://.../api"}
          │  2) Envía su pedido directo a esa cocina   → 202 Accepted + Location: /api/pedidos/{folio}
          └─ 3) Consulta GET /api/pedidos/{folio} hasta LISTO (y mientras está EN_FILA puede usar PUT / DELETE)
```

## API REST

### Cocina — `http://127.0.0.1:600X/api`

| Verbo | Ruta | Cuerpo JSON | Respuesta |
|---|---|---|---|
| `GET` | `/menu` | — | `200` carta del restaurante |
| `GET` | `/carga` | — | `200` `{carga, enFila, estacionesLibres, totalEstaciones, atendidos, ventas}` |
| `GET` | `/pedidos?estado=EN_FILA` | — | `200` lista (filtro opcional) / `400` estado inválido |
| `GET` | `/pedidos/{folio}` | — | `200` pedido / `404` |
| `POST` | `/pedidos` | `{"comensal":"Ana","idPlatillo":2,"notas":"..."}` | `202` + `Location` / `400` |
| `PUT` | `/pedidos/{folio}` | `{"idPlatillo":3,"notas":"..."}` (campos opcionales) | `200` / `400` / `404` / `409` si ya no está en fila |
| `DELETE` | `/pedidos/{folio}` | — | `200` pedido cancelado / `404` / `409` si ya no está en fila |

### Coordinador — `http://127.0.0.1:5000/api`

| Verbo | Ruta | Cuerpo JSON | Respuesta |
|---|---|---|---|
| `GET` | `/cocinas` | — | `200` estado de los 3 nodos |
| `GET` | `/menu` | — | `200` carta |
| `POST` | `/despachos` | `{"comensal":"Ana"}` | `200` `{cocina, url, carga}` / `400` / `503` sin cocinas |

Todos los errores llevan cuerpo JSON uniforme: `{"status":409,"error":"Conflict","mensaje":"..."}`. Las rutas inexistentes dan `404`, los verbos no permitidos `405` y el JSON mal formado `400`.

---

## Cómo ejecutarlo

**Requisitos:** JDK 11 o superior y Maven (sirve el que trae Apache NetBeans). La primera compilación descarga Jersey, Grizzly y Jackson.

### Opción A — Apache NetBeans
1. *File → Open Project* y selecciona la carpeta del repositorio (se reconoce como proyecto Maven).
2. Clic derecho en el proyecto → **Build**.
3. **Run File** (`Shift + F6`) en este orden: `CocinaNorte`, `CocinaCentro`, `CocinaSur`, `CoordinadorDespacho` y `ClienteComensalGUI`.
4. Cliente web: abre <http://127.0.0.1:5000/web/> (con `?demo=1` se ejecuta sola una demostración: ráfaga, PUT, DELETE y un 404).

### Opción B — Scripts (Windows)
```bat
compilar.bat        :: genera target\p6-servicios-web.jar con todas las dependencias
iniciar_todo.bat    :: 3 cocinas, coordinador, cliente Java y abre el cliente web
pruebas_curl.bat    :: recorre todos los verbos y códigos de estado con curl (con los nodos recién iniciados)
```

### Probar la tolerancia a fallos
Cierra la ventana de la **Cocina Sur**: en a lo más 3 s el coordinador la marca **OFFLINE** y reparte los pedidos solo entre Norte y Centro. Al volver a abrirla se reintegra sola. Si se cierran las tres, `POST /api/despachos` responde **503**.

---

## De la Práctica 4 a la Práctica 6

| Aspecto | P4 (Sockets TCP) | P6 (Servicios Web REST) |
|---|---|---|
| Transporte | `ServerSocket` / `Socket` | HTTP/1.1 sobre Grizzly |
| Contrato | Cadenas `PEDIR 2 Ana` / `LISTO 12 3 9 120.00` | Recursos + verbos HTTP + JSON |
| Errores | `ERROR Platillo_invalido` | Códigos `400`, `404`, `405`, `409`, `503` |
| Espera del pedido | Socket abierto hasta que el platillo está listo | `202 Accepted` y consulta del recurso |
| Clientes posibles | Solo Java con el mismo protocolo | Cualquier lenguaje con HTTP (Java, JS, curl) |

package com.mycompany.p6sistemasdistribuidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Logica de comunicacion HTTP del Cliente (comensal), sin nada de Swing.
 * Sustituye los Sockets de la Practica 4 por java.net.http.HttpClient y el
 * protocolo de texto por JSON. El flujo distribuido sigue siendo en dos pasos:
 *   1. POST /api/despachos al Coordinador -> URL de la cocina menos cargada.
 *   2. POST /api/pedidos a esa cocina      -> 202 Accepted con el folio.
 * Despues el cliente consulta GET /api/pedidos/{folio} hasta que este LISTO,
 * y mientras siga EN_FILA puede modificarlo (PUT) o cancelarlo (DELETE).
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class ClienteComensal {

    // Un solo HttpClient para toda la aplicacion: reutiliza conexiones y es thread-safe
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String urlCoordinador;
    private final String comensal;
    private final Consumer<String> trafico;

    /**
     * @param urlCoordinador p. ej. http://127.0.0.1:5000
     * @param trafico        recibe una linea por cada peticion/respuesta (para la consola)
     */
    public ClienteComensal(String urlCoordinador, String comensal, Consumer<String> trafico) {
        this.urlCoordinador = quitarDiagonal(urlCoordinador) + "/api";
        this.comensal = comensal;
        this.trafico = trafico;
    }

    public String getComensal() {
        return comensal;
    }

    /** GET /api/menu en el coordinador. */
    public List<Menu.Platillo> obtenerMenu() throws IOException {
        JsonNode arreglo = enviar("GET", urlCoordinador + "/menu", null);
        List<Menu.Platillo> menu = new ArrayList<>();
        for (JsonNode p : arreglo) {
            menu.add(new Menu.Platillo(p.path("id").asInt(), p.path("nombre").asText(),
                    p.path("precio").asDouble(), p.path("segundosPreparacion").asInt()));
        }
        return menu;
    }

    /** GET /api/cocinas en el coordinador. */
    public JsonNode consultarCocinas() throws IOException {
        return enviar("GET", urlCoordinador + "/cocinas", null);
    }

    /** Paso 1: POST /api/despachos -> que cocina me toca. */
    public Asignacion solicitarCocina() throws IOException {
        ObjectNode cuerpo = JSON.createObjectNode().put("comensal", comensal);
        JsonNode r = enviar("POST", urlCoordinador + "/despachos", cuerpo);
        return new Asignacion(r.path("cocina").asText(), r.path("url").asText());
    }

    /** Paso 2: POST /api/pedidos en la cocina asignada. Devuelve el pedido con su folio. */
    public PedidoRemoto enviarPedido(Asignacion a, int idPlatillo, String notas) throws IOException {
        ObjectNode cuerpo = JSON.createObjectNode()
                .put("comensal", comensal)
                .put("idPlatillo", idPlatillo);
        if (notas != null && !notas.isEmpty()) {
            cuerpo.put("notas", notas);
        }
        return new PedidoRemoto(enviar("POST", a.url + "/pedidos", cuerpo));
    }

    /**
     * GET /api/pedidos/{folio}: estado actual del pedido. Es la consulta periodica,
     * asi que va marcada como sondeo y solo se reporta si falla.
     */
    public PedidoRemoto consultarPedido(Asignacion a, int folio) throws IOException {
        return new PedidoRemoto(enviar("GET", a.url + "/pedidos/" + folio, null, true));
    }

    /** PUT /api/pedidos/{folio}: cambia platillo y/o notas (null = no cambiar). */
    public PedidoRemoto modificarPedido(Asignacion a, int folio, Integer idPlatillo, String notas) throws IOException {
        ObjectNode cuerpo = JSON.createObjectNode();
        if (idPlatillo != null) {
            cuerpo.put("idPlatillo", idPlatillo);
        }
        if (notas != null) {
            cuerpo.put("notas", notas);
        }
        return new PedidoRemoto(enviar("PUT", a.url + "/pedidos/" + folio, cuerpo));
    }

    /** DELETE /api/pedidos/{folio}: cancela el pedido mientras sigue en fila. */
    public PedidoRemoto cancelarPedido(Asignacion a, int folio) throws IOException {
        return new PedidoRemoto(enviar("DELETE", a.url + "/pedidos/" + folio, null));
    }

    /**
     * Envia una peticion HTTP con cuerpo JSON opcional y devuelve el JSON de la
     * respuesta. Cualquier codigo fuera de 2xx se convierte en ErrorHttp con el
     * mensaje que mando el servidor.
     */
    private JsonNode enviar(String metodo, String url, JsonNode cuerpo) throws IOException {
        return enviar(metodo, url, cuerpo, false);
    }

    private JsonNode enviar(String metodo, String url, JsonNode cuerpo, boolean sondeo) throws IOException {
        HttpRequest.BodyPublisher publicador = (cuerpo == null)
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo));
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .method(metodo, publicador);
        if (cuerpo != null) {
            rb.header("Content-Type", "application/json");
        }
        if (sondeo) {
            rb.header(FiltroBitacoraHttp.CABECERA_SONDEO, "cliente");
        } else {
            reportar("-> " + metodo + " " + url + (cuerpo != null ? "  " + cuerpo : ""));
        }
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Peticion interrumpida", e);
        } catch (IOException e) {
            reportar("<- sin respuesta de " + url + " (" + e.getClass().getSimpleName() + ")");
            throw new IOException("Sin respuesta de " + url, e);
        }

        JsonNode json = resp.body() == null || resp.body().isEmpty()
                ? JSON.createObjectNode() : JSON.readTree(resp.body());
        if (!sondeo || resp.statusCode() / 100 != 2) {
            reportar("<- " + resp.statusCode() + "  " + resumir(json));
        }

        if (resp.statusCode() / 100 != 2) {
            throw new ErrorHttp(resp.statusCode(), json.path("mensaje").asText("HTTP " + resp.statusCode()));
        }
        return json;
    }

    private void reportar(String linea) {
        if (trafico != null) {
            trafico.accept(linea);
        }
    }

    private static String resumir(JsonNode json) {
        String s = json.toString();
        return s.length() > 110 ? s.substring(0, 107) + "..." : s;
    }

    private static String quitarDiagonal(String url) {
        String u = url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    // Cocina asignada por el coordinador
    public static class Asignacion {
        public final String nombreCocina;
        public final String url;

        public Asignacion(String nombreCocina, String url) {
            this.nombreCocina = nombreCocina;
            this.url = url;
        }
    }

    // Vista del recurso /api/pedidos/{folio} tal como lo devolvio la cocina
    public static class PedidoRemoto {
        public final int folio;
        public final String estado;
        public final String platillo;
        public final int idPlatillo;
        public final double precio;
        public final int estacion;
        public final long segundosTranscurridos;
        public final int segundosPreparacion;
        public final String notas;

        PedidoRemoto(JsonNode p) {
            this.folio = p.path("folio").asInt();
            this.estado = p.path("estado").asText();
            this.platillo = p.path("platillo").asText();
            this.idPlatillo = p.path("idPlatillo").asInt();
            this.precio = p.path("precio").asDouble();
            this.estacion = p.path("estacion").asInt();
            this.segundosTranscurridos = p.path("segundosTranscurridos").asLong();
            this.segundosPreparacion = p.path("segundosPreparacion").asInt();
            this.notas = p.path("notas").isNull() ? "" : p.path("notas").asText("");
        }
    }

    // Respuesta HTTP con codigo de error (4xx / 5xx)
    public static class ErrorHttp extends IOException {
        public final int status;

        public ErrorHttp(int status, String mensaje) {
            super("HTTP " + status + ": " + mensaje);
            this.status = status;
        }
    }
}

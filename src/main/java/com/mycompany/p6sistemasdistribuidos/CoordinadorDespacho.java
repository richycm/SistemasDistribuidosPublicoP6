package com.mycompany.p6sistemasdistribuidos;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

/**
 * Coordinador (Maestro) del sistema de despacho, ahora como servicio web REST.
 * Igual que en la Practica 4 no cocina: consulta la carga de cada cocina y manda
 * al comensal a la menos saturada. La diferencia es que todo viaja por HTTP/JSON:
 *   - Hacia los comensales expone POST /api/despachos y GET /api/cocinas.
 *   - Hacia las cocinas actua como CLIENTE REST: GET http://cocina/api/carga.
 * Ademas sirve el cliente web (HTML + JavaScript) en http://127.0.0.1:5000/web/.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class CoordinadorDespacho {

    public static final int PUERTO_COORDINADOR = 5000;
    private static final Duration TIMEOUT_SONDEO = Duration.ofMillis(800);
    private static final int INTERVALO_MONITOREO_MS = 3000;

    // Nodos de cocina conocidos por el coordinador (entre estos se balancea)
    private static final List<InfoCocina> cocinas = new ArrayList<>();
    private static final AtomicInteger totalDespachos = new AtomicInteger(0);
    private static final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(TIMEOUT_SONDEO).build();
    private static final ObjectMapper json = new ObjectMapper();
    private static final ExecutorService sondeadores = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "Sondeo-Cocinas");
        t.setDaemon(true);
        return t;
    });
    private static final FiltroBitacoraHttp bitacora = new FiltroBitacoraHttp(CoordinadorDespacho::log);
    private static CoordinadorGUI gui;

    static {
        cocinas.add(new InfoCocina("Cocina Norte", "127.0.0.1", 6001));
        cocinas.add(new InfoCocina("Cocina Centro", "127.0.0.1", 6002));
        cocinas.add(new InfoCocina("Cocina Sur", "127.0.0.1", 6003));
    }

    public static List<InfoCocina> getCocinas() {
        return Collections.unmodifiableList(cocinas);
    }

    public static int getTotalDespachos() {
        return totalDespachos.get();
    }

    public static int getTotalPeticiones() {
        return bitacora.getTotalPeticiones();
    }

    public static void main(String[] args) {
        // Interfaz grafica de monitoreo del coordinador
        try {
            SwingUtilities.invokeAndWait(() -> {
                gui = new CoordinadorGUI(cocinas);
                gui.setVisible(true);
            });
        } catch (Exception e) {
            System.out.println("No se pudo abrir la interfaz grafica: " + e.getMessage());
        }

        iniciarServidorRest();

        // Hilo que refresca periodicamente el estado de las cocinas en la GUI
        iniciarMonitorDeCocinas();
    }

    public static void iniciarServidorRest() {
        String bindHost = System.getProperty("bind.host", "0.0.0.0");
        URI base = URI.create("http://" + bindHost + ":" + PUERTO_COORDINADOR + "/");

        ResourceConfig config = new ResourceConfig()
                .register(CoordinadorResource.class)
                .property(ServerProperties.WADL_FEATURE_DISABLE, true)
                .register(JacksonFeature.withoutExceptionMappers())
                .register(new FiltroCors())
                .register(bitacora)
                .register(MapeadorErrores.ErroresHttp.class)
                .register(MapeadorErrores.JsonInvalido.class);

        try {
            HttpServer servidor = GrizzlyHttpServerFactory.createHttpServer(base, config, false);
            // Cliente web estatico (src/main/resources/web) en /web/
            servidor.getServerConfiguration().addHttpHandler(
                    new CLStaticHttpHandler(CoordinadorDespacho.class.getClassLoader(), "/web/"), "/web");
            servidor.start();
            Runtime.getRuntime().addShutdownHook(new Thread(servidor::shutdownNow));

            log("INICIO", "==========================================================");
            log("INICIO", "Coordinador REST listo en http://127.0.0.1:" + PUERTO_COORDINADOR + "/api");
            log("INICIO", "Cliente web disponible en http://127.0.0.1:" + PUERTO_COORDINADOR + "/web/");
            log("INICIO", "Balanceo por menor carga entre " + cocinas.size() + " cocinas | JAX-RS + Grizzly");
            log("INICIO", "==========================================================");
        } catch (IOException e) {
            log("ERROR", "No se pudo abrir el puerto " + PUERTO_COORDINADOR + ": " + e.getMessage());
        }
    }

    // Hilo que sondea el estado de todas las cocinas cada cierto intervalo
    private static void iniciarMonitorDeCocinas() {
        Thread monitor = new Thread(() -> {
            while (true) {
                sondearTodas();
                notificarCambio();
                try {
                    Thread.sleep(INTERVALO_MONITOREO_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Monitor-Cocinas");
        monitor.setDaemon(true);
        monitor.start();
    }

    /**
     * Elige la cocina en linea con menor carga efectiva (cola + estaciones
     * ocupadas + pedidos ya despachados que aun no llegan). Sondea a todas en
     * el momento para decidir con datos frescos. Es synchronized para que dos
     * despachos simultaneos no lean la misma foto y elijan la misma cocina.
     */
    static synchronized InfoCocina elegirCocinaMenosCargada(String comensal) {
        sondearTodas();
        InfoCocina elegida = null;
        for (InfoCocina c : cocinas) {
            if (c.isOnline() && (elegida == null || c.cargaEfectiva() < elegida.cargaEfectiva())) {
                elegida = c;
            }
        }
        if (elegida != null) {
            log("DESPACHO", "Comensal [" + comensal + "] enviado a " + elegida.getNombre()
                    + " (" + elegida.getUrl() + ") | Carga efectiva: " + elegida.cargaEfectiva());
            elegida.registrarDespacho();
            totalDespachos.incrementAndGet();
        }
        return elegida;
    }

    // Sondea las cocinas en paralelo: el tiempo total es el de la mas lenta, no la suma
    private static void sondearTodas() {
        CompletableFuture<?>[] sondeos = new CompletableFuture<?>[cocinas.size()];
        for (int i = 0; i < cocinas.size(); i++) {
            InfoCocina c = cocinas.get(i);
            sondeos[i] = CompletableFuture.runAsync(() -> sondearCarga(c), sondeadores);
        }
        CompletableFuture.allOf(sondeos).join();
    }

    /**
     * Pide GET /api/carga a una cocina. Si responde 200 actualiza su estado;
     * si no responde (o responde otra cosa) la marca OFFLINE.
     */
    private static void sondearCarga(InfoCocina c) {
        boolean estabaOnline = c.isOnline();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(c.getUrl() + "/carga"))
                    .timeout(TIMEOUT_SONDEO)
                    .header("Accept", "application/json")
                    .header(FiltroBitacoraHttp.CABECERA_SONDEO, "coordinador")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode carga = json.readTree(resp.body());
                c.actualizarEstado(carga.path("carga").asInt(), carga.path("enFila").asInt(),
                        carga.path("estacionesLibres").asInt(), carga.path("totalEstaciones").asInt(),
                        carga.path("atendidos").asInt(), carga.path("ventas").asDouble(),
                        carga.path("recibidos").asInt());
                if (!estabaOnline) {
                    log("RECUPERACION", c.getNombre() + " (" + c.getUrl() + ") respondio 200 OK. Integrada al balanceo.");
                }
                return;
            }
        } catch (IOException | RuntimeException e) {
            // Cocina inalcanzable o respuesta invalida
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        c.marcarOffline();
        if (estabaOnline) {
            log("AVISO", c.getNombre() + " (" + c.getUrl() + ") no respondio a GET /api/carga. Marcada OFFLINE.");
        }
    }

    public static void log(String tag, String mensaje) {
        String tiempo = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String linea = String.format("[%s] [%s] %s", tiempo, tag, mensaje);
        System.out.println(linea);
        if (gui != null) {
            gui.agregarLog(linea);
        }
    }

    static void notificarCambio() {
        if (gui != null) {
            gui.refrescar();
        }
    }

    /**
     * Estado que el coordinador guarda de cada cocina. Jackson lo publica tal
     * cual en GET /api/cocinas a partir de sus getters.
     */
    @JsonPropertyOrder({"nombre", "url", "host", "puerto", "online", "carga", "enFila", "enCamino",
            "estacionesLibres", "totalEstaciones", "atendidos", "ventas"})
    public static class InfoCocina {
        private final String nombre;
        private final String host;
        private final int puerto;

        private boolean online = false;
        private int carga = 0;
        private int enFila = 0;
        private int estacionesLibres = 0;
        private int totalEstaciones = 0;
        private int atendidos = 0;
        private double ventas = 0.0;

        // Pedidos que la cocina dice haber recibido y comensales que el coordinador le ha mandado
        private int recibidos = 0;
        private int despachados = 0;

        public InfoCocina(String nombre, String host, int puerto) {
            this.nombre = nombre;
            this.host = host;
            this.puerto = puerto;
        }

        public synchronized void actualizarEstado(int carga, int enFila, int libres, int total, int atendidos,
                                                  double ventas, int recibidos) {
            // Si la cocina estaba caida o se reinicio entre dos sondeos (su contador retrocedio),
            // sus folios volvieron a empezar: se re-sincroniza la cuenta de despachos
            if (!online || recibidos < this.recibidos || recibidos > despachados) {
                this.despachados = recibidos;
            }
            this.online = true;
            this.carga = carga;
            this.enFila = enFila;
            this.estacionesLibres = libres;
            this.totalEstaciones = total;
            this.atendidos = atendidos;
            this.ventas = ventas;
            this.recibidos = recibidos;
        }

        public synchronized void marcarOffline() {
            this.online = false;
        }

        /*
         * Entre que el coordinador responde y el comensal hace su POST a la cocina
         * pasan unos milisegundos en los que GET /api/carga todavia no lo cuenta.
         * Esos pedidos "en camino" (despachados - recibidos) se suman a la carga
         * para que una rafaga no caiga entera en la misma cocina.
         */
        synchronized void registrarDespacho() {
            despachados++;
        }

        public synchronized int getEnCamino() {
            return Math.max(0, despachados - recibidos);
        }

        synchronized int cargaEfectiva() {
            return carga + getEnCamino();
        }

        public String getNombre() {
            return nombre;
        }

        public String getHost() {
            return host;
        }

        public int getPuerto() {
            return puerto;
        }

        public String getUrl() {
            return "http://" + host + ":" + puerto + "/api";
        }

        public synchronized boolean isOnline() {
            return online;
        }

        public synchronized int getCarga() {
            return carga;
        }

        public synchronized int getEnFila() {
            return enFila;
        }

        public synchronized int getEstacionesLibres() {
            return estacionesLibres;
        }

        public synchronized int getTotalEstaciones() {
            return totalEstaciones;
        }

        public synchronized int getAtendidos() {
            return atendidos;
        }

        public synchronized double getVentas() {
            return ventas;
        }
    }
}

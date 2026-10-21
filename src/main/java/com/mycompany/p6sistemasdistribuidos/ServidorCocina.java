package com.mycompany.p6sistemasdistribuidos;

import java.io.IOException;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.SwingUtilities;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

/**
 * Servidor de Cocina (Nodo Trabajador) publicado como servicio web REST.
 * Levanta un servidor HTTP embebido Grizzly en su puerto y le monta el recurso
 * JAX-RS {@link CocinaResource}. Cada instancia es un nodo independiente.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class ServidorCocina {

    private final int puerto;
    private final String nombre;
    private final Cocina cocina;
    private final FiltroBitacoraHttp bitacora;
    private CocinaGUI gui;

    public ServidorCocina(int puerto, String nombre, int estaciones) {
        this.puerto = puerto;
        this.nombre = nombre;
        this.cocina = new Cocina(nombre, estaciones);
        this.bitacora = new FiltroBitacoraHttp(this::log);
    }

    public void iniciar() {
        // Interfaz grafica de monitoreo de esta cocina
        try {
            SwingUtilities.invokeAndWait(() -> {
                gui = new CocinaGUI(nombre, puerto, cocina, bitacora);
                gui.setVisible(true);
                // Los eventos del modelo van a la consola estandar y a la GUI por el mismo log()
                cocina.setListener(new Cocina.CocinaListener() {
                    @Override
                    public void onEstadoCambiado() {
                        gui.onEstadoCambiado();
                    }

                    @Override
                    public void onEvento(String tag, String mensaje) {
                        log(tag, mensaje);
                    }
                });
            });
        } catch (Exception e) {
            System.out.println("No se pudo abrir la interfaz grafica: " + e.getMessage());
        }

        // Por defecto escucha en todas las interfaces (LAN). Con -Dbind.host=127.0.0.1
        // escucha solo en loopback (util para pruebas locales sin avisos de firewall).
        String bindHost = System.getProperty("bind.host", "0.0.0.0");
        URI base = URI.create("http://" + bindHost + ":" + puerto + "/");

        ResourceConfig config = new ResourceConfig()
                .register(CocinaResource.class)
                .register(new AbstractBinder() {
                    @Override
                    protected void configure() {
                        bind(cocina).to(Cocina.class);
                    }
                })
                .property(ServerProperties.WADL_FEATURE_DISABLE, true)
                .register(JacksonFeature.withoutExceptionMappers())
                .register(new FiltroCors())
                .register(bitacora)
                .register(MapeadorErrores.ErroresHttp.class)
                .register(MapeadorErrores.JsonInvalido.class);

        try {
            HttpServer servidor = GrizzlyHttpServerFactory.createHttpServer(base, config, false);
            servidor.start();
            Runtime.getRuntime().addShutdownHook(new Thread(servidor::shutdownNow));

            log("INICIO", "==========================================================");
            log("INICIO", nombre + " publicada como servicio REST en http://127.0.0.1:" + puerto + "/api");
            log("INICIO", "Recursos: " + cocina.getTotalEstaciones() + " estaciones | Semaforo FIFO | JAX-RS + Grizzly");
            log("INICIO", "==========================================================");
        } catch (IOException e) {
            log("ERROR", "No se pudo abrir el puerto " + puerto + ": " + e.getMessage());
        }
    }

    public void log(String tag, String mensaje) {
        String tiempo = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.println(String.format("[%s] [%s] [%s] %s", tiempo, nombre, tag, mensaje));
        if (gui != null) {
            gui.agregarLog(String.format("[%s] [%s] %s", tiempo, tag, mensaje));
        }
    }

    public static void main(String[] args) {
        // Uso: java ServidorCocina <puerto> <nombre> <estaciones>
        int puerto = (args.length > 0) ? Integer.parseInt(args[0]) : 6001;
        String nombre = (args.length > 1) ? args[1] : "Cocina Norte";
        int estaciones = (args.length > 2) ? Integer.parseInt(args[2]) : 3;
        new ServidorCocina(puerto, nombre, estaciones).iniciar();
    }
}

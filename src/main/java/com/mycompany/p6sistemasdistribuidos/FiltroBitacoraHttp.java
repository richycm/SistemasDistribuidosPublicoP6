package com.mycompany.p6sistemasdistribuidos;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Bitacora de trafico HTTP del servidor.
 * Intercepta cada peticion (para medir cuanto tarda) y cada respuesta, y manda a
 * la consola grafica una linea con el verbo, la ruta y el codigo de estado:
 *     POST /api/pedidos -> 202 Accepted (3 ms)
 *
 * Las peticiones que llevan la cabecera "X-Sondeo" (el monitor del coordinador
 * cada 3 s y la consulta de cada pedido que hace el cliente Java cada segundo)
 * solo se cuentan, para que la consola muestre el trafico que cambia el estado.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
@Provider
public class FiltroBitacoraHttp implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String CABECERA_SONDEO = "X-Sondeo";
    private static final String INICIO = "p6.inicio";

    private final BiConsumer<String, String> log;
    private final AtomicInteger totalPeticiones = new AtomicInteger(0);

    public FiltroBitacoraHttp(BiConsumer<String, String> log) {
        this.log = log;
    }

    public int getTotalPeticiones() {
        return totalPeticiones.get();
    }

    @Override
    public void filter(ContainerRequestContext peticion) {
        peticion.setProperty(INICIO, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext peticion, ContainerResponseContext respuesta) {
        totalPeticiones.incrementAndGet();
        if (peticion.getHeaderString(CABECERA_SONDEO) != null) {
            return;
        }

        Object inicio = peticion.getProperty(INICIO);
        long ms = (inicio instanceof Long) ? (System.nanoTime() - (Long) inicio) / 1_000_000 : 0;

        String ruta = "/" + peticion.getUriInfo().getPath();
        String query = peticion.getUriInfo().getRequestUri().getRawQuery();
        if (query != null && !query.isEmpty()) {
            ruta += "?" + query;
        }

        String motivo = respuesta.getStatusInfo() != null ? respuesta.getStatusInfo().getReasonPhrase() : "";
        log.accept("HTTP", String.format("%-6s %s -> %d %s (%d ms)",
                peticion.getMethod(), ruta, respuesta.getStatus(), motivo, ms));
    }
}

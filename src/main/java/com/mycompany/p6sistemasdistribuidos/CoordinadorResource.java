package com.mycompany.p6sistemasdistribuidos;

import com.mycompany.p6sistemasdistribuidos.CoordinadorDespacho.InfoCocina;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio web RESTful del Coordinador (JAX-RS).
 * Sustituye al comando "SOLICITAR_COCINA" sobre sockets de la Practica 4.
 *
 *   GET  /api/cocinas   -> estado de cada cocina (online, carga, estaciones...)
 *   GET  /api/menu      -> carta del restaurante
 *   POST /api/despachos -> asigna al comensal la cocina con menor carga
 *                          (503 Service Unavailable si ninguna responde)
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoordinadorResource {

    @GET
    @Path("/cocinas")
    public List<InfoCocina> listarCocinas() {
        return CoordinadorDespacho.getCocinas();
    }

    @GET
    @Path("/menu")
    public List<Menu.Platillo> obtenerMenu() {
        return Menu.obtenerPlatillos();
    }

    @POST
    @Path("/despachos")
    public Map<String, Object> despachar(SolicitudPedido solicitud) {
        String comensal = (solicitud == null || solicitud.getComensal() == null)
                ? "" : solicitud.getComensal().trim();
        if (comensal.isEmpty()) {
            throw ApiError.excepcion(Response.Status.BAD_REQUEST, "El campo 'comensal' es obligatorio.");
        }

        InfoCocina elegida = CoordinadorDespacho.elegirCocinaMenosCargada(comensal);
        CoordinadorDespacho.notificarCambio();
        if (elegida == null) {
            CoordinadorDespacho.log("RECHAZO", "No hay cocinas en linea para atender a [" + comensal + "]");
            throw ApiError.excepcion(Response.Status.SERVICE_UNAVAILABLE,
                    "No hay cocinas disponibles en este momento. Intenta mas tarde.");
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("comensal", comensal);
        json.put("cocina", elegida.getNombre());
        json.put("url", elegida.getUrl());
        json.put("carga", elegida.getCarga());
        return json;
    }
}

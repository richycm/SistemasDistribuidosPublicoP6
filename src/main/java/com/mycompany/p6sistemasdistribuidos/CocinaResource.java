package com.mycompany.p6sistemasdistribuidos;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio web RESTful de un nodo de cocina (JAX-RS).
 * Sustituye al protocolo de texto "CARGA" / "PEDIR" sobre sockets de la Practica 4.
 *
 *   GET    /api/menu               -> carta del restaurante
 *   GET    /api/carga              -> carga actual (la consulta el coordinador)
 *   GET    /api/pedidos[?estado=X] -> pedidos de esta cocina
 *   GET    /api/pedidos/{folio}    -> estado de un pedido
 *   POST   /api/pedidos            -> nuevo pedido (202 Accepted + Location)
 *   PUT    /api/pedidos/{folio}    -> cambia platillo/notas (solo EN_FILA)
 *   DELETE /api/pedidos/{folio}    -> cancela el pedido (solo EN_FILA)
 *
 * Jersey crea un hilo por peticion; el estado compartido vive en {@link Cocina},
 * que lo protege con su semaforo y bloques sincronizados.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CocinaResource {

    private final Cocina cocina;

    // Jersey crea el recurso en cada peticion e inyecta la unica Cocina del nodo
    @Inject
    public CocinaResource(Cocina cocina) {
        this.cocina = cocina;
    }

    @GET
    @Path("/menu")
    public List<Menu.Platillo> obtenerMenu() {
        return Menu.obtenerPlatillos();
    }

    @GET
    @Path("/carga")
    public Map<String, Object> obtenerCarga() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("cocina", cocina.getNombre());
        json.put("carga", cocina.getCargaActual());
        json.put("enFila", cocina.getPedidosEnFila());
        json.put("estacionesLibres", cocina.getEstacionesLibres());
        json.put("totalEstaciones", cocina.getTotalEstaciones());
        json.put("atendidos", cocina.getTotalAtendidos());
        json.put("cancelados", cocina.getTotalCancelados());
        json.put("recibidos", cocina.getTotalRecibidos());
        json.put("ventas", cocina.getVentasTotales());
        return json;
    }

    @GET
    @Path("/pedidos")
    public List<Pedido> listarPedidos(@QueryParam("estado") String estado) {
        Pedido.Estado filtro = null;
        if (estado != null && !estado.isEmpty()) {
            try {
                filtro = Pedido.Estado.valueOf(estado.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ApiError.excepcion(Response.Status.BAD_REQUEST,
                        "Estado desconocido: " + estado + ". Usa EN_FILA, PREPARANDO, LISTO o CANCELADO.");
            }
        }
        return cocina.listarPedidos(filtro);
    }

    @GET
    @Path("/pedidos/{folio}")
    public Pedido consultarPedido(@PathParam("folio") int folio) {
        Pedido pedido = cocina.buscarPedido(folio);
        if (pedido == null) {
            throw noExiste(folio);
        }
        return pedido;
    }

    @POST
    @Path("/pedidos")
    public Response crearPedido(SolicitudPedido solicitud, @Context UriInfo uriInfo) {
        if (solicitud == null) {
            throw ApiError.excepcion(Response.Status.BAD_REQUEST, "Falta el cuerpo JSON del pedido.");
        }
        String comensal = solicitud.getComensal() == null ? "" : solicitud.getComensal().trim();
        if (comensal.isEmpty()) {
            throw ApiError.excepcion(Response.Status.BAD_REQUEST, "El campo 'comensal' es obligatorio.");
        }
        Menu.Platillo platillo = validarPlatillo(solicitud.getIdPlatillo(), true);

        Pedido pedido = cocina.registrarPedido(comensal, platillo, limpiar(solicitud.getNotas()));

        // 202: aceptado pero todavia en proceso. Location dice donde consultarlo.
        URI ubicacion = uriInfo.getBaseUriBuilder().path("api/pedidos/{folio}").build(pedido.getFolio());
        return Response.accepted(pedido).location(ubicacion).build();
    }

    @PUT
    @Path("/pedidos/{folio}")
    public Pedido modificarPedido(@PathParam("folio") int folio, SolicitudPedido cambios) {
        if (cambios == null || (cambios.getIdPlatillo() == null && cambios.getNotas() == null)) {
            throw ApiError.excepcion(Response.Status.BAD_REQUEST,
                    "Envia al menos 'idPlatillo' o 'notas' para modificar el pedido.");
        }
        Menu.Platillo platillo = validarPlatillo(cambios.getIdPlatillo(), false);
        try {
            Pedido pedido = cocina.modificarPedido(folio, platillo, limpiar(cambios.getNotas()));
            if (pedido == null) {
                throw noExiste(folio);
            }
            return pedido;
        } catch (IllegalStateException e) {
            throw ApiError.excepcion(Response.Status.CONFLICT, e.getMessage());
        }
    }

    @DELETE
    @Path("/pedidos/{folio}")
    public Pedido cancelarPedido(@PathParam("folio") int folio) {
        try {
            Pedido pedido = cocina.cancelarPedido(folio);
            if (pedido == null) {
                throw noExiste(folio);
            }
            return pedido;
        } catch (IllegalStateException e) {
            throw ApiError.excepcion(Response.Status.CONFLICT, e.getMessage());
        }
    }

    // --- Validaciones auxiliares ---

    private Menu.Platillo validarPlatillo(Integer id, boolean obligatorio) {
        if (id == null) {
            if (obligatorio) {
                throw ApiError.excepcion(Response.Status.BAD_REQUEST, "El campo 'idPlatillo' es obligatorio.");
            }
            return null;
        }
        Menu.Platillo platillo = Menu.buscarPorId(id);
        if (platillo == null) {
            throw ApiError.excepcion(Response.Status.BAD_REQUEST,
                    "No existe el platillo " + id + ". Consulta GET /api/menu.");
        }
        return platillo;
    }

    private jakarta.ws.rs.WebApplicationException noExiste(int folio) {
        return ApiError.excepcion(Response.Status.NOT_FOUND,
                "No existe el pedido con folio " + folio + " en " + cocina.getNombre() + ".");
    }

    private static String limpiar(String texto) {
        return texto == null ? null : texto.trim();
    }
}

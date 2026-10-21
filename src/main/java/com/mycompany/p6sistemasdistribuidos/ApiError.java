package com.mycompany.p6sistemasdistribuidos;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construye las respuestas de error de la API con un cuerpo JSON uniforme:
 *   { "status": 404, "error": "Not Found", "mensaje": "..." }
 * Asi cualquier cliente (Java, navegador, curl) interpreta igual los fallos.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public final class ApiError {

    private ApiError() {
    }

    public static Map<String, Object> cuerpo(int status, String mensaje) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("status", status);
        Response.Status s = Response.Status.fromStatusCode(status);
        json.put("error", s != null ? s.getReasonPhrase() : "Error");
        json.put("mensaje", mensaje);
        return json;
    }

    public static Response respuesta(Response.Status status, String mensaje) {
        return Response.status(status)
                .entity(cuerpo(status.getStatusCode(), mensaje))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    // Para cortar el flujo de un endpoint con un error HTTP concreto
    public static WebApplicationException excepcion(Response.Status status, String mensaje) {
        return new WebApplicationException(respuesta(status, mensaje));
    }
}

package com.mycompany.p6sistemasdistribuidos;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Traductores de excepciones a respuestas HTTP con cuerpo JSON.
 * Sin ellos Jersey contestaria rutas inexistentes (404), verbos no permitidos
 * (405) o JSON mal formado (400) con una pagina HTML o texto plano.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public final class MapeadorErrores {

    private MapeadorErrores() {
    }

    /** 404, 405, 415... y los errores que lanzan los propios endpoints. */
    @Provider
    public static class ErroresHttp implements ExceptionMapper<WebApplicationException> {
        @Override
        public Response toResponse(WebApplicationException e) {
            Response original = e.getResponse();
            if (original.hasEntity()) {
                // Ya viene con cuerpo JSON (ApiError.excepcion)
                return original;
            }
            int status = original.getStatus();
            String mensaje;
            switch (status) {
                case 404:
                    mensaje = "La ruta solicitada no existe en esta API.";
                    break;
                case 405:
                    mensaje = "El verbo HTTP no esta permitido en esta ruta. Permitidos: " + original.getAllowedMethods();
                    break;
                case 415:
                    mensaje = "El cuerpo debe enviarse como application/json.";
                    break;
                default:
                    mensaje = e.getMessage();
            }
            Response.ResponseBuilder rb = Response.status(status)
                    .entity(ApiError.cuerpo(status, mensaje))
                    .type(MediaType.APPLICATION_JSON);
            if (status == 405) {
                rb.allow(original.getAllowedMethods());
            }
            return rb.build();
        }
    }

    /** JSON con sintaxis invalida o con tipos que no corresponden (p. ej. texto en idPlatillo). */
    @Provider
    public static class JsonInvalido implements ExceptionMapper<JsonProcessingException> {
        @Override
        public Response toResponse(JsonProcessingException e) {
            return ApiError.respuesta(Response.Status.BAD_REQUEST,
                    "JSON invalido: " + e.getOriginalMessage());
        }
    }
}

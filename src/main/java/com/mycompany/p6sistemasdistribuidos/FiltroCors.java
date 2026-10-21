package com.mycompany.p6sistemasdistribuidos;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

/**
 * Filtro CORS (Cross-Origin Resource Sharing).
 * El cliente web se sirve desde el coordinador (puerto 5000) pero hace peticiones
 * directas a las cocinas (puertos 6001-6003): para el navegador son origenes
 * distintos. Estas cabeceras le indican que la API acepta esas peticiones,
 * incluida la verificacion previa (preflight OPTIONS) de PUT y DELETE.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
@Provider
public class FiltroCors implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext peticion, ContainerResponseContext respuesta) {
        MultivaluedMap<String, Object> h = respuesta.getHeaders();
        h.putSingle("Access-Control-Allow-Origin", "*");
        h.putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.putSingle("Access-Control-Allow-Headers", "Origin, Content-Type, Accept");
        h.putSingle("Access-Control-Expose-Headers", "Location");
    }
}

package com.mycompany.p6sistemasdistribuidos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO (Data Transfer Object) del cuerpo JSON que reciben POST y PUT de
 * /api/pedidos. Jackson lo llena automaticamente con sus setters:
 *   { "comensal": "Ricardo", "idPlatillo": 2, "notas": "sin cebolla" }
 * En el PUT todos los campos son opcionales: solo se cambia lo que venga.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolicitudPedido {

    private String comensal;
    private Integer idPlatillo;
    private String notas;

    public String getComensal() {
        return comensal;
    }

    public void setComensal(String comensal) {
        this.comensal = comensal;
    }

    public Integer getIdPlatillo() {
        return idPlatillo;
    }

    public void setIdPlatillo(Integer idPlatillo) {
        this.idPlatillo = idPlatillo;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }
}

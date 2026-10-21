package com.mycompany.p6sistemasdistribuidos;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Recurso REST "pedido" de una cocina. Cada pedido se identifica por su folio y
 * se expone en la URI /api/pedidos/{folio}. Jackson lo convierte a JSON a partir
 * de sus getters publicos.
 *
 * Ciclo de vida:  EN_FILA -> PREPARANDO -> LISTO
 *                    \-> CANCELADO (solo mientras sigue en la fila)
 *
 * Los cambios de estado los hace la Cocina dentro de sus bloques sincronizados;
 * los campos son volatile para que los hilos HTTP que solo leen (GET) vean
 * siempre el valor mas reciente.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
@JsonPropertyOrder({"folio", "cocina", "comensal", "idPlatillo", "platillo", "precio", "notas", "estado",
        "estacion", "segundosTranscurridos", "segundosPreparacion", "horaRegistro", "horaListo"})
public class Pedido {

    public enum Estado { EN_FILA, PREPARANDO, LISTO, CANCELADO }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int folio;
    private final String cocina;
    private final String comensal;
    private final String horaRegistro;

    private volatile Menu.Platillo platillo;
    private volatile String notas;
    private volatile Estado estado = Estado.EN_FILA;
    private volatile int estacion = 0;
    private volatile long inicioPreparacion = 0;
    private volatile String horaListo = null;

    public Pedido(int folio, String cocina, String comensal, Menu.Platillo platillo, String notas) {
        this.folio = folio;
        this.cocina = cocina;
        this.comensal = comensal;
        this.platillo = platillo;
        this.notas = notas;
        this.horaRegistro = LocalTime.now().format(HORA);
    }

    // --- Transiciones (las invoca la Cocina con su candado tomado) ---

    void iniciarPreparacion(int numEstacion) {
        this.estacion = numEstacion;
        this.inicioPreparacion = System.currentTimeMillis();
        this.estado = Estado.PREPARANDO;
    }

    void marcarListo() {
        this.horaListo = LocalTime.now().format(HORA);
        this.estado = Estado.LISTO;
    }

    void marcarCancelado() {
        this.estado = Estado.CANCELADO;
    }

    void setPlatillo(Menu.Platillo platillo) {
        this.platillo = platillo;
    }

    void setNotas(String notas) {
        this.notas = notas;
    }

    Menu.Platillo platilloInterno() {
        return platillo;
    }

    // --- Representacion JSON del recurso ---

    public int getFolio() {
        return folio;
    }

    public String getCocina() {
        return cocina;
    }

    public String getComensal() {
        return comensal;
    }

    public int getIdPlatillo() {
        return platillo.id;
    }

    public String getPlatillo() {
        return platillo.nombre;
    }

    public double getPrecio() {
        return platillo.precio;
    }

    public int getSegundosPreparacion() {
        return platillo.segundosPreparacion;
    }

    public String getNotas() {
        return notas;
    }

    public Estado getEstado() {
        return estado;
    }

    public int getEstacion() {
        return estacion;
    }

    public String getHoraRegistro() {
        return horaRegistro;
    }

    public String getHoraListo() {
        return horaListo;
    }

    // Segundos que lleva en la estacion (o que tardo, si ya esta listo)
    public long getSegundosTranscurridos() {
        if (inicioPreparacion == 0) {
            return 0;
        }
        if (estado == Estado.LISTO) {
            return platillo.segundosPreparacion;
        }
        return (System.currentTimeMillis() - inicioPreparacion) / 1000;
    }
}

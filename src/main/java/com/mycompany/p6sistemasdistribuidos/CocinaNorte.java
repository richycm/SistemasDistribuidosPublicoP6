package com.mycompany.p6sistemasdistribuidos;

/**
 * Lanzador de la Cocina Norte (servicio REST en el puerto 6001).
 * Ejecuta este archivo con Run File (Shift + F6) para abrir esta cocina sin
 * necesidad de configurar argumentos en NetBeans.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class CocinaNorte {
    public static void main(String[] args) {
        new ServidorCocina(6001, "Cocina Norte", 3).iniciar();
    }
}
package com.mycompany.p6sistemasdistribuidos;

/**
 * Lanzador de la Cocina Centro (servicio REST en el puerto 6002).
 * Ejecuta este archivo con Run File (Shift + F6) para abrir esta cocina sin
 * necesidad de configurar argumentos en NetBeans.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class CocinaCentro {
    public static void main(String[] args) {
        new ServidorCocina(6002, "Cocina Centro", 3).iniciar();
    }
}
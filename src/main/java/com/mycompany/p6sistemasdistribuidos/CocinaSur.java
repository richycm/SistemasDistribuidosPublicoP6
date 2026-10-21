package com.mycompany.p6sistemasdistribuidos;

/**
 * Lanzador de la Cocina Sur (servicio REST en el puerto 6003).
 * Ejecuta este archivo con Run File (Shift + F6) para abrir esta cocina sin
 * necesidad de configurar argumentos en NetBeans.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class CocinaSur {
    public static void main(String[] args) {
        new ServidorCocina(6003, "Cocina Sur", 3).iniciar();
    }
}
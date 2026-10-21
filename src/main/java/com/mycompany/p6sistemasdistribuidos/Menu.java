package com.mycompany.p6sistemasdistribuidos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catalogo de platillos del restaurante distribuido.
 * Es el mismo de la Practica 4, pero ahora tambien se publica como recurso REST
 * (GET /api/menu) para que cualquier cliente, sin importar su lenguaje, lo consulte
 * en formato JSON en lugar de compilarlo dentro de su codigo.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class Menu {

    // Modelo de datos de un platillo del menu (Jackson serializa sus campos publicos)
    public static class Platillo {
        public final int id;
        public final String nombre;
        public final double precio;
        public final int segundosPreparacion;

        public Platillo(int id, String nombre, double precio, int segundosPreparacion) {
            this.id = id;
            this.nombre = nombre;
            this.precio = precio;
            this.segundosPreparacion = segundosPreparacion;
        }
    }

    private static final List<Platillo> PLATILLOS = new ArrayList<>();

    // Carta fija del restaurante (id, nombre, precio MXN, segundos de coccion)
    static {
        PLATILLOS.add(new Platillo(1, "Hamburguesa Clasica", 95.00, 6));
        PLATILLOS.add(new Platillo(2, "Pizza Individual", 120.00, 9));
        PLATILLOS.add(new Platillo(3, "Tacos al Pastor (orden)", 75.00, 5));
        PLATILLOS.add(new Platillo(4, "Ensalada Cesar", 85.00, 4));
        PLATILLOS.add(new Platillo(5, "Alitas BBQ (12 pzs)", 145.00, 8));
        PLATILLOS.add(new Platillo(6, "Pasta Alfredo", 110.00, 7));
    }

    public static List<Platillo> obtenerPlatillos() {
        return Collections.unmodifiableList(PLATILLOS);
    }

    public static Platillo buscarPorId(int id) {
        for (Platillo p : PLATILLOS) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }
}

package com.mycompany.p6sistemasdistribuidos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Recurso compartido de un nodo de cocina: un numero acotado de estaciones de
 * coccion protegidas por un Semaforo contador, igual que en la Practica 4.
 *
 * Lo nuevo en la Practica 6 es que el pedido ya no mantiene un socket abierto
 * mientras se cocina: el servicio web responde de inmediato (HTTP 202 Accepted),
 * el pedido se forma en una fila FIFO y un "jefe de cocina" (hilo despachador)
 * lo pasa a una estacion en cuanto el semaforo concede un permiso. El cliente
 * consulta despues el recurso /api/pedidos/{folio} para saber en que estado va.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class Cocina {

    private final String nombre;
    private final int totalEstaciones;
    private final Semaphore semaforo;
    private final Pedido[] estaciones;

    // Todos los pedidos recibidos, ordenados por folio (lectura concurrente segura)
    private final Map<Integer, Pedido> pedidos = new ConcurrentSkipListMap<>();
    // Fila de espera: el orden de llegada (folio) es el orden de atencion
    private final BlockingQueue<Pedido> fila = new LinkedBlockingQueue<>();
    private final ExecutorService cocineros;
    private final AtomicInteger folios = new AtomicInteger(0);

    private int pedidosEnFila = 0;
    private int totalAtendidos = 0;
    private int totalCancelados = 0;
    private double ventasTotales = 0.0;

    private CocinaListener listener;

    public Cocina(String nombre, int totalEstaciones) {
        this.nombre = nombre;
        this.totalEstaciones = totalEstaciones;
        this.semaforo = new Semaphore(totalEstaciones, true);
        this.estaciones = new Pedido[totalEstaciones];
        this.cocineros = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Cocinero-" + nombre);
            t.setDaemon(true);
            return t;
        });
        Thread jefe = new Thread(this::despacharEnOrden, "JefeDeCocina-" + nombre);
        jefe.setDaemon(true);
        jefe.start();
    }

    public interface CocinaListener {
        void onEstadoCambiado();
        void onEvento(String tag, String mensaje);
    }

    public void setListener(CocinaListener listener) {
        this.listener = listener;
    }

    private void notificarCambio() {
        if (listener != null) {
            listener.onEstadoCambiado();
        }
    }

    private void notificarEvento(String tag, String mensaje) {
        if (listener != null) {
            listener.onEvento(tag, mensaje);
        }
    }

    /**
     * POST /api/pedidos: registra el pedido y lo forma en la fila. No bloquea al
     * hilo HTTP; la espera por una estacion la hace el jefe de cocina.
     */
    public Pedido registrarPedido(String comensal, Menu.Platillo platillo, String notas) {
        Pedido pedido;
        synchronized (this) {
            pedido = new Pedido(folios.incrementAndGet(), nombre, comensal, platillo, notas);
            pedidos.put(pedido.getFolio(), pedido);
            pedidosEnFila++;
            // Se registra antes de formarlo: el jefe de cocina podria tomarlo de inmediato
            notificarEvento("FILA", "Folio " + pedido.getFolio() + ": [" + comensal + "] pidio "
                    + platillo.nombre + ". Formado en cola (En fila: " + pedidosEnFila + ").");
            fila.add(pedido);
        }
        notificarCambio();
        return pedido;
    }

    /**
     * Hilo despachador ("jefe de cocina"): toma el siguiente pedido de la fila en
     * orden de llegada y espera de forma pasiva en el semaforo hasta que haya una
     * estacion libre. Al ser un solo hilo el que adquiere los permisos, el orden
     * de atencion es exactamente el orden de los folios (FIFO estricto).
     */
    private void despacharEnOrden() {
        while (true) {
            Pedido pedido;
            try {
                pedido = fila.take();
                semaforo.acquire();
            } catch (InterruptedException e) {
                return;
            }

            int numEstacion = -1;
            synchronized (this) {
                if (pedido.getEstado() == Pedido.Estado.CANCELADO) {
                    // Lo cancelaron mientras esperaba el permiso: se devuelve
                    semaforo.release();
                    continue;
                }
                pedidosEnFila--;
                for (int i = 0; i < totalEstaciones; i++) {
                    if (estaciones[i] == null) {
                        estaciones[i] = pedido;
                        numEstacion = i + 1;
                        break;
                    }
                }
                pedido.iniciarPreparacion(numEstacion);
            }
            Menu.Platillo platillo = pedido.platilloInterno();
            notificarEvento("PREPARANDO", "Estacion #" + numEstacion + " cocina " + platillo.nombre
                    + " (folio " + pedido.getFolio() + ") para [" + pedido.getComensal() + "]");
            notificarCambio();

            final int estacion = numEstacion;
            cocineros.submit(() -> cocinar(pedido, estacion));
        }
    }

    /** Trabajo del cocinero: ocupa la estacion el tiempo del platillo y la libera. */
    private void cocinar(Pedido pedido, int numEstacion) {
        Menu.Platillo platillo = pedido.platilloInterno();
        try {
            Thread.sleep(platillo.segundosPreparacion * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (this) {
            estaciones[numEstacion - 1] = null;
            pedido.marcarListo();
            totalAtendidos++;
            ventasTotales += platillo.precio;
        }
        // Se registra antes de devolver el permiso para que la bitacora respete la causalidad:
        // el siguiente PREPARANDO de esta estacion siempre aparece despues de este LISTO
        notificarEvento("LISTO", String.format(Locale.US,
                "Folio %d: %s listo para [%s] desde Estacion #%d. Cobro: $%.2f MXN",
                pedido.getFolio(), platillo.nombre, pedido.getComensal(), numEstacion, platillo.precio));
        semaforo.release();
        notificarCambio();
    }

    /**
     * PUT /api/pedidos/{folio}: cambia platillo y/o notas. Solo se permite mientras
     * el pedido sigue en la fila (aun no entra a una estacion).
     */
    public Pedido modificarPedido(int folio, Menu.Platillo nuevoPlatillo, String nuevasNotas) {
        Pedido pedido;
        synchronized (this) {
            pedido = pedidos.get(folio);
            if (pedido == null) {
                return null;
            }
            if (pedido.getEstado() != Pedido.Estado.EN_FILA) {
                throw new IllegalStateException("El pedido " + folio + " ya esta " + pedido.getEstado()
                        + "; solo se puede modificar mientras esta EN_FILA.");
            }
            if (nuevoPlatillo != null) {
                pedido.setPlatillo(nuevoPlatillo);
            }
            if (nuevasNotas != null) {
                pedido.setNotas(nuevasNotas);
            }
        }
        notificarEvento("MODIFICADO", "Folio " + folio + ": [" + pedido.getComensal() + "] ahora pide "
                + pedido.getPlatillo() + (pedido.getNotas() != null && !pedido.getNotas().isEmpty()
                ? " | Notas: " + pedido.getNotas() : ""));
        notificarCambio();
        return pedido;
    }

    /**
     * DELETE /api/pedidos/{folio}: cancela un pedido que sigue en la fila.
     * Lo saca de la fila; si el jefe de cocina ya lo habia tomado y esperaba el
     * permiso, al obtenerlo vera el estado CANCELADO y lo devolvera.
     */
    public Pedido cancelarPedido(int folio) {
        Pedido pedido;
        synchronized (this) {
            pedido = pedidos.get(folio);
            if (pedido == null) {
                return null;
            }
            if (pedido.getEstado() != Pedido.Estado.EN_FILA) {
                throw new IllegalStateException("El pedido " + folio + " ya esta " + pedido.getEstado()
                        + "; solo se puede cancelar mientras esta EN_FILA.");
            }
            pedido.marcarCancelado();
            fila.remove(pedido);
            pedidosEnFila--;
            totalCancelados++;
        }
        notificarEvento("CANCELADO", "Folio " + folio + ": [" + pedido.getComensal() + "] cancelo su "
                + pedido.getPlatillo() + " antes de entrar a una estacion.");
        notificarCambio();
        return pedido;
    }

    public Pedido buscarPedido(int folio) {
        return pedidos.get(folio);
    }

    public List<Pedido> listarPedidos(Pedido.Estado filtro) {
        List<Pedido> lista = new ArrayList<>();
        for (Pedido p : pedidos.values()) {
            if (filtro == null || p.getEstado() == filtro) {
                lista.add(p);
            }
        }
        return lista;
    }

    // Carga = pedidos en la fila + estaciones actualmente ocupadas
    public synchronized int getCargaActual() {
        return pedidosEnFila + (totalEstaciones - semaforo.availablePermits());
    }

    public synchronized int getEstacionesLibres() {
        return semaforo.availablePermits();
    }

    public int getTotalEstaciones() {
        return totalEstaciones;
    }

    public synchronized int getPedidosEnFila() {
        return pedidosEnFila;
    }

    public synchronized int getTotalAtendidos() {
        return totalAtendidos;
    }

    public synchronized int getTotalCancelados() {
        return totalCancelados;
    }

    // Pedidos recibidos desde que arranco el nodo (el ultimo folio emitido)
    public int getTotalRecibidos() {
        return folios.get();
    }

    public synchronized double getVentasTotales() {
        return ventasTotales;
    }

    public String getNombre() {
        return nombre;
    }

    public synchronized Pedido getPedidoEnEstacion(int index) {
        return estaciones[index];
    }
}

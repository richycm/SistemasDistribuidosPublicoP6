package com.mycompany.p6sistemasdistribuidos;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Interfaz Grafica para un Servidor de Cocina (Nodo Trabajador REST).
 * Muestra en tiempo real las estaciones de coccion, la cola de pedidos, las
 * metricas de la cocina y la consola de trafico HTTP (verbo, ruta y codigo).
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class CocinaGUI extends JFrame implements Cocina.CocinaListener {

    private final String nombreCocina;
    private final int puerto;
    private final Cocina cocina;
    private final FiltroBitacoraHttp bitacora;

    // Paleta de colores Dark Theme (P1 a P5)
    private static final Color BG_MAIN = new Color(18, 20, 29);
    private static final Color BG_CARD = new Color(28, 32, 47);
    private static final Color BG_CONSOLE = new Color(13, 15, 22);
    private static final Color ACC_BLUE = new Color(59, 130, 246);
    private static final Color ACC_GREEN = new Color(34, 197, 94);
    private static final Color ACC_RED = new Color(239, 68, 68);
    private static final Color ACC_AMBER = new Color(245, 158, 11);
    private static final Color ACC_PURPLE = new Color(139, 92, 246);
    private static final Color TXT_MAIN = new Color(243, 244, 246);
    private static final Color TXT_MUTED = new Color(156, 163, 175);

    // Componentes de interfaz
    private final JLabel lblEstado = crearEtiqueta("COCINA: DISPONIBLE", Font.BOLD, 13, ACC_GREEN, 0);
    private final JLabel lblEstacionesLibres = crearEtiqueta("Estaciones: 0 / 0", Font.BOLD, 13, ACC_BLUE, 4);
    private final JLabel lblFila = crearEtiqueta("En Fila: 0", Font.BOLD, 13, ACC_AMBER, 4);
    private final JLabel lblAtendidos = crearEtiqueta("Atendidos: 0", Font.BOLD, 13, ACC_GREEN, 4);
    private final JLabel lblVentas = crearEtiqueta("Ventas: $0.00", Font.BOLD, 13, ACC_GREEN, 4);
    private final JLabel lblPeticiones = crearEtiqueta("Peticiones HTTP: 0", Font.BOLD, 12, ACC_PURPLE, 0);
    private final JLabel lblCola = crearEtiqueta("Cola de espera: (vacia)", Font.PLAIN, 12, TXT_MUTED, 2);
    private final JPanel pnlEstaciones = new JPanel();
    private final JTextArea txtLogs = new JTextArea();

    public CocinaGUI(String nombreCocina, int puerto, Cocina cocina, FiltroBitacoraHttp bitacora) {
        super("Sistemas Distribuidos - Practica 6: " + nombreCocina + " REST (Puerto " + puerto + ") | Ricardo Carmona Martinez");
        this.nombreCocina = nombreCocina;
        this.puerto = puerto;
        this.cocina = cocina;
        this.bitacora = bitacora;
        configurarVentana();
    }

    private void configurarVentana() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 680);
        setMinimumSize(new Dimension(800, 540));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(BG_MAIN);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        // --- ENCABEZADO Y METRICAS ---
        JPanel header = crearTarjeta(new BorderLayout(15, 8));
        JPanel pnlSuperior = new JPanel(new BorderLayout(15, 0));
        pnlSuperior.setOpaque(false);

        JPanel pnlTitulos = new JPanel(new GridLayout(2, 1, 0, 3));
        pnlTitulos.setOpaque(false);
        pnlTitulos.add(crearEtiqueta(nombreCocina.toUpperCase() + " (SERVICIO WEB REST)", Font.BOLD, 16, TXT_MAIN, 2));
        pnlTitulos.add(crearEtiqueta("Practica 6: Servicios Web | JAX-RS + Grizzly | Semaforo FIFO | Ricardo Carmona Martinez", Font.PLAIN, 12, TXT_MUTED, 2));

        JPanel pnlMetricas = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        pnlMetricas.setOpaque(false);
        pnlMetricas.add(lblEstado);
        pnlMetricas.add(lblEstacionesLibres);
        pnlMetricas.add(lblFila);
        pnlMetricas.add(lblAtendidos);
        pnlMetricas.add(lblVentas);

        pnlSuperior.add(pnlTitulos, BorderLayout.WEST);
        pnlSuperior.add(pnlMetricas, BorderLayout.EAST);
        header.add(pnlSuperior, BorderLayout.NORTH);

        // Barra con datos de conexion de este nodo
        String ipLocal = obtenerIpLocal();
        JPanel pnlConexionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        pnlConexionBar.setOpaque(false);
        pnlConexionBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 52, 70)));

        pnlConexionBar.add(crearEtiqueta("URL BASE:", Font.BOLD, 11, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("http://127.0.0.1:" + puerto + "/api", Font.BOLD, 12, ACC_BLUE, 0));
        pnlConexionBar.add(crearEtiqueta("|", Font.PLAIN, 12, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("LAN: " + ipLocal, Font.BOLD, 12, ACC_GREEN, 0));
        pnlConexionBar.add(crearEtiqueta("|", Font.PLAIN, 12, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("Formato: JSON", Font.PLAIN, 11, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("|", Font.PLAIN, 12, TXT_MUTED, 0));
        pnlConexionBar.add(lblPeticiones);

        header.add(pnlConexionBar, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        // --- VISUALIZADOR DE ESTACIONES ---
        JPanel pnlCentro = new JPanel(new BorderLayout(0, 8));
        pnlCentro.setOpaque(false);

        JPanel barraTituloEstaciones = new JPanel(new BorderLayout());
        barraTituloEstaciones.setOpaque(false);
        barraTituloEstaciones.add(crearEtiqueta("ESTACIONES DE COCCION EN TIEMPO REAL (RECURSO ACOTADO CON SEMAFORO)", Font.BOLD, 12, TXT_MUTED, 2), BorderLayout.WEST);
        pnlCentro.add(barraTituloEstaciones, BorderLayout.NORTH);

        int cols = Math.min(cocina.getTotalEstaciones(), 3);
        int filas = (int) Math.ceil(cocina.getTotalEstaciones() / (double) cols);
        pnlEstaciones.setLayout(new GridLayout(filas, cols, 12, 12));
        pnlEstaciones.setOpaque(false);
        pnlCentro.add(pnlEstaciones, BorderLayout.CENTER);
        pnlCentro.add(lblCola, BorderLayout.SOUTH);

        // --- CONSOLA DE TRAFICO HTTP ---
        JPanel pnlLogsWrapper = crearTarjeta(new BorderLayout(8, 8));
        JPanel barraTituloLogs = new JPanel(new BorderLayout());
        barraTituloLogs.setOpaque(false);
        barraTituloLogs.add(crearEtiqueta("CONSOLA DE PETICIONES HTTP Y EVENTOS DE LA COCINA", Font.BOLD, 12, TXT_MUTED, 2), BorderLayout.WEST);

        JButton btnLimpiar = crearBoton("Limpiar Consola", new Color(55, 65, 81), e -> txtLogs.setText(""));
        btnLimpiar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        barraTituloLogs.add(btnLimpiar, BorderLayout.EAST);
        pnlLogsWrapper.add(barraTituloLogs, BorderLayout.NORTH);

        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(BG_CONSOLE);
        txtLogs.setForeground(new Color(229, 231, 235));
        txtLogs.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollLogs = new JScrollPane(txtLogs);
        scrollLogs.setBorder(BorderFactory.createLineBorder(new Color(40, 46, 62)));
        pnlLogsWrapper.add(scrollLogs, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pnlCentro, pnlLogsWrapper);
        split.setDividerLocation(280);
        split.setResizeWeight(0.45);
        split.setDividerSize(6);
        split.setOpaque(false);
        split.setBorder(null);

        root.add(split, BorderLayout.CENTER);
        renderizarEstaciones();

        // Las estaciones muestran segundos transcurridos y el contador de peticiones crece solo
        new Timer(1000, e -> {
            lblPeticiones.setText("Peticiones HTTP: " + bitacora.getTotalPeticiones());
            renderizarEstaciones();
        }).start();
    }

    public void renderizarEstaciones() {
        pnlEstaciones.removeAll();
        for (int i = 0; i < cocina.getTotalEstaciones(); i++) {
            Pedido pedido = cocina.getPedidoEnEstacion(i);
            boolean ocupada = pedido != null;

            JPanel estacion = crearTarjeta(new BorderLayout(8, 8));
            estacion.setBackground(ocupada ? new Color(38, 30, 48) : BG_CARD);
            estacion.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ocupada ? ACC_AMBER : new Color(45, 52, 70), ocupada ? 2 : 1),
                    new EmptyBorder(10, 12, 10, 12)
            ));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(crearEtiqueta("Estacion #" + (i + 1), Font.BOLD, 12, TXT_MUTED, 0), BorderLayout.WEST);
            JLabel lblBadge = crearEtiqueta(ocupada ? "COCINANDO" : "LIBRE", Font.BOLD, 11, ocupada ? ACC_AMBER : ACC_GREEN, 4);
            top.add(lblBadge, BorderLayout.EAST);
            estacion.add(top, BorderLayout.NORTH);

            JLabel lblCentro = crearEtiqueta(ocupada ? pedido.getPlatillo() : "[ DISPONIBLE ]", Font.BOLD, 14, ocupada ? TXT_MAIN : TXT_MUTED, 0);
            estacion.add(lblCentro, BorderLayout.CENTER);

            String detalle = ocupada
                    ? "Folio " + pedido.getFolio() + " | " + pedido.getComensal() + " | "
                      + pedido.getSegundosTranscurridos() + "s / " + pedido.getSegundosPreparacion() + "s"
                    : "Lista para el siguiente pedido";
            JLabel lblPie = crearEtiqueta(detalle, Font.ITALIC, 11, TXT_MUTED, 0);
            estacion.add(lblPie, BorderLayout.SOUTH);

            pnlEstaciones.add(estacion);
        }
        pnlEstaciones.revalidate();
        pnlEstaciones.repaint();
    }

    private void renderizarCola() {
        List<Pedido> enFila = cocina.listarPedidos(Pedido.Estado.EN_FILA);
        if (enFila.isEmpty()) {
            lblCola.setText("Cola de espera: (vacia)");
            return;
        }
        StringBuilder sb = new StringBuilder("Cola de espera (FIFO): ");
        for (int i = 0; i < enFila.size(); i++) {
            Pedido p = enFila.get(i);
            if (i > 0) {
                sb.append("  ->  ");
            }
            sb.append("#").append(p.getFolio()).append(" ").append(p.getComensal())
                    .append(" (").append(p.getPlatillo()).append(")");
        }
        lblCola.setText(sb.toString());
    }

    public void agregarLog(String linea) {
        SwingUtilities.invokeLater(() -> {
            txtLogs.append(linea + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    @Override
    public void onEstadoCambiado() {
        SwingUtilities.invokeLater(() -> {
            int libres = cocina.getEstacionesLibres();
            boolean hayLugar = libres > 0;

            lblEstado.setText(hayLugar ? "COCINA: DISPONIBLE" : "COCINA: SATURADA");
            lblEstado.setForeground(hayLugar ? ACC_GREEN : ACC_RED);

            lblEstacionesLibres.setText("Estaciones: " + libres + " / " + cocina.getTotalEstaciones());
            lblFila.setText("En Fila: " + cocina.getPedidosEnFila());
            lblAtendidos.setText("Atendidos: " + cocina.getTotalAtendidos());
            lblVentas.setText(String.format(Locale.US, "Ventas: $%.2f", cocina.getVentasTotales()));

            renderizarEstaciones();
            renderizarCola();
        });
    }

    @Override
    public void onEvento(String tag, String mensaje) {
        String tiempo = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        agregarLog(String.format("[%s] [%s] %s", tiempo, tag, mensaje));
    }

    // Metodos auxiliares de estilo
    private static JPanel crearTarjeta(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(BG_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 52, 70), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));
        return p;
    }

    private static JLabel crearEtiqueta(String texto, int estilo, int tamano, Color color, int alineacion) {
        JLabel l = new JLabel(texto, alineacion);
        l.setFont(new Font("Segoe UI", estilo, tamano));
        l.setForeground(color);
        return l;
    }

    private static JButton crearBoton(String texto, Color bg, java.awt.event.ActionListener accion) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8, 16, 8, 16));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(accion);
        return b;
    }

    private static String obtenerIpLocal() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}

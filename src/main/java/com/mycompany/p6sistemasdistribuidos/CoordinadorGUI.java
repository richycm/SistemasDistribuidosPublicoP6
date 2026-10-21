package com.mycompany.p6sistemasdistribuidos;

import com.mycompany.p6sistemasdistribuidos.CoordinadorDespacho.InfoCocina;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Locale;

/**
 * Interfaz Grafica para el Coordinador (Maestro) REST.
 * Muestra en tiempo real el mapa de cocinas registradas, su estado en linea
 * segun GET /api/carga, la carga de cada una y la consola de peticiones HTTP
 * y decisiones de balanceo.
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class CoordinadorGUI extends JFrame {

    private final List<InfoCocina> cocinas;

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
    private final JLabel lblCocinasOnline = crearEtiqueta("Cocinas en linea: 0", Font.BOLD, 13, ACC_GREEN, 4);
    private final JLabel lblDespachos = crearEtiqueta("Despachos: 0", Font.BOLD, 13, ACC_BLUE, 4);
    private final JLabel lblPeticiones = crearEtiqueta("Peticiones HTTP: 0", Font.BOLD, 13, ACC_PURPLE, 4);
    private final JPanel pnlCocinas = new JPanel(new GridLayout(1, 3, 12, 12));
    private final JTextArea txtLogs = new JTextArea();

    public CoordinadorGUI(List<InfoCocina> cocinas) {
        super("Sistemas Distribuidos - Practica 6: Coordinador de Despacho REST | Ricardo Carmona Martinez");
        this.cocinas = cocinas;
        configurarVentana();
    }

    private void configurarVentana() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1060, 700);
        setMinimumSize(new Dimension(880, 560));
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
        pnlTitulos.add(crearEtiqueta("COORDINADOR DE DESPACHO (SERVICIO WEB REST)", Font.BOLD, 16, TXT_MAIN, 2));
        pnlTitulos.add(crearEtiqueta("Practica 6: Servicios Web | Balanceo por menor carga via GET /api/carga | Ricardo Carmona Martinez", Font.PLAIN, 12, TXT_MUTED, 2));

        JPanel pnlMetricas = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        pnlMetricas.setOpaque(false);
        pnlMetricas.add(lblCocinasOnline);
        pnlMetricas.add(lblDespachos);
        pnlMetricas.add(lblPeticiones);

        pnlSuperior.add(pnlTitulos, BorderLayout.WEST);
        pnlSuperior.add(pnlMetricas, BorderLayout.EAST);
        header.add(pnlSuperior, BorderLayout.NORTH);

        // Barra con datos de conexion para los comensales
        String ipLocal = obtenerIpLocal();
        JPanel pnlConexionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        pnlConexionBar.setOpaque(false);
        pnlConexionBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 52, 70)));

        int p = CoordinadorDespacho.PUERTO_COORDINADOR;
        pnlConexionBar.add(crearEtiqueta("API:", Font.BOLD, 11, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("http://127.0.0.1:" + p + "/api", Font.BOLD, 12, ACC_BLUE, 0));
        pnlConexionBar.add(crearEtiqueta("|", Font.PLAIN, 12, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("Cliente web:", Font.BOLD, 11, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("http://127.0.0.1:" + p + "/web/", Font.BOLD, 12, ACC_GREEN, 0));
        pnlConexionBar.add(crearEtiqueta("|", Font.PLAIN, 12, TXT_MUTED, 0));
        pnlConexionBar.add(crearEtiqueta("LAN: " + ipLocal, Font.PLAIN, 11, TXT_MUTED, 0));

        header.add(pnlConexionBar, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        // --- VISUALIZADOR DE COCINAS REGISTRADAS ---
        JPanel pnlCentro = new JPanel(new BorderLayout(0, 8));
        pnlCentro.setOpaque(false);

        JPanel barraTituloCocinas = new JPanel(new BorderLayout());
        barraTituloCocinas.setOpaque(false);
        barraTituloCocinas.add(crearEtiqueta("MAPA DE COCINAS Y CARGA EN TIEMPO REAL (NODOS REST)", Font.BOLD, 12, TXT_MUTED, 2), BorderLayout.WEST);
        pnlCentro.add(barraTituloCocinas, BorderLayout.NORTH);

        pnlCocinas.setOpaque(false);
        pnlCentro.add(pnlCocinas, BorderLayout.CENTER);

        // --- CONSOLA DE PETICIONES HTTP Y BALANCEO ---
        JPanel pnlLogsWrapper = crearTarjeta(new BorderLayout(8, 8));
        JPanel barraTituloLogs = new JPanel(new BorderLayout());
        barraTituloLogs.setOpaque(false);
        barraTituloLogs.add(crearEtiqueta("CONSOLA DE PETICIONES HTTP Y DECISIONES DE BALANCEO", Font.BOLD, 12, TXT_MUTED, 2), BorderLayout.WEST);

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
        split.setResizeWeight(0.5);
        split.setDividerSize(6);
        split.setOpaque(false);
        split.setBorder(null);

        root.add(split, BorderLayout.CENTER);
        renderizarCocinas();

        new Timer(1000, e -> lblPeticiones.setText("Peticiones HTTP: " + CoordinadorDespacho.getTotalPeticiones())).start();
    }

    private void renderizarCocinas() {
        pnlCocinas.removeAll();
        for (InfoCocina c : cocinas) {
            boolean online = c.isOnline();

            JPanel tarjeta = crearTarjeta(new BorderLayout(8, 8));
            tarjeta.setBackground(online ? BG_CARD : new Color(38, 26, 30));
            tarjeta.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(online ? new Color(45, 52, 70) : ACC_RED, online ? 1 : 2),
                    new EmptyBorder(12, 14, 12, 14)
            ));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(crearEtiqueta(c.getNombre(), Font.BOLD, 14, TXT_MAIN, 0), BorderLayout.WEST);
            JLabel lblBadge = crearEtiqueta(online ? "ONLINE  200 OK" : "OFFLINE", Font.BOLD, 11, online ? ACC_GREEN : ACC_RED, 4);
            top.add(lblBadge, BorderLayout.EAST);
            tarjeta.add(top, BorderLayout.NORTH);

            JPanel centro = new JPanel(new GridLayout(5, 1, 0, 4));
            centro.setOpaque(false);
            centro.add(crearEtiqueta(c.getUrl(), Font.PLAIN, 12, TXT_MUTED, 0));
            if (online) {
                String enCamino = c.getEnCamino() > 0 ? "  +" + c.getEnCamino() + " en camino" : "";
                centro.add(crearEtiqueta("Carga: " + c.getCarga() + "  (en fila: " + c.getEnFila() + ")" + enCamino, Font.BOLD, 13, ACC_AMBER, 0));
                centro.add(crearEtiqueta("Estaciones libres: " + c.getEstacionesLibres() + " / " + c.getTotalEstaciones(), Font.PLAIN, 12, ACC_BLUE, 0));
                centro.add(crearEtiqueta("Pedidos atendidos: " + c.getAtendidos(), Font.PLAIN, 12, ACC_GREEN, 0));
                centro.add(crearEtiqueta(String.format(Locale.US, "Ventas: $%.2f MXN", c.getVentas()), Font.PLAIN, 12, ACC_GREEN, 0));
            } else {
                centro.add(crearEtiqueta("Carga: --", Font.BOLD, 13, TXT_MUTED, 0));
                centro.add(crearEtiqueta("Estaciones libres: --", Font.PLAIN, 12, TXT_MUTED, 0));
                centro.add(crearEtiqueta("GET /api/carga sin respuesta", Font.ITALIC, 12, ACC_RED, 0));
                centro.add(crearEtiqueta("Excluida del balanceo", Font.ITALIC, 12, ACC_RED, 0));
            }
            tarjeta.add(centro, BorderLayout.CENTER);

            pnlCocinas.add(tarjeta);
        }
        pnlCocinas.revalidate();
        pnlCocinas.repaint();
    }

    public void agregarLog(String linea) {
        SwingUtilities.invokeLater(() -> {
            txtLogs.append(linea + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    public void refrescar() {
        SwingUtilities.invokeLater(() -> {
            int online = 0;
            for (InfoCocina c : cocinas) {
                if (c.isOnline()) {
                    online++;
                }
            }
            lblCocinasOnline.setText("Cocinas en linea: " + online + " / " + cocinas.size());
            lblCocinasOnline.setForeground(online > 0 ? ACC_GREEN : ACC_RED);
            lblDespachos.setText("Despachos: " + CoordinadorDespacho.getTotalDespachos());
            renderizarCocinas();
        });
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

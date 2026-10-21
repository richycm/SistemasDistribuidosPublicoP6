package com.mycompany.p6sistemasdistribuidos;

import com.fasterxml.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interfaz Grafica para Comensales Concurrentes (cliente REST en Java).
 * Cada pedido corre en su propio hilo: pide cocina al Coordinador, envia el
 * pedido a esa cocina y consulta su estado cada segundo. Mientras el pedido
 * siga en la fila, su tarjeta permite modificarlo (PUT) o cancelarlo (DELETE).
 *
 * Alumno: Ricardo Carmona Martinez
 * Materia: Sistemas Distribuidos - Practica 6
 */
public class ClienteComensalGUI extends JFrame {

    private static final String URL_DEFAULT = "http://127.0.0.1:" + CoordinadorDespacho.PUERTO_COORDINADOR;
    private static final int PEDIDOS_POR_RAFAGA = 12;
    private static final int INTERVALO_CONSULTA_MS = 1000;
    private static final AtomicInteger contadorPedidosGlobal = new AtomicInteger(1);

    // Componentes de conexion y pedido
    private final JTextField txtUrl = new JTextField(URL_DEFAULT, 21);
    private final JTextField txtComensal = new JTextField("Ricardo", 9);
    private final JTextField txtNotas = new JTextField("", 12);
    private final JComboBox<String> cmbPlatillos = new JComboBox<>();
    private volatile List<Menu.Platillo> platillos = new ArrayList<>(Menu.obtenerPlatillos());

    // Lista concurrente de las sesiones visuales de pedidos
    private final List<SesionVisualPedido> pedidosActivos = new CopyOnWriteArrayList<>();

    // Componentes de la interfaz
    private final JPanel pnlContenedorPedidos = new JPanel(new GridBagLayout());
    private final JScrollPane scrollPedidos = new JScrollPane();
    private final JTextArea txtLogs = new JTextArea();
    private final JLabel lblContadorActivos = crearEtiqueta("Pedidos en curso: 0", Font.BOLD, 12, new Color(59, 130, 246), 4);

    // Paleta de colores Dark Theme (P1 a P5)
    private static final Color BG_MAIN = new Color(18, 20, 29);
    private static final Color BG_CARD = new Color(28, 32, 47);
    private static final Color BG_CONSOLE = new Color(13, 15, 22);
    private static final Color ACC_BLUE = new Color(59, 130, 246);
    private static final Color ACC_GREEN = new Color(34, 197, 94);
    private static final Color ACC_RED = new Color(239, 68, 68);
    private static final Color ACC_AMBER = new Color(245, 158, 11);
    private static final Color ACC_PURPLE = new Color(139, 92, 246);
    private static final Color ACC_TEAL = new Color(20, 184, 166);
    private static final Color TXT_MAIN = new Color(243, 244, 246);
    private static final Color TXT_MUTED = new Color(156, 163, 175);

    public ClienteComensalGUI() {
        super("Sistemas Distribuidos - Practica 6: Cliente REST de Comensales | Ricardo Carmona Martinez");
        configurarVentana();
    }

    private void configurarVentana() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(980, 760);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(BG_MAIN);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        // --- ENCABEZADO Y CONFIGURACION DE CONEXION ---
        JPanel pnlHeader = crearTarjeta(new BorderLayout(10, 8));
        JPanel pnlBarraSuperior = new JPanel(new BorderLayout());
        pnlBarraSuperior.setOpaque(false);

        JPanel pnlTitulosHeader = new JPanel(new GridLayout(2, 1, 0, 2));
        pnlTitulosHeader.setOpaque(false);
        pnlTitulosHeader.add(crearEtiqueta("CLIENTE REST DE COMENSALES CONCURRENTES", Font.BOLD, 15, TXT_MAIN, 2));
        pnlTitulosHeader.add(crearEtiqueta("Practica 6: Servicios Web | java.net.http.HttpClient + JSON | Alumno: Ricardo Carmona Martinez", Font.PLAIN, 12, TXT_MUTED, 2));

        pnlBarraSuperior.add(pnlTitulosHeader, BorderLayout.WEST);
        pnlBarraSuperior.add(lblContadorActivos, BorderLayout.EAST);
        pnlHeader.add(pnlBarraSuperior, BorderLayout.NORTH);

        JPanel pnlParametros = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        pnlParametros.setOpaque(false);

        pnlParametros.add(crearEtiqueta("Coordinador:", Font.BOLD, 12, TXT_MUTED, 0));
        estilizarCampoTexto(txtUrl);
        txtUrl.setCaretPosition(0);
        pnlParametros.add(txtUrl);

        pnlParametros.add(crearEtiqueta("Comensal:", Font.BOLD, 12, TXT_MUTED, 0));
        estilizarCampoTexto(txtComensal);
        pnlParametros.add(txtComensal);

        JButton btnMenu = crearBoton("GET Menu", new Color(55, 65, 81), e -> cargarMenu());
        JButton btnCocinas = crearBoton("GET Cocinas", new Color(55, 65, 81), e -> consultarCocinas());
        pnlParametros.add(btnMenu);
        pnlParametros.add(btnCocinas);

        pnlHeader.add(pnlParametros, BorderLayout.CENTER);
        root.add(pnlHeader, BorderLayout.NORTH);

        // --- PANEL DE SELECCION Y BOTONES ---
        JPanel pnlAcciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pnlAcciones.setOpaque(false);

        pnlAcciones.add(crearEtiqueta("Platillo:", Font.BOLD, 12, TXT_MUTED, 0));
        cmbPlatillos.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cmbPlatillos.setBackground(BG_CONSOLE);
        cmbPlatillos.setForeground(TXT_MAIN);
        llenarComboPlatillos();
        pnlAcciones.add(cmbPlatillos);

        pnlAcciones.add(crearEtiqueta("Notas:", Font.BOLD, 12, TXT_MUTED, 0));
        estilizarCampoTexto(txtNotas);
        pnlAcciones.add(txtNotas);

        JButton btnPedir = crearBoton(" Enviar Pedido ", ACC_BLUE, e -> lanzarPedido());
        JButton btnRafaga = crearBoton(" Rafaga (" + PEDIDOS_POR_RAFAGA + " Pedidos) ", ACC_PURPLE, e -> lanzarRafaga());
        pnlAcciones.add(btnPedir);
        pnlAcciones.add(btnRafaga);

        // --- SECCION CENTRAL CON JSplitPane ---
        JPanel pnlPedidosWrapper = new JPanel(new BorderLayout(0, 6));
        pnlPedidosWrapper.setOpaque(false);
        pnlPedidosWrapper.add(crearEtiqueta("PEDIDOS (RECURSOS /api/pedidos/{folio} CONSULTADOS CADA SEGUNDO)", Font.BOLD, 11, TXT_MUTED, 2), BorderLayout.NORTH);

        pnlContenedorPedidos.setBackground(BG_CARD);
        scrollPedidos.setViewportView(pnlContenedorPedidos);
        scrollPedidos.setBorder(BorderFactory.createLineBorder(new Color(45, 52, 70)));
        scrollPedidos.getVerticalScrollBar().setUnitIncrement(12);
        pnlPedidosWrapper.add(scrollPedidos, BorderLayout.CENTER);

        // Consola de trafico HTTP
        JPanel pnlLogsWrapper = crearTarjeta(new BorderLayout(6, 6));
        JPanel topLogs = new JPanel(new BorderLayout());
        topLogs.setOpaque(false);
        topLogs.add(crearEtiqueta("CONSOLA DE TRAFICO HTTP (PETICIONES -> Y RESPUESTAS <-)", Font.BOLD, 11, TXT_MUTED, 2), BorderLayout.WEST);

        JButton btnLimpiar = crearBoton("Limpiar Logs", new Color(55, 65, 81), e -> txtLogs.setText(""));
        btnLimpiar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        topLogs.add(btnLimpiar, BorderLayout.EAST);
        pnlLogsWrapper.add(topLogs, BorderLayout.NORTH);

        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(BG_CONSOLE);
        txtLogs.setForeground(new Color(229, 231, 235));
        txtLogs.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane scrollLogs = new JScrollPane(txtLogs);
        scrollLogs.setBorder(BorderFactory.createLineBorder(new Color(40, 46, 62)));
        pnlLogsWrapper.add(scrollLogs, BorderLayout.CENTER);

        JSplitPane splitPrincipal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pnlPedidosWrapper, pnlLogsWrapper);
        splitPrincipal.setDividerLocation(330);
        splitPrincipal.setResizeWeight(0.55);
        splitPrincipal.setDividerSize(6);
        splitPrincipal.setOpaque(false);
        splitPrincipal.setBorder(null);

        JPanel pnlCentro = new JPanel(new BorderLayout(0, 10));
        pnlCentro.setOpaque(false);
        pnlCentro.add(pnlAcciones, BorderLayout.NORTH);
        pnlCentro.add(splitPrincipal, BorderLayout.CENTER);
        root.add(pnlCentro, BorderLayout.CENTER);

        actualizarListaVisualPedidos();
        log("INFO", "Cliente REST iniciado. Elige un platillo y presiona 'Enviar Pedido'.");
        cargarMenu();
    }

    private ClienteComensal nuevoCliente(String etiqueta) {
        return new ClienteComensal(txtUrl.getText(), etiqueta, linea -> log("HTTP", linea));
    }

    private String nombreComensal() {
        String nombre = txtComensal.getText().trim().replace(' ', '_');
        return nombre.isEmpty() ? "Comensal" : nombre;
    }

    /** GET /api/menu: la carta ya no esta "quemada" en el cliente, se pide al servicio. */
    private void cargarMenu() {
        ClienteComensal cliente = nuevoCliente(nombreComensal());
        new Thread(() -> {
            try {
                List<Menu.Platillo> remoto = cliente.obtenerMenu();
                platillos = remoto;
                SwingUtilities.invokeLater(this::llenarComboPlatillos);
                log("MENU", "Carta recibida del servicio: " + remoto.size() + " platillos.");
            } catch (IOException e) {
                log("AVISO", "No se pudo obtener el menu (" + e.getMessage() + "). Se usa la carta local.");
            }
        }, "Hilo-Menu").start();
    }

    private void llenarComboPlatillos() {
        int seleccion = Math.max(0, cmbPlatillos.getSelectedIndex());
        cmbPlatillos.removeAllItems();
        for (Menu.Platillo p : platillos) {
            cmbPlatillos.addItem(String.format(Locale.US, "%d. %s  -  $%.2f", p.id, p.nombre, p.precio));
        }
        if (seleccion < cmbPlatillos.getItemCount()) {
            cmbPlatillos.setSelectedIndex(seleccion);
        }
    }

    /** GET /api/cocinas: estado de los nodos segun el coordinador. */
    private void consultarCocinas() {
        ClienteComensal cliente = nuevoCliente(nombreComensal());
        new Thread(() -> {
            try {
                JsonNode cocinas = cliente.consultarCocinas();
                for (JsonNode c : cocinas) {
                    log("COCINAS", String.format("%-13s %-8s carga=%d  libres=%d/%d  atendidos=%d",
                            c.path("nombre").asText(), c.path("online").asBoolean() ? "ONLINE" : "OFFLINE",
                            c.path("carga").asInt(), c.path("estacionesLibres").asInt(),
                            c.path("totalEstaciones").asInt(), c.path("atendidos").asInt()));
                }
            } catch (IOException e) {
                log("ERROR", "GET /api/cocinas fallo: " + e.getMessage());
            }
        }, "Hilo-Cocinas").start();
    }

    private void lanzarRafaga() {
        for (int i = 0; i < PEDIDOS_POR_RAFAGA; i++) {
            lanzarPedido(platillos.get(i % platillos.size()), "");
        }
    }

    private void lanzarPedido() {
        int idx = Math.max(0, cmbPlatillos.getSelectedIndex());
        lanzarPedido(platillos.get(idx), txtNotas.getText().trim());
    }

    private void lanzarPedido(Menu.Platillo platillo, String notas) {
        String etiqueta = String.format("%s#%03d", nombreComensal(), contadorPedidosGlobal.getAndIncrement());
        SesionVisualPedido sesion = new SesionVisualPedido(nuevoCliente(etiqueta), platillo, notas);
        pedidosActivos.add(sesion);
        actualizarListaVisualPedidos();
        new Thread(sesion::ejecutarCiclo, "Hilo-Pedido-" + etiqueta).start();
    }

    private void actualizarListaVisualPedidos() {
        SwingUtilities.invokeLater(() -> {
            pnlContenedorPedidos.removeAll();

            int activos = 0;
            for (SesionVisualPedido s : pedidosActivos) {
                if (!s.terminado) {
                    activos++;
                }
            }
            lblContadorActivos.setText("Pedidos en curso: " + activos);

            if (pedidosActivos.isEmpty()) {
                JPanel vacio = new JPanel(new GridBagLayout());
                vacio.setOpaque(false);
                vacio.setBorder(new EmptyBorder(30, 10, 30, 10));
                vacio.add(crearEtiqueta("No hay pedidos. Elige un platillo y haz clic en 'Enviar Pedido'.",
                        Font.ITALIC, 13, TXT_MUTED, 0));

                GridBagConstraints gbcEmpty = new GridBagConstraints();
                gbcEmpty.weightx = 1.0;
                gbcEmpty.weighty = 1.0;
                gbcEmpty.fill = GridBagConstraints.BOTH;
                pnlContenedorPedidos.add(vacio, gbcEmpty);
            } else {
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = new Insets(0, 0, 6, 0);

                for (SesionVisualPedido s : pedidosActivos) {
                    pnlContenedorPedidos.add(s.tarjetaUI, gbc);
                    gbc.gridy++;
                }

                gbc.weighty = 1.0;
                pnlContenedorPedidos.add(Box.createGlue(), gbc);
            }

            pnlContenedorPedidos.revalidate();
            pnlContenedorPedidos.repaint();
        });
    }

    public void log(String tag, String mensaje) {
        String tiempo = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String linea = String.format("[%s] [%s] %s", tiempo, tag, mensaje);
        SwingUtilities.invokeLater(() -> {
            txtLogs.append(linea + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    /**
     * Representacion grafica de cada pedido. Delega las peticiones HTTP a
     * ClienteComensal y refleja el estado del recurso remoto.
     */
    class SesionVisualPedido {
        final ClienteComensal cliente;
        volatile Menu.Platillo platillo;
        volatile String notas;

        volatile boolean terminado = false;
        volatile ClienteComensal.Asignacion asignacion;
        volatile int folio = -1;
        volatile String ultimoEstado = "";

        final JPanel tarjetaUI = new JPanel(new BorderLayout(10, 4));
        final JLabel lblComensal = crearEtiqueta("", Font.BOLD, 13, TXT_MAIN, 2);
        final JLabel lblEstado = crearEtiqueta("Conectando...", Font.BOLD, 12, ACC_AMBER, 0);
        final JLabel lblDetalle = crearEtiqueta("Consultando al coordinador...", Font.PLAIN, 12, TXT_MUTED, 0);
        final JButton btnModificar = crearBoton("Modificar (PUT)", ACC_TEAL, e -> modificar());
        final JButton btnCancelar = crearBoton("Cancelar (DELETE)", ACC_RED, e -> cancelar());

        SesionVisualPedido(ClienteComensal cliente, Menu.Platillo platillo, String notas) {
            this.cliente = cliente;
            this.platillo = platillo;
            this.notas = notas;
            construirTarjetaUI();
        }

        private void construirTarjetaUI() {
            tarjetaUI.setBackground(new Color(24, 27, 39));
            tarjetaUI.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(45, 52, 70), 1),
                    new EmptyBorder(8, 12, 8, 12)
            ));

            lblComensal.setText(cliente.getComensal() + "  |  " + platillo.nombre);
            lblComensal.setPreferredSize(new Dimension(300, 20));
            tarjetaUI.add(lblComensal, BorderLayout.WEST);

            JPanel centro = new JPanel(new GridLayout(2, 1, 0, 2));
            centro.setOpaque(false);
            centro.add(lblEstado);
            centro.add(lblDetalle);
            tarjetaUI.add(centro, BorderLayout.CENTER);

            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            botones.setOpaque(false);
            btnModificar.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btnCancelar.setFont(new Font("Segoe UI", Font.BOLD, 11));
            botones.add(btnModificar);
            botones.add(btnCancelar);
            tarjetaUI.add(botones, BorderLayout.EAST);
            habilitarAcciones(false);
        }

        private void habilitarAcciones(boolean enFila) {
            btnModificar.setVisible(enFila);
            btnCancelar.setVisible(enFila);
        }

        void ejecutarCiclo() {
            try {
                // 1. POST /api/despachos: el coordinador decide la cocina
                asignacion = cliente.solicitarCocina();
                log("DESPACHO", "[" + cliente.getComensal() + "] asignado a " + asignacion.nombreCocina
                        + " (" + asignacion.url + ")");

                // 2. POST /api/pedidos en la cocina asignada -> 202 Accepted
                ClienteComensal.PedidoRemoto pedido = cliente.enviarPedido(asignacion, platillo.id, notas);
                folio = pedido.folio;
                mostrar(pedido);

                // 3. GET /api/pedidos/{folio} hasta que este LISTO o CANCELADO
                while (!terminado) {
                    Thread.sleep(INTERVALO_CONSULTA_MS);
                    if (terminado) {
                        break;
                    }
                    mostrar(cliente.consultarPedido(asignacion, folio));
                }
            } catch (IOException ex) {
                finalizarConError(ex.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        private void mostrar(ClienteComensal.PedidoRemoto p) {
            boolean cambioEstado = !p.estado.equals(ultimoEstado);
            if (cambioEstado && !ultimoEstado.isEmpty() && !"LISTO".equals(p.estado)) {
                log("ESTADO", "[" + cliente.getComensal() + "] folio " + p.folio + " en " + asignacion.nombreCocina
                        + ": " + ultimoEstado + " -> " + p.estado);
            }
            ultimoEstado = p.estado;
            if ("LISTO".equals(p.estado) || "CANCELADO".equals(p.estado)) {
                terminado = true;
            }
            if (cambioEstado && "LISTO".equals(p.estado)) {
                log("LISTO", String.format(Locale.US, "[%s] Folio %d en %s (Estacion #%d) tras %ds. Total: $%.2f MXN",
                        cliente.getComensal(), p.folio, asignacion.nombreCocina, p.estacion,
                        p.segundosPreparacion, p.precio));
            }

            SwingUtilities.invokeLater(() -> {
                lblComensal.setText(cliente.getComensal() + "  |  " + p.platillo);
                String extraNotas = p.notas.isEmpty() ? "" : " | Notas: " + p.notas;
                switch (p.estado) {
                    case "EN_FILA":
                        lblEstado.setText("FOLIO " + p.folio + " EN FILA - " + asignacion.nombreCocina.toUpperCase());
                        lblEstado.setForeground(ACC_AMBER);
                        lblDetalle.setText("Esperando estacion libre (202 Accepted)" + extraNotas);
                        break;
                    case "PREPARANDO":
                        lblEstado.setText("FOLIO " + p.folio + " PREPARANDO - " + asignacion.nombreCocina.toUpperCase());
                        lblEstado.setForeground(ACC_BLUE);
                        lblDetalle.setText("Estacion #" + p.estacion + " | " + p.segundosTranscurridos + "s / "
                                + p.segundosPreparacion + "s" + extraNotas);
                        break;
                    case "LISTO":
                        lblEstado.setText("PEDIDO LISTO - FOLIO " + p.folio);
                        lblEstado.setForeground(ACC_GREEN);
                        lblDetalle.setText(String.format(Locale.US, "%s | Estacion #%d | %ds | Total: $%.2f MXN%s",
                                asignacion.nombreCocina, p.estacion, p.segundosPreparacion, p.precio, extraNotas));
                        break;
                    case "CANCELADO":
                        lblEstado.setText("PEDIDO CANCELADO - FOLIO " + p.folio);
                        lblEstado.setForeground(ACC_RED);
                        lblDetalle.setText("Cancelado con DELETE antes de entrar a una estacion");
                        break;
                    default:
                        lblEstado.setText(p.estado);
                }
                habilitarAcciones("EN_FILA".equals(p.estado));
                if (terminado) {
                    actualizarListaVisualPedidos();
                }
            });
        }

        /** PUT /api/pedidos/{folio}: cambiar platillo y/o notas mientras sigue en fila. */
        private void modificar() {
            if (folio < 0) {
                return;
            }
            JComboBox<String> cmb = new JComboBox<>();
            for (Menu.Platillo p : platillos) {
                cmb.addItem(p.id + ". " + p.nombre);
            }
            for (int i = 0; i < platillos.size(); i++) {
                if (platillos.get(i).id == platillo.id) {
                    cmb.setSelectedIndex(i);
                }
            }
            JTextField txt = new JTextField(notas, 18);
            JPanel form = new JPanel(new GridLayout(4, 1, 0, 4));
            form.add(new JLabel("Nuevo platillo:"));
            form.add(cmb);
            form.add(new JLabel("Notas:"));
            form.add(txt);
            int r = JOptionPane.showConfirmDialog(ClienteComensalGUI.this, form,
                    "Modificar folio " + folio + " (PUT)", JOptionPane.OK_CANCEL_OPTION);
            if (r != JOptionPane.OK_OPTION) {
                return;
            }
            Menu.Platillo nuevo = platillos.get(cmb.getSelectedIndex());
            enviarModificacion(nuevo, txt.getText().trim());
        }

        void enviarModificacion(Menu.Platillo nuevo, String nuevasNotas) {
            new Thread(() -> {
                try {
                    mostrar(cliente.modificarPedido(asignacion, folio, nuevo.id, nuevasNotas));
                    platillo = nuevo;
                    notas = nuevasNotas;
                    log("MODIFICADO", "[" + cliente.getComensal() + "] folio " + folio + " ahora es " + nuevo.nombre);
                } catch (IOException e) {
                    log("RECHAZADO", "[" + cliente.getComensal() + "] " + e.getMessage());
                }
            }, "Hilo-PUT-" + folio).start();
        }

        /** DELETE /api/pedidos/{folio}. */
        void cancelar() {
            if (folio < 0) {
                return;
            }
            new Thread(() -> {
                try {
                    mostrar(cliente.cancelarPedido(asignacion, folio));
                    log("CANCELADO", "[" + cliente.getComensal() + "] folio " + folio + " cancelado.");
                } catch (IOException e) {
                    log("RECHAZADO", "[" + cliente.getComensal() + "] " + e.getMessage());
                }
            }, "Hilo-DELETE-" + folio).start();
        }

        private void finalizarConError(String msg) {
            log("ERROR", "[" + cliente.getComensal() + "]: " + msg);
            terminado = true;
            SwingUtilities.invokeLater(() -> {
                lblEstado.setText("ERROR");
                lblEstado.setForeground(ACC_RED);
                lblDetalle.setText(msg);
                habilitarAcciones(false);
                actualizarListaVisualPedidos();
            });
        }
    }

    // Metodos auxiliares de estilo
    private static JPanel crearTarjeta(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(BG_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 52, 70), 1),
                new EmptyBorder(10, 12, 10, 12)
        ));
        return p;
    }

    private static JLabel crearEtiqueta(String texto, int estilo, int tamano, Color color, int alineacion) {
        JLabel l = new JLabel(texto, alineacion);
        l.setFont(new Font("Segoe UI", estilo, tamano));
        l.setForeground(color);
        return l;
    }

    private static void estilizarCampoTexto(JTextField tf) {
        tf.setFont(new Font("Consolas", Font.BOLD, 12));
        tf.setBackground(BG_CONSOLE);
        tf.setForeground(TXT_MAIN);
        tf.setCaretColor(ACC_BLUE);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 52, 70), 1),
                new EmptyBorder(4, 6, 4, 6)
        ));
    }

    private static JButton crearBoton(String texto, Color bg, java.awt.event.ActionListener accion) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(7, 14, 7, 14));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (accion != null) {
            b.addActionListener(accion);
        }
        return b;
    }

    public static void main(String[] args) {
        // Argumento opcional: numero de pedidos a disparar automaticamente (modo demo)
        int autoPedidos = 0;
        if (args.length > 0) {
            try {
                autoPedidos = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                autoPedidos = 0;
            }
        }
        final int totalAuto = autoPedidos;

        SwingUtilities.invokeLater(() -> {
            ClienteComensalGUI gui = new ClienteComensalGUI();
            gui.setVisible(true);

            if (totalAuto > 0) {
                new Thread(() -> {
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (int i = 0; i < totalAuto; i++) {
                        final int seleccion = i;
                        SwingUtilities.invokeLater(() ->
                                gui.lanzarPedido(gui.platillos.get(seleccion % gui.platillos.size()), ""));
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }, "Demo-AutoPedidos").start();
            }
        });
    }
}

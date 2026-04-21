package com.iedexplorer;

import com.beanit.iec61850bean.*;
import com.iedexplorer.native_lib.*;
import org.pcap4j.core.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.geom.*;
import javax.swing.border.*;

/**
 * IEC 61850 Explorer - Similar a OMICRON IEDScout
 * Modos: SERVER (simular IED) y CLIENT (conectar a IED real)
 * Caracteristicas: Activity Monitor, Drag-and-Drop, SCL Loading
 */
public class IEDExplorerApp extends JFrame {

    // Modo actual
    private enum AppMode { SERVER, CLIENT }
    private AppMode currentMode = AppMode.CLIENT;

    // Cliente y servidor
    private IEC61850Client client;
    private IEC61850Server server;

    // Estado
    private boolean isConnected = false;
    private boolean isServerRunning = false;
    private ScheduledExecutorService pollExecutor;
    private ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // Archivo SCL cargado (para extraer GoCBs)
    private File loadedSclFile = null;
    private String loadedIedName = null;
    private String[] loadedIedNameplate = null; // [manufacturer, type, desc, configVersion]

    // DataTypeTemplates parsed from SCL for BdaEnum dropdown support
    private Map<String, LinkedHashMap<Integer, String>> sclEnumTypes = new HashMap<>();
    private Map<String, String> sclDaEnumType = new HashMap<>();    // "doTypeId.daName" → enumTypeId
    private Map<String, Map<String, String>> sclLnTypeDoTypes = new HashMap<>(); // lnTypeId → {doName → doTypeId}
    private Map<String, String> sclLnClassToLnType = new HashMap<>();  // lnClass → lnTypeId
    private List<SclGoCB> sclGoCBs = new ArrayList<>();

    // CID descargado del IED
    private byte[] downloadedCidData = null;
    private String downloadedCidFilename = null;

    // Clase para almacenar info de GoCB desde SCL
    private static class SclGoCB {
        String ldInst;       // Logical Device
        String lnClass;      // LN class (usualmente LLN0)
        String cbName;       // Control block name
        String appID;        // Application ID
        String datSet;       // DataSet name
        int confRev;         // Configuration revision
        String macAddress;   // Destination MAC
        String goID;         // GOOSE ID

        @Override
        public String toString() {
            return ldInst + "/" + lnClass + "." + cbName;
        }
    }

    // Componentes GUI principales
    private JRadioButton rbServer, rbClient;
    private JPanel cardPanel;
    private CardLayout cardLayout;

    // Panel Server
    private JButton btnSelectFile;
    private JLabel lblFileName;
    private JTextField tfServerPort;
    private JButton btnStartStop;

    // Panel Client
    private JTextField tfHost;
    private JTextField tfClientPort;
    private JButton btnConnect;
    private JCheckBox cbPolling;
    private JSpinner spinnerInterval;

    // Comunes
    private JLabel lblStatus;
    private JLabel lblIedInfo;   // placa de identificación del IED (FC=DC)
    private JPanel statusIndicator;
    private JTree modelTree;
    private DefaultMutableTreeNode rootNode;
    private DefaultTreeModel treeModel;
    private Map<String, DefaultMutableTreeNode> nodeMap = new HashMap<>();
    private JTextArea logArea;

    // Watchlist - nodos seleccionados para monitorear
    private Set<String> watchlist = new HashSet<>();
    private JPopupMenu treePopupMenu;
    private JLabel lblWatchlistCount;

    // Activity Monitor Panel
    private JTable monitorTable;
    private DefaultTableModel monitorTableModel;
    private Map<String, MonitorItem> monitorItems = new LinkedHashMap<>();

    // Reports Panel
    private JTable reportsTable;
    private DefaultTableModel reportsTableModel;
    private JTable reportDataTable;
    private DefaultTableModel reportDataTableModel;
    private Map<String, Rcb> rcbMap = new HashMap<>();

    // GOOSE Panel
    private JTable gooseTable;
    private DefaultTableModel gooseTableModel;
    private JTable gooseDataTable;
    private DefaultTableModel gooseDataTableModel;
    private JTextArea gooseLogArea;
    private GooseSubscriber gooseSubscriber;
    private GoosePublisher goosePublisher;
    private Map<Integer, GoosePublisher> activePublishers = new LinkedHashMap<>();
    private GooseUdpBridge gooseUdpBridge;
    private JComboBox<String> gooseInterfaceCombo;
    private JButton btnGooseStartStop;
    private JButton btnGoosePublish;
    private JButton btnGooseStateChange;
    private JButton btnPublicarTodos;
    private JLabel lblGooseStatus;
    private JLabel lblPublishStatus;
    private Map<String, PcapNetworkInterface> interfaceMap = new HashMap<>();
    private Map<Integer, GooseSubscriber.GooseMessage> gooseMessages = new LinkedHashMap<>();
    private JComboBox<String> cbGooseState;  // For DbPos state selection

    // Sampled Values Panel (using libiec61850)
    private JTable svTable;
    private DefaultTableModel svTableModel;
    private JTextArea svLogArea;
    private NativeSVSubscriber svSubscriber;
    private JComboBox<String> svInterfaceCombo;
    private JTextField svAppIdField;
    private JButton btnSvStartStop;
    private JLabel lblSvStatus;
    private boolean nativeLibAvailable = false;

    // Native GOOSE Subscriber (optional, for enhanced GOOSE support)
    private NativeGooseSubscriber nativeGooseSubscriber;

    // Setting Groups Panel
    private JTable settingGroupsTable;
    private DefaultTableModel settingGroupsTableModel;
    private JTable sgValuesTable;
    private DefaultTableModel sgValuesTableModel;

    // Dataset Panel
    private JTable datasetTable;
    private DefaultTableModel datasetTableModel;
    private JTable datasetMembersTable;
    private DefaultTableModel datasetMembersTableModel;

    // Data Model Panel
    private JTree dataModelTree;
    private DefaultMutableTreeNode dataModelRootNode;
    private DefaultTreeModel dataModelTreeModel;
    private JTable dataModelAttrTable;
    private DefaultTableModel dataModelAttrTableModel;

    // Clase para items del monitor
    private static class MonitorItem {
        String reference;
        String name;
        String fc;
        String value;
        String oldValue;  // Para detectar cambios
        String type;
        FcModelNode node;
        long lastChangeTime;

        MonitorItem(String reference, String name, String fc, String type, FcModelNode node) {
            this.reference = reference;
            this.name = name;
            this.fc = fc;
            this.type = type;
            this.node = node;
            this.value = "";
            this.oldValue = "";
            this.lastChangeTime = 0;
        }
    }

    // ===== ICONOS PERSONALIZADOS =====
    private Map<String, Icon> iconCache = new HashMap<>();

    // Crear icono circular de color
    private Icon createCircleIcon(Color color, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x + 1, y + 1, size - 2, size - 2);
                g2.setColor(color.darker());
                g2.drawOval(x + 1, y + 1, size - 2, size - 2);
                g2.dispose();
            }
            @Override
            public int getIconWidth() { return size; }
            @Override
            public int getIconHeight() { return size; }
        };
    }

    // Crear icono de medidor (para MMXU, MMTR)
    private Icon createMeterIcon(Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Fondo del medidor
                g2.setColor(new Color(240, 240, 240));
                g2.fillRoundRect(x + 1, y + 1, 14, 14, 3, 3);
                g2.setColor(color);
                g2.drawRoundRect(x + 1, y + 1, 14, 14, 3, 3);
                // Escala
                g2.setColor(color);
                g2.drawArc(x + 3, y + 4, 10, 10, 0, 180);
                // Aguja
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(x + 8, y + 12, x + 12, y + 6);
                g2.dispose();
            }
            @Override
            public int getIconWidth() { return 16; }
            @Override
            public int getIconHeight() { return 16; }
        };
    }

    // Crear icono de interruptor (breaker) - Estilo profesional como IEDScout
    private Icon createBreakerIcon(String state) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color color;
                Color bgColor;
                boolean closed;
                String label;

                if (state.equalsIgnoreCase("on") || state.equals("2")) {
                    color = COLOR_BREAKER_ON;
                    bgColor = new Color(200, 255, 200);
                    closed = true;
                    label = "I";  // IEC symbol for ON
                } else if (state.equalsIgnoreCase("off") || state.equals("1")) {
                    color = COLOR_BREAKER_OFF;
                    bgColor = new Color(255, 200, 200);
                    closed = false;
                    label = "O";  // IEC symbol for OFF
                } else {
                    color = COLOR_BREAKER_INTERMEDIATE;
                    bgColor = new Color(255, 240, 200);
                    closed = false;
                    label = "?";
                }

                // Fondo redondeado
                g2.setColor(bgColor);
                g2.fillRoundRect(x, y, 16, 16, 4, 4);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x, y, 15, 15, 4, 4);

                // Simbolo de switch
                g2.setStroke(new BasicStroke(2f));
                // Linea base izquierda
                g2.drawLine(x + 2, y + 10, x + 5, y + 10);
                // Linea base derecha
                g2.drawLine(x + 11, y + 10, x + 14, y + 10);
                // Contacto movil
                if (closed) {
                    g2.drawLine(x + 5, y + 10, x + 11, y + 10);  // Cerrado (horizontal)
                } else {
                    g2.drawLine(x + 5, y + 10, x + 10, y + 5);   // Abierto (diagonal)
                }
                // Punto de contacto
                g2.fillOval(x + 4, y + 8, 4, 4);

                g2.dispose();
            }
            @Override
            public int getIconWidth() { return 16; }
            @Override
            public int getIconHeight() { return 16; }
        };
    }

    // Icono grande para Activity Monitor (24x24)
    private Icon createLargeBreakerIcon(String state) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color color, bgColor;
                boolean closed;

                if (state.equalsIgnoreCase("on") || state.equals("2")) {
                    color = COLOR_BREAKER_ON;
                    bgColor = new Color(200, 255, 200);
                    closed = true;
                } else if (state.equalsIgnoreCase("off") || state.equals("1")) {
                    color = COLOR_BREAKER_OFF;
                    bgColor = new Color(255, 200, 200);
                    closed = false;
                } else {
                    color = COLOR_BREAKER_INTERMEDIATE;
                    bgColor = new Color(255, 240, 200);
                    closed = false;
                }

                // Fondo
                g2.setColor(bgColor);
                g2.fillRoundRect(x, y, 22, 22, 6, 6);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(x, y, 21, 21, 6, 6);

                // Switch grande
                g2.setStroke(new BasicStroke(3f));
                g2.drawLine(x + 3, y + 14, x + 7, y + 14);
                g2.drawLine(x + 15, y + 14, x + 19, y + 14);
                if (closed) {
                    g2.drawLine(x + 7, y + 14, x + 15, y + 14);
                } else {
                    g2.drawLine(x + 7, y + 14, x + 14, y + 7);
                }
                g2.fillOval(x + 5, y + 12, 5, 5);

                g2.dispose();
            }
            @Override
            public int getIconWidth() { return 22; }
            @Override
            public int getIconHeight() { return 22; }
        };
    }

    // Crear icono de carpeta/nodo
    private Icon createNodeIcon(String type, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (type.equals("LD")) {
                    // Dispositivo logico - rectangulo con borde
                    g2.setColor(new Color(100, 100, 200));
                    g2.fillRoundRect(x + 1, y + 2, 14, 12, 3, 3);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.BOLD, 8));
                    g2.drawString("LD", x + 3, y + 11);
                } else if (type.equals("LN")) {
                    // Nodo logico - hexagono
                    g2.setColor(color);
                    int[] xp = {x+3, x+13, x+15, x+13, x+3, x+1};
                    int[] yp = {y+2, y+2, y+8, y+14, y+14, y+8};
                    g2.fillPolygon(xp, yp, 6);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.PLAIN, 7));
                    g2.drawString("LN", x + 4, y + 10);
                } else if (type.equals("DO")) {
                    // Data Object - cuadrado
                    g2.setColor(color);
                    g2.fillRect(x + 2, y + 2, 12, 12);
                    g2.setColor(color.darker());
                    g2.drawRect(x + 2, y + 2, 12, 12);
                } else {
                    // DA - circulo pequeño
                    g2.setColor(color);
                    g2.fillOval(x + 4, y + 4, 8, 8);
                }
                g2.dispose();
            }
            @Override
            public int getIconWidth() { return 16; }
            @Override
            public int getIconHeight() { return 16; }
        };
    }

    private void initIcons() {
        // Iconos para estados de breaker
        iconCache.put("breaker_on", createBreakerIcon("on"));
        iconCache.put("breaker_off", createBreakerIcon("off"));
        iconCache.put("breaker_intermediate", createBreakerIcon("intermediate"));

        // Iconos para tipos de LN
        iconCache.put("ln_xcbr", createNodeIcon("LN", new Color(200, 50, 50)));   // Rojo para breakers
        iconCache.put("ln_xswi", createNodeIcon("LN", new Color(200, 100, 50)));  // Naranja para switches
        iconCache.put("ln_mmxu", createMeterIcon(new Color(0, 100, 200)));        // Azul para mediciones
        iconCache.put("ln_mmtr", createMeterIcon(new Color(0, 150, 100)));        // Verde para energia
        iconCache.put("ln_cswi", createNodeIcon("LN", new Color(150, 100, 200))); // Purpura para control
        iconCache.put("ln_default", createNodeIcon("LN", new Color(100, 150, 100)));

        // Iconos para nodos
        iconCache.put("ld", createNodeIcon("LD", new Color(100, 100, 200)));
        iconCache.put("do", createNodeIcon("DO", new Color(150, 150, 200)));
        iconCache.put("da", createCircleIcon(new Color(100, 180, 100), 12));

        // Iconos para GOOSE
        iconCache.put("goose_container", createNodeIcon("GO", new Color(255, 140, 0)));  // Naranja
        iconCache.put("goose_gcb", createNodeIcon("GC", new Color(200, 100, 0)));        // Naranja oscuro
    }

    // ===== DRAG AND DROP SIMPLIFICADO =====
    private Point dragStart;
    private boolean isDragging = false;

    private void setupDragAndDrop() {
        // Mouse listener en el arbol para detectar drag
        modelTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                isDragging = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                dragStart = null;
            }
        });

        modelTree.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null && !isDragging) {
                    int dx = Math.abs(e.getX() - dragStart.x);
                    int dy = Math.abs(e.getY() - dragStart.y);
                    if (dx > 5 || dy > 5) {
                        isDragging = true;
                        // Iniciar drag
                        TreePath[] paths = modelTree.getSelectionPaths();
                        if (paths != null && paths.length > 0) {
                            modelTree.getTransferHandler().exportAsDrag(modelTree, e, TransferHandler.COPY);
                        }
                    }
                }
            }
        });

        // TransferHandler simple para el arbol
        modelTree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                TreePath[] paths = modelTree.getSelectionPaths();
                if (paths == null) return null;

                StringBuilder sb = new StringBuilder();
                for (TreePath path : paths) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.getUserObject() instanceof NodeInfo) {
                        NodeInfo info = (NodeInfo) node.getUserObject();
                        if (info.node instanceof FcModelNode) {
                            String ref = info.node.getReference().toString();
                            Fc fc = ((FcModelNode) info.node).getFc();
                            sb.append(ref).append("$").append(fc.toString()).append("\n");
                        }
                    }
                }
                return new StringSelection(sb.toString());
            }
        });

        // Drop target en el panel del monitor
        monitorTable.setDropTarget(new DropTarget() {
            @Override
            public synchronized void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    monitorTable.setBorder(BorderFactory.createLineBorder(new Color(0, 150, 255), 2));
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public synchronized void dragExit(DropTargetEvent dte) {
                monitorTable.setBorder(null);
            }

            @Override
            public synchronized void drop(DropTargetDropEvent dtde) {
                monitorTable.setBorder(null);
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    String data = (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);

                    // Agregar nodos desde la seleccion actual del arbol
                    TreePath[] paths = modelTree.getSelectionPaths();
                    if (paths != null) {
                        for (TreePath path : paths) {
                            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                            addNodeToMonitor(node);
                        }
                    }
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    log("Drop error: " + e.getMessage());
                    dtde.dropComplete(false);
                }
            }
        });
    }

    // Colores
    private static final Color COLOR_RUNNING = new Color(0, 170, 0);
    private static final Color COLOR_STOPPED = new Color(200, 0, 0);
    private static final Color COLOR_CONNECTING = new Color(255, 165, 0);
    private static final Color COLOR_BREAKER_ON = new Color(0, 180, 0);
    private static final Color COLOR_BREAKER_OFF = new Color(220, 50, 50);
    private static final Color COLOR_BREAKER_INTERMEDIATE = new Color(255, 165, 0);

    // Info de conexion
    private JLabel lblConnectionInfo;
    private String currentHost = "";
    private int currentPort = 0;
    private String connectedLocalIp = "";  // IP local usada para conectar al IED

    public IEDExplorerApp() {
        client = new IEC61850Client();
        server = new IEC61850Server();

        initIcons();  // Inicializar iconos
        initUI();
        setupListeners();
        setupDragAndDrop();  // Configurar drag and drop

        // Default: modo cliente
        switchToClientMode();
    }

    private void initUI() {
        setTitle("IED Navigator - IEC 61850 Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Barra de menu
        setJMenuBar(createMenuBar());

        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));

        // === TOOLBAR PROFESIONAL ===
        JToolBar toolbar = createToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // === Panel de contenido ===
        JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        // === Panel Superior: Modo + Status + Connection Info ===
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));

        // Selector de modo
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.setBorder(BorderFactory.createTitledBorder("Modo"));
        ButtonGroup modeGroup = new ButtonGroup();
        rbServer = new JRadioButton("Servidor");
        rbClient = new JRadioButton("Cliente", true);
        modeGroup.add(rbServer);
        modeGroup.add(rbClient);
        modePanel.add(rbServer);
        modePanel.add(rbClient);
        topPanel.add(modePanel, BorderLayout.WEST);

        // Status y Connection Info
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        statusIndicator = new JPanel();
        statusIndicator.setPreferredSize(new Dimension(16, 16));
        statusIndicator.setBackground(COLOR_STOPPED);
        statusIndicator.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        lblStatus = new JLabel("Desconectado");
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD));
        statusPanel.add(statusIndicator);
        statusPanel.add(lblStatus);

        // Separador
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        statusPanel.add(sep);

        // Info de conexion (IP:Puerto)
        lblConnectionInfo = new JLabel("Sin conexion");
        lblConnectionInfo.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblConnectionInfo.setForeground(new Color(0, 80, 160));
        statusPanel.add(new JLabel("Conexion:"));
        statusPanel.add(lblConnectionInfo);

        topPanel.add(statusPanel, BorderLayout.CENTER);

        contentPanel.add(topPanel, BorderLayout.NORTH);

        // === Panel Central: Cards (Server/Client) + Tree ===
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Cards para Server/Client
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(createServerPanel(), "SERVER");
        cardPanel.add(createClientPanel(), "CLIENT");
        cardPanel.setPreferredSize(new Dimension(300, 150));

        // Panel izquierdo (cards + log)
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.add(cardPanel, BorderLayout.NORTH);

        // Log
        logArea = new JTextArea(8, 30);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        leftPanel.add(logScroll, BorderLayout.CENTER);

        // Tree del modelo con soporte Drag
        rootNode = new DefaultMutableTreeNode("Modelo");
        treeModel = new DefaultTreeModel(rootNode);
        modelTree = new JTree(treeModel);
        modelTree.setRootVisible(true);
        modelTree.setCellRenderer(new ModelTreeCellRenderer());
        modelTree.setDragEnabled(true);
        modelTree.setRowHeight(20);  // Altura consistente para iconos
        modelTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        modelTree.setLargeModel(true);
        JScrollPane treeScroll = new JScrollPane(modelTree);
        treeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Modelo de Datos"));

        // Panel derecho con tabs: Activity Monitor, Reports, GOOSE, Setting Groups, Dataset, Data Model
        JTabbedPane rightTabbedPane = new JTabbedPane();
        rightTabbedPane.addTab("Monitor", createMonitorPanel());
        rightTabbedPane.addTab("Reports", createReportsPanel());
        rightTabbedPane.addTab("GOOSE", createGoosePanel());
        // rightTabbedPane.addTab("SV (SMV)", createSampledValuesPanel()); // SIN SMV
        rightTabbedPane.addTab("Setting Groups", createSettingGroupsPanel());
        rightTabbedPane.addTab("Dataset", createDatasetPanel());
        rightTabbedPane.addTab("Data Model", createDataModelPanel());
        rightTabbedPane.setTabPlacement(JTabbedPane.TOP);

        // Check for native library availability
        checkNativeLibrary();

        // SplitPane principal (izquierda: controles+log, centro: tree, derecha: tabs)
        // Establecer tamaños minimos para evitar que se colapsen
        treeScroll.setMinimumSize(new Dimension(300, 200));
        rightTabbedPane.setMinimumSize(new Dimension(400, 200));
        leftPanel.setMinimumSize(new Dimension(250, 200));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, rightTabbedPane);
        rightSplit.setDividerLocation(350);
        rightSplit.setResizeWeight(0.4);
        rightSplit.setOneTouchExpandable(true);  // Flechitas para expandir/colapsar

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setDividerLocation(280);
        mainSplit.setResizeWeight(0.2);
        mainSplit.setOneTouchExpandable(true);  // Flechitas para expandir/colapsar

        centerPanel.add(mainSplit, BorderLayout.CENTER);

        contentPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Barra de estado inferior
        JPanel statusBar = createStatusBar();
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Menu Archivo
        JMenu menuFile = new JMenu("Archivo");
        JMenuItem miLoadScl = new JMenuItem("Cargar SCL/CID...");
        miLoadScl.addActionListener(e -> {
            if (currentMode == AppMode.SERVER) {
                selectSclFile();
            } else {
                // En modo cliente, permitir cargar SCL para GoCBs
                loadSclForGoCBs();
            }
        });
        menuFile.add(miLoadScl);
        menuFile.addSeparator();
        JMenuItem miExit = new JMenuItem("Salir");
        miExit.addActionListener(e -> System.exit(0));
        menuFile.add(miExit);
        menuBar.add(menuFile);

        // Menu Herramientas
        JMenu menuTools = new JMenu("Herramientas");
        JMenuItem miGetCid = new JMenuItem("Obtener CID del IED...");
        miGetCid.addActionListener(e -> obtenerCidDelIed());
        menuTools.add(miGetCid);
        JMenuItem miSaveCid = new JMenuItem("Guardar CID...");
        miSaveCid.addActionListener(e -> guardarCid());
        menuTools.add(miSaveCid);
        menuBar.add(menuTools);

        // Menu Ayuda
        JMenu menuHelp = new JMenu("Ayuda");
        JMenuItem miAbout = new JMenuItem("Acerca de...");
        miAbout.addActionListener(e -> showAboutDialog());
        menuHelp.add(miAbout);
        menuBar.add(menuHelp);

        return menuBar;
    }

    private void showAboutDialog() {
        String nativeStatus = nativeLibAvailable ?
            "<span style='color: green;'>✓ libiec61850 disponible</span>" :
            "<span style='color: red;'>✗ libiec61850 no encontrada</span>";

        String message =
            "<html><body style='width: 380px; padding: 10px;'>" +
            "<h2 style='color: #2E86AB; margin-bottom: 5px;'>IED Navigator</h2>" +
            "<p style='color: #666; font-size: 11px;'>Version 2.0 - Hybrid Edition</p>" +
            "<hr style='margin: 10px 0;'>" +
            "<p><b>IEC 61850 Explorer Tool</b></p>" +
            "<p>Herramienta profesional para explorar, monitorear y configurar " +
            "dispositivos IED compatibles con el estandar IEC 61850.</p>" +
            "<br>" +
            "<p><b>Caracteristicas:</b></p>" +
            "<ul>" +
            "<li>Cliente/Servidor MMS (iec61850bean)</li>" +
            "<li>Monitoreo de datos en tiempo real</li>" +
            "<li>Reports (URCB/BRCB)</li>" +
            "<li>GOOSE Subscriber/Publisher</li>" +
            "<li><b>Sampled Values (SMV)</b> - via libiec61850</li>" +
            "<li>Carga y descarga de archivos SCL/CID</li>" +
            "</ul>" +
            "<p style='font-size: 10px;'>" + nativeStatus + "</p>" +
            "<hr style='margin: 10px 0;'>" +
            "<p><b>Desarrollado por:</b></p>" +
            "<p style='color: #2E86AB; font-size: 13px;'><b>Emilio Medina</b></p>" +
            "<p style='font-size: 11px;'>Técnico Superior en Electrónica</p>" +
            "<br>" +
            "<p style='color: #888; font-size: 10px;'>" +
            "Bibliotecas: iec61850bean (MMS), libiec61850 (GOOSE/SV), pcap4j, JNA<br>" +
            "&copy; 2024 - Todos los derechos reservados</p>" +
            "</body></html>";

        JOptionPane.showMessageDialog(this, message, "Acerca de IED Navigator",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void loadSclForGoCBs() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Cargar SCL para GoCBs");
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".icd") || name.endsWith(".cid") ||
                       name.endsWith(".scd") || name.endsWith(".scl");
            }
            public String getDescription() {
                return "SCL Files (*.icd, *.cid, *.scd, *.scl)";
            }
        });

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            log("Cargando SCL para GoCBs: " + file.getName());

            int iedIndex = detectAndSelectIED(file);
            if (iedIndex == -2) return; // User cancelled

            loadedSclFile = file;
            if (iedIndex >= 0) {
                parseGoCBsFromScl(file, iedIndex);
            } else {
                parseGoCBsFromScl(file);
            }
            refreshGooseControlBlocks();
            log("GoCBs cargados: " + sclGoCBs.size());
        }
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        // Logo / Titulo
        JLabel lblTitle = new JLabel("  IED Navigator  ");
        lblTitle.setFont(new Font("Arial", Font.BOLD, 14));
        lblTitle.setForeground(new Color(0, 80, 160));
        toolbar.add(lblTitle);
        toolbar.addSeparator(new Dimension(20, 0));

        // Botones de toolbar
        JButton btnNewConnection = new JButton("Nueva Conexion");
        btnNewConnection.setToolTipText("Conectar a un IED");
        btnNewConnection.addActionListener(e -> {
            rbClient.setSelected(true);
            switchToClientMode();
        });
        toolbar.add(btnNewConnection);

        JButton btnSimulate = new JButton("Simular IED");
        btnSimulate.setToolTipText("Cargar SCL y simular un IED");
        btnSimulate.addActionListener(e -> {
            rbServer.setSelected(true);
            switchToServerMode();
        });
        toolbar.add(btnSimulate);

        toolbar.addSeparator();

        JButton btnClearLog = new JButton("Limpiar Log");
        btnClearLog.addActionListener(e -> logArea.setText(""));
        toolbar.add(btnClearLog);

        toolbar.add(Box.createHorizontalGlue());

        // Info version
        JLabel lblVersion = new JLabel("v1.0 | IEC 61850  ");
        lblVersion.setForeground(Color.GRAY);
        toolbar.add(lblVersion);

        return toolbar;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
        ));
        statusBar.setBackground(new Color(240, 240, 240));

        JLabel lblReady = new JLabel("Listo");
        statusBar.add(lblReady, BorderLayout.WEST);

        // Placa de identificación del IED (se llena tras conectar con FC=DC)
        lblIedInfo = new JLabel(" ");
        lblIedInfo.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        lblIedInfo.setForeground(new Color(40, 80, 160));
        statusBar.add(lblIedInfo, BorderLayout.CENTER);

        JLabel lblTime = new JLabel();
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            lblTime.setText(sdf.format(new Date()));
        });
        timer.start();
        statusBar.add(lblTime, BorderLayout.EAST);

        return statusBar;
    }

    private JPanel createServerPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Simulador IED (Servidor IEC 61850)"));

        // Fila 1: Seleccionar archivo SCL
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnSelectFile = new JButton("Cargar SCL/ICD/CID...");
        btnSelectFile.setToolTipText("Cargar archivo SCL, SCD, ICD o CID para simular IED");
        lblFileName = new JLabel("Ningun archivo");
        lblFileName.setForeground(Color.GRAY);
        row1.add(btnSelectFile);
        row1.add(lblFileName);
        panel.add(row1);

        // Fila 2: Puerto
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Puerto:"));
        tfServerPort = new JTextField("102", 6);
        row2.add(tfServerPort);

        // Info
        JLabel lblInfo = new JLabel("(102=MMS, 49151=pruebas)");
        lblInfo.setForeground(Color.GRAY);
        lblInfo.setFont(lblInfo.getFont().deriveFont(Font.ITALIC, 10f));
        row2.add(lblInfo);
        panel.add(row2);

        // Fila 3: Boton Start/Stop
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnStartStop = new JButton("Iniciar Simulacion");
        btnStartStop.setPreferredSize(new Dimension(200, 35));
        btnStartStop.setEnabled(false);
        btnStartStop.setToolTipText("Iniciar servidor IEC 61850 para simular el IED");
        row3.add(btnStartStop);
        panel.add(row3);

        // Fila 4: Informacion de uso
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel lblUsage = new JLabel("<html><small>1. Carga SCL/ICD  2. Inicia servidor  3. Conecta cliente</small></html>");
        lblUsage.setForeground(new Color(100, 100, 150));
        row4.add(lblUsage);
        panel.add(row4);

        return panel;
    }

    private JPanel createClientPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Cliente IEC 61850"));

        // Fila 1: Host
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Host:"));
        tfHost = new JTextField("192.168.1.100", 15);
        row1.add(tfHost);
        panel.add(row1);

        // Fila 2: Puerto
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Puerto:"));
        tfClientPort = new JTextField("102", 6);
        row2.add(tfClientPort);
        panel.add(row2);

        // Fila 3: Boton Connect
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnConnect = new JButton("Conectar");
        btnConnect.setPreferredSize(new Dimension(200, 35));
        row3.add(btnConnect);
        panel.add(row3);

        // Fila 4: Polling
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cbPolling = new JCheckBox("Polling");
        cbPolling.setEnabled(false);
        row4.add(cbPolling);
        row4.add(new JLabel("Intervalo (ms):"));
        spinnerInterval = new JSpinner(new SpinnerNumberModel(2000, 500, 60000, 500));
        spinnerInterval.setEnabled(false);
        row4.add(spinnerInterval);
        panel.add(row4);

        // Fila 5: Watchlist info
        JPanel row5 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lblWatchlistCount = new JLabel("Watchlist: 0 nodos");
        lblWatchlistCount.setForeground(new Color(0, 100, 180));
        row5.add(lblWatchlistCount);
        JButton btnClearWatchlist = new JButton("Limpiar");
        btnClearWatchlist.setMargin(new Insets(2, 5, 2, 5));
        btnClearWatchlist.addActionListener(e -> clearWatchlist());
        row5.add(btnClearWatchlist);
        panel.add(row5);

        // Fila 6: Obtener/Guardar CID
        JPanel row6 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnGetCid = new JButton("Obtener CID");
        btnGetCid.setMargin(new Insets(2, 5, 2, 5));
        btnGetCid.setToolTipText("Buscar y descargar archivo CID del IED");
        btnGetCid.addActionListener(e -> obtenerCidDelIed());
        row6.add(btnGetCid);
        JButton btnSaveCid = new JButton("Guardar CID");
        btnSaveCid.setMargin(new Insets(2, 5, 2, 5));
        btnSaveCid.setToolTipText("Guardar el CID descargado en disco");
        btnSaveCid.addActionListener(e -> guardarCid());
        row6.add(btnSaveCid);
        panel.add(row6);

        return panel;
    }

    private void clearWatchlist() {
        watchlist.clear();
        monitorItems.clear();
        updateWatchlistLabel();
        refreshMonitorTable();
        log("Watchlist limpiada");
        modelTree.repaint();
    }

    private void updateWatchlistLabel() {
        lblWatchlistCount.setText("Watchlist: " + watchlist.size() + " nodos");
    }

    private JPanel createMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Activity Monitor (Drag & Drop)"));
        panel.setMinimumSize(new Dimension(300, 200));

        // Tabla con mas columnas como IEDScout
        String[] columns = {"Nombre", "FC", "Tipo", "Valor", "Estado"};
        monitorTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        monitorTable = new JTable(monitorTableModel);
        monitorTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        monitorTable.setRowHeight(22);
        monitorTable.setShowGrid(true);
        monitorTable.setGridColor(new Color(220, 220, 220));

        // Configurar anchos de columna
        monitorTable.getColumnModel().getColumn(0).setPreferredWidth(180);  // Nombre
        monitorTable.getColumnModel().getColumn(1).setPreferredWidth(35);   // FC
        monitorTable.getColumnModel().getColumn(2).setPreferredWidth(70);   // Tipo
        monitorTable.getColumnModel().getColumn(3).setPreferredWidth(100);  // Valor
        monitorTable.getColumnModel().getColumn(4).setPreferredWidth(60);   // Estado

        // Renderer para colorear valores
        monitorTable.setDefaultRenderer(Object.class, new MonitorTableRenderer());

        // Sorter para filtrado
        monitorSorter = new TableRowSorter<>(monitorTableModel);
        monitorTable.setRowSorter(monitorSorter);

        // El drop se configura en setupDragAndDrop()
        monitorTable.setFillsViewportHeight(true);

        JScrollPane tableScroll = new JScrollPane(monitorTable);
        tableScroll.getViewport().setBackground(Color.WHITE);

        // Panel de area de drop visual
        JPanel dropHintPanel = new JPanel(new BorderLayout());
        dropHintPanel.setBackground(new Color(245, 250, 255));
        dropHintPanel.setBorder(BorderFactory.createDashedBorder(
            new Color(100, 150, 200), 2, 5, 3, true));
        JLabel lblDropHint = new JLabel("<html><center>Arrastra nodos desde el arbol<br>para monitorear valores</center></html>");
        lblDropHint.setForeground(new Color(100, 150, 200));
        lblDropHint.setHorizontalAlignment(SwingConstants.CENTER);
        dropHintPanel.add(lblDropHint, BorderLayout.CENTER);
        dropHintPanel.setPreferredSize(new Dimension(250, 60));

        // Header fila 1: conteo + botones
        JPanel headerRow1 = new JPanel(new BorderLayout());
        JLabel lblCount = new JLabel(" Items: 0");
        lblCount.setFont(lblCount.getFont().deriveFont(Font.BOLD));
        headerRow1.add(lblCount, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        JButton btnRemove = new JButton("Quitar");
        btnRemove.setMargin(new Insets(2, 8, 2, 8));
        btnRemove.addActionListener(e -> removeSelectedFromMonitor());
        btnPanel.add(btnRemove);
        JButton btnClear = new JButton("Limpiar");
        btnClear.setMargin(new Insets(2, 8, 2, 8));
        btnClear.addActionListener(e -> clearMonitor());
        btnPanel.add(btnClear);
        headerRow1.add(btnPanel, BorderLayout.EAST);

        // Header fila 2: filtros
        JPanel headerRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        headerRow2.add(new JLabel("FC:"));
        monitorFcFilter = new JComboBox<>(new String[]{
            "Todos", "ST", "MX", "CF", "DC", "SP", "SV", "BL", "CO", "SE", "SG", "EX", "RP", "BR", "OR"
        });
        monitorFcFilter.setPreferredSize(new Dimension(70, 22));
        monitorFcFilter.addActionListener(e -> applyMonitorFilter());
        headerRow2.add(monitorFcFilter);
        headerRow2.add(new JLabel("  Nombre:"));
        monitorNameFilter = new javax.swing.JTextField(14);
        monitorNameFilter.setToolTipText("Filtrar por nombre (p.ej. MMXU, stVal)");
        monitorNameFilter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyMonitorFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyMonitorFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyMonitorFilter(); }
        });
        headerRow2.add(monitorNameFilter);
        JButton btnClearFilter = new JButton("✕");
        btnClearFilter.setMargin(new Insets(1, 4, 1, 4));
        btnClearFilter.setToolTipText("Limpiar filtros");
        btnClearFilter.addActionListener(e -> {
            monitorFcFilter.setSelectedIndex(0);
            monitorNameFilter.setText("");
        });
        headerRow2.add(btnClearFilter);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(headerRow1, BorderLayout.NORTH);
        headerPanel.add(headerRow2, BorderLayout.SOUTH);

        // Combinar todo
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(tableScroll, BorderLayout.CENTER);
        contentPanel.add(dropHintPanel, BorderLayout.SOUTH);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        // Guardar referencia al label de conteo para actualizar
        monitorCountLabel = lblCount;

        return panel;
    }

    private JLabel monitorCountLabel;
    private TableRowSorter<DefaultTableModel> monitorSorter;
    private JComboBox<String> monitorFcFilter;
    private javax.swing.JTextField monitorNameFilter;

    private void clearMonitor() {
        monitorItems.clear();
        watchlist.clear();
        updateWatchlistLabel();
        refreshMonitorTable();
        modelTree.repaint();
        log("Monitor limpiado");
    }

    // ==================== REPORTS PANEL (Professional) ====================
    private JPanel createReportsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // ===== TOP: RCB List with controls =====
        JPanel rcbPanel = new JPanel(new BorderLayout());
        rcbPanel.setBorder(BorderFactory.createTitledBorder("Report Control Blocks (URCB/BRCB)"));

        String[] rcbColumns = {"RCB Reference", "Tipo", "Dataset", "TrgOps", "IntgPd", "Estado"};
        reportsTableModel = new DefaultTableModel(rcbColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        reportsTable = new JTable(reportsTableModel);
        reportsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reportsTable.setRowHeight(22);
        reportsTable.getColumnModel().getColumn(0).setPreferredWidth(250);
        reportsTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        reportsTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        reportsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        reportsTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        reportsTable.getColumnModel().getColumn(5).setPreferredWidth(100);

        // Color rows based on status
        reportsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    String status = (String) table.getValueAt(row, 5);
                    if ("Habilitado".equals(status)) {
                        c.setBackground(new Color(200, 255, 200));
                        if (column == 5) setForeground(new Color(0, 120, 0));
                    } else {
                        c.setBackground(Color.WHITE);
                        if (column == 5) setForeground(Color.GRAY);
                    }
                }
                return c;
            }
        });

        JScrollPane rcbScroll = new JScrollPane(reportsTable);
        rcbScroll.setPreferredSize(new Dimension(300, 180));

        // Control buttons
        JPanel rcbBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));

        JButton btnRefreshRcb = new JButton("Cargar RCBs");
        btnRefreshRcb.setToolTipText("Cargar Report Control Blocks del modelo");
        btnRefreshRcb.addActionListener(e -> refreshReportControlBlocks());
        rcbBtnPanel.add(btnRefreshRcb);

        rcbBtnPanel.add(new JSeparator(SwingConstants.VERTICAL));

        JButton btnEnableRcb = new JButton("Habilitar");
        btnEnableRcb.setBackground(new Color(0, 150, 0));
        btnEnableRcb.setForeground(Color.WHITE);
        btnEnableRcb.setToolTipText("Habilitar el RCB seleccionado para recibir reportes");
        btnEnableRcb.addActionListener(e -> enableSelectedReport(true));
        rcbBtnPanel.add(btnEnableRcb);

        JButton btnDisableRcb = new JButton("Deshabilitar");
        btnDisableRcb.setToolTipText("Deshabilitar el RCB seleccionado");
        btnDisableRcb.addActionListener(e -> enableSelectedReport(false));
        rcbBtnPanel.add(btnDisableRcb);

        JButton btnEnableAll = new JButton("Habilitar Todos");
        btnEnableAll.setToolTipText("Habilitar todos los RCBs");
        btnEnableAll.addActionListener(e -> enableAllReports(true));
        rcbBtnPanel.add(btnEnableAll);

        // Statistics
        JLabel lblRcbCount = new JLabel("  RCBs: 0");
        rcbBtnPanel.add(lblRcbCount);

        rcbPanel.add(rcbScroll, BorderLayout.CENTER);
        rcbPanel.add(rcbBtnPanel, BorderLayout.SOUTH);

        // ===== BOTTOM: Received Report Data =====
        JPanel dataPanel = new JPanel(new BorderLayout());
        dataPanel.setBorder(BorderFactory.createTitledBorder("Datos de Reportes Recibidos (Tiempo Real)"));

        String[] dataColumns = {"Timestamp", "RCB", "SeqNum", "Referencia", "Valor Anterior", "Valor Nuevo", "Razon"};
        reportDataTableModel = new DefaultTableModel(dataColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        reportDataTable = new JTable(reportDataTableModel);
        reportDataTable.setRowHeight(20);
        reportDataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        reportDataTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        reportDataTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        reportDataTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        reportDataTable.getColumnModel().getColumn(3).setPreferredWidth(200);
        reportDataTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        reportDataTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        reportDataTable.getColumnModel().getColumn(6).setPreferredWidth(80);

        // Color for value changes
        reportDataTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected && column == 5) {
                    // New value column - highlight
                    c.setBackground(new Color(255, 255, 200));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (!isSelected) {
                    c.setBackground(Color.WHITE);
                }
                return c;
            }
        });

        JScrollPane dataScroll = new JScrollPane(reportDataTable);

        JPanel dataBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));

        JButton btnClearReports = new JButton("Limpiar");
        btnClearReports.addActionListener(e -> reportDataTableModel.setRowCount(0));
        dataBtnPanel.add(btnClearReports);

        JCheckBox cbAutoScroll = new JCheckBox("Auto-scroll", true);
        dataBtnPanel.add(cbAutoScroll);

        JLabel lblReportCount = new JLabel("  Reportes: 0");
        dataBtnPanel.add(lblReportCount);

        // Update counts periodically
        javax.swing.Timer reportTimer = new javax.swing.Timer(500, e -> {
            lblRcbCount.setText("  RCBs: " + reportsTableModel.getRowCount());
            lblReportCount.setText("  Reportes: " + reportDataTableModel.getRowCount());
        });
        reportTimer.start();

        dataPanel.add(dataScroll, BorderLayout.CENTER);
        dataPanel.add(dataBtnPanel, BorderLayout.SOUTH);

        // Split
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rcbPanel, dataPanel);
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.4);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void enableAllReports(boolean enable) {
        if (client == null || !isConnected) {
            log("Error: no hay conexion activa con el IED");
            return;
        }

        // Snapshot de los RCBs actuales
        List<String> names = new ArrayList<>();
        List<Rcb> rcbs = new ArrayList<>();
        for (int i = 0; i < reportsTableModel.getRowCount(); i++) {
            String rcbName = (String) reportsTableModel.getValueAt(i, 0);
            Rcb rcb = rcbMap.get(rcbName);
            if (rcb != null) {
                names.add(rcbName);
                rcbs.add(rcb);
            }
        }

        backgroundExecutor.submit(() -> {
            int ok = 0, fail = 0;
            for (int i = 0; i < rcbs.size(); i++) {
                final String rcbName = names.get(i);
                final Rcb rcb = rcbs.get(i);
                final int rowIdx = i;
                try {
                    if (enable) {
                        client.enableReporting(rcb, report -> {
                            SwingUtilities.invokeLater(() -> handleReportReceived(report));
                        });
                    } else {
                        client.disableReporting(rcb);
                    }
                    SwingUtilities.invokeLater(() ->
                        reportsTableModel.setValueAt(enable ? "Habilitado" : "Deshabilitado", rowIdx, 5)
                    );
                    ok++;
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                        log("Error en " + rcbName + ": " + e.getMessage())
                    );
                    fail++;
                }
            }
            final int finalOk = ok, finalFail = fail;
            SwingUtilities.invokeLater(() ->
                log((enable ? "Habilitar" : "Deshabilitar") + " todos: " + finalOk + " OK, " + finalFail + " error(es)")
            );
        });
    }

    private void refreshReportControlBlocks() {
        reportsTableModel.setRowCount(0);
        rcbMap.clear();

        // Obtener modelo segun el modo
        ServerModel model = null;
        if (currentMode == AppMode.SERVER && server != null) {
            model = server.getServerModel();
        } else if (currentMode == AppMode.CLIENT && client != null && isConnected) {
            model = client.getServerModel();
        }

        if (model == null) {
            log("No hay modelo cargado para obtener RCBs");
            return;
        }

        try {
            // Buscar todos los RCBs en el modelo (busqueda recursiva)
            log("Buscando RCBs en el modelo...");
            for (ModelNode ld : model.getChildren()) {
                String ldName = ld.getName();
                for (ModelNode ln : ld.getChildren()) {
                    if (ln.getChildren() == null) continue;
                    String lnName = ln.getName();

                    // Debug: mostrar nodos de LLN0
                    if (lnName.equals("LLN0")) {
                        log("LN " + ldName + "/" + lnName + " tiene " + ln.getChildren().size() + " hijos");
                    }

                    for (ModelNode node : ln.getChildren()) {
                        // Buscar RCBs directamente y tambien dentro de contenedores
                        searchForRcbs(node, ldName, lnName);
                    }
                }
            }
            log("RCBs encontrados: " + rcbMap.size());

            if (rcbMap.isEmpty()) {
                log("Nota: Este IED puede no tener RCBs configurados, o pueden estar en una estructura diferente.");
            }
        } catch (Exception e) {
            log("Error obteniendo RCBs: " + e.getMessage());
        }
    }

    private void searchForRcbs(ModelNode node, String ldName, String lnName) {
        if (node instanceof Urcb) {
            Urcb urcb = (Urcb) node;
            String name = ldName + "/" + lnName + "." + urcb.getName();
            String dataset = urcb.getDatSet() != null ? urcb.getDatSet().getStringValue() : "";
            boolean enabled = urcb.getRptEna() != null && urcb.getRptEna().getValue();

            String trgOps = "";
            String intgPd = "";
            if (urcb.getTrgOps() != null) {
                BdaTriggerConditions tc = urcb.getTrgOps();
                StringBuilder sb = new StringBuilder();
                if (tc.isDataChange()) sb.append("dchg ");
                if (tc.isQualityChange()) sb.append("qchg ");
                if (tc.isDataUpdate()) sb.append("dupd ");
                if (tc.isIntegrity()) sb.append("intg ");
                if (tc.isGeneralInterrogation()) sb.append("gi ");
                trgOps = sb.toString().trim();
            }
            if (urcb.getIntgPd() != null) {
                intgPd = String.valueOf(urcb.getIntgPd().getValue());
            }

            rcbMap.put(name, urcb);
            reportsTableModel.addRow(new Object[]{name, "URCB", dataset, trgOps, intgPd, enabled ? "Habilitado" : "Deshabilitado"});

        } else if (node instanceof Brcb) {
            Brcb brcb = (Brcb) node;
            String name = ldName + "/" + lnName + "." + brcb.getName();
            String dataset = brcb.getDatSet() != null ? brcb.getDatSet().getStringValue() : "";
            boolean enabled = brcb.getRptEna() != null && brcb.getRptEna().getValue();

            String trgOps = "";
            String intgPd = "";
            if (brcb.getTrgOps() != null) {
                BdaTriggerConditions tc = brcb.getTrgOps();
                StringBuilder sb = new StringBuilder();
                if (tc.isDataChange()) sb.append("dchg ");
                if (tc.isQualityChange()) sb.append("qchg ");
                if (tc.isDataUpdate()) sb.append("dupd ");
                if (tc.isIntegrity()) sb.append("intg ");
                if (tc.isGeneralInterrogation()) sb.append("gi ");
                trgOps = sb.toString().trim();
            }
            if (brcb.getIntgPd() != null) {
                intgPd = String.valueOf(brcb.getIntgPd().getValue());
            }

            rcbMap.put(name, brcb);
            reportsTableModel.addRow(new Object[]{name, "BRCB", dataset, trgOps, intgPd, enabled ? "Habilitado" : "Deshabilitado"});
        }

        // Buscar recursivamente en subnodos (por si los RCBs estan en contenedores)
        if (node.getChildren() != null) {
            for (ModelNode child : node.getChildren()) {
                searchForRcbs(child, ldName, lnName);
            }
        }
    }

    private void enableSelectedReport(boolean enable) {
        int row = reportsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un RCB primero");
            return;
        }

        if (client == null || !isConnected) {
            log("Error: no hay conexion activa con el IED");
            return;
        }

        String rcbName = (String) reportsTableModel.getValueAt(row, 0);
        Rcb rcb = rcbMap.get(rcbName);
        if (rcb == null) return;

        final int tableRow = row;
        backgroundExecutor.submit(() -> {
            try {
                if (enable) {
                    client.enableReporting(rcb, report -> {
                        SwingUtilities.invokeLater(() -> handleReportReceived(report));
                    });
                    SwingUtilities.invokeLater(() -> {
                        reportsTableModel.setValueAt("Habilitado", tableRow, 5);
                        log("Report habilitado: " + rcbName);
                    });
                } else {
                    client.disableReporting(rcb);
                    SwingUtilities.invokeLater(() -> {
                        reportsTableModel.setValueAt("Deshabilitado", tableRow, 5);
                        log("Report deshabilitado: " + rcbName);
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                    log("Error " + (enable ? "habilitando" : "deshabilitando") + " report " + rcbName + ": " + e.getMessage())
                );
            }
        });
    }

    // Store previous values for comparison
    private Map<String, String> previousReportValues = new HashMap<>();

    private void handleReportReceived(Report report) {
        try {
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            String rcbRef = report.getRptId();
            String seqNum = report.getSqNum() != null ? String.valueOf(report.getSqNum()) : "";

            List<FcModelNode> values = report.getValues();
            List<BdaReasonForInclusion> reasons = report.getReasonCodes();

            if (values != null) {
                for (int i = 0; i < values.size(); i++) {
                    FcModelNode node = values.get(i);
                    String ref = node.getReference().toString();
                    String newValue = "";
                    if (node instanceof BasicDataAttribute) {
                        newValue = ((BasicDataAttribute) node).getValueString();
                    }

                    // Get previous value
                    String prevValue = previousReportValues.getOrDefault(ref, "");

                    // Get reason
                    String reason = "";
                    if (reasons != null && i < reasons.size()) {
                        BdaReasonForInclusion r = reasons.get(i);
                        if (r.isDataChange()) reason = "dchg";
                        else if (r.isQualityChange()) reason = "qchg";
                        else if (r.isDataUpdate()) reason = "dupd";
                        else if (r.isIntegrity()) reason = "intg";
                        else if (r.isGeneralInterrogation()) reason = "gi";
                    }

                    // Add to table: Timestamp, RCB, SeqNum, Referencia, Valor Anterior, Valor Nuevo, Razon
                    reportDataTableModel.insertRow(0, new Object[]{
                        timestamp, rcbRef, seqNum, ref, prevValue, newValue, reason
                    });

                    // Store current value as previous
                    previousReportValues.put(ref, newValue);

                    // Actualizar el árbol "Modelo de Datos" con el nuevo valor
                    // (iec61850bean ya actualizó el nodo internamente; solo notificamos al árbol)
                    updateSingleNodeInTree(ref);
                }
            }
            log("Report recibido: " + rcbRef + " (SeqNum: " + seqNum + ", " + (values != null ? values.size() : 0) + " valores)");
        } catch (Exception e) {
            log("Error procesando report: " + e.getMessage());
        }
    }

    // ==================== GOOSE PANEL (Hybrid: libiec61850 + pcap4j fallback) ====================
    private JPanel createGoosePanel() {
        JPanel panel = new JPanel(new BorderLayout(3, 3));
        panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        // Initialize Native GOOSE subscriber (libiec61850) - preferred
        nativeGooseSubscriber = new NativeGooseSubscriber();
        nativeGooseSubscriber.setLogListener(msg -> logGoose("[NATIVE] " + msg));
        nativeGooseSubscriber.setMessageListener(msg -> handleNativeGooseMessage(msg));

        // Initialize pcap4j GOOSE subscriber as fallback
        gooseSubscriber = new GooseSubscriber();
        gooseSubscriber.setLogListener(msg -> logGoose("[PCAP] " + msg));
        gooseSubscriber.setMessageListener(msg -> handleGooseMessage(msg));

        // Publisher (still uses pcap4j)
        goosePublisher = new GoosePublisher();
        goosePublisher.setLogListener(msg -> logGoose("[PUB] " + msg));
        goosePublisher.setPublishListener(pubMsg -> {
            // Convert published message to GooseSubscriber.GooseMessage for the table
            GooseSubscriber.GooseMessage tableMsg = new GooseSubscriber.GooseMessage();
            tableMsg.timestamp = pubMsg.timestamp;
            tableMsg.srcMac = "LOCAL";
            tableMsg.dstMac = "01:0C:CD:01:00:01";
            tableMsg.gocbRef = pubMsg.gocbRef;
            tableMsg.goId = pubMsg.goId;
            tableMsg.datSet = pubMsg.datSet;
            tableMsg.appId = pubMsg.appId;
            tableMsg.stNum = pubMsg.stNum;
            tableMsg.sqNum = pubMsg.sqNum;
            tableMsg.confRev = 1;
            tableMsg.numDataSetEntries = pubMsg.dataValues != null ? pubMsg.dataValues.size() : 0;
            // Convert data values
            if (pubMsg.dataValues != null) {
                int idx = 0;
                for (GoosePublisher.DataValue dv : pubMsg.dataValues) {
                    String typeName = dv.type.name();
                    tableMsg.dataEntries.add(new GooseSubscriber.DataEntry(idx++, typeName, dv.value));
                }
            }
            handleGooseMessage(tableMsg);
        });

        // UDP Bridge for WiFi/routed networks
        gooseUdpBridge = new GooseUdpBridge();
        gooseUdpBridge.setLogListener(msg -> logGoose("[UDP] " + msg));
        gooseUdpBridge.setMessageListener(msg -> handleGooseMessage(msg));

        // ===== TOP PANEL: Two rows for better visibility =====
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(240, 240, 245));
        topPanel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));

        // Row 1: Interface + Capture Button
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        row1.setOpaque(false);

        row1.add(new JLabel("Interface:"));
        gooseInterfaceCombo = new JComboBox<>();
        gooseInterfaceCombo.setPreferredSize(new Dimension(300, 26));
        loadNetworkInterfaces();
        row1.add(gooseInterfaceCombo);

        JButton btnRefresh = new JButton("↻");
        btnRefresh.setToolTipText("Refrescar interfaces");
        btnRefresh.setMargin(new Insets(2, 6, 2, 6));
        btnRefresh.addActionListener(e -> loadNetworkInterfaces());
        row1.add(btnRefresh);

        row1.add(Box.createHorizontalStrut(15));

        // CAPTURE BUTTON - Prominente
        btnGooseStartStop = new JButton("▶ CAPTURAR GOOSE");
        btnGooseStartStop.setBackground(new Color(46, 125, 50));
        btnGooseStartStop.setForeground(Color.WHITE);
        btnGooseStartStop.setFocusPainted(false);
        btnGooseStartStop.setFont(btnGooseStartStop.getFont().deriveFont(Font.BOLD));
        btnGooseStartStop.setPreferredSize(new Dimension(160, 28));
        btnGooseStartStop.addActionListener(e -> toggleGooseCapture());
        row1.add(btnGooseStartStop);

        lblGooseStatus = new JLabel("Detenido");
        lblGooseStatus.setForeground(Color.GRAY);
        row1.add(lblGooseStatus);

        topPanel.add(row1, BorderLayout.NORTH);

        // Row 2: Publisher controls (less prominent)
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        row2.setOpaque(false);

        row2.add(new JLabel("Publicar:"));
        btnGoosePublish = new JButton("▶ Publicar");
        btnGoosePublish.setBackground(new Color(21, 101, 192));
        btnGoosePublish.setForeground(Color.WHITE);
        btnGoosePublish.setFocusPainted(false);
        btnGoosePublish.addActionListener(e -> toggleGoosePublishing());
        row2.add(btnGoosePublish);

        lblPublishStatus = new JLabel("Detenido");
        lblPublishStatus.setForeground(Color.GRAY);
        row2.add(lblPublishStatus);

        row2.add(Box.createHorizontalStrut(20));
        row2.add(new JLabel("Estado:"));
        cbGooseState = new JComboBox<>(new String[]{"OFF", "ON", "INTERMEDIATE", "BAD"});
        cbGooseState.setPreferredSize(new Dimension(110, 24));
        row2.add(cbGooseState);

        btnGooseStateChange = new JButton("⚡ Cambio");
        btnGooseStateChange.setToolTipText("Enviar cambio de estado GOOSE");
        btnGooseStateChange.setBackground(new Color(230, 81, 0));
        btnGooseStateChange.setForeground(Color.WHITE);
        btnGooseStateChange.setFocusPainted(false);
        btnGooseStateChange.addActionListener(e -> publishGooseStateChange());
        btnGooseStateChange.setEnabled(false);
        row2.add(btnGooseStateChange);

        topPanel.add(row2, BorderLayout.SOUTH);

        panel.add(topPanel, BorderLayout.NORTH);

        // ===== CENTER: Messages Table =====
        String[] captureColumns = {"Tiempo", "AppID", "st#", "sq#", "gocbRef", "Datos"};
        gooseDataTableModel = new DefaultTableModel(captureColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        gooseDataTable = new JTable(gooseDataTableModel);
        gooseDataTable.setRowHeight(20);
        gooseDataTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        gooseDataTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        gooseDataTable.getColumnModel().getColumn(1).setPreferredWidth(55);
        gooseDataTable.getColumnModel().getColumn(2).setPreferredWidth(40);
        gooseDataTable.getColumnModel().getColumn(3).setPreferredWidth(40);
        gooseDataTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        gooseDataTable.getColumnModel().getColumn(5).setPreferredWidth(250);

        // Color based on stNum
        gooseDataTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    try {
                        Object stNumObj = table.getValueAt(row, 2);
                        if (stNumObj != null) {
                            int stNum = Integer.parseInt(stNumObj.toString());
                            c.setBackground(stNum > 1 ? new Color(255, 253, 208) : Color.WHITE);
                        }
                    } catch (Exception e) { c.setBackground(Color.WHITE); }
                }
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(gooseDataTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Mensajes GOOSE"));
        panel.add(tableScroll, BorderLayout.CENTER);

        // ===== BOTTOM: GoCBs + Log =====
        JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        bottomPanel.setPreferredSize(new Dimension(100, 140));

        // GoCBs from model (expanded columns like IEDScout)
        String[] gooseColumns = {"GoCB Reference", "GoID", "DataSet", "AppID", "MAC", "ConfRev", "Estado"};
        gooseTableModel = new DefaultTableModel(gooseColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        gooseTable = new JTable(gooseTableModel);
        gooseTable.setRowHeight(18);
        gooseTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        gooseTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        gooseTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        gooseTable.getColumnModel().getColumn(3).setPreferredWidth(45);
        gooseTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        gooseTable.getColumnModel().getColumn(5).setPreferredWidth(50);
        gooseTable.getColumnModel().getColumn(6).setPreferredWidth(70);

        // Right-click context menu for GoCBs table
        JPopupMenu gcbPopupMenu = new JPopupMenu();
        JMenuItem menuPublicar = new JMenuItem("Publicar este GoCB");
        menuPublicar.addActionListener(e -> {
            int row = gooseTable.getSelectedRow();
            if (row >= 0 && row < sclGoCBs.size()) {
                publishSelectedGoCB(row);
            }
        });
        gcbPopupMenu.add(menuPublicar);

        JMenuItem menuDetenerUno = new JMenuItem("Detener este GoCB");
        menuDetenerUno.addActionListener(e -> {
            int row = gooseTable.getSelectedRow();
            if (row >= 0 && activePublishers.containsKey(row)) {
                GoosePublisher pub = activePublishers.get(row);
                pub.stopPublishing();
                pub.close();
                activePublishers.remove(row);
                if (row < gooseTableModel.getRowCount()) {
                    gooseTableModel.setValueAt("Detenido", row, 6);
                }
                logGoose("GoCB #" + row + " detenido");
                if (activePublishers.isEmpty()) {
                    btnPublicarTodos.setText("Publicar Todos");
                    btnPublicarTodos.setBackground(new Color(0, 130, 60));
                    lblPublishStatus.setText("  Detenido");
                    lblPublishStatus.setForeground(Color.GRAY);
                }
            }
        });
        gcbPopupMenu.add(menuDetenerUno);

        gcbPopupMenu.addSeparator();

        // Per-GoCB state change submenu
        JMenu menuCambiarEstado = new JMenu("Cambiar Estado de este GoCB");
        gcbPopupMenu.add(menuCambiarEstado);

        // Dynamically populate state change options when popup is shown
        gcbPopupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                menuCambiarEstado.removeAll();
                int row = gooseTable.getSelectedRow();
                if (row < 0 || row >= sclGoCBs.size()) return;

                // Detect first data value type for this GoCB
                GoosePublisher pub = activePublishers.get(row);
                GoosePublisher.DataValue.Type firstType = null;

                if (pub != null && !pub.getDataValues().isEmpty()) {
                    firstType = pub.getDataValues().get(0).type;
                } else {
                    // Infer from DataSet
                    SclGoCB gcb = sclGoCBs.get(row);
                    List<GoosePublisher.DataValue> inferred = buildDataValuesFromDataSet(gcb);
                    if (!inferred.isEmpty()) firstType = inferred.get(0).type;
                }

                if (firstType == GoosePublisher.DataValue.Type.BOOLEAN) {
                    for (String[] opt : new String[][]{{"TRUE", "TRUE"}, {"FALSE", "FALSE"}}) {
                        JMenuItem mi = new JMenuItem(opt[0]);
                        String val = opt[1];
                        mi.addActionListener(ev -> { int r = gooseTable.getSelectedRow(); if (r >= 0) changeGoCBState(r, val); });
                        menuCambiarEstado.add(mi);
                    }
                } else if (firstType == GoosePublisher.DataValue.Type.DBPOS) {
                    for (String[] opt : new String[][]{
                            {"ON (Cerrado)", "ON"}, {"OFF (Abierto)", "OFF"},
                            {"INTERMEDIATE", "INTERMEDIATE"}, {"BAD", "BAD"}}) {
                        JMenuItem mi = new JMenuItem(opt[0]);
                        String val = opt[1];
                        mi.addActionListener(ev -> { int r = gooseTable.getSelectedRow(); if (r >= 0) changeGoCBState(r, val); });
                        menuCambiarEstado.add(mi);
                    }
                } else {
                    // Generic: ON/OFF as boolean-like
                    for (String[] opt : new String[][]{{"TRUE", "TRUE"}, {"FALSE", "FALSE"}}) {
                        JMenuItem mi = new JMenuItem(opt[0]);
                        String val = opt[1];
                        mi.addActionListener(ev -> { int r = gooseTable.getSelectedRow(); if (r >= 0) changeGoCBState(r, val); });
                        menuCambiarEstado.add(mi);
                    }
                }

                // Add per-member state change for all DataSet entries
                if (pub != null && pub.getDataValues().size() > 1) {
                    menuCambiarEstado.addSeparator();
                    for (int i = 0; i < pub.getDataValues().size(); i++) {
                        GoosePublisher.DataValue dv = pub.getDataValues().get(i);
                        JMenu memberMenu = new JMenu("[" + i + "] " + dv.name + " (" + dv.type + ")");
                        final int idx = i;
                        final int pubRow = row;
                        if (dv.type == GoosePublisher.DataValue.Type.BOOLEAN) {
                            JMenuItem miT = new JMenuItem("TRUE");
                            miT.addActionListener(ev -> setPublisherDataValue(pubRow, idx, true));
                            memberMenu.add(miT);
                            JMenuItem miF = new JMenuItem("FALSE");
                            miF.addActionListener(ev -> setPublisherDataValue(pubRow, idx, false));
                            memberMenu.add(miF);
                        } else if (dv.type == GoosePublisher.DataValue.Type.DBPOS) {
                            for (String[] opt : new String[][]{{"ON", "2"}, {"OFF", "1"}, {"INTERMEDIATE", "0"}, {"BAD", "3"}}) {
                                JMenuItem mi = new JMenuItem(opt[0]);
                                String val = opt[1];
                                mi.addActionListener(ev -> setPublisherDataValue(pubRow, idx, Integer.parseInt(val)));
                                memberMenu.add(mi);
                            }
                        } else if (dv.type == GoosePublisher.DataValue.Type.BITSTRING) {
                            JMenuItem miGood = new JMenuItem("GOOD (0x0000)");
                            miGood.addActionListener(ev -> setPublisherDataValue(pubRow, idx, 0));
                            memberMenu.add(miGood);
                            JMenuItem miBad = new JMenuItem("INVALID (0x0004)");
                            miBad.addActionListener(ev -> setPublisherDataValue(pubRow, idx, 4));
                            memberMenu.add(miBad);
                        } else {
                            JMenuItem miCustom = new JMenuItem("Valor personalizado...");
                            miCustom.addActionListener(ev -> {
                                String val = JOptionPane.showInputDialog(null, "Nuevo valor para " + dv.name + ":", String.valueOf(dv.value));
                                if (val != null) {
                                    try { setPublisherDataValue(pubRow, idx, Integer.parseInt(val)); }
                                    catch (NumberFormatException ex) { setPublisherDataValue(pubRow, idx, val); }
                                }
                            });
                            memberMenu.add(miCustom);
                        }
                        menuCambiarEstado.add(memberMenu);
                    }
                }
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        gcbPopupMenu.addSeparator();

        JMenuItem menuPublicarTodos = new JMenuItem("Publicar TODOS los GoCBs");
        menuPublicarTodos.addActionListener(e -> publishAllGoCBs());
        gcbPopupMenu.add(menuPublicarTodos);

        JMenuItem menuDetener = new JMenuItem("Detener TODOS");
        menuDetener.addActionListener(e -> stopAllPublishers());
        gcbPopupMenu.add(menuDetener);

        gooseTable.setComponentPopupMenu(gcbPopupMenu);
        gooseTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int row = gooseTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        gooseTable.setRowSelectionInterval(row, row);
                    }
                }
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int row = gooseTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        gooseTable.setRowSelectionInterval(row, row);
                    }
                }
            }
        });

        JPanel gcbPanel = new JPanel(new BorderLayout());
        JScrollPane gcbScroll = new JScrollPane(gooseTable);
        JButton btnLoadGcb = new JButton("Cargar GoCBs");
        btnLoadGcb.addActionListener(e -> refreshGooseControlBlocks());
        JButton btnClearGcb = new JButton("Limpiar");
        btnClearGcb.addActionListener(e -> {
            gooseTableModel.setRowCount(0);
            sclGoCBs.clear();
            logGoose("GoCBs limpiados");
        });

        // Boton para cargar SCL/CID manualmente
        JButton btnLoadScl = new JButton("Cargar SCL...");
        btnLoadScl.setToolTipText("Cargar archivo SCL/CID/ICD/SCD para obtener GoCBs");
        btnLoadScl.addActionListener(e -> cargarSclParaGoose());

        btnPublicarTodos = new JButton("Publicar Todos");
        btnPublicarTodos.setToolTipText("Publicar TODOS los GoCBs simultaneamente (modo IED)");
        btnPublicarTodos.setBackground(new Color(0, 130, 60));
        btnPublicarTodos.setForeground(Color.WHITE);
        btnPublicarTodos.addActionListener(e -> {
            if (!activePublishers.isEmpty()) {
                stopAllPublishers();
            } else {
                publishAllGoCBs();
            }
        });

        JPanel gcbBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        gcbBtnPanel.add(btnLoadScl);
        gcbBtnPanel.add(btnLoadGcb);
        gcbBtnPanel.add(btnPublicarTodos);
        gcbBtnPanel.add(btnClearGcb);
        gcbPanel.add(gcbScroll, BorderLayout.CENTER);
        gcbPanel.add(gcbBtnPanel, BorderLayout.SOUTH);
        gcbPanel.setBorder(BorderFactory.createTitledBorder("GoCBs del Modelo"));
        bottomPanel.add(gcbPanel);

        // Log
        gooseLogArea = new JTextArea();
        gooseLogArea.setEditable(false);
        gooseLogArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane logScroll = new JScrollPane(gooseLogArea);
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(logScroll, BorderLayout.CENTER);
        JPanel logBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JButton btnClear = new JButton("Limpiar");
        btnClear.addActionListener(e -> {
            gooseLogArea.setText("");
            gooseDataTableModel.setRowCount(0);
            // Reset publisher counters
            if (goosePublisher != null) {
                goosePublisher.resetCounters();
            }
            logGoose("Log y contadores limpiados");
        });
        JLabel lblCount = new JLabel("Msgs: 0");
        javax.swing.Timer countTimer = new javax.swing.Timer(500, e -> lblCount.setText("Msgs: " + gooseDataTableModel.getRowCount()));
        countTimer.start();
        logBtnPanel.add(btnClear);
        logBtnPanel.add(lblCount);
        logPanel.add(logBtnPanel, BorderLayout.SOUTH);
        logPanel.setBorder(BorderFactory.createTitledBorder("Log"));
        bottomPanel.add(logPanel);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadNetworkInterfaces() {
        gooseInterfaceCombo.removeAllItems();
        interfaceMap.clear();

        // Add special options first
        gooseInterfaceCombo.addItem("★ Loopback Interno (pruebas misma maquina)");
        gooseInterfaceCombo.addItem("★ GOOSE sobre UDP (WiFi/Hotspot)");

        try {
            List<PcapNetworkInterface> interfaces = GooseSubscriber.getNetworkInterfaces();
            for (PcapNetworkInterface nif : interfaces) {
                String desc = nif.getDescription() != null ? nif.getDescription() : nif.getName();

                // Get IP addresses for this interface
                StringBuilder ips = new StringBuilder();
                for (PcapAddress addr : nif.getAddresses()) {
                    if (addr.getAddress() != null) {
                        String ip = addr.getAddress().getHostAddress();
                        // Filter out IPv6 and loopback
                        if (!ip.contains(":") && !ip.startsWith("127.")) {
                            if (ips.length() > 0) ips.append(", ");
                            ips.append(ip);
                        }
                    }
                }

                String ipInfo = ips.length() > 0 ? " - " + ips.toString() : "";
                String name = desc + ipInfo;
                gooseInterfaceCombo.addItem(name);
                interfaceMap.put(name, nif);

                // Log para debug
                if (ips.length() > 0) {
                    logGoose("Interface: " + desc + " -> " + ips.toString());
                }
            }
            if (interfaces.isEmpty()) {
                gooseInterfaceCombo.addItem("No se encontraron interfaces (ejecutar como Admin?)");
            }
        } catch (Exception e) {
            gooseInterfaceCombo.addItem("Error: " + e.getMessage());
            log("Error cargando interfaces: " + e.getMessage());
        }
    }

    // Flag for internal loopback mode (for testing on same machine)
    private volatile boolean internalLoopbackEnabled = false;
    // Flag for UDP bridge mode (for WiFi/routed networks)
    private volatile boolean udpBridgeEnabled = false;

    private void toggleGooseCapture() {
        // Check if any subscriber is running
        boolean nativeRunning = nativeGooseSubscriber != null && nativeGooseSubscriber.isRunning();
        boolean pcapRunning = gooseSubscriber != null && gooseSubscriber.isRunning();
        boolean loopbackRunning = internalLoopbackEnabled;
        boolean udpRunning = gooseUdpBridge != null && gooseUdpBridge.isReceiving();

        if (nativeRunning || pcapRunning || loopbackRunning || udpRunning) {
            // Stop capture
            if (nativeRunning) {
                nativeGooseSubscriber.stop();
            }
            if (pcapRunning) {
                gooseSubscriber.stop();
            }
            if (udpRunning) {
                gooseUdpBridge.stopReceiving();
            }
            internalLoopbackEnabled = false;
            udpBridgeEnabled = false;
            btnGooseStartStop.setText("▶ Capturar");
            btnGooseStartStop.setBackground(new Color(46, 125, 50));
            lblGooseStatus.setText("Detenido");
            lblGooseStatus.setForeground(Color.GRAY);
        } else {
            // Start capture - prefer native library
            String selected = (String) gooseInterfaceCombo.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(this,
                    "Seleccione una interface de red valida.\n" +
                    "Si no aparecen interfaces, ejecute como Administrador.",
                    "GOOSE Capture", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean started = false;

            // Check for special options first (before checking nif)
            if (selected.contains("Loopback Interno") || selected.contains("Internal Loopback")) {
                // Internal loopback mode - doesn't use real network
                internalLoopbackEnabled = true;
                started = true;
                logGoose("=== MODO LOOPBACK INTERNO ACTIVADO ===");
                logGoose("Los mensajes GOOSE publicados se mostraran directamente");
                logGoose("(Sin pasar por la red - para pruebas en misma maquina)");
                lblGooseStatus.setText("Loopback Interno");

                // Start timer for loopback stats
                javax.swing.Timer loopbackTimer = new javax.swing.Timer(2000, ev -> {
                    if (internalLoopbackEnabled) {
                        int gooseRows = gooseDataTableModel.getRowCount();
                        lblGooseStatus.setText("Loopback: " + gooseRows + " msgs");
                    }
                });
                loopbackTimer.start();
            } else if (selected.contains("GOOSE sobre UDP") || selected.contains("UDP")) {
                // UDP Bridge mode - for WiFi and routed networks
                udpBridgeEnabled = true;
                logGoose("=== MODO GOOSE SOBRE UDP (WiFi/Hotspot) ===");
                logGoose("Puerto UDP: " + GooseUdpBridge.DEFAULT_PORT);
                logGoose("Este modo permite GOOSE sobre redes WiFi/IP");

                if (gooseUdpBridge.startReceiving()) {
                    started = true;
                    lblGooseStatus.setText("UDP Listener activo");
                    logGoose("*** ESCUCHANDO GOOSE-UDP en puerto " + GooseUdpBridge.DEFAULT_PORT + " ***");
                    logGoose("La otra maquina debe publicar en modo 'GOOSE sobre UDP'");

                    // Timer for UDP stats
                    javax.swing.Timer udpTimer = new javax.swing.Timer(2000, ev -> {
                        if (udpBridgeEnabled && gooseUdpBridge.isReceiving()) {
                            int received = gooseUdpBridge.getReceivedCount();
                            lblGooseStatus.setText("UDP: " + received + " msgs");
                        }
                    });
                    udpTimer.start();
                } else {
                    logGoose("ERROR: No se pudo iniciar receptor UDP");
                    logGoose("Verifique que el puerto " + GooseUdpBridge.DEFAULT_PORT + " no este en uso");
                    udpBridgeEnabled = false;
                }
            } else {
                // Real network capture - need valid interface
                PcapNetworkInterface nif = interfaceMap.get(selected);

                if (nif == null) {
                    logGoose("ERROR: Interface no encontrada en el mapa");
                    JOptionPane.showMessageDialog(this,
                        "Interface no valida. Recargue las interfaces.",
                        "GOOSE Capture", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Detectar si es WiFi y advertir
                String ifDesc = nif.getDescription() != null ? nif.getDescription().toLowerCase() : "";
                boolean isWifi = ifDesc.contains("wireless") || ifDesc.contains("wi-fi") ||
                                ifDesc.contains("wifi") || ifDesc.contains("802.11") ||
                                ifDesc.contains("wlan");
                if (isWifi) {
                    logGoose("*** ADVERTENCIA: Interface WiFi detectada ***");
                    logGoose("WiFi tiene limitaciones para captura GOOSE (multicast L2)");
                    logGoose("Se recomienda usar 'GOOSE sobre UDP' para WiFi");
                }

                String deviceName = nif.getName();
                logGoose("Interface seleccionada: " + nif.getDescription());
                logGoose("Device: " + deviceName);
                // Real network capture using pcap4j
                logGoose("=== INICIANDO CAPTURA pcap4j ===");
                logGoose("MACs: " + nif.getLinkLayerAddresses());
                logGoose("IPs: " + nif.getAddresses());

                try {
                    if (gooseSubscriber.start(nif)) {
                        started = true;
                        lblGooseStatus.setText("Capturando (pcap4j)");
                        logGoose("*** CAPTURA INICIADA - Esperando tramas GOOSE ***");
                        logGoose("Filtro: multicast/EtherType 0x88B8");
                        if (isWifi) {
                            logGoose("NOTA: En WiFi, algunos paquetes multicast pueden no capturarse");
                        }

                        // Timer para mostrar estadisticas de captura
                        javax.swing.Timer captureTimer = new javax.swing.Timer(2000, ev -> {
                            if (gooseSubscriber != null && gooseSubscriber.isRunning()) {
                                int pkts = gooseSubscriber.getPacketCount();
                                int goose = gooseSubscriber.getGooseCount();
                                lblGooseStatus.setText("Capturando: " + pkts + " pkts, " + goose + " GOOSE");
                            }
                        });
                        captureTimer.start();
                    } else {
                        logGoose("ERROR: No se pudo iniciar captura pcap4j");
                        logGoose("Posibles causas:");
                        logGoose("  - Ejecutar como Administrador");
                        logGoose("  - Verificar que Npcap este instalado con WinPcap compatibility");
                        logGoose("  - La interfaz WiFi puede no soportar modo promiscuo");
                        if (isWifi) {
                            logGoose("  - WiFi NO soporta captura GOOSE - use Ethernet");
                        }
                    }
                } catch (Exception e) {
                    logGoose("EXCEPCION iniciando captura: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            if (started) {
                btnGooseStartStop.setText("⬛ Detener");
                btnGooseStartStop.setBackground(new Color(200, 50, 50));
                lblGooseStatus.setForeground(new Color(0, 150, 0));
            } else if (!internalLoopbackEnabled && !udpBridgeEnabled) {
                // Only show error if we tried a real network capture
                JOptionPane.showMessageDialog(this,
                    "Error iniciando captura GOOSE.\n" +
                    "Verifique:\n" +
                    "- Ejecutar como Administrador\n" +
                    "- Npcap instalado con 'WinPcap API-compatible Mode'\n" +
                    "- Para WiFi, use 'GOOSE sobre UDP'",
                    "GOOSE Capture", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Publish a single GoCB by index. If multi-publishers are active, only adds/replaces this one.
     */
    private void publishSelectedGoCB(int gcbIndex) {
        if (gcbIndex < 0 || gcbIndex >= sclGoCBs.size()) {
            logGoose("Indice de GoCB invalido: " + gcbIndex);
            return;
        }

        // If this specific GoCB is already publishing, stop only that one
        if (activePublishers.containsKey(gcbIndex)) {
            GoosePublisher existing = activePublishers.get(gcbIndex);
            existing.stopPublishing();
            existing.close();
            activePublishers.remove(gcbIndex);
            logGoose("Publisher #" + gcbIndex + " detenido para reconfigurar");
        }

        String selected = (String) gooseInterfaceCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                "Seleccione una interface de red primero.",
                "Publicar GoCB", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isLoopback = selected.contains("Loopback Interno");
        boolean isUdp = selected.contains("GOOSE sobre UDP") || selected.contains("UDP");
        PcapNetworkInterface nif = null;
        if (!isLoopback && !isUdp) {
            if (!interfaceMap.containsKey(selected)) {
                JOptionPane.showMessageDialog(this,
                    "Seleccione una interface de red valida.",
                    "Publicar GoCB", JOptionPane.WARNING_MESSAGE);
                return;
            }
            nif = interfaceMap.get(selected);
        }

        SclGoCB gcb = sclGoCBs.get(gcbIndex);
        GoosePublisher pub = createPublisherForGoCB(gcb, gcbIndex);

        boolean initOk = isLoopback || isUdp || pub.init(nif);
        if (initOk) {
            pub.startPublishing();
            activePublishers.put(gcbIndex, pub);

            logGoose("=== GoCB #" + gcbIndex + " publicando: " + gcb.toString() + " ===");
            logGoose("  goID: " + pub.getGoId() + " | AppID: " + String.format("0x%04X", pub.getAppId()));

            // Update table status
            if (gcbIndex < gooseTableModel.getRowCount()) {
                gooseTableModel.setValueAt("Publicando", gcbIndex, 6);
            }

            btnGoosePublish.setText("Detener Publicacion");
            btnGoosePublish.setBackground(new Color(200, 50, 50));
            String modeStr = isLoopback ? "Loopback" : (isUdp ? "UDP" : "L2");
            lblPublishStatus.setText("  " + activePublishers.size() + " GoCB(s) publicando (" + modeStr + ")");
            lblPublishStatus.setForeground(new Color(0, 150, 0));
        } else {
            logGoose("ERROR: No se pudo inicializar publisher para " + gcb.cbName);
            pub.close();
        }
    }

    /**
     * Creates and configures a GoosePublisher for a specific GoCB from SCL data.
     */
    private GoosePublisher createPublisherForGoCB(SclGoCB gcb, int gcbIndex) {
        GoosePublisher pub = new GoosePublisher();

        String gocbRef = gcb.ldInst + "/LLN0$GO$" + gcb.cbName;
        pub.setGocbRef(gocbRef);
        pub.setGoId(gcb.goID != null && !gcb.goID.isEmpty() ? gcb.goID : gcb.cbName);
        pub.setDatSet(gcb.ldInst + "/LLN0$" + (gcb.datSet != null ? gcb.datSet : "DataSet1"));

        // Parse AppID
        if (gcb.appID != null && !gcb.appID.isEmpty()) {
            try {
                pub.setAppId(Integer.parseInt(gcb.appID, 16));
            } catch (NumberFormatException e) {
                try { pub.setAppId(Integer.parseInt(gcb.appID)); }
                catch (NumberFormatException e2) { pub.setAppId(0x0001 + gcbIndex); }
            }
        } else {
            pub.setAppId(0x0001 + gcbIndex);
        }

        if (gcb.confRev > 0) pub.setConfRev(gcb.confRev);

        // Destination MAC
        if (gcb.macAddress != null && !gcb.macAddress.isEmpty()) {
            try {
                pub.setDstMac(gcb.macAddress.replace(":", "-").replace(".", "-"));
            } catch (Exception e) {
                logGoose("  MAC invalido para " + gcb.cbName + ": " + gcb.macAddress);
            }
        }

        // Build data values from DataSet
        List<GoosePublisher.DataValue> dataValues = buildDataValuesFromDataSet(gcb);
        if (!dataValues.isEmpty()) {
            pub.setDataValues(dataValues);
        }

        // Set listeners
        pub.setLogListener(msg -> logGoose("[GoCB#" + gcbIndex + "] " + msg));
        pub.setPublishListener(pubMsg -> {
            GooseSubscriber.GooseMessage tableMsg = new GooseSubscriber.GooseMessage();
            tableMsg.timestamp = pubMsg.timestamp;
            tableMsg.srcMac = "LOCAL";
            tableMsg.dstMac = "01:0C:CD:01:00:01";
            tableMsg.gocbRef = pubMsg.gocbRef;
            tableMsg.goId = pubMsg.goId;
            tableMsg.datSet = pubMsg.datSet;
            tableMsg.appId = pubMsg.appId;
            tableMsg.stNum = pubMsg.stNum;
            tableMsg.sqNum = pubMsg.sqNum;
            tableMsg.confRev = gcb.confRev;
            tableMsg.numDataSetEntries = pubMsg.dataValues != null ? pubMsg.dataValues.size() : 0;
            if (pubMsg.dataValues != null) {
                int idx = 0;
                for (GoosePublisher.DataValue dv : pubMsg.dataValues) {
                    tableMsg.dataEntries.add(new GooseSubscriber.DataEntry(idx++, dv.type.name(), dv.value));
                }
            }
            handleGooseMessage(tableMsg);
        });

        return pub;
    }

    /**
     * Change the state of a specific GoCB publisher's data values.
     */
    private void changeGoCBState(int gcbIndex, String state) {
        GoosePublisher pub = activePublishers.get(gcbIndex);
        if (pub == null || !pub.isPublishing()) {
            logGoose("GoCB #" + gcbIndex + " no esta publicando");
            return;
        }

        // Determine value based on state and first data type
        List<GoosePublisher.DataValue> values = pub.getDataValues();
        if (values.isEmpty()) return;

        GoosePublisher.DataValue firstVal = values.get(0);
        switch (firstVal.type) {
            case BOOLEAN:
                pub.setDataValue(0, "ON".equalsIgnoreCase(state) || "TRUE".equalsIgnoreCase(state));
                break;
            case DBPOS:
                int dbpos;
                switch (state.toUpperCase()) {
                    case "ON": dbpos = 2; break;
                    case "OFF": dbpos = 1; break;
                    case "INTERMEDIATE": dbpos = 0; break;
                    case "BAD": dbpos = 3; break;
                    default: dbpos = 1;
                }
                pub.setDataValue(0, dbpos);
                break;
            case INTEGER: case UNSIGNED:
                try { pub.setDataValue(0, Integer.parseInt(state)); }
                catch (NumberFormatException e) { pub.setDataValue(0, "ON".equalsIgnoreCase(state) ? 1 : 0); }
                break;
            default:
                pub.setDataValue(0, "ON".equalsIgnoreCase(state));
        }

        pub.publishStateChange();
        SclGoCB gcb = gcbIndex < sclGoCBs.size() ? sclGoCBs.get(gcbIndex) : null;
        String gcbName = gcb != null ? gcb.toString() : "#" + gcbIndex;
        logGoose("GoCB " + gcbName + " -> " + state.toUpperCase() + " (stNum=" + pub.getStNum() + ")");

        // Sincronizar cambio de vuelta al Data Model del servidor
        syncPublisherToServerModel(gcbIndex, 0);
    }

    /**
     * Set a specific data value in an active publisher and trigger state change.
     */
    private void setPublisherDataValue(int gcbIndex, int dataIndex, Object value) {
        GoosePublisher pub = activePublishers.get(gcbIndex);
        if (pub == null || !pub.isPublishing()) {
            logGoose("GoCB #" + gcbIndex + " no esta publicando");
            return;
        }
        pub.setDataValue(dataIndex, value);
        pub.publishStateChange();

        GoosePublisher.DataValue dv = pub.getDataValues().get(dataIndex);
        logGoose("GoCB#" + gcbIndex + " [" + dataIndex + "] " + dv.name + " = " + value + " (stNum=" + pub.getStNum() + ")");

        // Sincronizar cambio de vuelta al Data Model del servidor
        syncPublisherToServerModel(gcbIndex, dataIndex);
    }

    /**
     * Sincroniza un valor cambiado en un GOOSE publisher de vuelta al Data Model del servidor.
     * Busca el miembro FCDA correspondiente en el DataSet y actualiza el BDA en el modelo.
     */
    private void syncPublisherToServerModel(int gcbIndex, int dataIndex) {
        if (server == null || !isServerRunning) return;
        if (gcbIndex >= sclGoCBs.size()) return;

        SclGoCB gcb = sclGoCBs.get(gcbIndex);
        SclDataSet ds = findDataSetForGoCB(gcb);
        if (ds == null || dataIndex >= ds.members.size()) return;

        GoosePublisher pub = activePublishers.get(gcbIndex);
        if (pub == null) return;
        List<GoosePublisher.DataValue> pubValues = pub.getDataValues();
        if (dataIndex >= pubValues.size()) return;

        String member = ds.members.get(dataIndex);
        String modelRef = buildModelRefFromFCDA(member);
        if (modelRef == null) return;

        // Convertir el valor del publisher a string para setDataValue
        GoosePublisher.DataValue dv = pubValues.get(dataIndex);
        String strValue = convertPublisherValueToString(dv);

        boolean success = server.setDataValue(modelRef, strValue);
        if (success) {
            log("GOOSE -> Modelo: " + formatReference(modelRef) + " = " + strValue);
            updateSingleNodeInTree(modelRef);
            updateServerMonitorValues();
        }
    }

    /**
     * Convierte un valor de DataValue del publisher a String para setDataValue del servidor.
     */
    private String convertPublisherValueToString(GoosePublisher.DataValue dv) {
        if (dv.value == null) return "0";

        switch (dv.type) {
            case BOOLEAN:
                return Boolean.TRUE.equals(dv.value) ? "true" : "false";
            case DBPOS:
                int dbpos = ((Number) dv.value).intValue();
                switch (dbpos) {
                    case 0: return "INTERMEDIATE_STATE";
                    case 1: return "OFF";
                    case 2: return "ON";
                    case 3: return "BAD_STATE";
                    default: return String.valueOf(dbpos);
                }
            case INTEGER: case UNSIGNED: case BITSTRING:
                return String.valueOf(dv.value);
            case FLOAT:
                return String.valueOf(dv.value);
            default:
                return String.valueOf(dv.value);
        }
    }

    /**
     * Publish ALL GoCBs simultaneously (IED simulation mode).
     * Creates one GoosePublisher per GoCB and starts all of them.
     */
    private void publishAllGoCBs() {
        if (sclGoCBs.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No hay GoCBs cargados.\nCargue un archivo SCL primero.",
                "Publicar Todos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Stop any existing publishers first
        stopAllPublishers();

        String selected = (String) gooseInterfaceCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                "Seleccione una interface de red primero.",
                "Publicar Todos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isLoopback = selected.contains("Loopback Interno");
        boolean isUdp = selected.contains("GOOSE sobre UDP") || selected.contains("UDP");
        PcapNetworkInterface nif = null;

        if (!isLoopback && !isUdp) {
            if (!interfaceMap.containsKey(selected)) {
                JOptionPane.showMessageDialog(this,
                    "Seleccione una interface de red valida.",
                    "Publicar Todos", JOptionPane.WARNING_MESSAGE);
                return;
            }
            nif = interfaceMap.get(selected);
        }

        logGoose("=== PUBLICANDO TODOS LOS GoCBs (" + sclGoCBs.size() + ") ===");
        int successCount = 0;

        for (int i = 0; i < sclGoCBs.size(); i++) {
            SclGoCB gcb = sclGoCBs.get(i);
            GoosePublisher pub = createPublisherForGoCB(gcb, i);

            boolean initOk = isLoopback || isUdp || pub.init(nif);

            if (initOk) {
                pub.startPublishing();
                activePublishers.put(i, pub);
                successCount++;
                logGoose("  [" + i + "] " + pub.getGocbRef() + " -> AppID=" + String.format("0x%04X", pub.getAppId())
                    + ", DataValues=" + pub.getDataValues().size());
            } else {
                logGoose("  ERROR: No se pudo inicializar publisher para " + gcb.cbName);
                pub.close();
            }
        }

        if (successCount > 0) {
            logGoose("=== " + successCount + "/" + sclGoCBs.size() + " GoCBs publicando ===");
            btnGoosePublish.setText("Detener Publicacion");
            btnGoosePublish.setBackground(new Color(200, 50, 50));
            btnPublicarTodos.setText("Detener Todos");
            btnPublicarTodos.setBackground(new Color(200, 50, 50));
            String modeStr = isLoopback ? "Loopback" : (isUdp ? "UDP" : "L2");
            lblPublishStatus.setText("  " + successCount + " GoCBs publicando (" + modeStr + ")");
            lblPublishStatus.setForeground(new Color(0, 150, 0));

            // Update GoCBs table "Estado" column
            for (int i = 0; i < gooseTableModel.getRowCount() && i < sclGoCBs.size(); i++) {
                if (activePublishers.containsKey(i)) {
                    gooseTableModel.setValueAt("Publicando", i, 6);
                }
            }
        } else {
            logGoose("ERROR: No se pudo iniciar ninguna publicacion");
        }
    }

    /**
     * Stop all active publishers (multi-GoCB and single)
     */
    private void stopAllPublishers() {
        // Stop multi-GoCB publishers
        for (Map.Entry<Integer, GoosePublisher> entry : activePublishers.entrySet()) {
            try {
                entry.getValue().stopPublishing();
                entry.getValue().close();
            } catch (Exception e) {
                logGoose("Error deteniendo publisher #" + entry.getKey() + ": " + e.getMessage());
            }
        }
        int count = activePublishers.size();
        activePublishers.clear();

        // Also stop single publisher if running
        if (goosePublisher.isPublishing()) {
            goosePublisher.stopPublishing();
            goosePublisher.resetCounters();
            count++;
        }

        if (count > 0) {
            btnGoosePublish.setText("Iniciar Publicacion");
            btnGoosePublish.setBackground(new Color(0, 100, 180));
            btnGooseStateChange.setEnabled(false);
            btnPublicarTodos.setText("Publicar Todos");
            btnPublicarTodos.setBackground(new Color(0, 130, 60));
            lblPublishStatus.setText("  Detenido");
            lblPublishStatus.setForeground(Color.GRAY);
            logGoose(count + " publisher(s) detenidos");

            // Update GoCBs table "Estado" column
            for (int i = 0; i < gooseTableModel.getRowCount(); i++) {
                gooseTableModel.setValueAt("Detenido", i, 6);
            }
        }
    }

    /**
     * Build DataValue list from the DataSet associated with a GoCB.
     * Infers data types from FCDA member names (daName).
     */
    private List<GoosePublisher.DataValue> buildDataValuesFromDataSet(SclGoCB gcb) {
        List<GoosePublisher.DataValue> values = new ArrayList<>();
        if (gcb.datSet == null) return values;

        // Find matching DataSet
        SclDataSet foundDs = null;
        for (SclDataSet ds : sclDataSets) {
            if (ds.name != null && ds.name.equals(gcb.datSet)) {
                if (ds.ldInst == null || ds.ldInst.equals(gcb.ldInst)) {
                    foundDs = ds;
                    break;
                }
            }
        }

        if (foundDs == null || foundDs.members.isEmpty()) {
            // Fallback: default data values
            return values;
        }

        for (String member : foundDs.members) {
            // Member format: "ldInst/PrefixLNClassInst.doName.daName [FC]"
            String name = member;
            // Remove [FC] suffix for clean name
            int bracketIdx = member.lastIndexOf('[');
            if (bracketIdx > 0) {
                name = member.substring(0, bracketIdx).trim();
            }

            // Infer type from daName/doName
            GoosePublisher.DataValue.Type type = inferDataType(member);
            Object defaultValue = getDefaultValueForType(type);
            values.add(new GoosePublisher.DataValue(name, type, defaultValue));
        }

        return values;
    }

    /**
     * Infer IEC 61850 data type from FCDA member name.
     */
    private GoosePublisher.DataValue.Type inferDataType(String memberName) {
        String lower = memberName.toLowerCase();

        // Quality attributes
        if (lower.contains(".q ") || lower.contains(".q[") || lower.endsWith(".q")) {
            return GoosePublisher.DataValue.Type.BITSTRING;
        }
        // Timestamp
        if (lower.contains(".t ") || lower.contains(".t[") || lower.endsWith(".t")) {
            return GoosePublisher.DataValue.Type.UNSIGNED;
        }
        // Boolean types: general, stVal for SPS, etc.
        if (lower.contains(".general") || lower.contains(".stval") ||
            lower.contains(".op ") || lower.contains(".op[")) {
            return GoosePublisher.DataValue.Type.BOOLEAN;
        }
        // Double bit position (SPC, DPC)
        if (lower.contains("pos.") && (lower.contains(".stval") || lower.contains(".ctlval"))) {
            return GoosePublisher.DataValue.Type.DBPOS;
        }
        // Measured values (MV, CMV)
        if (lower.contains(".mag") || lower.contains(".instmag") || lower.contains(".cval")) {
            return GoosePublisher.DataValue.Type.FLOAT;
        }
        // Counter/integer values
        if (lower.contains(".ctlval") || lower.contains(".setval") || lower.contains(".actval")) {
            return GoosePublisher.DataValue.Type.INTEGER;
        }
        // Default to BOOLEAN for unknown (most common in protection schemes)
        return GoosePublisher.DataValue.Type.BOOLEAN;
    }

    /**
     * Get a sensible default value for a given data type.
     */
    private Object getDefaultValueForType(GoosePublisher.DataValue.Type type) {
        switch (type) {
            case BOOLEAN: return false;
            case INTEGER: return 0;
            case UNSIGNED: return (long)(System.currentTimeMillis() / 1000);
            case FLOAT: return 0.0f;
            case BITSTRING: return 0;
            case DBPOS: return 1; // OFF
            case VISIBLE_STRING: return "";
            default: return false;
        }
    }

    private void toggleGoosePublishing() {
        if (goosePublisher.isPublishing() || !activePublishers.isEmpty()) {
            // Stop all publishing
            stopAllPublishers();
        } else {
            // Start publishing
            String selected = (String) gooseInterfaceCombo.getSelectedItem();
            if (selected == null) {
                JOptionPane.showMessageDialog(this,
                    "Seleccione una opcion primero.",
                    "GOOSE Publisher", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean publishStarted = false;

            // Check for special modes
            if (selected.contains("Loopback Interno")) {
                // Loopback mode - no real network, just simulate
                internalLoopbackEnabled = true;
                publishStarted = true;
                logGoose("Publisher iniciado en modo LOOPBACK INTERNO");
                logGoose("Los mensajes se mostraran localmente sin red");
            } else if (selected.contains("GOOSE sobre UDP") || selected.contains("UDP")) {
                // UDP mode - initialize UDP sender
                udpBridgeEnabled = true;
                if (gooseUdpBridge.initSender(null)) { // null = broadcast
                    publishStarted = true;
                    logGoose("Publisher iniciado en modo UDP BROADCAST");
                    logGoose("Puerto: " + GooseUdpBridge.DEFAULT_PORT);
                } else {
                    logGoose("ERROR: No se pudo inicializar UDP sender");
                    udpBridgeEnabled = false;
                }
            } else {
                // Real network - need valid interface
                if (!interfaceMap.containsKey(selected)) {
                    JOptionPane.showMessageDialog(this,
                        "Seleccione una interface de red valida.",
                        "GOOSE Publisher", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                PcapNetworkInterface nif = interfaceMap.get(selected);
                if (goosePublisher.init(nif)) {
                    publishStarted = true;
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Error inicializando GOOSE Publisher.\nVerifique que la interface es correcta y ejecute como Admin.",
                        "GOOSE Publisher", JOptionPane.ERROR_MESSAGE);
                }
            }

            if (publishStarted) {
                // Set initial state from combo
                updatePublisherState();
                goosePublisher.startPublishing();
                btnGoosePublish.setText("Detener Publicacion");
                btnGoosePublish.setBackground(new Color(200, 50, 50));
                btnGooseStateChange.setEnabled(true);

                String modeStr = internalLoopbackEnabled ? "Loopback" : (udpBridgeEnabled ? "UDP" : "L2");
                lblPublishStatus.setText("  Publicando " + modeStr + " (stNum=" + goosePublisher.getStNum() + ")");
                lblPublishStatus.setForeground(new Color(0, 150, 0));

                // Update status periodically
                javax.swing.Timer pubTimer = new javax.swing.Timer(500, e -> {
                    if (goosePublisher.isPublishing()) {
                        lblPublishStatus.setText("  " + modeStr + " stNum=" + goosePublisher.getStNum() + ", sqNum=" + goosePublisher.getSqNum());
                    }
                });
                pubTimer.start();
            }
        }
    }

    private void updatePublisherState() {
        int selectedIndex = cbGooseState.getSelectedIndex();
        int dbposValue;
        switch (selectedIndex) {
            case 0: dbposValue = 1; break;  // OFF
            case 1: dbposValue = 2; break;  // ON
            case 2: dbposValue = 0; break;  // INTERMEDIATE
            case 3: dbposValue = 3; break;  // BAD
            default: dbposValue = 1;
        }
        goosePublisher.setDataValue(0, dbposValue);
    }

    private void publishGooseStateChange() {
        boolean anyActive = goosePublisher.isPublishing() || !activePublishers.isEmpty();
        if (!anyActive) {
            logGoose("Publisher no esta activo");
            return;
        }

        // Update state from combo
        updatePublisherState();

        String stateName = (String) cbGooseState.getSelectedItem();

        // Publish state change on single publisher
        if (goosePublisher.isPublishing()) {
            goosePublisher.publishStateChange();
            logGoose("Cambio de estado publicado: " + stateName + " (stNum=" + goosePublisher.getStNum() + ")");
        }

        // Publish state change on ALL multi-publishers
        for (Map.Entry<Integer, GoosePublisher> entry : activePublishers.entrySet()) {
            GoosePublisher pub = entry.getValue();
            if (pub.isPublishing()) {
                // Set first boolean data value to match the state change
                int selectedIndex = cbGooseState.getSelectedIndex();
                boolean boolValue = (selectedIndex == 1); // ON=true, others=false
                pub.setDataValue(0, boolValue);
                pub.publishStateChange();
            }
        }

        if (!activePublishers.isEmpty()) {
            logGoose("Cambio de estado en " + activePublishers.size() + " GoCBs: " + stateName);
        }

        // If UDP bridge is enabled, also send over UDP
        if (udpBridgeEnabled) {
            if (gooseUdpBridge.send(goosePublisher)) {
                logGoose("[UDP] Mensaje enviado por broadcast UDP");
            }
        }

        // Update status
        String stInfo = goosePublisher.isPublishing() ?
            "stNum=" + goosePublisher.getStNum() :
            activePublishers.size() + " GoCBs";
        lblPublishStatus.setText("  CAMBIO! " + stInfo);
        lblPublishStatus.setForeground(new Color(200, 100, 0));

        // Reset status color after a moment
        javax.swing.Timer resetTimer = new javax.swing.Timer(2000, e -> {
            if (goosePublisher.isPublishing() || !activePublishers.isEmpty()) {
                lblPublishStatus.setForeground(new Color(0, 150, 0));
            }
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    private void handleGooseMessage(GooseSubscriber.GooseMessage msg) {
        SwingUtilities.invokeLater(() -> {
            // Build data string
            StringBuilder dataStr = new StringBuilder();
            for (GooseSubscriber.DataEntry entry : msg.dataEntries) {
                if (dataStr.length() > 0) dataStr.append(", ");
                dataStr.append(entry.value);
            }

            // Add to table (limit to 1000 rows)
            if (gooseDataTableModel.getRowCount() > 1000) {
                gooseDataTableModel.removeRow(gooseDataTableModel.getRowCount() - 1);
            }

            // New columns: Tiempo, AppID, st#, sq#, gocbRef, Datos
            gooseDataTableModel.insertRow(0, new Object[]{
                msg.timestamp,
                String.format("%04X", msg.appId),
                msg.stNum,
                msg.sqNum,
                msg.gocbRef != null ? msg.gocbRef : (msg.goId != null ? msg.goId : ""),
                dataStr.toString()
            });

            // Store message
            gooseMessages.put(msg.appId, msg);
        });
    }

    /**
     * Handle GOOSE messages from native libiec61850 subscriber
     */
    private void handleNativeGooseMessage(NativeGooseSubscriber.NativeGooseMessage msg) {
        SwingUtilities.invokeLater(() -> {
            // Build data string from native data values
            StringBuilder dataStr = new StringBuilder();
            for (NativeGooseSubscriber.DataValue dv : msg.dataValues) {
                if (dataStr.length() > 0) dataStr.append(", ");
                dataStr.append(dv.value);
            }

            // Add to table (limit to 1000 rows)
            if (gooseDataTableModel.getRowCount() > 1000) {
                gooseDataTableModel.removeRow(gooseDataTableModel.getRowCount() - 1);
            }

            // Columns: Tiempo, AppID, st#, sq#, gocbRef, Datos
            gooseDataTableModel.insertRow(0, new Object[]{
                msg.timestamp,
                String.format("%04X", msg.appId),
                msg.stNum,
                msg.sqNum,
                msg.goCbRef != null ? msg.goCbRef : (msg.goId != null ? msg.goId : ""),
                dataStr.toString()
            });
        });
    }

    private void refreshGooseControlBlocks() {
        gooseTableModel.setRowCount(0);

        // Usar GoCBs parseados del archivo SCL (como IEDScout)
        if (!sclGoCBs.isEmpty()) {
            for (SclGoCB gcb : sclGoCBs) {
                String ref = gcb.ldInst + "/" + gcb.lnClass + "." + gcb.cbName;
                String goId = gcb.goID != null ? gcb.goID : gcb.cbName;
                String datSet = gcb.datSet != null ? gcb.datSet : "";
                String appId = gcb.appID != null ? gcb.appID : "";
                String mac = gcb.macAddress != null ? gcb.macAddress : "";
                String confRev = String.valueOf(gcb.confRev);
                String estado = "Disponible";
                gooseTableModel.addRow(new Object[]{ref, goId, datSet, appId, mac, confRev, estado});
            }
            log("GoCBs del SCL: " + gooseTableModel.getRowCount());
            logGoose("Cargados " + gooseTableModel.getRowCount() + " GoCBs del SCL");
            return;
        }

        // Fallback: Si no hay SCL cargado, intentar obtener del modelo MMS
        ServerModel model = null;
        if (currentMode == AppMode.SERVER && server != null) {
            model = server.getServerModel();
        } else if (currentMode == AppMode.CLIENT && client != null && isConnected) {
            model = client.getServerModel();
        }

        if (model == null) {
            log("No hay modelo cargado. Cargue un archivo SCL para ver los GoCBs.");
            return;
        }

        try {
            // Buscar GoCBs en LLN0 de cada LD
            // Los GoCBs tienen atributos caracteristicos: GoEna, GoID, DatSet, ConfRev, NdsCom
            for (ModelNode ld : model.getChildren()) {
                String ldName = ld.getName();

                for (ModelNode ln : ld.getChildren()) {
                    if (ln.getChildren() == null) continue;
                    String lnName = ln.getName();

                    // Debug: mostrar nodos de LLN0
                    if (lnName.equals("LLN0")) {
                        log("Buscando GoCBs en " + ldName + "/" + lnName + " (" +
                            (ln.getChildren() != null ? ln.getChildren().size() : 0) + " nodos)");
                    }

                    for (ModelNode node : ln.getChildren()) {
                        String nodeName = node.getName();
                        String nodeNameUpper = nodeName.toUpperCase();

                        // Excluir SGCB, Report CBs y nodos de datos comunes
                        if (nodeNameUpper.contains("SGCB") ||
                            node instanceof Urcb ||
                            node instanceof Brcb ||
                            nodeNameUpper.equals("MOD") ||
                            nodeNameUpper.equals("BEH") ||
                            nodeNameUpper.equals("HEALTH") ||
                            nodeNameUpper.equals("NAMPLT")) {
                            continue;
                        }

                        // Detectar GoCB por sus atributos caracteristicos
                        boolean hasGoEna = false;
                        boolean hasGoID = false;
                        boolean hasDatSet = false;
                        String datSet = "";
                        String goID = "";

                        if (node.getChildren() != null) {
                            for (ModelNode attr : node.getChildren()) {
                                String attrName = attr.getName().toLowerCase();
                                if (attrName.equals("goena")) hasGoEna = true;
                                if (attrName.equals("goid")) {
                                    hasGoID = true;
                                    if (attr instanceof BasicDataAttribute) {
                                        goID = ((BasicDataAttribute) attr).getValueString();
                                        if (goID == null) goID = "";
                                    }
                                }
                                if (attrName.equals("datset")) {
                                    hasDatSet = true;
                                    if (attr instanceof BasicDataAttribute) {
                                        datSet = ((BasicDataAttribute) attr).getValueString();
                                        if (datSet == null) datSet = "";
                                    }
                                }
                            }
                        }

                        // Es GoCB si tiene GoEna o (GoID y DatSet)
                        boolean isGoCB = hasGoEna || (hasGoID && hasDatSet);

                        // Tambien por patrones de nombre conocidos
                        if (!isGoCB) {
                            isGoCB = nodeNameUpper.startsWith("GOCB") ||
                                    nodeNameUpper.startsWith("GCB") ||
                                    (nodeNameUpper.contains("CONTROL") && nodeNameUpper.contains("DATASET"));
                        }

                        if (isGoCB) {
                            String ref = lnName + "." + nodeName;
                            String displayDatSet = !datSet.isEmpty() ? datSet : (!goID.isEmpty() ? goID : nodeName);
                            gooseTableModel.addRow(new Object[]{ldName + "/" + ref, goID, displayDatSet, "", "", "", "MMS"});
                            log("  GoCB encontrado: " + ldName + "/" + ref);
                        }
                    }
                }
            }

            if (gooseTableModel.getRowCount() == 0) {
                // Debug: listar todos los nodos de LLN0 del primer LD
                log("No se encontraron GoCBs. Listando nodos de LLN0:");
                for (ModelNode ld : model.getChildren()) {
                    for (ModelNode ln : ld.getChildren()) {
                        if (ln.getName().equals("LLN0") && ln.getChildren() != null) {
                            for (ModelNode node : ln.getChildren()) {
                                String type = node.getClass().getSimpleName();
                                log("  - " + node.getName() + " [" + type + "]");
                            }
                            break;
                        }
                    }
                    break; // Solo primer LD
                }
            } else {
                log("GoCBs encontrados (MMS): " + gooseTableModel.getRowCount());
            }
        } catch (Exception e) {
            log("Error obteniendo GoCBs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Abre dialogo para cargar archivo SCL/CID/ICD/SCD y extraer GoCBs
     * Funciona tanto en modo Cliente como Servidor
     */
    private void cargarSclParaGoose() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Cargar archivo SCL para obtener GoCBs");
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".cid") || name.endsWith(".icd") ||
                       name.endsWith(".scd") || name.endsWith(".scl");
            }
            public String getDescription() {
                return "Archivos SCL (*.cid, *.icd, *.scd, *.scl)";
            }
        });

        // Intentar usar el ultimo directorio
        if (loadedSclFile != null && loadedSclFile.getParentFile() != null) {
            fc.setCurrentDirectory(loadedSclFile.getParentFile());
        }

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            log("Cargando SCL para GoCBs: " + file.getName());
            logGoose("Cargando: " + file.getName());

            try {
                // Detect multiple IEDs in the file (SCD files)
                int iedIndex = detectAndSelectIED(file);
                if (iedIndex == -2) return; // User cancelled

                if (iedIndex >= 0) {
                    parseGoCBsFromScl(file, iedIndex);
                } else {
                    parseGoCBsFromScl(file);
                }
                loadedSclFile = file;
                refreshGooseControlBlocks();
                log("GoCBs cargados: " + sclGoCBs.size());
                logGoose("GoCBs encontrados: " + sclGoCBs.size());
            } catch (Exception e) {
                log("Error cargando SCL: " + e.getMessage());
                logGoose("ERROR: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Error cargando archivo SCL:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Detecta si un archivo SCL contiene multiples IEDs y muestra dialogo de seleccion.
     * @return indice del IED seleccionado (>=0), -1 si solo hay un IED, -2 si usuario cancelo
     */
    private int detectAndSelectIED(File sclFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(sclFile);

            NodeList ieds = doc.getElementsByTagName("IED");
            if (ieds.getLength() <= 1) {
                return -1; // Solo un IED o ninguno, no requiere seleccion
            }

            // Construir lista de nombres de IED
            List<String> iedNames = new ArrayList<>();
            for (int i = 0; i < ieds.getLength(); i++) {
                Element ied = (Element) ieds.item(i);
                String name = ied.getAttribute("name");
                if (name == null || name.isEmpty()) name = "IED_" + i;
                iedNames.add(name);
            }

            log("Archivo SCD contiene " + iedNames.size() + " IEDs: " + iedNames);

            // Mostrar dialogo de seleccion
            int selected = showIEDSelectionDialog(iedNames, sclFile.getName());
            if (selected < 0) return -2; // Cancelado
            return selected;

        } catch (Exception e) {
            log("Error detectando IEDs: " + e.getMessage());
            return -1; // Fallback: tratar como archivo con un solo IED
        }
    }

    /**
     * Parsea DataTypeTemplates del DOM SCL para construir mapas de lookup de enumeraciones.
     * Rellena sclEnumTypes, sclDaEnumType, sclLnTypeDoTypes, sclLnClassToLnType.
     */
    private void parseSclDataTypeTemplates(Document doc) {
        sclEnumTypes.clear();
        sclDaEnumType.clear();
        sclLnTypeDoTypes.clear();
        sclLnClassToLnType.clear();

        // Parse EnumType definitions
        NodeList enumTypes = doc.getElementsByTagName("EnumType");
        for (int i = 0; i < enumTypes.getLength(); i++) {
            Element et = (Element) enumTypes.item(i);
            String id = et.getAttribute("id");
            LinkedHashMap<Integer, String> vals = new LinkedHashMap<>();
            NodeList enumVals = et.getElementsByTagName("EnumVal");
            for (int j = 0; j < enumVals.getLength(); j++) {
                Element ev = (Element) enumVals.item(j);
                try {
                    int ord = Integer.parseInt(ev.getAttribute("ord").trim());
                    vals.put(ord, ev.getTextContent().trim());
                } catch (NumberFormatException ignore) {}
            }
            if (!vals.isEmpty()) sclEnumTypes.put(id, vals);
        }

        // Parse DOType → DA (bType=Enum) → EnumType id
        NodeList doTypes = doc.getElementsByTagName("DOType");
        for (int i = 0; i < doTypes.getLength(); i++) {
            Element dot = (Element) doTypes.item(i);
            String doTypeId = dot.getAttribute("id");
            NodeList das = dot.getElementsByTagName("DA");
            for (int j = 0; j < das.getLength(); j++) {
                Element da = (Element) das.item(j);
                if ("Enum".equals(da.getAttribute("bType"))) {
                    String daName = da.getAttribute("name");
                    String enumType = da.getAttribute("type");
                    if (!daName.isEmpty() && !enumType.isEmpty()) {
                        sclDaEnumType.put(doTypeId + "." + daName, enumType);
                    }
                }
            }
        }

        // Parse LNodeType → DO → DOType
        NodeList lnTypes = doc.getElementsByTagName("LNodeType");
        for (int i = 0; i < lnTypes.getLength(); i++) {
            Element lnt = (Element) lnTypes.item(i);
            String lnTypeId = lnt.getAttribute("id");
            String lnClass = lnt.getAttribute("lnClass");
            sclLnClassToLnType.putIfAbsent(lnClass, lnTypeId);
            Map<String, String> doMap = new HashMap<>();
            NodeList doEls = lnt.getElementsByTagName("DO");
            for (int j = 0; j < doEls.getLength(); j++) {
                Element doEl = (Element) doEls.item(j);
                String doName = doEl.getAttribute("name");
                String doType = doEl.getAttribute("type");
                if (!doName.isEmpty() && !doType.isEmpty()) doMap.put(doName, doType);
            }
            sclLnTypeDoTypes.put(lnTypeId, doMap);
        }
        log("DataTypeTemplates: " + sclEnumTypes.size() + " EnumTypes, " + sclDaEnumType.size() + " DA enumeradas");
    }

    /** Extrae el lnClass de 4 letras a partir del nombre completo del LN (con prefijo e instancia). */
    private String extractLnClass(String lnFull) {
        if (lnFull == null || lnFull.isEmpty()) return "";
        // Strip trailing digits (instance number), but keep LLN0 intact
        if ("LLN0".equals(lnFull)) return "LLN0";
        String noInst = lnFull.replaceAll("\\d+$", "");
        // lnClass is the last 4 uppercase letters of the remaining string
        if (noInst.length() >= 4) return noInst.substring(noInst.length() - 4);
        return noInst.isEmpty() ? lnFull : noInst;
    }

    /** Devuelve el mapa ordinal→etiqueta si el nodo es un BdaInt8/Int8U con EnumType en el SCL, o null si no aplica. */
    private LinkedHashMap<Integer, String> getEnumOptionsForNode(ModelNode node) {
        // iec61850bean mapea bType="Enum" a BdaInt8 (signed); BdaInt8U es posible en variantes
        if (!(node instanceof BdaInt8) && !(node instanceof BdaInt8U)) return null;
        // Reference format: IEDNameLDInst/LNName.DOName.DAName
        String ref = node.getReference().toString();
        int slashIdx = ref.indexOf('/');
        if (slashIdx < 0) return null;
        String[] parts = ref.substring(slashIdx + 1).split("\\.");
        if (parts.length < 3) return null;

        String lnClass = extractLnClass(parts[0]);
        String doName = parts[1];
        String daName = parts[2];

        String lnTypeId = sclLnClassToLnType.get(lnClass);
        if (lnTypeId == null) lnTypeId = sclLnClassToLnType.get(parts[0]); // exact match fallback
        if (lnTypeId == null) return null;

        Map<String, String> doMap = sclLnTypeDoTypes.get(lnTypeId);
        if (doMap == null) return null;
        String doTypeId = doMap.get(doName);
        if (doTypeId == null) return null;

        String enumTypeId = sclDaEnumType.get(doTypeId + "." + daName);
        if (enumTypeId == null) return null;

        return sclEnumTypes.get(enumTypeId);
    }

    /**
     * Formatea el valor raw de un BdaInt8U como "ord [label]" si existe EnumType en el SCL, o devuelve raw.
     * Usar en lugar de bda.getValueString() para DAs que son enums.
     */
    private String formatEnumValue(ModelNode node, String rawValue) {
        LinkedHashMap<Integer, String> enumVals = getEnumOptionsForNode(node);
        if (enumVals == null || enumVals.isEmpty()) return rawValue;
        try {
            int ord = Integer.parseInt(rawValue.trim());
            String label = enumVals.get(ord);
            return label != null ? ord + " [" + label + "]" : rawValue;
        } catch (NumberFormatException ignore) {
            return rawValue;
        }
    }

    /** Muestra un JComboBox con los valores del enum y devuelve el ordinal seleccionado como String, o null. */
    private String showEnumDialog(String daName, int currentOrd, LinkedHashMap<Integer, String> enumVals) {
        List<String> display = new ArrayList<>();
        List<Integer> ords = new ArrayList<>();
        int selIdx = 0, idx = 0;
        for (Map.Entry<Integer, String> e : enumVals.entrySet()) {
            display.add(e.getKey() + " [" + e.getValue() + "]");
            ords.add(e.getKey());
            if (e.getKey() == currentOrd) selIdx = idx;
            idx++;
        }
        JComboBox<String> combo = new JComboBox<>(display.toArray(new String[0]));
        combo.setSelectedIndex(selIdx);
        combo.setPreferredSize(new Dimension(260, 26));

        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.add(new JLabel("Seleccionar valor para " + daName + ":"), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Establecer Valor", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && combo.getSelectedIndex() >= 0) {
            return String.valueOf(ords.get(combo.getSelectedIndex()));
        }
        return null;
    }

    /**
     * Parsea el archivo SCL para extraer GSEControl (GoCBs)
     * Similar a como lo hace IEDScout
     */
    private void parseGoCBsFromScl(File sclFile) {
        sclGoCBs.clear();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(sclFile);

            // Obtener nombre del IED
            NodeList iedNodes = doc.getElementsByTagName("IED");
            if (iedNodes.getLength() > 0) {
                Element iedEl = (Element) iedNodes.item(0);
                loadedIedName = iedEl.getAttribute("name");
                loadedIedNameplate = new String[] {
                    iedEl.getAttribute("manufacturer"),
                    iedEl.getAttribute("type"),
                    iedEl.getAttribute("desc"),
                    iedEl.getAttribute("configVersion")
                };
            }

            // Parsear DataTypeTemplates para lookup de enumeraciones
            parseSclDataTypeTemplates(doc);

            // Buscar todos los elementos GSEControl
            NodeList gseControls = doc.getElementsByTagName("GSEControl");
            log("Encontrados " + gseControls.getLength() + " elementos GSEControl");

            // Mapa para obtener info de Communication/GSE
            Map<String, Map<String, String>> gseInfo = new HashMap<>();

            // Parsear Communication/GSE para obtener MAC y APPID
            NodeList gseNodes = doc.getElementsByTagName("GSE");
            for (int i = 0; i < gseNodes.getLength(); i++) {
                Element gse = (Element) gseNodes.item(i);
                String ldInst = gse.getAttribute("ldInst");
                String cbName = gse.getAttribute("cbName");
                String key = ldInst + "/" + cbName;

                Map<String, String> info = new HashMap<>();

                // Buscar Address/P elements
                NodeList pNodes = gse.getElementsByTagName("P");
                for (int j = 0; j < pNodes.getLength(); j++) {
                    Element p = (Element) pNodes.item(j);
                    String type = p.getAttribute("type");
                    String value = p.getTextContent();
                    if ("MAC-Address".equals(type)) {
                        info.put("mac", value);
                    } else if ("APPID".equals(type)) {
                        info.put("appid", value);
                    }
                }
                gseInfo.put(key, info);
            }

            // Procesar cada GSEControl
            for (int i = 0; i < gseControls.getLength(); i++) {
                Element gseCtrl = (Element) gseControls.item(i);

                SclGoCB gcb = new SclGoCB();
                gcb.cbName = gseCtrl.getAttribute("name");
                gcb.datSet = gseCtrl.getAttribute("datSet");

                // GSEControl "appID" attribute is actually the goID string
                String sclAppId = gseCtrl.getAttribute("appID");
                gcb.goID = sclAppId;

                String confRevStr = gseCtrl.getAttribute("confRev");
                if (!confRevStr.isEmpty()) {
                    try {
                        gcb.confRev = Integer.parseInt(confRevStr);
                    } catch (NumberFormatException e) {
                        gcb.confRev = 1;
                    }
                }

                // Obtener LDevice inst del padre
                org.w3c.dom.Node parent = gseCtrl.getParentNode();
                while (parent != null) {
                    if (parent instanceof Element) {
                        Element parentEl = (Element) parent;
                        if ("LN0".equals(parentEl.getTagName())) {
                            gcb.lnClass = "LLN0";
                        } else if ("LDevice".equals(parentEl.getTagName())) {
                            gcb.ldInst = parentEl.getAttribute("inst");
                            break;
                        }
                    }
                    parent = parent.getParentNode();
                }

                if (gcb.lnClass == null) {
                    gcb.lnClass = "LLN0"; // Default
                }

                // Obtener info de Communication/GSE (numeric APPID + MAC)
                String key = gcb.ldInst + "/" + gcb.cbName;
                if (gseInfo.containsKey(key)) {
                    Map<String, String> info = gseInfo.get(key);
                    gcb.macAddress = info.get("mac");
                    if (info.containsKey("appid")) {
                        gcb.appID = info.get("appid");
                    }
                }

                sclGoCBs.add(gcb);
                log("  GoCB: " + gcb.toString() + " appID=" + gcb.appID + " goID=" + gcb.goID);
            }

        } catch (Exception e) {
            log("Error parseando GoCBs del SCL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parsea GoCBs, DataSets y Reports filtrando por índice de IED
     */
    private void parseGoCBsFromScl(File sclFile, int iedIndex) {
        sclGoCBs.clear();
        sclDataSets.clear();
        sclReports.clear();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(sclFile);

            // Obtener lista de IEDs
            NodeList ieds = doc.getElementsByTagName("IED");
            if (iedIndex >= ieds.getLength()) {
                log("Índice de IED inválido: " + iedIndex);
                parseGoCBsFromScl(sclFile); // Fallback
                return;
            }

            Element selectedIED = (Element) ieds.item(iedIndex);
            String iedName = selectedIED.getAttribute("name");
            loadedIedName = iedName;
            loadedIedNameplate = new String[] {
                selectedIED.getAttribute("manufacturer"),
                selectedIED.getAttribute("type"),
                selectedIED.getAttribute("desc"),
                selectedIED.getAttribute("configVersion")
            };
            log("Parseando SCL para IED: " + iedName);

            // Parsear DataTypeTemplates para lookup de enumeraciones
            parseSclDataTypeTemplates(doc);

            // Parsear DataSets del IED seleccionado
            parseDataSetsFromIED(selectedIED);
            log("DataSets encontrados: " + sclDataSets.size());

            // Parsear ReportControl del IED seleccionado
            parseReportsFromIED(selectedIED);
            log("Reports encontrados: " + sclReports.size());

            // Mapa para obtener info de Communication/GSE para este IED
            Map<String, Map<String, String>> gseInfo = new HashMap<>();

            // Parsear Communication/GSE solo para el IED seleccionado
            NodeList connectedAPs = doc.getElementsByTagName("ConnectedAP");
            for (int c = 0; c < connectedAPs.getLength(); c++) {
                Element connAP = (Element) connectedAPs.item(c);
                if (!iedName.equals(connAP.getAttribute("iedName"))) continue;

                NodeList gseNodes = connAP.getElementsByTagName("GSE");
                for (int i = 0; i < gseNodes.getLength(); i++) {
                    Element gse = (Element) gseNodes.item(i);
                    String ldInst = gse.getAttribute("ldInst");
                    String cbName = gse.getAttribute("cbName");
                    String key = ldInst + "/" + cbName;

                    Map<String, String> info = new HashMap<>();
                    NodeList pNodes = gse.getElementsByTagName("P");
                    for (int j = 0; j < pNodes.getLength(); j++) {
                        Element p = (Element) pNodes.item(j);
                        String type = p.getAttribute("type");
                        String value = p.getTextContent();
                        if ("MAC-Address".equals(type)) {
                            info.put("mac", value);
                        } else if ("APPID".equals(type)) {
                            info.put("appid", value);
                        }
                    }
                    gseInfo.put(key, info);
                }
            }

            // Buscar GSEControl solo dentro del IED seleccionado
            NodeList gseControls = selectedIED.getElementsByTagName("GSEControl");
            log("Encontrados " + gseControls.getLength() + " GSEControl para " + iedName);

            for (int i = 0; i < gseControls.getLength(); i++) {
                Element gseCtrl = (Element) gseControls.item(i);

                SclGoCB gcb = new SclGoCB();
                gcb.cbName = gseCtrl.getAttribute("name");
                gcb.datSet = gseCtrl.getAttribute("datSet");

                // GSEControl "appID" attribute is actually the goID string (IEC 61850 SCL naming)
                String sclAppId = gseCtrl.getAttribute("appID");
                gcb.goID = sclAppId; // This is the textual goID for the GOOSE PDU

                String confRevStr = gseCtrl.getAttribute("confRev");
                if (!confRevStr.isEmpty()) {
                    try { gcb.confRev = Integer.parseInt(confRevStr); }
                    catch (NumberFormatException e) { gcb.confRev = 1; }
                }

                // Obtener LDevice inst del padre
                org.w3c.dom.Node parent = gseCtrl.getParentNode();
                while (parent != null) {
                    if (parent instanceof Element) {
                        Element parentEl = (Element) parent;
                        if ("LN0".equals(parentEl.getTagName())) {
                            gcb.lnClass = "LLN0";
                        } else if ("LDevice".equals(parentEl.getTagName())) {
                            gcb.ldInst = parentEl.getAttribute("inst");
                            break;
                        }
                    }
                    parent = parent.getParentNode();
                }

                if (gcb.lnClass == null) gcb.lnClass = "LLN0";

                // Obtener info de Communication/GSE (numeric APPID + MAC)
                String key = gcb.ldInst + "/" + gcb.cbName;
                if (gseInfo.containsKey(key)) {
                    Map<String, String> info = gseInfo.get(key);
                    gcb.macAddress = info.get("mac");
                    if (info.containsKey("appid")) {
                        gcb.appID = info.get("appid"); // Numeric APPID from Communication section
                    }
                }

                sclGoCBs.add(gcb);
                log("  GoCB: " + gcb.toString() + " appID=" + gcb.appID + " goID=" + gcb.goID);
            }

        } catch (Exception e) {
            log("Error parseando GoCBs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parsea DataSets de un elemento IED
     */
    private void parseDataSetsFromIED(Element iedElement) {
        NodeList dataSets = iedElement.getElementsByTagName("DataSet");

        for (int i = 0; i < dataSets.getLength(); i++) {
            Element dsElement = (Element) dataSets.item(i);

            SclDataSet ds = new SclDataSet();
            ds.name = dsElement.getAttribute("name");
            ds.desc = dsElement.getAttribute("desc");

            // Obtener LDevice padre
            org.w3c.dom.Node parent = dsElement.getParentNode();
            while (parent != null) {
                if (parent instanceof Element) {
                    Element parentEl = (Element) parent;
                    if ("LN0".equals(parentEl.getTagName()) || "LN".equals(parentEl.getTagName())) {
                        ds.lnClass = parentEl.getAttribute("lnClass");
                        if (ds.lnClass == null || ds.lnClass.isEmpty()) {
                            ds.lnClass = "LLN0";
                        }
                    } else if ("LDevice".equals(parentEl.getTagName())) {
                        ds.ldInst = parentEl.getAttribute("inst");
                        break;
                    }
                }
                parent = parent.getParentNode();
            }

            // Obtener miembros (FCDA)
            NodeList fcdas = dsElement.getElementsByTagName("FCDA");
            for (int j = 0; j < fcdas.getLength(); j++) {
                Element fcda = (Element) fcdas.item(j);
                StringBuilder member = new StringBuilder();

                String ldInst = fcda.getAttribute("ldInst");
                String prefix = fcda.getAttribute("prefix");
                String lnClass = fcda.getAttribute("lnClass");
                String lnInst = fcda.getAttribute("lnInst");
                String doName = fcda.getAttribute("doName");
                String daName = fcda.getAttribute("daName");
                String fc = fcda.getAttribute("fc");

                if (ldInst != null && !ldInst.isEmpty()) member.append(ldInst).append("/");
                if (prefix != null && !prefix.isEmpty()) member.append(prefix);
                member.append(lnClass);
                if (lnInst != null && !lnInst.isEmpty()) member.append(lnInst);
                member.append(".").append(doName);
                if (daName != null && !daName.isEmpty()) member.append(".").append(daName);
                member.append(" [").append(fc).append("]");

                ds.members.add(member.toString());
            }

            sclDataSets.add(ds);
        }
    }

    /**
     * Parsea ReportControl de un elemento IED
     */
    private void parseReportsFromIED(Element iedElement) {
        NodeList reports = iedElement.getElementsByTagName("ReportControl");

        for (int i = 0; i < reports.getLength(); i++) {
            Element rptElement = (Element) reports.item(i);

            SclReport rpt = new SclReport();
            rpt.name = rptElement.getAttribute("name");
            rpt.rptID = rptElement.getAttribute("rptID");
            rpt.datSet = rptElement.getAttribute("datSet");
            rpt.buffered = "true".equals(rptElement.getAttribute("buffered"));

            String confRevStr = rptElement.getAttribute("confRev");
            if (confRevStr != null && !confRevStr.isEmpty()) {
                try { rpt.confRev = Integer.parseInt(confRevStr); }
                catch (NumberFormatException e) { rpt.confRev = 1; }
            }

            // Obtener LDevice padre
            org.w3c.dom.Node parent = rptElement.getParentNode();
            while (parent != null) {
                if (parent instanceof Element) {
                    Element parentEl = (Element) parent;
                    if ("LN0".equals(parentEl.getTagName()) || "LN".equals(parentEl.getTagName())) {
                        rpt.lnClass = parentEl.getAttribute("lnClass");
                        if (rpt.lnClass == null || rpt.lnClass.isEmpty()) {
                            rpt.lnClass = "LLN0";
                        }
                    } else if ("LDevice".equals(parentEl.getTagName())) {
                        rpt.ldInst = parentEl.getAttribute("inst");
                        break;
                    }
                }
                parent = parent.getParentNode();
            }

            sclReports.add(rpt);
        }
    }

    private void logGoose(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            gooseLogArea.append("[" + timestamp + "] " + message + "\n");
            gooseLogArea.setCaretPosition(gooseLogArea.getDocument().getLength());
        });
    }

    // ===== SAMPLED VALUES PANEL (using libiec61850) =====

    private void checkNativeLibrary() {
        try {
            // Verificar si el archivo DLL existe
            String libPath = System.getProperty("jna.library.path");
            log("JNA library path: " + (libPath != null ? libPath : "no configurado"));

            java.io.File dllFile = new java.io.File("lib/iec61850.dll");
            if (dllFile.exists()) {
                log("DLL encontrada: " + dllFile.getAbsolutePath() + " (" + dllFile.length() + " bytes)");
            } else {
                log("ADVERTENCIA: lib/iec61850.dll no encontrada");
            }

            nativeLibAvailable = NativeSVSubscriber.isNativeLibraryAvailable();
            if (nativeLibAvailable) {
                log("libiec61850 nativa CARGADA - SV y GOOSE nativo habilitados");
            } else {
                log("libiec61850 NO pudo cargar - usando pcap4j para GOOSE");
                log("Nota: SV (Sampled Values) requiere libiec61850");
            }
        } catch (UnsatisfiedLinkError e) {
            nativeLibAvailable = false;
            log("Error cargando libiec61850: " + e.getMessage());
            log("Posibles causas: DLL no encontrada, arquitectura incorrecta (32/64 bit), o faltan dependencias");
        } catch (Exception e) {
            nativeLibAvailable = false;
            log("Error verificando libiec61850: " + e.getMessage());
        }
    }

    private JPanel createSampledValuesPanel() {
        JPanel panel = new JPanel(new BorderLayout(3, 3));
        panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        // Initialize SV subscriber
        svSubscriber = new NativeSVSubscriber();
        svSubscriber.setLogListener(msg -> logSV(msg));
        svSubscriber.setMessageListener(msg -> handleSVMessage(msg));

        // ===== TOP BAR: Interface + AppID + Controls =====
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        topBar.setBackground(new Color(240, 245, 250));

        // Interface selector (left)
        JPanel ifacePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        ifacePanel.setOpaque(false);
        ifacePanel.add(new JLabel("Red:"));
        svInterfaceCombo = new JComboBox<>();
        svInterfaceCombo.setPreferredSize(new Dimension(250, 24));
        loadSVNetworkInterfaces();
        ifacePanel.add(svInterfaceCombo);

        JButton btnRefresh = new JButton("↻");
        btnRefresh.setToolTipText("Refrescar interfaces");
        btnRefresh.setMargin(new Insets(2, 6, 2, 6));
        btnRefresh.addActionListener(e -> loadSVNetworkInterfaces());
        ifacePanel.add(btnRefresh);

        ifacePanel.add(Box.createHorizontalStrut(15));
        ifacePanel.add(new JLabel("AppID (hex):"));
        svAppIdField = new JTextField("4000", 6);
        svAppIdField.setToolTipText("APPID en hexadecimal (ej: 4000, 4001)");
        ifacePanel.add(svAppIdField);

        topBar.add(ifacePanel, BorderLayout.WEST);

        // Control buttons (center)
        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 2));
        ctrlPanel.setOpaque(false);

        btnSvStartStop = new JButton("▶ Capturar SV");
        btnSvStartStop.setBackground(new Color(46, 125, 50));
        btnSvStartStop.setForeground(Color.WHITE);
        btnSvStartStop.setFocusPainted(false);
        btnSvStartStop.addActionListener(e -> toggleSVCapture());
        ctrlPanel.add(btnSvStartStop);

        lblSvStatus = new JLabel("Detenido");
        lblSvStatus.setForeground(Color.GRAY);
        ctrlPanel.add(lblSvStatus);

        topBar.add(ctrlPanel, BorderLayout.CENTER);

        // Library status (right)
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        statusPanel.setOpaque(false);
        JLabel lblLibStatus = new JLabel();
        lblLibStatus.setFont(lblLibStatus.getFont().deriveFont(Font.ITALIC, 10f));
        if (nativeLibAvailable) {
            lblLibStatus.setText("libiec61850 OK");
            lblLibStatus.setForeground(new Color(0, 130, 0));
        } else {
            lblLibStatus.setText("libiec61850 no disponible - Coloque libiec61850.dll en la carpeta lib/");
            lblLibStatus.setForeground(Color.RED);
        }
        statusPanel.add(lblLibStatus);
        topBar.add(statusPanel, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);

        // ===== CENTER: SV Messages Table =====
        String[] svColumns = {"Tiempo", "AppID", "svId", "smpCnt", "smpRate", "confRev", "Muestras"};
        svTableModel = new DefaultTableModel(svColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        svTable = new JTable(svTableModel);
        svTable.setRowHeight(20);
        svTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        svTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        svTable.getColumnModel().getColumn(1).setPreferredWidth(55);
        svTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        svTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        svTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        svTable.getColumnModel().getColumn(5).setPreferredWidth(55);
        svTable.getColumnModel().getColumn(6).setPreferredWidth(300);

        JScrollPane tableScroll = new JScrollPane(svTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Mensajes Sampled Values"));
        panel.add(tableScroll, BorderLayout.CENTER);

        // ===== BOTTOM: Log =====
        svLogArea = new JTextArea();
        svLogArea.setEditable(false);
        svLogArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        JScrollPane logScroll = new JScrollPane(svLogArea);
        logScroll.setPreferredSize(new Dimension(100, 100));

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(logScroll, BorderLayout.CENTER);

        JPanel logBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JButton btnClear = new JButton("Limpiar");
        btnClear.addActionListener(e -> { svLogArea.setText(""); svTableModel.setRowCount(0); });
        JLabel lblCount = new JLabel("ASDUs: 0");
        javax.swing.Timer countTimer = new javax.swing.Timer(500, e -> {
            if (svSubscriber != null) {
                lblCount.setText("ASDUs: " + svSubscriber.getAsduCount());
            }
        });
        countTimer.start();
        logBtnPanel.add(btnClear);
        logBtnPanel.add(lblCount);
        logPanel.add(logBtnPanel, BorderLayout.SOUTH);
        logPanel.setBorder(BorderFactory.createTitledBorder("Log SV"));

        panel.add(logPanel, BorderLayout.SOUTH);

        // Disable if native lib not available
        if (!nativeLibAvailable) {
            btnSvStartStop.setEnabled(false);
            btnSvStartStop.setText("SV No Disponible");
            lblSvStatus.setText("Requiere libiec61850.dll");
            lblSvStatus.setForeground(Color.RED);
        }

        return panel;
    }

    private void loadSVNetworkInterfaces() {
        if (svInterfaceCombo == null) return;
        svInterfaceCombo.removeAllItems();

        try {
            java.util.Enumeration<java.net.NetworkInterface> nets =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                java.net.NetworkInterface nif = nets.nextElement();
                if (nif.isUp() && !nif.isLoopback()) {
                    StringBuilder ips = new StringBuilder();
                    java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        String ip = addrs.nextElement().getHostAddress();
                        if (!ip.contains(":") && !ip.startsWith("127.")) {
                            if (ips.length() > 0) ips.append(", ");
                            ips.append(ip);
                        }
                    }
                    String name = nif.getDisplayName();
                    if (ips.length() > 0) {
                        name += " - " + ips.toString();
                    }
                    svInterfaceCombo.addItem(nif.getName() + "|" + name);
                }
            }
        } catch (Exception e) {
            logSV("Error listando interfaces: " + e.getMessage());
        }
    }

    private void toggleSVCapture() {
        if (!nativeLibAvailable) {
            JOptionPane.showMessageDialog(this,
                "libiec61850 nativa no esta disponible.\n" +
                "Copie iec61850.dll al directorio lib/ o al PATH del sistema.",
                "SV Subscriber", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (svSubscriber.isRunning()) {
            // Stop
            svSubscriber.stop();
            btnSvStartStop.setText("▶ Capturar SV");
            btnSvStartStop.setBackground(new Color(46, 125, 50));
            lblSvStatus.setText("Detenido");
            lblSvStatus.setForeground(Color.GRAY);
        } else {
            // Start
            String selected = (String) svInterfaceCombo.getSelectedItem();
            if (selected == null || selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Seleccione una interface de red",
                    "SV Subscriber", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Parse interface name (before |)
            String interfaceId = selected.contains("|") ? selected.split("\\|")[0] : selected;

            // Parse AppID
            int appId;
            try {
                appId = Integer.parseInt(svAppIdField.getText().trim(), 16);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "AppID invalido. Use formato hexadecimal (ej: 4000)",
                    "SV Subscriber", JOptionPane.WARNING_MESSAGE);
                return;
            }

            logSV("Iniciando captura SV en " + interfaceId + " para AppID 0x" + String.format("%04X", appId));

            if (svSubscriber.start(interfaceId, appId)) {
                btnSvStartStop.setText("⬛ Detener");
                btnSvStartStop.setBackground(new Color(200, 50, 50));
                lblSvStatus.setText("Capturando...");
                lblSvStatus.setForeground(new Color(0, 150, 0));
            } else {
                JOptionPane.showMessageDialog(this,
                    "Error iniciando SV Subscriber.\n" +
                    "Verifique la interface y permisos de administrador.",
                    "SV Subscriber", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleSVMessage(NativeSVSubscriber.SVMessage msg) {
        SwingUtilities.invokeLater(() -> {
            // Build samples string
            StringBuilder samplesStr = new StringBuilder();
            int count = 0;
            for (NativeSVSubscriber.DataSample sample : msg.samples) {
                if (count > 0) samplesStr.append(", ");
                if (count < 8) { // Show first 8 values
                    samplesStr.append(String.format("%.2f", sample.value));
                }
                count++;
            }
            if (count > 8) {
                samplesStr.append("... (+" + (count - 8) + ")");
            }

            // Limit table rows
            if (svTableModel.getRowCount() > 500) {
                svTableModel.removeRow(svTableModel.getRowCount() - 1);
            }

            // Add to table
            svTableModel.insertRow(0, new Object[]{
                msg.timestamp,
                String.format("%04X", msg.appId),
                msg.svId != null ? msg.svId : "",
                msg.smpCnt,
                msg.smpRate,
                msg.confRev,
                samplesStr.toString()
            });
        });
    }

    private void logSV(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            svLogArea.append("[" + timestamp + "] " + message + "\n");
            svLogArea.setCaretPosition(svLogArea.getDocument().getLength());
        });
    }

    private void addNodeToMonitor(DefaultMutableTreeNode treeNode) {
        Object userObj = treeNode.getUserObject();
        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if (info.node instanceof FcModelNode) {
                FcModelNode fcNode = (FcModelNode) info.node;
                String ref = info.node.getReference().toString();
                Fc fc = fcNode.getFc();
                String fullRef = ref + "$" + fc.toString();

                if (!monitorItems.containsKey(fullRef)) {
                    String type = "";
                    if (info.node instanceof BasicDataAttribute) {
                        type = info.node.getClass().getSimpleName().replace("Bda", "");
                    } else {
                        type = info.node.getClass().getSimpleName()
                            .replace("FcDataObject", "DO")
                            .replace("ConstructedDataAttribute", "SDO");
                    }

                    // Usar referencia completa como nombre (ej: XCBR1/Pos/stVal)
                    String displayName = formatReference(ref);
                    MonitorItem item = new MonitorItem(ref, displayName, fc.toString(), type, fcNode);
                    if (info.node instanceof BasicDataAttribute) {
                        item.value = formatEnumValue(info.node, ((BasicDataAttribute) info.node).getValueString());
                        if (item.value == null) item.value = "";
                    }
                    monitorItems.put(fullRef, item);
                    watchlist.add(fullRef);
                    updateWatchlistLabel();
                    refreshMonitorTable();
                    log("Monitor: + " + displayName + " [" + fc + "]");
                }
            }
        }
        // Agregar hijos tambien (si es un DO) - solo BasicDataAttributes
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            addNodeToMonitor((DefaultMutableTreeNode) treeNode.getChildAt(i));
        }
        modelTree.repaint();
    }

    // Formatear referencia para mostrar de forma legible
    // Entrada: "TEMPLATEControl/XCBR1.Pos.stVal" -> "XCBR1/Pos/stVal"
    private String formatReference(String ref) {
        if (ref == null) return "";
        // Quitar el LD si es muy largo
        int slashIdx = ref.indexOf('/');
        if (slashIdx > 0) {
            String withoutLd = ref.substring(slashIdx + 1);
            // Reemplazar puntos por /
            return withoutLd.replace(".", "/");
        }
        return ref.replace(".", "/");
    }

    private void removeSelectedFromMonitor() {
        int[] rows = monitorTable.getSelectedRows();
        if (rows.length == 0) return;

        // Obtener las keys a eliminar (recorrer de atras hacia adelante)
        List<String> keysToRemove = new ArrayList<>();
        List<MonitorItem> itemsList = new ArrayList<>(monitorItems.values());

        for (int row : rows) {
            if (row >= 0 && row < itemsList.size()) {
                MonitorItem item = itemsList.get(row);
                String fullRef = item.reference + "$" + item.fc;
                keysToRemove.add(fullRef);
            }
        }

        for (String key : keysToRemove) {
            monitorItems.remove(key);
            watchlist.remove(key);
        }

        updateWatchlistLabel();
        refreshMonitorTable();
        modelTree.repaint();
    }

    private void refreshMonitorTable() {
        monitorTableModel.setRowCount(0);
        for (MonitorItem item : monitorItems.values()) {
            // Determinar estado (cambio reciente)
            String status = "";
            if (item.lastChangeTime > 0) {
                long ago = System.currentTimeMillis() - item.lastChangeTime;
                if (ago < 5000) {
                    status = "CHANGED";
                }
            }
            // Columnas: Nombre, FC, Tipo, Valor, Estado
            monitorTableModel.addRow(new Object[]{
                item.name,
                item.fc,
                item.type,
                item.value,
                status
            });
        }
        // Actualizar contador y reaplicar filtro
        if (monitorCountLabel != null) {
            monitorCountLabel.setText(" Items: " + monitorItems.size());
        }
        applyMonitorFilter();
    }

    /** Aplica filtro de FC y nombre al monitor. Actualiza el contador con visibles/total. */
    private void applyMonitorFilter() {
        if (monitorSorter == null) return;
        String fc   = monitorFcFilter != null ? (String) monitorFcFilter.getSelectedItem() : "Todos";
        String name = monitorNameFilter != null ? monitorNameFilter.getText().trim().toLowerCase() : "";

        boolean fcAll  = fc == null || fc.equals("Todos");
        boolean nameAll = name.isEmpty();

        if (fcAll && nameAll) {
            monitorSorter.setRowFilter(null);
        } else {
            final String fcFinal = fc;
            monitorSorter.setRowFilter(RowFilter.andFilter(java.util.Arrays.asList(
                fcAll ? null : RowFilter.regexFilter("(?i)^" + fcFinal + "$", 1),
                nameAll ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(name), 0)
            ).stream().filter(f -> f != null).collect(java.util.stream.Collectors.toList())));
        }

        // Actualizar contador con visible/total
        if (monitorCountLabel != null) {
            int visible = monitorTable.getRowCount();
            int total   = monitorItems.size();
            if (visible == total) {
                monitorCountLabel.setText(" Items: " + total);
            } else {
                monitorCountLabel.setText(" Items: " + total + "  (visible: " + visible + ")");
            }
        }
    }

    private void updateMonitorValues() {
        int row = 0;
        for (MonitorItem item : monitorItems.values()) {
            if (row >= monitorTableModel.getRowCount()) break;

            if (item.node instanceof BasicDataAttribute) {
                BasicDataAttribute bda = (BasicDataAttribute) item.node;
                String newVal = bda.getValueString();
                if (newVal == null) newVal = "";

                // Detectar cambio
                if (!newVal.equals(item.value)) {
                    item.oldValue = item.value;
                    item.value = newVal;
                    item.lastChangeTime = System.currentTimeMillis();

                    // Actualizar todas las columnas visibles
                    monitorTableModel.setValueAt(item.name, row, 0);  // Nombre
                    monitorTableModel.setValueAt(item.fc, row, 1);    // FC
                    monitorTableModel.setValueAt(item.type, row, 2);  // Tipo
                    monitorTableModel.setValueAt(newVal, row, 3);     // Valor
                    monitorTableModel.setValueAt("CHANGED", row, 4);  // Estado

                    // Log del cambio
                    log("CAMBIO: " + item.name + " = " + newVal);
                } else {
                    // Limpiar estado si paso el tiempo
                    long ago = System.currentTimeMillis() - item.lastChangeTime;
                    if (ago > 3000 && item.lastChangeTime > 0) {
                        monitorTableModel.setValueAt("", row, 4);
                    }
                }
            }
            row++;
        }
        // Forzar repintado
        monitorTable.repaint();
    }

    // Renderer para la tabla del monitor
    private class MonitorTableRenderer extends DefaultTableCellRenderer {
        private final Color BG_CHANGED = new Color(255, 255, 200);  // Amarillo claro
        private final Color FG_ON = new Color(0, 150, 0);
        private final Color FG_OFF = new Color(200, 0, 0);
        private final Color FG_INTERMEDIATE = new Color(255, 140, 0);
        private final Color FG_CHANGED = new Color(0, 100, 200);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setFont(getFont().deriveFont(Font.PLAIN));
            setForeground(Color.BLACK);
            setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);

            if (value == null) return this;
            String v = value.toString().toLowerCase();

            // Columna Valor (3)
            if (column == 3) {
                if (v.equals("on")) {
                    setForeground(FG_ON);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (v.equals("off")) {
                    setForeground(FG_OFF);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (v.contains("intermediate") || v.contains("bad")) {
                    setForeground(FG_INTERMEDIATE);
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            }

            // Columna Estado (4)
            if (column == 4 && v.equals("changed")) {
                setForeground(FG_CHANGED);
                setFont(getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    setBackground(BG_CHANGED);
                }
            }

            // Columna FC (1) - color segun FC
            if (column == 1) {
                if (v.equals("st")) {
                    setForeground(new Color(0, 100, 0));
                } else if (v.equals("mx")) {
                    setForeground(new Color(0, 0, 150));
                } else if (v.equals("co")) {
                    setForeground(new Color(150, 0, 0));
                }
            }

            return this;
        }
    }

    private void setupListeners() {
        // Cambio de modo
        rbServer.addActionListener(e -> switchToServerMode());
        rbClient.addActionListener(e -> switchToClientMode());

        // Servidor
        btnSelectFile.addActionListener(e -> selectSclFile());
        btnStartStop.addActionListener(e -> toggleServer());

        // Cliente
        btnConnect.addActionListener(e -> toggleConnection());
        cbPolling.addActionListener(e -> togglePolling());

        // Crear popup menu para watchlist
        createTreePopupMenu();

        // Doble click en arbol para leer/editar valor, click derecho para menu
        modelTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (currentMode == AppMode.CLIENT && isConnected) {
                        readSelectedNode();
                    } else if (currentMode == AppMode.SERVER && isServerRunning) {
                        // En modo servidor, doble click abre editor de valor
                        setSelectedNodeCustomValue();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleTreePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleTreePopup(e);
            }
        });

        // Listener del cliente
        client.setValueChangeListener(new IEC61850Client.ValueChangeListener() {
            @Override
            public void onValueChanged(String reference, String value, String type) {
                SwingUtilities.invokeLater(() -> updateNodeValue(reference, value));
            }

            @Override
            public void onError(String reference, String error) {
                log("ERROR: " + reference + " - " + error);
            }

            @Override
            public void onConnectionClosed(String reason) {
                SwingUtilities.invokeLater(() -> {
                    handleDisconnect();
                    log("Conexion cerrada: " + reason);
                });
            }
        });
    }

    private JPopupMenu serverPopupMenu;  // Menu para modo servidor

    private void createTreePopupMenu() {
        // === Menu para modo CLIENTE ===
        treePopupMenu = new JPopupMenu();

        JMenuItem miAddToWatchlist = new JMenuItem("Agregar a Watchlist");
        miAddToWatchlist.addActionListener(e -> addSelectedToWatchlist());
        treePopupMenu.add(miAddToWatchlist);

        JMenuItem miRemoveFromWatchlist = new JMenuItem("Quitar de Watchlist");
        miRemoveFromWatchlist.addActionListener(e -> removeSelectedFromWatchlist());
        treePopupMenu.add(miRemoveFromWatchlist);

        treePopupMenu.addSeparator();

        JMenuItem miReadValue = new JMenuItem("Leer Valor");
        miReadValue.addActionListener(e -> readSelectedNode());
        treePopupMenu.add(miReadValue);

        JMenuItem miAddToMonitor = new JMenuItem("Agregar a Monitor");
        miAddToMonitor.addActionListener(e -> {
            TreePath path = modelTree.getSelectionPath();
            if (path != null) {
                addNodeToMonitor((DefaultMutableTreeNode) path.getLastPathComponent());
            }
        });
        treePopupMenu.add(miAddToMonitor);

        treePopupMenu.addSeparator();

        // FC=BL: Bloquear / Desbloquear valor del DO
        JMenuItem miBlock   = new JMenuItem("Bloquear valor (blkEna=true)");
        JMenuItem miUnblock = new JMenuItem("Desbloquear valor (blkEna=false)");
        miBlock.addActionListener(e   -> toggleBlocking(true));
        miUnblock.addActionListener(e -> toggleBlocking(false));
        treePopupMenu.add(miBlock);
        treePopupMenu.add(miUnblock);

        // Mostrar/ocultar ítems BL según el nodo seleccionado
        treePopupMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                boolean hasBlk = getSelectedBlkEnaNode() != null;
                miBlock.setVisible(hasBlk);
                miUnblock.setVisible(hasBlk);
            }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        // === Menu para modo SERVIDOR se construye dinamicamente ===
        serverPopupMenu = new JPopupMenu();
    }

    /**
     * Build the server popup menu dynamically based on the selected node's data type.
     */
    private void buildServerPopupForNode(DefaultMutableTreeNode treeNode) {
        serverPopupMenu.removeAll();

        Object userObj = treeNode.getUserObject();
        if (!(userObj instanceof NodeInfo)) return;
        NodeInfo info = (NodeInfo) userObj;

        // Detect data type
        boolean isBoolean = (info.node instanceof BdaBoolean)
            || (info.type != null && info.type.equalsIgnoreCase("Boolean"));
        boolean isDbpos = (info.node instanceof BdaDoubleBitPos)
            || (info.type != null && info.type.contains("DoubleBit"));
        boolean isTapCmd = (info.node instanceof BdaTapCommand);
        LinkedHashMap<Integer, String> precomputedEnumVals = getEnumOptionsForNode(info.node);
        boolean isEnum  = (precomputedEnumVals != null);
        boolean isDO = (info.node instanceof FcDataObject);

        // If it's a DO, check child stVal type
        if (isDO) {
            for (ModelNode child : info.node.getChildren()) {
                if ("stVal".equalsIgnoreCase(child.getName())) {
                    if (child instanceof BdaBoolean) isBoolean = true;
                    else if (child instanceof BdaDoubleBitPos) isDbpos = true;
                    else if (child instanceof BdaTapCommand) isTapCmd = true;
                    else if (child instanceof BdaInt8 || child instanceof BdaInt8U) {
                        precomputedEnumVals = getEnumOptionsForNode(child);
                        if (precomputedEnumVals != null) isEnum = true;
                    }
                    break;
                }
            }
        }

        if (isBoolean) {
            JMenuItem miTrue = new JMenuItem("Establecer TRUE");
            miTrue.addActionListener(e -> setSelectedNodeValue("true"));
            serverPopupMenu.add(miTrue);

            JMenuItem miFalse = new JMenuItem("Establecer FALSE");
            miFalse.addActionListener(e -> setSelectedNodeValue("false"));
            serverPopupMenu.add(miFalse);
        } else if (isDbpos) {
            JMenuItem miOn = new JMenuItem("Establecer ON (cerrado)");
            miOn.addActionListener(e -> setSelectedNodeValue("on"));
            serverPopupMenu.add(miOn);

            JMenuItem miOff = new JMenuItem("Establecer OFF (abierto)");
            miOff.addActionListener(e -> setSelectedNodeValue("off"));
            serverPopupMenu.add(miOff);

            JMenuItem miInter = new JMenuItem("Establecer INTERMEDIATE");
            miInter.addActionListener(e -> setSelectedNodeValue("intermediate"));
            serverPopupMenu.add(miInter);

            JMenuItem miBad = new JMenuItem("Establecer BAD_STATE");
            miBad.addActionListener(e -> setSelectedNodeValue("bad"));
            serverPopupMenu.add(miBad);
        } else if (isTapCmd) {
            for (String cmd : new String[]{"STOP", "LOWER", "HIGHER", "RESERVED"}) {
                JMenuItem mi = new JMenuItem("Establecer " + cmd);
                mi.addActionListener(e -> setSelectedNodeValue(cmd.toLowerCase()));
                serverPopupMenu.add(mi);
            }
        } else if (isEnum) {
            // Enum: show dropdown with enum values from SCL DataTypeTemplates
            JMenuItem miEnum = new JMenuItem("Establecer valor (enum)...");
            miEnum.addActionListener(e -> setSelectedNodeCustomValue());
            serverPopupMenu.add(miEnum);
        } else {
            // Generic: show custom value dialog
            JMenuItem miSet = new JMenuItem("Establecer valor...");
            miSet.addActionListener(e -> setSelectedNodeCustomValue());
            serverPopupMenu.add(miSet);
        }

        serverPopupMenu.addSeparator();

        JMenuItem miSetCustom = new JMenuItem("Valor personalizado...");
        miSetCustom.addActionListener(e -> setSelectedNodeCustomValue());
        serverPopupMenu.add(miSetCustom);

        serverPopupMenu.addSeparator();

        JMenuItem miPublishGoose = new JMenuItem("Publicar GOOSE (cambio de estado)");
        miPublishGoose.addActionListener(e -> publishGooseFromSelection());
        serverPopupMenu.add(miPublishGoose);
    }

    /**
     * Publica GOOSE cuando se cambia un valor en el servidor
     */
    private void publishGooseFromSelection() {
        if (!isServerRunning) {
            JOptionPane.showMessageDialog(this, "El servidor debe estar activo", "GOOSE", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Publicar cambio en multi-publishers activos
        if (!activePublishers.isEmpty()) {
            for (Map.Entry<Integer, GoosePublisher> entry : activePublishers.entrySet()) {
                entry.getValue().publishStateChange();
            }
            log("GOOSE: Cambio de estado en " + activePublishers.size() + " GoCBs");
            logGoose("Estado publicado en " + activePublishers.size() + " GoCBs");
        } else if (goosePublisher != null && goosePublisher.isPublishing()) {
            goosePublisher.publishStateChange();
            log("GOOSE: Cambio de estado publicado (stNum=" + goosePublisher.getStNum() + ")");
            logGoose("Estado publicado: stNum=" + goosePublisher.getStNum());
        } else {
            String selected = (String) gooseInterfaceCombo.getSelectedItem();
            if (selected != null && interfaceMap.containsKey(selected)) {
                PcapNetworkInterface nif = interfaceMap.get(selected);
                if (goosePublisher.init(nif)) {
                    goosePublisher.publishStateChange();
                    log("GOOSE: Mensaje unico publicado");
                    logGoose("GOOSE publicado (una vez)");
                } else {
                    JOptionPane.showMessageDialog(this,
                        "No se pudo publicar GOOSE.\nVerifique la interfaz seleccionada.",
                        "GOOSE Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this,
                    "Seleccione una interfaz de red en la pestana GOOSE primero.",
                    "GOOSE", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void handleTreePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;

        // Seleccionar el nodo bajo el cursor
        TreePath path = modelTree.getPathForLocation(e.getX(), e.getY());
        if (path == null) return;

        modelTree.setSelectionPath(path);

        // Check if it's an FCDA node (DataSet member) -> special popup
        DefaultMutableTreeNode clickedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = clickedNode.getUserObject();
        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if ("FCDA".equals(info.prefix)) {
                JPopupMenu fcdaPopup = new JPopupMenu();
                JMenuItem miNavigate = new JMenuItem("Ver en modelo de datos");
                miNavigate.addActionListener(ev -> navigateToFcdaInModel(info.name));
                fcdaPopup.add(miNavigate);

                // If server model is loaded, also add type-aware state change options
                if (currentMode == AppMode.SERVER && server != null && server.getServerModel() != null) {
                    fcdaPopup.addSeparator();
                    GoosePublisher.DataValue.Type fcdaType = inferDataType(info.name);
                    String[][] opts;
                    if (fcdaType == GoosePublisher.DataValue.Type.BOOLEAN) {
                        opts = new String[][]{{"Establecer TRUE", "true"}, {"Establecer FALSE", "false"}};
                    } else if (fcdaType == GoosePublisher.DataValue.Type.DBPOS) {
                        opts = new String[][]{{"Establecer ON", "on"}, {"Establecer OFF", "off"},
                            {"Establecer INTERMEDIATE", "intermediate"}, {"Establecer BAD", "bad"}};
                    } else {
                        opts = new String[][]{{"Establecer TRUE", "true"}, {"Establecer FALSE", "false"}};
                    }
                    for (String[] opt : opts) {
                        JMenuItem mi = new JMenuItem(opt[0]);
                        String val = opt[1];
                        mi.addActionListener(ev -> {
                            DefaultMutableTreeNode targetNode = findNodeInModel(info.name);
                            if (targetNode != null) {
                                modelTree.setSelectionPath(new TreePath(targetNode.getPath()));
                                setSelectedNodeValue(val);
                            }
                        });
                        fcdaPopup.add(mi);
                    }
                }

                fcdaPopup.show(modelTree, e.getX(), e.getY());
                return;
            }
        }

        if (currentMode == AppMode.CLIENT && isConnected) {
            treePopupMenu.show(modelTree, e.getX(), e.getY());
        } else if (currentMode == AppMode.SERVER && server != null && server.getServerModel() != null) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
            buildServerPopupForNode(selectedNode);
            serverPopupMenu.show(modelTree, e.getX(), e.getY());
        }
    }

    /**
     * Navigate to an FCDA member reference in the data model tree.
     * FCDA format: "[idx] ldInst/prefixLNClassInst.doName.daName [FC]"
     */
    private void navigateToFcdaInModel(String fcdaName) {
        DefaultMutableTreeNode targetNode = findNodeInModel(fcdaName);
        if (targetNode != null) {
            TreePath treePath = new TreePath(targetNode.getPath());
            modelTree.expandPath(treePath);
            modelTree.setSelectionPath(treePath);
            modelTree.scrollPathToVisible(treePath);
            log("Navegando a: " + fcdaName);
        } else {
            logGoose("No se encontro el nodo en el modelo: " + fcdaName);
        }
    }

    /**
     * Find a tree node matching an FCDA reference.
     * Searches the model tree for a node whose reference matches the FCDA member path.
     */
    private DefaultMutableTreeNode findNodeInModel(String fcdaName) {
        // Parse FCDA name: "[idx] ldInst/prefixLNClassInst.doName.daName [FC]"
        String clean = fcdaName;
        // Remove [idx] prefix
        if (clean.startsWith("[")) {
            int closeBracket = clean.indexOf(']');
            if (closeBracket > 0) clean = clean.substring(closeBracket + 1).trim();
        }
        // Remove [FC] suffix
        int fcBracket = clean.lastIndexOf('[');
        if (fcBracket > 0) clean = clean.substring(0, fcBracket).trim();

        // Split into parts: "ldInst/lnRef.doName.daName"
        String[] slashParts = clean.split("/", 2);
        String ldInst = slashParts.length > 1 ? slashParts[0] : "";
        String remainder = slashParts.length > 1 ? slashParts[1] : slashParts[0];

        // Split remainder into LN.DO.DA
        String[] dotParts = remainder.split("\\.");
        // dotParts[0] = prefixLNClassInst, dotParts[1] = doName, dotParts[2] = daName (optional)

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) modelTree.getModel().getRoot();
        return searchTreeForFcda(root, ldInst, dotParts, 0);
    }

    private DefaultMutableTreeNode searchTreeForFcda(DefaultMutableTreeNode node, String ldInst, String[] parts, int depth) {
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            Object obj = child.getUserObject();

            if (obj instanceof NodeInfo) {
                NodeInfo info = (NodeInfo) obj;
                String nodeName = info.name != null ? info.name : "";

                // Match LD level
                if (depth == 0 && nodeName.contains(ldInst)) {
                    DefaultMutableTreeNode result = searchTreeForFcda(child, ldInst, parts, 1);
                    if (result != null) return result;
                }

                // Match LN level
                if (depth == 1 && parts.length > 0) {
                    String lnRef = parts[0];
                    if (nodeName.equalsIgnoreCase(lnRef) || nodeName.toUpperCase().contains(lnRef.toUpperCase())) {
                        if (parts.length == 1) return child;
                        DefaultMutableTreeNode result = searchTreeForFcda(child, ldInst, parts, 2);
                        if (result != null) return result;
                    }
                }

                // Match DO level
                if (depth == 2 && parts.length > 1) {
                    if (nodeName.equalsIgnoreCase(parts[1])) {
                        if (parts.length == 2) return child;
                        DefaultMutableTreeNode result = searchTreeForFcda(child, ldInst, parts, 3);
                        if (result != null) return result;
                    }
                }

                // Match DA level
                if (depth == 3 && parts.length > 2) {
                    if (nodeName.equalsIgnoreCase(parts[2])) {
                        return child;
                    }
                }
            }

            // Continue searching children at same depth for LD level
            if (depth == 0) {
                DefaultMutableTreeNode result = searchTreeForFcda(child, ldInst, parts, 0);
                if (result != null) return result;
            }
        }
        return null;
    }

    // Establecer valor en servidor (para simulacion)
    private void setSelectedNodeValue(String value) {
        TreePath path = modelTree.getSelectionPath();
        if (path == null || server == null || server.getServerModel() == null) return;

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = treeNode.getUserObject();

        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            String ref = info.node.getReference().toString();

            // Si es un DO, buscar el stVal
            if (info.node instanceof FcDataObject) {
                ref = ref + ".stVal";
            }

            boolean success = server.setDataValue(ref, value);
            if (success) {
                log("SET: " + formatReference(ref) + " = " + value.toUpperCase());
                // Actualizar solo el nodo afectado (rapido, no congela el EDT)
                updateSingleNodeInTree(ref);
                updateServerMonitorValues();

                // Publicar GOOSE si el publisher unico está activo
                if (goosePublisher != null && goosePublisher.isPublishing()) {
                    if (updateGoosePublisherValues()) {
                        goosePublisher.publishStateChange();
                        logGoose("GOOSE publicado: " + formatReference(ref) + " = " + value);
                    }
                }

                // Propagar cambio a multi-publishers activos
                if (!activePublishers.isEmpty()) {
                    propagateValueToPublishers(ref, value);
                }

            } else {
                log("Error estableciendo valor: nodo no encontrado en el modelo (" + ref + ")");
            }
        }
    }

    /**
     * Sincroniza los valores del GOOSE publisher unico con los datos del servidor.
     * Recorre los DataSets de los GoCBs y actualiza los valores desde el modelo.
     * @return true si algun valor cambio
     */
    private boolean updateGoosePublisherValues() {
        if (goosePublisher == null || server == null) return false;

        ServerModel model = server.getServerModel();
        if (model == null) return false;

        // Usar el primer GoCB disponible para el publisher unico
        if (sclGoCBs.isEmpty() || sclDataSets.isEmpty()) return false;

        SclGoCB gcb = sclGoCBs.get(0);
        SclDataSet ds = findDataSetForGoCB(gcb);
        if (ds == null) return false;

        try {
            boolean changed = false;
            List<GoosePublisher.DataValue> pubValues = goosePublisher.getDataValues();

            for (int i = 0; i < ds.members.size() && i < pubValues.size(); i++) {
                String member = ds.members.get(i);
                String modelRef = buildModelRefFromFCDA(member);
                Fc fc = extractFcFromMember(member);
                if (modelRef == null || fc == null) continue;

                ModelNode node = model.findModelNode(modelRef, fc);
                if (node instanceof BasicDataAttribute) {
                    BasicDataAttribute bda = (BasicDataAttribute) node;
                    Object newVal = convertBdaToPublisherValue(bda, pubValues.get(i).type);
                    if (newVal != null && !newVal.equals(pubValues.get(i).value)) {
                        goosePublisher.setDataValue(i, newVal);
                        changed = true;
                    }
                }
            }
            return changed;
        } catch (Exception e) {
            log("Error actualizando valores GOOSE: " + e.getMessage());
            return false;
        }
    }

    /**
     * Propagate a value change from the data model to active GOOSE publishers.
     * Uses the server model as source of truth: reads current values for each
     * FCDA member in the publisher's DataSet directly from the model.
     */
    private void propagateValueToPublishers(String ref, String value) {
        if (server == null || server.getServerModel() == null) return;
        ServerModel model = server.getServerModel();

        for (Map.Entry<Integer, GoosePublisher> entry : activePublishers.entrySet()) {
            int gcbIdx = entry.getKey();
            GoosePublisher pub = entry.getValue();
            if (!pub.isPublishing() || gcbIdx >= sclGoCBs.size()) continue;

            SclGoCB gcb = sclGoCBs.get(gcbIdx);

            // Buscar dataset asociado
            SclDataSet ds = findDataSetForGoCB(gcb);
            if (ds == null) continue;

            boolean changed = false;
            List<GoosePublisher.DataValue> pubValues = pub.getDataValues();

            for (int i = 0; i < ds.members.size() && i < pubValues.size(); i++) {
                String member = ds.members.get(i);
                // Construir referencia iec61850bean desde FCDA
                String modelRef = buildModelRefFromFCDA(member);
                Fc fc = extractFcFromMember(member);
                if (modelRef == null || fc == null) continue;

                ModelNode node = model.findModelNode(modelRef, fc);
                if (node instanceof BasicDataAttribute) {
                    BasicDataAttribute bda = (BasicDataAttribute) node;
                    Object newVal = convertBdaToPublisherValue(bda, pubValues.get(i).type);
                    if (newVal != null && !newVal.equals(pubValues.get(i).value)) {
                        pub.setDataValue(i, newVal);
                        changed = true;
                    }
                }
            }

            if (changed) {
                pub.publishStateChange();
                logGoose("Modelo -> GoCB#" + gcbIdx + " sincronizado (stNum=" + pub.getStNum() + ")");
            }
        }
    }

    /**
     * Find the SclDataSet associated with a GoCB.
     */
    private SclDataSet findDataSetForGoCB(SclGoCB gcb) {
        for (SclDataSet ds : sclDataSets) {
            if (ds.name != null && ds.name.equals(gcb.datSet)
                && (ds.ldInst == null || ds.ldInst.equals(gcb.ldInst))) {
                return ds;
            }
        }
        return null;
    }

    /**
     * Build an iec61850bean model reference from an FCDA member string.
     * FCDA format: "ldInst/prefixLNClassInst.doName.daName [FC]"
     * Model ref format: "IEDNameLDInst/prefixLNClassInst.doName.daName"
     */
    private String buildModelRefFromFCDA(String member) {
        int bracket = member.lastIndexOf('[');
        String clean = bracket > 0 ? member.substring(0, bracket).trim() : member.trim();
        if (clean.isEmpty()) return null;

        if (loadedIedName != null) {
            // Prepend IED name to ldInst: "ldInst/LN..." -> "IEDNameldInst/LN..."
            int slash = clean.indexOf('/');
            if (slash > 0) {
                String ldInst = clean.substring(0, slash);
                String rest = clean.substring(slash + 1);
                return loadedIedName + ldInst + "/" + rest;
            }
        }
        return clean;
    }

    /**
     * Extract the Functional Constraint from an FCDA member string "[FC]".
     */
    private Fc extractFcFromMember(String member) {
        int open = member.lastIndexOf('[');
        int close = member.lastIndexOf(']');
        if (open >= 0 && close > open) {
            String fcStr = member.substring(open + 1, close).trim();
            try { return Fc.valueOf(fcStr); }
            catch (Exception e) { return Fc.ST; }
        }
        return Fc.ST;
    }

    /**
     * Convert a BasicDataAttribute value to the type expected by the GOOSE publisher.
     */
    private Object convertBdaToPublisherValue(BasicDataAttribute bda, GoosePublisher.DataValue.Type targetType) {
        String val = bda.getValueString();
        if (val == null) return null;

        switch (targetType) {
            case BOOLEAN:
                return val.equalsIgnoreCase("true") || val.equals("1");
            case DBPOS:
                String lower = val.toLowerCase();
                if (lower.contains("on") || lower.equals("2")) return 2;
                if (lower.contains("off") || lower.equals("1")) return 1;
                if (lower.contains("bad") || lower.equals("3")) return 3;
                return 0; // intermediate
            case INTEGER:
            case UNSIGNED:
                try { return Integer.parseInt(val); }
                catch (NumberFormatException e) { return 0; }
            case FLOAT:
                try { return Float.parseFloat(val); }
                catch (NumberFormatException e) { return 0.0f; }
            case BITSTRING:
                try { return Integer.parseInt(val); }
                catch (NumberFormatException e) { return 0; }
            default:
                return val;
        }
    }

    // Actualizar valores del monitor en modo servidor
    private void updateServerMonitorValues() {
        ServerModel model = server.getServerModel();
        if (model == null) return;

        int row = 0;
        for (MonitorItem item : monitorItems.values()) {
            if (row >= monitorTableModel.getRowCount()) break;

            // Buscar el nodo actualizado en el modelo del servidor
            try {
                String ref = item.reference;
                Fc fc = Fc.valueOf(item.fc);
                ModelNode node = model.findModelNode(ref, fc);

                if (node instanceof BasicDataAttribute) {
                    BasicDataAttribute bda = (BasicDataAttribute) node;
                    String newVal = formatEnumValue(node, bda.getValueString());
                    if (newVal == null) newVal = "";

                    // Actualizar si cambio
                    if (!newVal.equals(item.value)) {
                        item.oldValue = item.value;
                        item.value = newVal;
                        item.lastChangeTime = System.currentTimeMillis();

                        monitorTableModel.setValueAt(newVal, row, 3);     // Valor
                        monitorTableModel.setValueAt("CHANGED", row, 4);  // Estado
                    }
                }
            } catch (Exception e) {
                // Ignorar errores
            }
            row++;
        }
        monitorTable.repaint();
    }

    private void setSelectedNodeCustomValue() {
        TreePath path = modelTree.getSelectionPath();
        if (path == null || server == null || server.getServerModel() == null) return;

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = treeNode.getUserObject();

        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            String ref = info.node.getReference().toString();
            String currentValue = info.value != null ? info.value : "";

            // Determinar el tipo de dato para mostrar opciones apropiadas
            String newValue = null;

            if (info.node instanceof BdaDoubleBitPos ||
                (info.type != null && info.type.contains("DoubleBit"))) {
                // Mostrar dropdown para DoubleBitPos
                newValue = showDoubleBitPosDialog(info.name, currentValue);
            } else if (info.node instanceof BdaBoolean ||
                       (info.type != null && info.type.equals("Boolean"))) {
                // Mostrar dropdown para Boolean
                newValue = showBooleanDialog(info.name, currentValue);
            } else if (info.node instanceof BdaTapCommand) {
                // Mostrar dropdown para TapCommand
                newValue = showTapCommandDialog(info.name, currentValue);
            } else if (info.node instanceof BdaInt8 || info.node instanceof BdaInt8U) {
                // Puede ser un enum (bType="Enum" → BdaInt8 en iec61850bean)
                int currentOrd = 0;
                try {
                    if (info.node instanceof BdaInt8) currentOrd = ((BdaInt8) info.node).getValue();
                    else currentOrd = ((BdaInt8U) info.node).getValue();
                } catch (Exception ignore) {}
                LinkedHashMap<Integer, String> enumVals = getEnumOptionsForNode(info.node);
                if (enumVals != null && !enumVals.isEmpty()) {
                    newValue = showEnumDialog(info.name, currentOrd, enumVals);
                } else {
                    // No es enum conocido — input numérico
                    newValue = JOptionPane.showInputDialog(this,
                        "Nuevo valor para " + info.name + " (entero):", String.valueOf(currentOrd));
                }
            } else {
                // Otros tipos: usar input de texto
                newValue = JOptionPane.showInputDialog(this,
                    "Nuevo valor para " + info.name + ":",
                    currentValue);
            }

            if (newValue != null && !newValue.isEmpty()) {
                boolean success = server.setDataValue(ref, newValue);
                if (success) {
                    log("SET: " + formatReference(ref) + " = " + newValue);
                    updateSingleNodeInTree(ref);
                    updateServerMonitorValues();

                    // Propagar a GOOSE publishers activos
                    if (goosePublisher != null && goosePublisher.isPublishing()) {
                        if (updateGoosePublisherValues()) {
                            goosePublisher.publishStateChange();
                            logGoose("GOOSE publicado: " + formatReference(ref) + " = " + newValue);
                        }
                    }
                    if (!activePublishers.isEmpty()) {
                        propagateValueToPublishers(ref, newValue);
                    }

                } else {
                    log("Error estableciendo valor: nodo no encontrado en el modelo (" + ref + ")");
                }
            }
        }
    }

    // Dialogo con dropdown para DoubleBitPos
    private String showDoubleBitPosDialog(String name, String currentValue) {
        String[] options = {"INTERMEDIATE_STATE", "OFF", "ON", "BAD_STATE"};
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(currentValue.toUpperCase().replace(" ", "_"));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JLabel("Seleccionar estado para " + name + ":"), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Establecer Estado", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String selected = (String) combo.getSelectedItem();
            // Convertir a formato esperado por el servidor
            switch (selected) {
                case "ON": return "on";
                case "OFF": return "off";
                case "INTERMEDIATE_STATE": return "intermediate";
                case "BAD_STATE": return "bad";
                default: return selected.toLowerCase();
            }
        }
        return null;
    }

    // Dialogo con dropdown para Boolean
    private String showBooleanDialog(String name, String currentValue) {
        String[] options = {"true", "false"};
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(currentValue.toLowerCase());

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JLabel("Seleccionar valor para " + name + ":"), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Establecer Valor", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return (String) combo.getSelectedItem();
        }
        return null;
    }

    // Dialogo con dropdown para TapCommand
    private String showTapCommandDialog(String name, String currentValue) {
        String[] options = {"STOP", "LOWER", "HIGHER", "RESERVED"};
        JComboBox<String> combo = new JComboBox<>(options);
        combo.setSelectedItem(currentValue.toUpperCase());

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(new JLabel("Seleccionar comando para " + name + ":"), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Establecer Comando", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return ((String) combo.getSelectedItem()).toLowerCase();
        }
        return null;
    }

    /**
     * Actualiza un único nodo del árbol por referencia (rápido, no congela el EDT).
     * Usar esto en lugar de updateServerTreeValues() para cambios individuales.
     */
    private void updateSingleNodeInTree(String reference) {
        // Buscar nodo exacto en el mapa
        DefaultMutableTreeNode treeNode = nodeMap.get(reference);
        if (treeNode != null && treeNode.getUserObject() instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) treeNode.getUserObject();
            if (info.node instanceof BasicDataAttribute) {
                info.value = formatEnumValue(info.node, ((BasicDataAttribute) info.node).getValueString());
                treeModel.nodeChanged(treeNode);
                return;
            }
        }
        // Fallback: si no está en nodeMap, buscar por referencia sin FC
        for (Map.Entry<String, DefaultMutableTreeNode> entry : nodeMap.entrySet()) {
            if (entry.getKey().startsWith(reference)) {
                DefaultMutableTreeNode node = entry.getValue();
                if (node.getUserObject() instanceof NodeInfo) {
                    NodeInfo info = (NodeInfo) node.getUserObject();
                    if (info.node instanceof BasicDataAttribute) {
                        info.value = formatEnumValue(info.node, ((BasicDataAttribute) info.node).getValueString());
                        treeModel.nodeChanged(node);
                    }
                }
            }
        }
    }

    private void updateServerTreeValues() {
        ServerModel model = server.getServerModel();
        if (model != null) {
            // Guardar expansion antes de actualizar
            java.util.Enumeration<TreePath> expanded = modelTree.getExpandedDescendants(new TreePath(rootNode));

            updateTreeValues(model);

            // NO usar reload() - solo actualizar nodos visibles
            updateVisibleTreeNodes(rootNode);

            // Restaurar expansion
            if (expanded != null) {
                while (expanded.hasMoreElements()) {
                    modelTree.expandPath(expanded.nextElement());
                }
            }
        }
    }

    private void addSelectedToWatchlist() {
        TreePath path = modelTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        addNodeToWatchlist(treeNode);
        updateWatchlistLabel();
        modelTree.repaint();
    }

    private void addNodeToWatchlist(DefaultMutableTreeNode treeNode) {
        Object userObj = treeNode.getUserObject();
        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if (info.node instanceof FcModelNode) {
                String ref = info.node.getReference().toString();
                Fc fc = ((FcModelNode) info.node).getFc();
                String fullRef = ref + "$" + fc.toString();
                if (watchlist.add(fullRef)) {
                    log("Agregado a watchlist: " + info.name + " [" + fc + "]");
                }
            }
        }
        // Agregar hijos recursivamente
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            addNodeToWatchlist((DefaultMutableTreeNode) treeNode.getChildAt(i));
        }
    }

    private void removeSelectedFromWatchlist() {
        TreePath path = modelTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        removeNodeFromWatchlist(treeNode);
        updateWatchlistLabel();
        modelTree.repaint();
    }

    private void removeNodeFromWatchlist(DefaultMutableTreeNode treeNode) {
        Object userObj = treeNode.getUserObject();
        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if (info.node instanceof FcModelNode) {
                String ref = info.node.getReference().toString();
                Fc fc = ((FcModelNode) info.node).getFc();
                String fullRef = ref + "$" + fc.toString();
                if (watchlist.remove(fullRef)) {
                    log("Quitado de watchlist: " + info.name);
                }
            }
        }
        // Quitar hijos recursivamente
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            removeNodeFromWatchlist((DefaultMutableTreeNode) treeNode.getChildAt(i));
        }
    }

    private void switchToServerMode() {
        // Si estamos conectados como cliente, desconectar primero
        if (isConnected && client != null) {
            disconnect();
            log("Cliente desconectado al cambiar a modo Servidor");
        }
        currentMode = AppMode.SERVER;
        cardLayout.show(cardPanel, "SERVER");
        clearModel();
        updateStatus(false, "Modo Servidor");
    }

    private void switchToClientMode() {
        // Si el servidor esta corriendo, detenerlo primero
        if (isServerRunning && server != null) {
            server.stop();
            isServerRunning = false;
            btnStartStop.setText("Iniciar Simulacion");
            log("Servidor detenido al cambiar a modo Cliente");
        }
        currentMode = AppMode.CLIENT;
        cardLayout.show(cardPanel, "CLIENT");
        clearModel();
        updateStatus(false, "Modo Cliente");
    }

    /**
     * Obtiene el archivo CID del IED conectado
     */
    private void obtenerCidDelIed() {
        if (!isConnected || client == null) {
            JOptionPane.showMessageDialog(this, "Primero conecte a un IED", "No conectado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        log("Buscando archivos SCL/CID en el IED...");

        backgroundExecutor.submit(() -> {
            try {
                // Buscar archivos SCL en el IED
                List<String> sclFiles = client.findSclFiles();

                if (sclFiles.isEmpty()) {
                    // Intentar listar directorio raiz para ver que hay
                    log("No se encontraron archivos SCL. Listando directorio raiz...");
                    try {
                        List<FileInformation> rootFiles = client.listFiles("");
                        if (rootFiles != null && !rootFiles.isEmpty()) {
                            log("Archivos en el IED:");
                            for (FileInformation fi : rootFiles) {
                                log("  - " + fi.getFilename() + " (" + fi.getFileSize() + " bytes)");
                            }
                        }
                    } catch (Exception e) {
                        log("No se pudo listar archivos: " + e.getMessage());
                    }

                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "No se encontraron archivos CID/ICD/SCL en el IED.\nEl IED puede no soportar el servicio de archivos.",
                            "Archivos no encontrados", JOptionPane.INFORMATION_MESSAGE);
                    });
                    return;
                }

                // Si hay varios archivos, permitir seleccionar
                String selectedFile;
                if (sclFiles.size() == 1) {
                    selectedFile = sclFiles.get(0);
                } else {
                    // Mostrar dialogo para seleccionar
                    final String[] files = sclFiles.toArray(new String[0]);
                    final String[] selected = new String[1];

                    SwingUtilities.invokeAndWait(() -> {
                        selected[0] = (String) JOptionPane.showInputDialog(this,
                            "Seleccione el archivo a descargar:",
                            "Archivos SCL encontrados",
                            JOptionPane.QUESTION_MESSAGE,
                            null, files, files[0]);
                    });

                    selectedFile = selected[0];
                    if (selectedFile == null) {
                        log("Descarga cancelada");
                        return;
                    }
                }

                log("Descargando: " + selectedFile);

                // Descargar el archivo
                downloadedCidData = client.downloadFile(selectedFile);
                downloadedCidFilename = selectedFile;

                // Extraer solo el nombre del archivo
                int lastSlash = selectedFile.lastIndexOf('/');
                String filename = lastSlash >= 0 ? selectedFile.substring(lastSlash + 1) : selectedFile;

                log("CID descargado: " + filename + " (" + downloadedCidData.length + " bytes)");

                // Parsear GoCBs del CID descargado
                try {
                    // Guardar temporalmente para parsear
                    File tempFile = File.createTempFile("ied_cid_", ".cid");
                    tempFile.deleteOnExit();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                        fos.write(downloadedCidData);
                    }

                    parseGoCBsFromScl(tempFile);
                    loadedSclFile = tempFile;

                    SwingUtilities.invokeLater(() -> {
                        refreshGooseControlBlocks();
                        JOptionPane.showMessageDialog(this,
                            "CID descargado exitosamente:\n" + filename + "\n\nGoCBs encontrados: " + sclGoCBs.size() +
                            "\n\nUse 'Guardar CID' para guardarlo en disco.",
                            "CID Descargado", JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (Exception parseEx) {
                    log("Error parseando CID: " + parseEx.getMessage());
                }

            } catch (Exception e) {
                log("Error obteniendo CID: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Error obteniendo CID:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    /**
     * Auto-descarga el CID del IED silenciosamente (sin dialogos)
     * para obtener GoCBs, Reports, etc.
     */
    private void autoDownloadCid() {
        if (!isConnected || client == null) return;

        log("Buscando CID en el IED para obtener GoCBs...");

        backgroundExecutor.submit(() -> {
            try {
                // Buscar archivos SCL en el IED
                List<String> sclFiles = client.findSclFiles();

                if (sclFiles.isEmpty()) {
                    log("No se encontro CID en el IED - GoCBs no disponibles via SCL");
                    log("Nota: Para ver GoCBs, cargue manualmente un archivo SCL (Archivo -> Cargar SCL/CID)");
                    return;
                }

                // Preferir archivos .cid sobre .icd
                String selectedFile = null;
                for (String f : sclFiles) {
                    if (f.toLowerCase().endsWith(".cid")) {
                        selectedFile = f;
                        break;
                    }
                }
                if (selectedFile == null) {
                    selectedFile = sclFiles.get(0);
                }

                log("Descargando CID automaticamente: " + selectedFile);

                // Descargar el archivo
                downloadedCidData = client.downloadFile(selectedFile);
                downloadedCidFilename = selectedFile;

                // Extraer nombre
                int lastSlash = selectedFile.lastIndexOf('/');
                String filename = lastSlash >= 0 ? selectedFile.substring(lastSlash + 1) : selectedFile;

                log("CID descargado: " + filename + " (" + downloadedCidData.length + " bytes)");

                // Parsear GoCBs del CID descargado
                File tempFile = File.createTempFile("ied_cid_auto_", ".cid");
                tempFile.deleteOnExit();
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                    fos.write(downloadedCidData);
                }

                parseGoCBsFromScl(tempFile);
                loadedSclFile = tempFile;

                // Actualizar UI
                SwingUtilities.invokeLater(() -> {
                    // Refrescar panel de GoCBs
                    refreshGooseControlBlocks();
                    log("GoCBs cargados automaticamente del CID");
                });

            } catch (Exception e) {
                log("Auto-descarga CID falló: " + e.getMessage());
                log("Para ver GoCBs, cargue un archivo SCL manualmente");
            }
        });
    }

    /**
     * Guarda el CID descargado en disco
     */
    private void guardarCid() {
        if (downloadedCidData == null || downloadedCidData.length == 0) {
            JOptionPane.showMessageDialog(this,
                "No hay CID descargado.\nPrimero use 'Obtener CID' para descargar el archivo del IED.",
                "Sin CID", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar CID");

        // Nombre sugerido
        String suggestedName = downloadedCidFilename;
        if (suggestedName != null) {
            int lastSlash = suggestedName.lastIndexOf('/');
            if (lastSlash >= 0) {
                suggestedName = suggestedName.substring(lastSlash + 1);
            }
        } else {
            suggestedName = "ied_config.cid";
        }
        fc.setSelectedFile(new File(suggestedName));

        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".cid") || name.endsWith(".icd") || name.endsWith(".scl");
            }
            public String getDescription() {
                return "SCL Files (*.cid, *.icd, *.scl)";
            }
        });

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();

            // Asegurar extension
            if (!file.getName().toLowerCase().endsWith(".cid") &&
                !file.getName().toLowerCase().endsWith(".icd") &&
                !file.getName().toLowerCase().endsWith(".scl")) {
                file = new File(file.getAbsolutePath() + ".cid");
            }

            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(downloadedCidData);
                log("CID guardado en: " + file.getAbsolutePath());
                JOptionPane.showMessageDialog(this,
                    "CID guardado exitosamente:\n" + file.getAbsolutePath(),
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                log("Error guardando CID: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Error guardando archivo:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void selectSclFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".icd") || name.endsWith(".cid") ||
                       name.endsWith(".scd") || name.endsWith(".scl");
            }
            public String getDescription() {
                return "SCL Files (*.icd, *.cid, *.scd, *.scl)";
            }
        });

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            loadedSclFile = file; // Almacenar archivo para extraer GoCBs
            lblFileName.setText(file.getName());
            updateStatus(false, "Analizando SCL...");
            statusIndicator.setBackground(COLOR_CONNECTING);

            backgroundExecutor.submit(() -> {
                try {
                    log("Analizando: " + file.getName() + " (" + (file.length()/1024) + " KB)");

                    // Primero obtener lista de IEDs disponibles
                    List<String> availableIEDs = server.getAvailableIEDs(file.getAbsolutePath());
                    int iedCount = availableIEDs.size();
                    log("IEDs encontrados: " + iedCount);

                    int selectedIED = 0; // Por defecto el primero

                    // Si hay múltiples IEDs, mostrar diálogo de selección
                    if (iedCount > 1) {
                        final int[] selection = {-1};
                        SwingUtilities.invokeAndWait(() -> {
                            selection[0] = showIEDSelectionDialog(availableIEDs, file.getName());
                        });

                        if (selection[0] < 0) {
                            // Usuario canceló
                            SwingUtilities.invokeLater(() -> {
                                updateStatus(false, "Carga cancelada");
                                lblFileName.setText("");
                            });
                            return;
                        }
                        selectedIED = selection[0];
                        log("IED seleccionado: " + availableIEDs.get(selectedIED) + " (índice " + selectedIED + ")");
                    }

                    // Cargar el IED seleccionado
                    SwingUtilities.invokeLater(() -> updateStatus(false, "Cargando IED..."));
                    long startTime = System.currentTimeMillis();

                    boolean success = server.loadSclFileWithIED(file.getAbsolutePath(), selectedIED);

                    // Parsear GoCBs del SCL (filtrado por IED seleccionado)
                    parseGoCBsFromScl(file, selectedIED);

                    long elapsed = System.currentTimeMillis() - startTime;
                    log("Parsing completado en " + elapsed + "ms, success=" + success);
                    log("GoCBs encontrados en SCL: " + sclGoCBs.size());

                    final int finalSelectedIED = selectedIED;
                    SwingUtilities.invokeLater(() -> {
                        try {
                            if (success) {
                                btnStartStop.setEnabled(true);
                                String iedName = availableIEDs.size() > finalSelectedIED ?
                                    availableIEDs.get(finalSelectedIED) : "IED";
                                updateStatus(false, "SCL cargado - " + iedName);
                                lblFileName.setText(file.getName() + " [" + iedName + "]");
                                // Mostrar nameplate del IED en status bar
                                if (loadedIedNameplate != null) {
                                    String mfr  = loadedIedNameplate[0].isEmpty() ? "?" : loadedIedNameplate[0];
                                    String type = loadedIedNameplate[1].isEmpty() ? "?" : loadedIedNameplate[1];
                                    String cfgV = loadedIedNameplate[3].isEmpty() ? "" : "  cfg:" + loadedIedNameplate[3];
                                    String plate = String.format("  IED: %s  |  Fabricante: %s  |  Tipo: %s%s",
                                        iedName, mfr, type, cfgV);
                                    lblIedInfo.setText(plate);
                                    log("Nameplate: fabricante=" + mfr + " tipo=" + type + cfgV.trim());
                                }
                                log("Construyendo arbol...");
                                displayServerModel();
                                log("SCL cargado correctamente");
                                // Actualizar GoCBs automaticamente
                                refreshGooseControlBlocks();
                            } else {
                                updateStatus(false, "Error cargando SCL");
                                log("ERROR: No se pudo cargar el SCL");
                            }
                        } catch (Exception e) {
                            log("ERROR en UI: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    log("ERROR en background: " + e.getMessage());
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        updateStatus(false, "Error: " + e.getMessage());
                    });
                }
            });
        }
    }

    /**
     * Muestra diálogo para seleccionar IED cuando hay múltiples en el archivo
     */
    private int showIEDSelectionDialog(List<String> ieds, String fileName) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel lblInfo = new JLabel("<html>El archivo <b>" + fileName + "</b> contiene " +
            ieds.size() + " IEDs.<br>Seleccione cuál desea simular:</html>");
        panel.add(lblInfo, BorderLayout.NORTH);

        // Lista de IEDs con iconos
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (int i = 0; i < ieds.size(); i++) {
            listModel.addElement((i + 1) + ". " + ieds.get(i));
        }
        JList<String> iedList = new JList<>(listModel);
        iedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        iedList.setSelectedIndex(0);
        iedList.setVisibleRowCount(Math.min(ieds.size(), 8));
        iedList.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(iedList);
        scrollPane.setPreferredSize(new Dimension(350, 150));
        panel.add(scrollPane, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Seleccionar IED", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return iedList.getSelectedIndex();
        }
        return -1; // Cancelado
    }

    private void toggleServer() {
        if (isServerRunning) {
            server.stop();
            isServerRunning = false;
            btnStartStop.setText("Iniciar Simulacion");
            updateStatus(false, "Simulacion detenida");
            updateConnectionInfo("", 0);
            log("Simulacion IED detenida");
        } else {
            try {
                int port = Integer.parseInt(tfServerPort.getText().trim());
                updateStatus(false, "Iniciando simulacion IED...");
                statusIndicator.setBackground(COLOR_CONNECTING);

                final int finalPort = port;
                backgroundExecutor.submit(() -> {
                    boolean success = server.start(finalPort);

                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            isServerRunning = true;
                            currentPort = finalPort;
                            String localIp = getLocalIpAddress();
                            currentHost = localIp;
                            btnStartStop.setText("Detener Simulacion");
                            updateStatus(true, "IED Simulado ACTIVO");
                            updateConnectionInfo(localIp + " (servidor)", finalPort);
                            log("SIMULACION IED ACTIVA");
                            log("IP: " + localIp + " | Puerto: " + finalPort);
                            log("Conecta cliente a: " + localIp + ":" + finalPort);

                            // Auto-seleccionar interfaz de red para GOOSE (igual que en modo cliente)
                            autoSelectGooseInterface(localIp);
                        } else {
                            updateStatus(false, "Error iniciando simulacion");
                            updateConnectionInfo("", 0);
                            log("ERROR: No se pudo iniciar el servidor");
                            log("Verifica que el puerto " + finalPort + " no este en uso");
                        }
                    });
                });

            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Puerto invalido");
            }
        }
    }

    private void toggleConnection() {
        if (isConnected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        String host = tfHost.getText().trim();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese el host");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(tfClientPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Puerto invalido");
            return;
        }

        btnConnect.setEnabled(false);
        updateStatus(false, "Conectando a " + host + ":" + port + "...");
        statusIndicator.setBackground(COLOR_CONNECTING);
        log("Conectando a " + host + ":" + port + "...");

        backgroundExecutor.submit(() -> {
            try {
                log("Iniciando conexion...");
                long startTime = System.currentTimeMillis();

                client.connect(host, port);

                long elapsed = System.currentTimeMillis() - startTime;
                log("Conexion establecida en " + elapsed + "ms");

                // Detectar interfaz local usada para la conexion
                String localIp = detectLocalInterface(host);
                log("Interfaz local detectada: " + localIp);

                final String finalHost = host;
                final int finalPort = port;
                final String finalLocalIp = localIp;
                SwingUtilities.invokeLater(() -> {
                    try {
                        isConnected = true;
                        currentHost = finalHost;
                        currentPort = finalPort;
                        connectedLocalIp = finalLocalIp;
                        btnConnect.setText("Desconectar");
                        btnConnect.setEnabled(true);
                        cbPolling.setEnabled(true);
                        spinnerInterval.setEnabled(true);
                        updateStatus(true, "Conectado");
                        updateConnectionInfo(finalHost, finalPort);
                        log("Construyendo arbol del modelo...");
                        displayClientModel();
                        log("Conectado! Modelo recibido.");

                        // Leer placa de identificación del IED (FC=DC) en background
                        backgroundExecutor.submit(() -> {
                            Map<String, String> plate = client.readDeviceNameplate();
                            if (!plate.isEmpty()) {
                                String info = String.format("  IED: %s  |  FW: %s  |  Config: %s",
                                    plate.getOrDefault("vendor",    "?"),
                                    plate.getOrDefault("swRev",     "?"),
                                    plate.getOrDefault("configRev", "?"));
                                SwingUtilities.invokeLater(() -> {
                                    lblIedInfo.setText(info);
                                    log("Placa IED →" + info.trim());
                                });
                            }
                        });

                        // Auto-seleccionar interfaz de red para GOOSE
                        autoSelectGooseInterface(finalLocalIp);

                        // Auto-descargar CID para obtener GoCBs (en background)
                        autoDownloadCid();
                    } catch (Exception e) {
                        log("ERROR en UI despues de conexion: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

            } catch (Exception e) {
                log("ERROR de conexion: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setEnabled(true);
                    updateStatus(false, "Error: " + e.getMessage());
                });
            }
        });
    }

    private void disconnect() {
        stopPolling();
        client.disconnect();
        handleDisconnect();
        log("Desconectado");
    }

    private void handleDisconnect() {
        isConnected = false;
        currentHost = "";
        currentPort = 0;
        connectedLocalIp = "";
        btnConnect.setText("Conectar");
        btnConnect.setEnabled(true);
        cbPolling.setEnabled(false);
        cbPolling.setSelected(false);
        spinnerInterval.setEnabled(false);
        updateStatus(false, "Desconectado");
        updateConnectionInfo("", 0);
        lblIedInfo.setText(" ");
        clearModel();
    }

    /**
     * Detecta la interfaz local usada para conectar a un host remoto.
     * Crea una conexion temporal para determinar cual IP local se usaria.
     */
    private String detectLocalInterface(String remoteHost) {
        try {
            // Crear socket temporal para detectar la ruta
            java.net.DatagramSocket socket = new java.net.DatagramSocket();
            socket.connect(java.net.InetAddress.getByName(remoteHost), 102);
            String localIp = socket.getLocalAddress().getHostAddress();
            socket.close();
            return localIp;
        } catch (Exception e) {
            log("No se pudo detectar interfaz local: " + e.getMessage());
            return "";
        }
    }

    /**
     * Auto-selecciona la interfaz de red en el combo de GOOSE
     * basandose en la IP local usada para la conexion MMS.
     */
    private void autoSelectGooseInterface(String localIp) {
        if (localIp == null || localIp.isEmpty() || gooseInterfaceCombo == null) {
            log("autoSelectGooseInterface: parametros invalidos - localIp=" + localIp);
            return;
        }

        log("=== AUTO-SELECCION DE INTERFAZ ===");
        log("Buscando interfaz para IP: " + localIp);
        log("Interfaces disponibles (" + gooseInterfaceCombo.getItemCount() + "):");
        for (int i = 0; i < gooseInterfaceCombo.getItemCount(); i++) {
            String item = gooseInterfaceCombo.getItemAt(i);
            boolean matches = item != null && item.contains(localIp);
            log("  [" + i + "] " + item + (matches ? " <-- MATCH" : ""));
        }

        // Buscar en el combo la interfaz que contenga esta IP
        for (int i = 0; i < gooseInterfaceCombo.getItemCount(); i++) {
            String item = gooseInterfaceCombo.getItemAt(i);
            if (item != null && item.contains(localIp)) {
                gooseInterfaceCombo.setSelectedIndex(i);
                logGoose("*** INTERFAZ AUTO-SELECCIONADA: " + item);
                log(">>> SELECCIONADA: " + item);

                // Tambien seleccionar en SV si existe
                if (svInterfaceCombo != null) {
                    for (int j = 0; j < svInterfaceCombo.getItemCount(); j++) {
                        String svItem = svInterfaceCombo.getItemAt(j);
                        if (svItem != null && svItem.contains(localIp)) {
                            svInterfaceCombo.setSelectedIndex(j);
                            break;
                        }
                    }
                }
                return;
            }
        }

        // Si no encontro por IP exacta, buscar por segmento de red (192.168.100.x)
        String[] parts = localIp.split("\\.");
        if (parts.length >= 3) {
            String subnet = parts[0] + "." + parts[1] + "." + parts[2] + ".";
            log("Buscando por subred: " + subnet);
            for (int i = 0; i < gooseInterfaceCombo.getItemCount(); i++) {
                String item = gooseInterfaceCombo.getItemAt(i);
                if (item != null && item.contains(subnet)) {
                    gooseInterfaceCombo.setSelectedIndex(i);
                    logGoose("*** INTERFAZ AUTO-SELECCIONADA (subred): " + item);
                    log("Interfaz GOOSE seleccionada por subred: " + subnet);
                    return;
                }
            }
        }

        log("ADVERTENCIA: No se encontro interfaz para " + localIp + " - Seleccione manualmente");
        logGoose("ADVERTENCIA: Seleccione la interfaz correcta manualmente!");
    }

    private void updateConnectionInfo(String host, int port) {
        if (host == null || host.isEmpty() || port == 0) {
            lblConnectionInfo.setText("Sin conexion");
            lblConnectionInfo.setForeground(Color.GRAY);
        } else {
            lblConnectionInfo.setText(host + ":" + port);
            lblConnectionInfo.setForeground(new Color(0, 100, 0));
        }
    }

    // Obtener IP local del sistema (sin priorización - retorna la primera IP privada)
    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                java.net.NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Solo considerar IPs de red privada (no link-local)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") ||
                            (ip.startsWith("172.") && !ip.startsWith("169.254."))) {
                            return ip;
                        }
                    }
                }
            }
            // Fallback
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private void togglePolling() {
        if (cbPolling.isSelected()) {
            startPolling();
        } else {
            stopPolling();
        }
    }

    private void startPolling() {
        if (pollExecutor != null) {
            pollExecutor.shutdown();
        }

        int interval = (Integer) spinnerInterval.getValue();
        pollExecutor = Executors.newSingleThreadScheduledExecutor();

        pollExecutor.scheduleAtFixedRate(() -> {
            if (isConnected) {
                try {
                    refreshAllValues();
                } catch (Exception e) {
                    log("Polling error: " + e.getMessage());
                }
            }
        }, 0, interval, TimeUnit.MILLISECONDS);

        log("Polling iniciado (cada " + interval + "ms)");
    }

    private void stopPolling() {
        if (pollExecutor != null) {
            pollExecutor.shutdown();
            pollExecutor = null;
            log("Polling detenido");
        }
    }

    private void refreshAllValues() {
        ServerModel model = client.getServerModel();
        if (model == null) return;

        int readCount = 0;

        // Si hay items en watchlist, usar solo esos (MUY rapido)
        if (!watchlist.isEmpty()) {
            readCount = pollWatchlistItems(model);
        } else {
            // Sin watchlist: leer nodos visibles/expandidos en el arbol
            List<FcModelNode> nodesToRead = new ArrayList<>();
            collectVisibleNodes(rootNode, nodesToRead);

            for (FcModelNode node : nodesToRead) {
                try {
                    client.readNodeValues(node);
                    readCount++;
                } catch (Exception e) {
                    // Continuar con otros nodos
                }
            }
        }

        final int finalCount = readCount;
        final boolean usingWatchlist = !watchlist.isEmpty();

        // Actualizar arbol y monitor en el hilo de UI
        SwingUtilities.invokeLater(() -> {
            updateVisibleTreeNodes(rootNode);
            updateMonitorValues();  // Actualizar tabla del monitor
            if (finalCount > 0) {
                String mode = usingWatchlist ? "watchlist" : "visibles";
                log("Polling: " + finalCount + " nodos (" + mode + ")");
            }
        });
    }

    // Leer solo los items de la watchlist (muy rapido)
    private int pollWatchlistItems(ServerModel model) {
        int count = 0;
        for (String fullRef : watchlist) {
            try {
                // Parsear referencia: "LD/LN.DO.DA$FC"
                int idx = fullRef.lastIndexOf("$");
                if (idx < 0) continue;

                String ref = fullRef.substring(0, idx);
                String fcStr = fullRef.substring(idx + 1);
                Fc fc = Fc.valueOf(fcStr);

                ModelNode node = model.findModelNode(ref, fc);
                if (node instanceof FcModelNode) {
                    client.readNodeValues((FcModelNode) node);
                    count++;
                }
            } catch (Exception e) {
                // Continuar con otros
            }
        }
        return count;
    }

    // Recolectar solo los nodos ST/MX que estan expandidos/visibles
    private void collectVisibleNodes(DefaultMutableTreeNode treeNode, List<FcModelNode> result) {
        TreePath path = new TreePath(treeNode.getPath());

        // Si este nodo no esta expandido o visible, saltar
        if (treeNode != rootNode && !modelTree.isVisible(path)) {
            return;
        }

        Object userObj = treeNode.getUserObject();
        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if (info.node instanceof FcModelNode) {
                FcModelNode fcNode = (FcModelNode) info.node;
                Fc fc = fcNode.getFc();
                // Solo ST y MX
                if (fc == Fc.ST || fc == Fc.MX) {
                    result.add(fcNode);
                }
            }
        }

        // Procesar hijos solo si esta expandido
        if (modelTree.isExpanded(path) || treeNode == rootNode) {
            for (int i = 0; i < treeNode.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeNode.getChildAt(i);
                collectVisibleNodes(child, result);
            }
        }
    }

    // Actualizar valores mostrados en nodos visibles
    private void updateVisibleTreeNodes(DefaultMutableTreeNode treeNode) {
        Object userObj = treeNode.getUserObject();
        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if (info.node instanceof BasicDataAttribute) {
                BasicDataAttribute bda = (BasicDataAttribute) info.node;
                String newValue = formatEnumValue(info.node, bda.getValueString());
                if (newValue == null) newValue = "";
                if (!newValue.equals(info.value == null ? "" : info.value)) {
                    info.value = newValue;
                    treeModel.nodeChanged(treeNode);
                }
            }
        }

        // Procesar hijos
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeNode.getChildAt(i);
            updateVisibleTreeNodes(child);
        }
    }

    /**
     * Obtiene el nodo blkEna[BL] del DO seleccionado en el árbol (cliente).
     * Busca en el DO seleccionado o en el nodo actual si ya es blkEna[BL].
     */
    private com.beanit.iec61850bean.FcModelNode getSelectedBlkEnaNode() {
        TreePath path = modelTree.getSelectionPath();
        if (path == null) return null;
        Object userObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(userObj instanceof NodeInfo)) return null;
        NodeInfo info = (NodeInfo) userObj;
        if (info.node == null) return null;

        // Si el nodo seleccionado ya es blkEna[BL]
        if ("blkEna".equalsIgnoreCase(info.node.getName()) && "BL".equals(info.fc)) {
            return info.node instanceof com.beanit.iec61850bean.FcModelNode
                ? (com.beanit.iec61850bean.FcModelNode) info.node : null;
        }

        // Si es un DO, buscar blkEna entre sus hijos
        if ("DO".equals(info.prefix)) {
            String doRef = info.node.getReference().toString();
            return client.findBlkEnaNode(doRef);
        }
        return null;
    }

    /** Activa o desactiva el bloqueo (blkEna) del DO seleccionado en modo cliente. */
    private void toggleBlocking(boolean block) {
        com.beanit.iec61850bean.FcModelNode blkNode = getSelectedBlkEnaNode();
        if (blkNode == null) {
            log("Este nodo no soporta FC=BL (blkEna no encontrado)");
            return;
        }
        backgroundExecutor.submit(() -> {
            try {
                client.setBlocking(blkNode, block);
                String ref = blkNode.getReference().toString();
                SwingUtilities.invokeLater(() -> {
                    log((block ? "Bloqueado" : "Desbloqueado") + ": " + ref);
                    // Refrescar el nodo en el árbol
                    TreePath path = modelTree.getSelectionPath();
                    if (path != null) {
                        updateTreeNodeRecursive((DefaultMutableTreeNode) path.getLastPathComponent());
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> log("Error setBlocking: " + ex.getMessage()));
            }
        });
    }

    private void readSelectedNode() {
        TreePath path = modelTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = treeNode.getUserObject();

        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if (info.node instanceof FcModelNode) {
                try {
                    log("Leyendo: " + info.node.getReference());
                    client.readNodeValues((FcModelNode) info.node);

                    // Actualizar este nodo si es BDA
                    if (info.node instanceof BasicDataAttribute) {
                        BasicDataAttribute bda = (BasicDataAttribute) info.node;
                        info.value = bda.getValueString();
                        treeModel.nodeChanged(treeNode);
                        log("Valor: " + info.value);
                    } else {
                        // Si es un DO/SDO, actualizar todos los hijos
                        updateTreeNodeRecursive(treeNode);
                        log("DO actualizado con hijos");
                    }
                } catch (Exception e) {
                    log("Error leyendo: " + e.getMessage());
                }
            }
        }
    }

    private void updateTreeNodeRecursive(DefaultMutableTreeNode treeNode) {
        Object userObj = treeNode.getUserObject();
        if (userObj instanceof NodeInfo) {
            NodeInfo info = (NodeInfo) userObj;
            if (info.node instanceof BasicDataAttribute) {
                BasicDataAttribute bda = (BasicDataAttribute) info.node;
                info.value = bda.getValueString();
                treeModel.nodeChanged(treeNode);
            }
        }
        // Actualizar hijos
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeNode.getChildAt(i);
            updateTreeNodeRecursive(child);
        }
    }

    private void displayServerModel() {
        ServerModel model = server.getServerModel();
        if (model == null) {
            log("ERROR: ServerModel es null");
            return;
        }
        log("ServerModel tiene " + model.getChildren().size() + " LDs");
        buildTree(model);
    }

    private void displayClientModel() {
        ServerModel model = client.getServerModel();
        if (model == null) {
            log("ERROR: Cliente ServerModel es null");
            return;
        }
        log("Cliente ServerModel tiene " + model.getChildren().size() + " LDs");
        buildTree(model);
    }

    private void buildTree(ServerModel model) {
        try {
            log("buildTree: iniciando...");
            rootNode.removeAllChildren();
            nodeMap.clear();

            int nodeCount = countNodes(model);
            log("buildTree: " + nodeCount + " nodos totales");
            rootNode.setUserObject("Modelo (" + nodeCount + " nodos)");

            int ldIdx = 0;
            for (ModelNode ld : model.getChildren()) {
                ldIdx++;
                DefaultMutableTreeNode ldNode = createTreeNode(ld, "LD");
                rootNode.add(ldNode);
                buildTreeRecursive(ldNode, ld);

                String ldName = ld.getName();
                // Extraer solo el nombre del LD (sin prefijo IED)
                if (ldName.contains("/")) {
                    ldName = ldName.substring(ldName.indexOf("/") + 1);
                }

                // Agregar DataSets bajo este LD (como IEDScout)
                addDataSetsToLdNode(ldNode, ldName);

                // Agregar ReportControl bajo este LD
                addReportsToLdNode(ldNode, ldName);

                // Agregar GoCBs bajo este LD (como IEDScout)
                addGoCBsToLdNode(ldNode, ld.getName());
            }
            log("buildTree: " + ldIdx + " LDs agregados");

            treeModel.reload();
            log("buildTree: arbol recargado");

            // Expandir primer nivel
            modelTree.expandRow(0);
            log("buildTree: completado");
        } catch (Exception e) {
            log("ERROR en buildTree: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Cache para DataSets y Reports parseados del SCL
    private List<SclDataSet> sclDataSets = new ArrayList<>();
    private List<SclReport> sclReports = new ArrayList<>();

    private static class SclDataSet {
        String ldInst;
        String lnClass;
        String name;
        String desc;
        List<String> members = new ArrayList<>();
    }

    private static class SclReport {
        String ldInst;
        String lnClass;
        String name;
        String rptID;
        String datSet;
        boolean buffered;
        int confRev;
    }

    /**
     * Agrega nodos DataSet al árbol bajo el LD especificado
     */
    private void addDataSetsToLdNode(DefaultMutableTreeNode ldNode, String ldName) {
        // Buscar DataSets que pertenecen a este LD
        List<SclDataSet> ldDataSets = new ArrayList<>();
        for (SclDataSet ds : sclDataSets) {
            if (ds.ldInst != null && ds.ldInst.equals(ldName)) {
                ldDataSets.add(ds);
            }
        }

        if (ldDataSets.isEmpty()) return;

        // Crear nodo contenedor "DataSets"
        NodeInfo containerInfo = new NodeInfo();
        containerInfo.name = "DataSets (" + ldDataSets.size() + ")";
        containerInfo.prefix = "DS";
        DefaultMutableTreeNode dsContainer = new DefaultMutableTreeNode(containerInfo);

        // Agregar cada DataSet
        for (SclDataSet ds : ldDataSets) {
            NodeInfo dsInfo = new NodeInfo();
            dsInfo.name = ds.name;
            dsInfo.prefix = "DSET";
            dsInfo.value = ds.desc != null ? ds.desc : "";
            DefaultMutableTreeNode dsNode = new DefaultMutableTreeNode(dsInfo);

            // Agregar miembros del DataSet
            int idx = 0;
            for (String member : ds.members) {
                NodeInfo memberInfo = new NodeInfo();
                memberInfo.name = "[" + idx + "] " + member;
                memberInfo.prefix = "FCDA";
                dsNode.add(new DefaultMutableTreeNode(memberInfo));
                idx++;
            }

            dsContainer.add(dsNode);
        }

        // Insertar despues de LLN0
        int insertIndex = findInsertIndexAfterLLN0(ldNode);
        ldNode.insert(dsContainer, insertIndex);
    }

    /**
     * Agrega nodos ReportControl al árbol bajo el LD especificado
     */
    private void addReportsToLdNode(DefaultMutableTreeNode ldNode, String ldName) {
        List<SclReport> ldReports = new ArrayList<>();
        for (SclReport rpt : sclReports) {
            if (rpt.ldInst != null && rpt.ldInst.equals(ldName)) {
                ldReports.add(rpt);
            }
        }

        if (ldReports.isEmpty()) return;

        // Separar en buffered y unbuffered
        List<SclReport> brcbs = new ArrayList<>();
        List<SclReport> urcbs = new ArrayList<>();
        for (SclReport rpt : ldReports) {
            if (rpt.buffered) brcbs.add(rpt);
            else urcbs.add(rpt);
        }

        // Agregar BRCB (Buffered Reports)
        if (!brcbs.isEmpty()) {
            NodeInfo brcbInfo = new NodeInfo();
            brcbInfo.name = "BRCB (" + brcbs.size() + ")";
            brcbInfo.prefix = "RPT";
            DefaultMutableTreeNode brcbContainer = new DefaultMutableTreeNode(brcbInfo);

            for (SclReport rpt : brcbs) {
                addReportNode(brcbContainer, rpt);
            }

            int insertIndex = findInsertIndexAfterLLN0(ldNode) + 1;
            ldNode.insert(brcbContainer, Math.min(insertIndex, ldNode.getChildCount()));
        }

        // Agregar URCB (Unbuffered Reports)
        if (!urcbs.isEmpty()) {
            NodeInfo urcbInfo = new NodeInfo();
            urcbInfo.name = "URCB (" + urcbs.size() + ")";
            urcbInfo.prefix = "RPT";
            DefaultMutableTreeNode urcbContainer = new DefaultMutableTreeNode(urcbInfo);

            for (SclReport rpt : urcbs) {
                addReportNode(urcbContainer, rpt);
            }

            int insertIndex = findInsertIndexAfterLLN0(ldNode) + 2;
            ldNode.insert(urcbContainer, Math.min(insertIndex, ldNode.getChildCount()));
        }
    }

    private void addReportNode(DefaultMutableTreeNode container, SclReport rpt) {
        NodeInfo rptInfo = new NodeInfo();
        rptInfo.name = rpt.name;
        rptInfo.prefix = rpt.buffered ? "BRCB" : "URCB";
        DefaultMutableTreeNode rptNode = new DefaultMutableTreeNode(rptInfo);

        addGocbProperty(rptNode, "rptID", rpt.rptID);
        addGocbProperty(rptNode, "datSet", rpt.datSet);
        addGocbProperty(rptNode, "confRev", String.valueOf(rpt.confRev));

        container.add(rptNode);
    }

    private int findInsertIndexAfterLLN0(DefaultMutableTreeNode ldNode) {
        for (int i = 0; i < ldNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) ldNode.getChildAt(i);
            Object obj = child.getUserObject();
            if (obj instanceof NodeInfo) {
                NodeInfo info = (NodeInfo) obj;
                if ("LLN0".equals(info.name)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    /**
     * Agrega nodos GoCB al arbol bajo el LD especificado
     * Similar a como IEDScout muestra los GOOSE en el modelo de datos
     */
    private void addGoCBsToLdNode(DefaultMutableTreeNode ldNode, String ldName) {
        if (sclGoCBs.isEmpty()) return;

        // Extraer nombre corto del LD (sin prefijo IED/)
        String shortLdName = ldName;
        if (ldName.contains("/")) {
            shortLdName = ldName.substring(ldName.indexOf("/") + 1);
        }

        // Buscar GoCBs que pertenecen a este LD
        List<SclGoCB> ldGocbs = new ArrayList<>();
        for (SclGoCB gcb : sclGoCBs) {
            if (gcb.ldInst != null && (gcb.ldInst.equals(ldName) || gcb.ldInst.equals(shortLdName))) {
                ldGocbs.add(gcb);
            }
        }

        if (ldGocbs.isEmpty()) return;

        // Crear nodo contenedor "GOOSE"
        NodeInfo containerInfo = new NodeInfo();
        containerInfo.name = "GOOSE (" + ldGocbs.size() + ")";
        containerInfo.prefix = "GoCB";
        containerInfo.isGocbContainer = true;
        DefaultMutableTreeNode gooseContainer = new DefaultMutableTreeNode(containerInfo);

        // Agregar cada GoCB
        for (SclGoCB gcb : ldGocbs) {
            NodeInfo gcbInfo = new NodeInfo();
            gcbInfo.name = gcb.cbName;
            gcbInfo.prefix = "GCB";
            gcbInfo.gocb = gcb;
            gcbInfo.value = gcb.goID != null ? gcb.goID : "";
            DefaultMutableTreeNode gcbNode = new DefaultMutableTreeNode(gcbInfo);

            // Agregar propiedades del GoCB como hijos
            addGocbProperty(gcbNode, "goID", gcb.goID);
            addGocbProperty(gcbNode, "confRev", String.valueOf(gcb.confRev));
            addGocbProperty(gcbNode, "appID", gcb.appID);
            if (gcb.macAddress != null) {
                addGocbProperty(gcbNode, "MAC", gcb.macAddress);
            }

            // Agregar el DataSet asociado con sus miembros
            addDataSetToGoCB(gcbNode, gcb.datSet, shortLdName);

            gooseContainer.add(gcbNode);
        }

        // Insertar el contenedor GOOSE despues de LLN0 (si existe)
        int insertIndex = 0;
        for (int i = 0; i < ldNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) ldNode.getChildAt(i);
            Object obj = child.getUserObject();
            if (obj instanceof NodeInfo) {
                NodeInfo info = (NodeInfo) obj;
                if ("LLN0".equals(info.name)) {
                    insertIndex = i + 1;
                    break;
                }
            }
        }
        ldNode.insert(gooseContainer, Math.min(insertIndex, ldNode.getChildCount()));
    }

    private void addGocbProperty(DefaultMutableTreeNode parent, String name, String value) {
        if (value == null || value.isEmpty()) return;
        NodeInfo propInfo = new NodeInfo();
        propInfo.name = name;
        propInfo.prefix = "ATTR";
        propInfo.value = value;
        parent.add(new DefaultMutableTreeNode(propInfo));
    }

    /**
     * Agrega el DataSet asociado a un GoCB mostrando sus miembros
     */
    private void addDataSetToGoCB(DefaultMutableTreeNode gcbNode, String datSetName, String ldInst) {
        if (datSetName == null || datSetName.isEmpty()) return;

        // Buscar el DataSet por nombre en el LD correspondiente
        SclDataSet foundDs = null;
        for (SclDataSet ds : sclDataSets) {
            if (ds.name != null && ds.name.equals(datSetName)) {
                // Verificar que pertenece al mismo LD o es un DataSet global
                if (ds.ldInst == null || ds.ldInst.equals(ldInst)) {
                    foundDs = ds;
                    break;
                }
            }
        }

        // Crear nodo del DataSet
        NodeInfo dsInfo = new NodeInfo();
        dsInfo.name = "datSet: " + datSetName;
        dsInfo.prefix = "DSET";
        DefaultMutableTreeNode dsNode = new DefaultMutableTreeNode(dsInfo);

        if (foundDs != null && !foundDs.members.isEmpty()) {
            // Agregar miembros del DataSet
            int idx = 0;
            for (String member : foundDs.members) {
                NodeInfo memberInfo = new NodeInfo();
                memberInfo.name = "[" + idx + "] " + member;
                memberInfo.prefix = "FCDA";
                dsNode.add(new DefaultMutableTreeNode(memberInfo));
                idx++;
            }
            dsInfo.value = foundDs.members.size() + " members";
        } else {
            dsInfo.value = "(no members found)";
        }

        gcbNode.add(dsNode);
    }

    private void buildTreeRecursive(DefaultMutableTreeNode parent, ModelNode modelNode) {
        Collection<ModelNode> children = modelNode.getChildren();
        if (children == null) return;

        for (ModelNode child : children) {
            String prefix = getNodePrefix(child);
            DefaultMutableTreeNode childNode = createTreeNode(child, prefix);
            parent.add(childNode);

            // Guardar referencia
            if (child instanceof FcModelNode) {
                String ref = child.getReference().toString();
                nodeMap.put(ref, childNode);
            }

            // Solo expandir si no es BasicDataAttribute (evitar arbol enorme)
            if (!(child instanceof BasicDataAttribute)) {
                buildTreeRecursive(childNode, child);
            }
        }
    }

    private DefaultMutableTreeNode createTreeNode(ModelNode node, String prefix) {
        NodeInfo info = new NodeInfo();
        info.name = node.getName();
        info.prefix = prefix;
        info.node = node;

        if (node instanceof FcModelNode) {
            info.fc = ((FcModelNode) node).getFc().toString();
        }

        if (node instanceof BasicDataAttribute) {
            BasicDataAttribute bda = (BasicDataAttribute) node;
            info.value = formatEnumValue(node, bda.getValueString());
            info.type = bda.getClass().getSimpleName().replace("Bda", "");
        }

        return new DefaultMutableTreeNode(info);
    }

    private String getNodePrefix(ModelNode node) {
        if (node instanceof LogicalDevice) return "LD";
        if (node instanceof LogicalNode) return "LN";
        if (node instanceof FcDataObject) return "DO";
        if (node instanceof ConstructedDataAttribute) return "SDO";
        if (node instanceof BasicDataAttribute) return "DA";
        return "";
    }

    private int countNodes(ServerModel model) {
        int count = 0;
        for (ModelNode ld : model.getChildren()) {
            count += countNodesRecursive(ld);
        }
        return count;
    }

    private int countNodesRecursive(ModelNode node) {
        int count = 1;
        Collection<ModelNode> children = node.getChildren();
        if (children != null) {
            for (ModelNode child : children) {
                count += countNodesRecursive(child);
            }
        }
        return count;
    }

    private void updateNodeValue(String reference, String value) {
        DefaultMutableTreeNode treeNode = nodeMap.get(reference);
        if (treeNode != null) {
            Object userObj = treeNode.getUserObject();
            if (userObj instanceof NodeInfo) {
                ((NodeInfo) userObj).value = value;
                treeModel.nodeChanged(treeNode);
            }
        }
    }

    private void updateTreeValues(ServerModel model) {
        for (ModelNode ld : model.getChildren()) {
            updateTreeValuesRecursive(ld);
        }
    }

    private void updateTreeValuesRecursive(ModelNode node) {
        if (node instanceof BasicDataAttribute) {
            BasicDataAttribute bda = (BasicDataAttribute) node;
            String ref = bda.getReference().toString();
            updateNodeValue(ref, bda.getValueString());
        }

        Collection<ModelNode> children = node.getChildren();
        if (children != null) {
            for (ModelNode child : children) {
                updateTreeValuesRecursive(child);
            }
        }
    }

    private void clearModel() {
        rootNode.removeAllChildren();
        rootNode.setUserObject("Modelo");
        nodeMap.clear();
        treeModel.reload();
    }

    private void updateStatus(boolean active, String message) {
        statusIndicator.setBackground(active ? COLOR_RUNNING : COLOR_STOPPED);
        lblStatus.setText(message);
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            logArea.append("[" + sdf.format(new Date()) + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // Clase para almacenar info del nodo en el arbol
    private static class NodeInfo {
        String name;
        String prefix;
        String fc;
        String value;
        String type;
        ModelNode node;
        SclGoCB gocb;        // Para nodos GoCB
        boolean isGocbContainer; // True si es el contenedor "GOOSE"

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(prefix).append("] ").append(name);
            if (fc != null && !fc.isEmpty()) {
                sb.append(" [").append(fc).append("]");
            }
            if (value != null && !value.isEmpty()) {
                sb.append(" = ").append(value);
            }
            return sb.toString();
        }
    }

    // Renderer personalizado para el arbol (con iconos y colores)
    private class ModelTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Color WATCHLIST_COLOR = new Color(0, 100, 200);
        private final Color COLOR_ON = new Color(0, 150, 0);
        private final Color COLOR_OFF = new Color(200, 0, 0);
        private final Color COLOR_INTERMEDIATE = new Color(255, 140, 0);
        private final Color COLOR_BL = new Color(120, 80, 180);  // violeta: FC=BL (bloqueo)

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof NodeInfo) {
                    NodeInfo info = (NodeInfo) userObj;

                    // Verificar si esta en watchlist
                    boolean inWatchlist = false;
                    if (info.node instanceof FcModelNode) {
                        String ref = info.node.getReference().toString();
                        Fc fc = ((FcModelNode) info.node).getFc();
                        String fullRef = ref + "$" + fc.toString();
                        inWatchlist = watchlist.contains(fullRef);
                    }

                    // === ASIGNAR ICONOS ===
                    Icon icon = getIconForNode(info);
                    if (icon != null) {
                        setIcon(icon);
                    }

                    // === COLOREAR SEGUN ESTADO ===
                    if (info.value != null && !info.value.isEmpty()) {
                        String v = info.value.toLowerCase();
                        // Extraer etiqueta de valores formateados como "2 [Warning]"
                        String label = v.contains("[") && v.contains("]")
                            ? v.substring(v.indexOf('[') + 1, v.lastIndexOf(']')).trim() : v;
                        String ord   = v.contains(" ") ? v.substring(0, v.indexOf(' ')).trim() : v;

                        boolean isOn  = label.equals("on") || label.equals("ok") || label.equals("closed")
                            || label.equals("true") || (!v.contains("[") && (v.equals("on") || v.equals("2")));
                        boolean isOff = label.equals("off") || label.equals("alarm") || label.equals("open")
                            || label.equals("false") || (!v.contains("[") && (v.equals("off") || v.equals("1")));
                        boolean isWarn = label.equals("warning") || label.contains("intermediate")
                            || label.equals("bad") || label.equals("bad_state") || label.equals("blocked")
                            || label.equals("test") || (!v.contains("[") && (v.equals("0") || v.equals("3")));

                        if (isOn) {
                            setForeground(COLOR_ON);
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else if (isOff) {
                            setForeground(COLOR_OFF);
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else if (isWarn) {
                            setForeground(COLOR_INTERMEDIATE);
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else if (inWatchlist) {
                            setForeground(WATCHLIST_COLOR);
                        }
                    } else if (inWatchlist) {
                        setForeground(WATCHLIST_COLOR);
                    }

                    // FC=BL: nodo de bloqueo → color violeta + prefijo candado
                    if ("BL".equals(info.fc)) {
                        setForeground(COLOR_BL);
                        if (!getText().startsWith("🔒")) setText("🔒 " + getText());
                    }

                    // Agregar asterisco si esta en watchlist
                    if (inWatchlist && !getText().startsWith("*")) {
                        setText("* " + getText());
                    }
                }
            }

            return this;
        }

        private Icon getIconForNode(NodeInfo info) {
            String name = info.name != null ? info.name.toUpperCase() : "";
            String prefix = info.prefix != null ? info.prefix : "";

            // === LOGICAL DEVICE ===
            if (prefix.equals("LD")) {
                return iconCache.get("ld");
            }

            // === LOGICAL NODE - por tipo ===
            if (prefix.equals("LN")) {
                if (name.startsWith("XCBR")) {
                    return iconCache.get("ln_xcbr");
                } else if (name.startsWith("XSWI") || name.startsWith("CSWI")) {
                    return iconCache.get("ln_xswi");
                } else if (name.startsWith("MMXU") || name.startsWith("MSQI")) {
                    return iconCache.get("ln_mmxu");
                } else if (name.startsWith("MMTR") || name.startsWith("MSTA")) {
                    return iconCache.get("ln_mmtr");
                } else if (name.startsWith("CSWI") || name.startsWith("CILO")) {
                    return iconCache.get("ln_cswi");
                }
                return iconCache.get("ln_default");
            }

            // === DATA OBJECT ===
            if (prefix.equals("DO")) {
                // Pos de breaker/switch -> icono de interruptor
                if (name.equals("POS") || name.equals("BLKOPN") || name.equals("BLKCLS")) {
                    return iconCache.get("breaker_intermediate");
                }
                return iconCache.get("do");
            }

            // === DATA ATTRIBUTE ===
            if (prefix.equals("DA") || prefix.equals("SDO")) {
                // stVal de posicion -> icono segun estado
                if (name.equalsIgnoreCase("stVal") && info.value != null) {
                    String v = info.value.toLowerCase();
                    String label = v.contains("[") && v.contains("]")
                        ? v.substring(v.indexOf('[') + 1, v.lastIndexOf(']')).trim() : v;
                    boolean iconOn  = label.equals("on") || label.equals("ok") || label.equals("closed")
                        || (!v.contains("[") && v.equals("2"));
                    boolean iconOff = label.equals("off") || label.equals("alarm") || label.equals("open")
                        || (!v.contains("[") && v.equals("1"));
                    if (iconOn)  return iconCache.get("breaker_on");
                    if (iconOff) return iconCache.get("breaker_off");
                    return iconCache.get("breaker_intermediate");
                }
                return iconCache.get("da");
            }

            // === GOOSE Control Block ===
            if (prefix.equals("GoCB") || info.isGocbContainer) {
                return iconCache.get("goose_container");
            }
            if (prefix.equals("GCB")) {
                return iconCache.get("goose_gcb");
            }
            if (prefix.equals("ATTR")) {
                return iconCache.get("da");
            }

            return null;
        }
    }

    // ========== SETTING GROUPS PANEL ==========
    private JPanel createSettingGroupsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton btnRefreshSG = new JButton("Actualizar");
        btnRefreshSG.addActionListener(e -> refreshSettingGroups());
        toolbar.add(btnRefreshSG);
        toolbar.addSeparator();
        JButton btnActivateSG = new JButton("Activar Grupo");
        btnActivateSG.addActionListener(e -> activateSelectedSettingGroup());
        toolbar.add(btnActivateSG);
        JButton btnEditSG = new JButton("Editar Grupo");
        btnEditSG.addActionListener(e -> editSelectedSettingGroup());
        toolbar.add(btnEditSG);
        panel.add(toolbar, BorderLayout.NORTH);

        // Tabla de Setting Groups
        String[] sgColumns = {"Nombre", "Grupo Activo", "Grupo Editable", "Tipo", "Referencia"};
        settingGroupsTableModel = new DefaultTableModel(sgColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        settingGroupsTable = new JTable(settingGroupsTableModel);
        settingGroupsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        settingGroupsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSettingGroupValues();
            }
        });
        JScrollPane sgScroll = new JScrollPane(settingGroupsTable);
        sgScroll.setBorder(BorderFactory.createTitledBorder("Setting Groups (SGCB)"));

        // Tabla de valores del Setting Group seleccionado
        String[] valColumns = {"Atributo", "Valor", "FC", "Tipo"};
        sgValuesTableModel = new DefaultTableModel(valColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        sgValuesTable = new JTable(sgValuesTableModel);
        JScrollPane valScroll = new JScrollPane(sgValuesTable);
        valScroll.setBorder(BorderFactory.createTitledBorder("Valores del Setting Group"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sgScroll, valScroll);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.4);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void refreshSettingGroups() {
        settingGroupsTableModel.setRowCount(0);

        ServerModel model = currentMode == AppMode.SERVER ? server.getServerModel() : client.getServerModel();
        if (model == null) {
            log("No hay modelo cargado para Setting Groups");
            return;
        }

        // Poblar tabla con nodos SE/SG/SP del modelo local
        for (ModelNode ld : model.getChildren()) {
            for (ModelNode ln : ld.getChildren()) {
                if (ln instanceof LogicalNode) {
                    findSettingGroups((LogicalNode) ln, ld.getName() + "/" + ln.getName());
                }
            }
        }
        log("Setting Groups: " + settingGroupsTableModel.getRowCount() + " nodos encontrados");

        // En modo cliente: leer actSG/numOfSGs reales del IED via MMS
        if (currentMode == AppMode.CLIENT && isConnected && client != null) {
            backgroundExecutor.submit(() -> {
                for (ModelNode ld : model.getChildren()) {
                    String ldName = ld.getName();
                    int[] vals = client.readSGCBValues(ldName);
                    if (vals != null) {
                        final int actSg  = vals[0];
                        final int numSgs = vals[1];
                        SwingUtilities.invokeLater(() -> {
                            log(String.format("[SGCB] %s → actSG=%d, numOfSGs=%d", ldName, actSg, numSgs));
                            // Actualizar columnas 1 y 2 para las filas de este LD
                            for (int r = 0; r < settingGroupsTableModel.getRowCount(); r++) {
                                String ref = (String) settingGroupsTableModel.getValueAt(r, 0);
                                if (ref != null && ref.startsWith(ldName + "/")) {
                                    settingGroupsTableModel.setValueAt(String.valueOf(actSg),  r, 1);
                                    settingGroupsTableModel.setValueAt(String.valueOf(numSgs), r, 2);
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    private void findSettingGroups(LogicalNode ln, String path) {
        for (ModelNode child : ln.getChildren()) {
            String name = child.getName().toUpperCase();
            // Buscar nodos de configuracion (SE, SG, SP)
            if (child instanceof FcDataObject) {
                FcDataObject fcdo = (FcDataObject) child;
                Fc fc = fcdo.getFc();
                if (fc == Fc.SE || fc == Fc.SG || fc == Fc.SP) {
                    settingGroupsTableModel.addRow(new Object[]{
                        path + "." + child.getName(),
                        "1",  // Active group
                        "1",  // Editable group
                        fc.toString(),
                        child.getReference().toString()
                    });
                }
            }
        }
    }

    private void showSettingGroupValues() {
        sgValuesTableModel.setRowCount(0);
        int row = settingGroupsTable.getSelectedRow();
        if (row < 0) return;

        String ref = (String) settingGroupsTableModel.getValueAt(row, 4);
        ServerModel model = currentMode == AppMode.SERVER ? server.getServerModel() : client.getServerModel();
        if (model == null) return;

        // Buscar el nodo y mostrar sus valores
        FcModelNode node = (FcModelNode) model.findModelNode(ref, null);
        if (node != null) {
            addSettingGroupValues(node, "");
        }
    }

    private void addSettingGroupValues(ModelNode node, String prefix) {
        if (node instanceof BasicDataAttribute) {
            BasicDataAttribute bda = (BasicDataAttribute) node;
            sgValuesTableModel.addRow(new Object[]{
                prefix + node.getName(),
                bda.getValueString(),
                bda.getFc().toString(),
                bda.getClass().getSimpleName().replace("Bda", "")
            });
        }
        for (ModelNode child : node.getChildren()) {
            addSettingGroupValues(child, prefix + node.getName() + ".");
        }
    }

    private void activateSelectedSettingGroup() {
        int row = settingGroupsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un Setting Group");
            return;
        }

        if (currentMode != AppMode.CLIENT || !isConnected || client == null) {
            JOptionPane.showMessageDialog(this,
                "Activar grupo disponible solo en modo Cliente conectado.",
                "No disponible", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Extraer ldName de la referencia "LD0/LLN0.DO"
        String ref    = (String) settingGroupsTableModel.getValueAt(row, 0);
        String numStr = (String) settingGroupsTableModel.getValueAt(row, 2);
        String ldName = ref.contains("/") ? ref.split("/")[0] : "LD0";

        int numSgs = 1;
        try { numSgs = Integer.parseInt(numStr); } catch (Exception ignored) {}

        // Construir opciones de grupo
        String[] opciones = new String[numSgs];
        for (int i = 0; i < numSgs; i++) opciones[i] = "Grupo " + (i + 1);

        String sel = (String) JOptionPane.showInputDialog(this,
            "Seleccione el grupo de ajuste a activar en " + ldName + ":\n\n"
            + "⚠  ATENCIÓN: esto modifica la protección activa en tiempo real.",
            "Activar Setting Group", JOptionPane.WARNING_MESSAGE,
            null, opciones, opciones[0]);

        if (sel == null) return;

        int groupNum = Integer.parseInt(sel.replace("Grupo ", ""));

        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Confirma activar el " + sel + " en " + ldName + "?\n"
            + "Esta acción cambia la configuración de protección activa.",
            "Confirmar cambio de grupo",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        final String finalLd = ldName;
        final int    finalGn = groupNum;
        backgroundExecutor.submit(() -> {
            try {
                client.selectActiveSG(finalLd, finalGn);
                SwingUtilities.invokeLater(() -> {
                    log("[SGCB] Grupo " + finalGn + " activado en " + finalLd);
                    JOptionPane.showMessageDialog(this,
                        "Grupo " + finalGn + " activado en " + finalLd,
                        "OK", JOptionPane.INFORMATION_MESSAGE);
                    refreshSettingGroups();  // actualizar para reflejar nuevo actSG
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    log("[ERROR SGCB] " + ex.getMessage());
                    JOptionPane.showMessageDialog(this,
                        "Error al activar grupo: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void editSelectedSettingGroup() {
        int row = settingGroupsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un Setting Group");
            return;
        }
        String ref = (String) settingGroupsTableModel.getValueAt(row, 0);
        String fc  = (String) settingGroupsTableModel.getValueAt(row, 3);
        JOptionPane.showMessageDialog(this,
            "Edición de ajustes (FC=" + fc + ") para:\n" + ref
            + "\n\nUse la pestaña 'Data Model' o el árbol del modelo\n"
            + "para modificar valores individuales con doble clic.",
            "Editar Setting Group", JOptionPane.INFORMATION_MESSAGE);
    }

    // ========== DATASET PANEL ==========
    private JPanel createDatasetPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton btnRefreshDS = new JButton("Actualizar");
        btnRefreshDS.addActionListener(e -> refreshDatasets());
        toolbar.add(btnRefreshDS);
        toolbar.addSeparator();
        JButton btnCreateDS = new JButton("Crear Dataset");
        btnCreateDS.addActionListener(e -> createNewDataset());
        toolbar.add(btnCreateDS);
        JButton btnDeleteDS = new JButton("Eliminar Dataset");
        btnDeleteDS.addActionListener(e -> deleteSelectedDataset());
        toolbar.add(btnDeleteDS);
        panel.add(toolbar, BorderLayout.NORTH);

        // Tabla de Datasets
        String[] dsColumns = {"Nombre", "Miembros", "Referencia", "Deletable"};
        datasetTableModel = new DefaultTableModel(dsColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        datasetTable = new JTable(datasetTableModel);
        datasetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        datasetTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDatasetMembers();
            }
        });
        JScrollPane dsScroll = new JScrollPane(datasetTable);
        dsScroll.setBorder(BorderFactory.createTitledBorder("Datasets"));

        // Tabla de miembros del Dataset
        String[] memberColumns = {"#", "Referencia", "FC", "Valor"};
        datasetMembersTableModel = new DefaultTableModel(memberColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        datasetMembersTable = new JTable(datasetMembersTableModel);
        JScrollPane memberScroll = new JScrollPane(datasetMembersTable);
        memberScroll.setBorder(BorderFactory.createTitledBorder("Miembros del Dataset"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, dsScroll, memberScroll);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.4);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void refreshDatasets() {
        datasetTableModel.setRowCount(0);

        // Obtener modelo segun el modo
        ServerModel model = null;
        if (currentMode == AppMode.SERVER && server != null) {
            model = server.getServerModel();
        } else if (currentMode == AppMode.CLIENT && client != null && isConnected) {
            model = client.getServerModel();
        }

        if (model == null) {
            log("No hay modelo cargado para Datasets");
            return;
        }

        // Obtener datasets del modelo
        Collection<DataSet> datasets = model.getDataSets();
        if (datasets != null && !datasets.isEmpty()) {
            for (DataSet ds : datasets) {
                int memberCount = ds.getMembers() != null ? ds.getMembers().size() : 0;
                datasetTableModel.addRow(new Object[]{
                    ds.getReferenceStr(),
                    memberCount,
                    ds.getReferenceStr(),
                    ds.isDeletable() ? "Si" : "No"
                });
            }
            log("Datasets encontrados: " + datasetTableModel.getRowCount());
        } else {
            // Si no hay datasets en el modelo, buscarlos manualmente en LLN0
            log("Buscando datasets en LLN0...");
            for (ModelNode ld : model.getChildren()) {
                for (ModelNode ln : ld.getChildren()) {
                    String lnName = ln.getName();
                    if (lnName.equalsIgnoreCase("LLN0")) {
                        // Buscar DataSets definidos como FcDataObjects
                        for (ModelNode node : ln.getChildren()) {
                            String nodeName = node.getName();
                            // Los datasets suelen tener nombres como Control_DataSet, etc.
                            if (nodeName.toLowerCase().contains("dataset") ||
                                nodeName.startsWith("DS") ||
                                nodeName.startsWith("ds")) {
                                String ref = ld.getName() + "/" + ln.getName() + "." + nodeName;
                                int memberCount = node.getChildren() != null ? node.getChildren().size() : 0;
                                datasetTableModel.addRow(new Object[]{
                                    ref,
                                    memberCount,
                                    ref,
                                    "No"
                                });
                            }
                        }
                    }
                }
            }
            log("Datasets encontrados (busqueda manual): " + datasetTableModel.getRowCount());
        }
    }

    private void showDatasetMembers() {
        datasetMembersTableModel.setRowCount(0);
        int row = datasetTable.getSelectedRow();
        if (row < 0) return;

        String dsRef = (String) datasetTableModel.getValueAt(row, 2);

        // Obtener modelo segun el modo
        ServerModel model = null;
        if (currentMode == AppMode.SERVER && server != null) {
            model = server.getServerModel();
        } else if (currentMode == AppMode.CLIENT && client != null && isConnected) {
            model = client.getServerModel();
        }

        if (model == null) return;

        // Buscar el dataset y mostrar sus miembros
        for (DataSet ds : model.getDataSets()) {
            if (ds.getReferenceStr().equals(dsRef)) {
                List<FcModelNode> members = ds.getMembers();
                if (members != null) {
                    int idx = 1;
                    for (FcModelNode member : members) {
                        String value = "";
                        if (member instanceof BasicDataAttribute) {
                            value = ((BasicDataAttribute) member).getValueString();
                        }
                        datasetMembersTableModel.addRow(new Object[]{
                            idx++,
                            member.getReference().toString(),
                            member.getFc().toString(),
                            value
                        });
                    }
                }
                break;
            }
        }
    }

    private void createNewDataset() {
        String name = JOptionPane.showInputDialog(this, "Nombre del nuevo Dataset:", "Crear Dataset", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isEmpty()) {
            log("Creando Dataset: " + name);
            JOptionPane.showMessageDialog(this, "Dataset creado: " + name + "\n(Funcionalidad en desarrollo)");
            refreshDatasets();
        }
    }

    private void deleteSelectedDataset() {
        int row = datasetTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un Dataset");
            return;
        }
        String name = (String) datasetTableModel.getValueAt(row, 0);
        String deletable = (String) datasetTableModel.getValueAt(row, 3);

        if (!"Si".equals(deletable)) {
            JOptionPane.showMessageDialog(this, "Este Dataset no puede ser eliminado", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Eliminar Dataset: " + name + "?",
            "Confirmar eliminacion",
            JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            log("Eliminando Dataset: " + name);
            datasetTableModel.removeRow(row);
        }
    }

    // ========== DATA MODEL PANEL ==========
    private JPanel createDataModelPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton btnRefreshDM = new JButton("Actualizar");
        btnRefreshDM.addActionListener(e -> refreshDataModel());
        toolbar.add(btnRefreshDM);
        toolbar.addSeparator();
        JButton btnExpandAll = new JButton("Expandir Todo");
        btnExpandAll.addActionListener(e -> expandAllDataModel());
        toolbar.add(btnExpandAll);
        JButton btnCollapseAll = new JButton("Colapsar Todo");
        btnCollapseAll.addActionListener(e -> collapseAllDataModel());
        toolbar.add(btnCollapseAll);
        toolbar.addSeparator();
        JButton btnExportXML = new JButton("Exportar XML");
        btnExportXML.addActionListener(e -> exportDataModelToXML());
        toolbar.add(btnExportXML);
        panel.add(toolbar, BorderLayout.NORTH);

        // Arbol del Data Model
        dataModelRootNode = new DefaultMutableTreeNode("Data Model");
        dataModelTreeModel = new DefaultTreeModel(dataModelRootNode);
        dataModelTree = new JTree(dataModelTreeModel);
        dataModelTree.setRowHeight(20);
        dataModelTree.setCellRenderer(new DataModelTreeCellRenderer());
        dataModelTree.addTreeSelectionListener(e -> showDataModelAttributes());
        JScrollPane treeScroll = new JScrollPane(dataModelTree);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Estructura del Modelo"));

        // Tabla de atributos
        String[] attrColumns = {"Propiedad", "Valor", "Tipo"};
        dataModelAttrTableModel = new DefaultTableModel(attrColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        dataModelAttrTable = new JTable(dataModelAttrTableModel);
        JScrollPane attrScroll = new JScrollPane(dataModelAttrTable);
        attrScroll.setBorder(BorderFactory.createTitledBorder("Propiedades"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, attrScroll);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.6);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void refreshDataModel() {
        dataModelRootNode.removeAllChildren();

        ServerModel model = currentMode == AppMode.SERVER ? server.getServerModel() : client.getServerModel();
        if (model == null) {
            log("No hay modelo cargado para Data Model");
            dataModelTreeModel.reload();
            return;
        }

        // Construir arbol detallado del modelo
        dataModelRootNode.setUserObject("Data Model - " + countNodes(model) + " nodos");

        for (ModelNode ld : model.getChildren()) {
            DefaultMutableTreeNode ldNode = new DefaultMutableTreeNode(new DataModelNodeInfo(ld, "LD"));
            dataModelRootNode.add(ldNode);
            buildDataModelTree(ldNode, ld);
        }

        dataModelTreeModel.reload();
        dataModelTree.expandRow(0);
        log("Data Model actualizado");
    }

    private void buildDataModelTree(DefaultMutableTreeNode parent, ModelNode modelNode) {
        for (ModelNode child : modelNode.getChildren()) {
            String type = getDataModelNodeType(child);
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new DataModelNodeInfo(child, type));
            parent.add(childNode);

            if (!(child instanceof BasicDataAttribute)) {
                buildDataModelTree(childNode, child);
            }
        }
    }

    private String getDataModelNodeType(ModelNode node) {
        if (node instanceof LogicalDevice) return "LD";
        if (node instanceof LogicalNode) return "LN";
        if (node instanceof FcDataObject) return "DO";
        if (node instanceof ConstructedDataAttribute) return "DA";
        if (node instanceof BasicDataAttribute) return "BDA";
        return "NODE";
    }

    private void showDataModelAttributes() {
        dataModelAttrTableModel.setRowCount(0);

        TreePath path = dataModelTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = treeNode.getUserObject();

        if (userObj instanceof DataModelNodeInfo) {
            DataModelNodeInfo info = (DataModelNodeInfo) userObj;
            ModelNode node = info.node;

            dataModelAttrTableModel.addRow(new Object[]{"Nombre", node.getName(), "String"});
            dataModelAttrTableModel.addRow(new Object[]{"Referencia", node.getReference().toString(), "ObjectReference"});
            dataModelAttrTableModel.addRow(new Object[]{"Tipo", info.type, "String"});

            if (node instanceof FcModelNode) {
                FcModelNode fcNode = (FcModelNode) node;
                dataModelAttrTableModel.addRow(new Object[]{"Functional Constraint", fcNode.getFc().toString(), "Fc"});
            }

            if (node instanceof BasicDataAttribute) {
                BasicDataAttribute bda = (BasicDataAttribute) node;
                dataModelAttrTableModel.addRow(new Object[]{"Valor", bda.getValueString(), bda.getClass().getSimpleName()});
                dataModelAttrTableModel.addRow(new Object[]{"Clase BDA", bda.getClass().getSimpleName(), "Class"});
            }

            dataModelAttrTableModel.addRow(new Object[]{"Hijos", node.getChildren().size(), "int"});
        }
    }

    private void expandAllDataModel() {
        for (int i = 0; i < dataModelTree.getRowCount(); i++) {
            dataModelTree.expandRow(i);
        }
    }

    private void collapseAllDataModel() {
        for (int i = dataModelTree.getRowCount() - 1; i > 0; i--) {
            dataModelTree.collapseRow(i);
        }
    }

    private void exportDataModelToXML() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("datamodel_export.xml"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            log("Exportando Data Model a: " + file.getName());
            JOptionPane.showMessageDialog(this,
                "Data Model exportado a:\n" + file.getAbsolutePath() + "\n(Funcionalidad en desarrollo)",
                "Exportar XML", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // Clase para info del nodo en Data Model
    private static class DataModelNodeInfo {
        ModelNode node;
        String type;

        DataModelNodeInfo(ModelNode node, String type) {
            this.node = node;
            this.type = type;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(type).append("] ").append(node.getName());
            if (node instanceof FcModelNode) {
                sb.append(" [").append(((FcModelNode) node).getFc()).append("]");
            }
            if (node instanceof BasicDataAttribute) {
                String val = ((BasicDataAttribute) node).getValueString();
                if (val != null && !val.isEmpty()) {
                    sb.append(" = ").append(val);
                }
            }
            return sb.toString();
        }
    }

    // Renderer para Data Model Tree
    private class DataModelTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof DataModelNodeInfo) {
                    DataModelNodeInfo info = (DataModelNodeInfo) userObj;

                    // Usar iconos del cache
                    switch (info.type) {
                        case "LD":
                            setIcon(iconCache.get("ld"));
                            break;
                        case "LN":
                            String name = info.node.getName().toUpperCase();
                            if (name.startsWith("XCBR") || name.startsWith("XSWI")) {
                                setIcon(iconCache.get("ln_xcbr"));
                            } else if (name.startsWith("MMXU") || name.startsWith("MMTR")) {
                                setIcon(iconCache.get("ln_mmxu"));
                            } else {
                                setIcon(iconCache.get("ln_default"));
                            }
                            break;
                        case "DO":
                            setIcon(iconCache.get("do"));
                            break;
                        case "DA":
                        case "BDA":
                            setIcon(iconCache.get("da"));
                            break;
                    }
                }
            }
            return this;
        }
    }

    public static void main(String[] args) {
        // Look and Feel
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {}
        }

        SwingUtilities.invokeLater(() -> {
            IEDExplorerApp app = new IEDExplorerApp();
            app.setVisible(true);
        });
    }
}

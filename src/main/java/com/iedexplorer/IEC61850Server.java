package com.iedexplorer;

import com.beanit.iec61850bean.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Collection;

/**
 * Servidor IEC 61850 usando iec61850bean
 * Basado en el codigo de la APK Android que funciona correctamente
 */
public class IEC61850Server implements ServerEventListener {

    private ServerSap serverSap;
    private ServerModel serverModel;
    private int port = 102;
    private boolean running = false;

    // Referencias a nodos para actualizacion
    private final Map<String, BasicDataAttribute> attributeRefs = new HashMap<>();

    // Listener para eventos
    private ServerListener listener;

    public interface ServerListener {
        void onServerStarted(int port);
        void onServerStopped();
        void onClientWrite(String nodeRef, String value);
        void onError(String message);
    }

    public void setServerListener(ServerListener listener) {
        this.listener = listener;
    }

    // Cache de modelos parseados para selección de IED
    private List<ServerModel> parsedModels = null;
    private String currentSclPath = null;

    /**
     * Obtiene la lista de IEDs disponibles en un archivo SCL
     * Retorna lista de nombres de IED (basado en el primer LD de cada modelo)
     */
    public List<String> getAvailableIEDs(String sclPath) {
        List<String> iedNames = new ArrayList<>();
        try {
            File sclFile = new File(sclPath);
            if (!sclFile.exists()) return iedNames;

            try (FileInputStream fis = new FileInputStream(sclFile)) {
                parsedModels = SclParser.parse(fis);
                currentSclPath = sclPath;

                if (parsedModels != null) {
                    for (ServerModel model : parsedModels) {
                        // El nombre del IED se deriva del primer LD
                        if (model.getChildren() != null && !model.getChildren().isEmpty()) {
                            ModelNode firstLd = model.getChildren().iterator().next();
                            String ldName = firstLd.getName();
                            // Extraer nombre del IED del LD (formato: IEDName/LDInst o solo LDInst)
                            iedNames.add(ldName.contains("/") ? ldName.split("/")[0] : "IED_" + iedNames.size());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error getting IED list: " + e.getMessage());
        }
        return iedNames;
    }

    /**
     * Carga un IED específico por índice
     */
    public boolean loadSclFileWithIED(String sclPath, int iedIndex) {
        try {
            // Si ya parseamos este archivo, usar el cache
            if (parsedModels != null && sclPath.equals(currentSclPath)) {
                if (iedIndex >= 0 && iedIndex < parsedModels.size()) {
                    serverModel = parsedModels.get(iedIndex);
                    indexAttributes(serverModel);
                    System.out.println("[SERVER] Loaded IED index " + iedIndex + " from cache");
                    System.out.println("[SERVER] LDs found: " + serverModel.getChildren().size());
                    // Mostrar info de debug también para modelos cacheados
                    debugPrintModelInfo(serverModel);
                    return true;
                }
            }

            // Si no, parsear de nuevo
            return loadSclFile(sclPath, iedIndex);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            return false;
        }
    }

    /**
     * Carga el modelo desde un archivo SCL (igual que la APK)
     */
    public boolean loadSclFile(String sclPath) {
        return loadSclFile(sclPath, 0); // Por defecto cargar el primer IED
    }

    /**
     * Carga el modelo desde un archivo SCL, seleccionando un IED específico
     */
    public boolean loadSclFile(String sclPath, int iedIndex) {
        try {
            System.out.println("[SERVER] Loading SCL file: " + sclPath);
            File sclFile = new File(sclPath);
            if (!sclFile.exists()) {
                System.err.println("[ERROR] File not found: " + sclPath);
                if (listener != null) listener.onError("File not found: " + sclPath);
                return false;
            }

            System.out.println("[SERVER] File size: " + sclFile.length() + " bytes");
            System.out.println("[SERVER] Parsing SCL (this may take a moment for large files)...");

            // Parsear SCL usando InputStream (igual que la APK)
            long startTime = System.currentTimeMillis();
            try (FileInputStream fis = new FileInputStream(sclFile)) {
                List<ServerModel> models = SclParser.parse(fis);

                long parseTime = System.currentTimeMillis() - startTime;
                System.out.println("[SERVER] SCL parsed in " + parseTime + "ms");

                if (models == null || models.isEmpty()) {
                    System.err.println("[ERROR] No server models found in SCL file");
                    if (listener != null) listener.onError("No server models found in SCL file");
                    return false;
                }

                System.out.println("[SERVER] Found " + models.size() + " IED(s) in SCL file");

                // Seleccionar el IED especificado
                if (iedIndex >= 0 && iedIndex < models.size()) {
                    serverModel = models.get(iedIndex);
                    System.out.println("[SERVER] Selected IED index: " + iedIndex);
                } else {
                    serverModel = models.get(0);
                    System.out.println("[SERVER] Using first IED (index 0)");
                }

                // Guardar cache para futuras selecciones
                parsedModels = models;
                currentSclPath = sclPath;
            }

            // Indexar atributos para actualizacion
            System.out.println("[SERVER] Indexing attributes...");
            indexAttributes(serverModel);

            System.out.println("[SERVER] Model loaded from: " + sclPath);
            System.out.println("[SERVER] LDs found: " + serverModel.getChildren().size());

            // Mostrar información detallada del modelo para debugging
            debugPrintModelInfo(serverModel);

            return true;

        } catch (SclParseException e) {
            System.err.println("[ERROR] SCL parse error: " + e.getMessage());
            e.printStackTrace();
            if (listener != null) listener.onError("SCL parse error: " + e.getMessage());
            return false;
        } catch (IOException e) {
            System.err.println("[ERROR] IO error: " + e.getMessage());
            e.printStackTrace();
            if (listener != null) listener.onError("IO error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retorna el número de IEDs disponibles en el último archivo parseado
     */
    public int getIEDCount() {
        return parsedModels != null ? parsedModels.size() : 0;
    }

    /**
     * Carga el modelo desde un InputStream
     */
    public boolean loadSclStream(InputStream inputStream) {
        try {
            List<ServerModel> models = SclParser.parse(inputStream);

            if (models == null || models.isEmpty()) {
                System.err.println("[ERROR] No server models found");
                return false;
            }

            serverModel = models.get(0);
            indexAttributes(serverModel);

            System.out.println("[SERVER] Model loaded from stream");
            return true;

        } catch (SclParseException e) {
            System.err.println("[ERROR] SCL parse error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Indexa los atributos del modelo para actualizacion posterior
     */
    private void indexAttributes(ServerModel model) {
        attributeRefs.clear();

        for (ModelNode ld : model.getChildren()) {
            indexNode(ld, "");
        }

        System.out.println("[SERVER] Attributes indexed: " + attributeRefs.size());
    }

    private void indexNode(ModelNode node, String prefix) {
        String ref = prefix.isEmpty() ? node.getName() : prefix + "." + node.getName();

        if (node instanceof BasicDataAttribute) {
            attributeRefs.put(ref, (BasicDataAttribute) node);
        }

        // Null check - some nodes return null instead of empty collection
        Collection<ModelNode> children = node.getChildren();
        if (children != null) {
            for (ModelNode child : children) {
                indexNode(child, ref);
            }
        }
    }

    /**
     * Inicia el servidor (igual que la APK)
     */
    public boolean start(int port) {
        if (running) {
            System.out.println("[WARN] Server already running");
            return true;
        }

        if (serverModel == null) {
            System.err.println("[ERROR] No model loaded");
            if (listener != null) listener.onError("No model loaded");
            return false;
        }

        this.port = port;

        try {
            // Crear ServerSap igual que la APK: (port, backlog, bindAddress, model, socketFactory)
            serverSap = new ServerSap(port, 0, null, serverModel, null);

            // Iniciar escucha pasando this como ServerEventListener
            serverSap.startListening(this);

            running = true;
            System.out.println("[SERVER] Server started on port " + port);

            if (listener != null) listener.onServerStarted(port);

            return true;

        } catch (IOException e) {
            System.err.println("[ERROR] Error starting server: " + e.getMessage());
            if (listener != null) listener.onError("Error starting server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Detiene el servidor
     */
    public void stop() {
        if (serverSap != null) {
            serverSap.stop();
            serverSap = null;
        }
        running = false;
        System.out.println("[SERVER] Server stopped");

        if (listener != null) listener.onServerStopped();
    }

    public boolean isRunning() {
        return running;
    }

    public ServerModel getServerModel() {
        return serverModel;
    }

    /**
     * Actualiza un valor en el modelo (igual que la APK)
     */
    public boolean setDataValue(String nodeRef, String value) {
        if (serverModel == null) {
            System.err.println("[WARN] Cannot set value - no model loaded");
            return false;
        }

        try {
            // Extraer FC de la referencia si existe
            Fc fc = extractFc(nodeRef);
            String cleanRef = cleanReference(nodeRef);

            System.out.println("[SERVER] Setting: " + cleanRef + " [" + fc + "] = " + value);

            ModelNode node = serverModel.findModelNode(cleanRef, fc);

            // Si no se encuentra, probar con otros FCs
            if (node == null) {
                Fc[] fcsToTry = {Fc.ST, Fc.MX, Fc.CO, Fc.CF, Fc.SP, Fc.SG};
                for (Fc tryFc : fcsToTry) {
                    if (tryFc != fc) {
                        node = serverModel.findModelNode(cleanRef, tryFc);
                        if (node != null) {
                            fc = tryFc;
                            break;
                        }
                    }
                }
            }

            if (node instanceof BasicDataAttribute) {
                BasicDataAttribute bda = (BasicDataAttribute) node;
                setBasicDataAttributeValue(bda, value);

                // Notificar a clientes via reports (solo si el servidor está activo)
                if (running && serverSap != null) {
                    try {
                        List<BasicDataAttribute> changedData = new ArrayList<>();
                        changedData.add(bda);
                        serverSap.setValues(changedData);
                        System.out.println("[SERVER] Clients notified via reports");
                    } catch (Exception e) {
                        System.out.println("[SERVER] Value changed locally (no clients to notify): " + e.getMessage());
                    }
                }

                String newValue = bda.getValueString();
                System.out.println("[SERVER] Value set: " + cleanRef + " [" + fc + "] = " + newValue);

                return true;
            } else {
                System.err.println("[ERROR] Node not found: " + cleanRef);
                return false;
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Setting value: " + e.getMessage());
            return false;
        }
    }

    private Fc extractFc(String nodeRef) {
        if (nodeRef.contains("$")) {
            String[] parts = nodeRef.split("\\$");
            if (parts.length >= 2) {
                String lastPart = parts[parts.length - 1];
                Fc fc = tryParseFc(lastPart);
                if (fc != null) return fc;

                fc = tryParseFc(parts[1]);
                if (fc != null) return fc;
            }
        }
        return Fc.ST;
    }

    private Fc tryParseFc(String fcStr) {
        try {
            return Fc.valueOf(fcStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String cleanReference(String nodeRef) {
        if (nodeRef.contains("$")) {
            String[] parts = nodeRef.split("\\$");
            String lastPart = parts[parts.length - 1];
            if (tryParseFc(lastPart) != null) {
                return parts[0];
            }

            if (parts.length >= 3) {
                StringBuilder sb = new StringBuilder(parts[0]);
                for (int i = 2; i < parts.length; i++) {
                    sb.append(".").append(parts[i]);
                }
                return sb.toString();
            }
        }
        return nodeRef;
    }

    /**
     * Establece valor en BDA (igual que la APK)
     */
    private void setBasicDataAttributeValue(BasicDataAttribute bda, String value) {
        try {
            if (bda instanceof BdaBoolean) {
                ((BdaBoolean) bda).setValue(Boolean.parseBoolean(value) || "1".equals(value));
            } else if (bda instanceof BdaInt8) {
                ((BdaInt8) bda).setValue(Byte.parseByte(value));
            } else if (bda instanceof BdaInt16) {
                ((BdaInt16) bda).setValue(Short.parseShort(value));
            } else if (bda instanceof BdaInt32) {
                ((BdaInt32) bda).setValue(Integer.parseInt(value));
            } else if (bda instanceof BdaInt64) {
                ((BdaInt64) bda).setValue(Long.parseLong(value));
            } else if (bda instanceof BdaFloat32) {
                ((BdaFloat32) bda).setFloat(Float.parseFloat(value));
            } else if (bda instanceof BdaFloat64) {
                ((BdaFloat64) bda).setDouble(Double.parseDouble(value));
            } else if (bda instanceof BdaVisibleString) {
                ((BdaVisibleString) bda).setValue(value);
            } else if (bda instanceof BdaDoubleBitPos) {
                setDbposValue((BdaDoubleBitPos) bda, value);
            } else if (bda instanceof BdaCheck) {
                BdaCheck check = (BdaCheck) bda;
                check.setSynchrocheck("true".equalsIgnoreCase(value) || "1".equals(value));
            } else if (bda instanceof BdaTapCommand) {
                setTapCommandValue((BdaTapCommand) bda, value);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Setting value: " + e.getMessage());
        }
    }

    private void setDbposValue(BdaDoubleBitPos dbpos, String value) {
        try {
            String lowerValue = value.toLowerCase().trim();

            BdaDoubleBitPos.DoubleBitPos newPos;
            if (lowerValue.equals("off") || lowerValue.equals("01") || lowerValue.equals("1")) {
                newPos = BdaDoubleBitPos.DoubleBitPos.OFF;
            } else if (lowerValue.equals("on") || lowerValue.equals("10") || lowerValue.equals("2")) {
                newPos = BdaDoubleBitPos.DoubleBitPos.ON;
            } else if (lowerValue.equals("intermediate") || lowerValue.equals("00") || lowerValue.equals("0")) {
                newPos = BdaDoubleBitPos.DoubleBitPos.INTERMEDIATE_STATE;
            } else if (lowerValue.equals("bad") || lowerValue.equals("11") || lowerValue.equals("3")) {
                newPos = BdaDoubleBitPos.DoubleBitPos.BAD_STATE;
            } else {
                newPos = BdaDoubleBitPos.DoubleBitPos.INTERMEDIATE_STATE;
            }

            // Usar setValue con byte array directamente para evitar problemas con mirror
            byte[] bytes = new byte[1];
            switch (newPos) {
                case INTERMEDIATE_STATE: bytes[0] = 0x00; break;
                case OFF: bytes[0] = 0x40; break;  // 01 in MSB
                case ON: bytes[0] = (byte) 0x80; break;  // 10 in MSB
                case BAD_STATE: bytes[0] = (byte) 0xC0; break;  // 11 in MSB
            }
            dbpos.setValue(bytes);
            System.out.println("[SERVER] DoubleBitPos set to: " + newPos);
        } catch (Exception e) {
            System.err.println("[ERROR] Setting DoubleBitPos: " + e.getMessage());
        }
    }

    private void setTapCommandValue(BdaTapCommand tap, String value) {
        String lowerValue = value.toLowerCase().trim();

        if (lowerValue.equals("stop") || lowerValue.equals("0")) {
            tap.setTapCommand(BdaTapCommand.TapCommand.STOP);
        } else if (lowerValue.equals("lower") || lowerValue.equals("1")) {
            tap.setTapCommand(BdaTapCommand.TapCommand.LOWER);
        } else if (lowerValue.equals("higher") || lowerValue.equals("2")) {
            tap.setTapCommand(BdaTapCommand.TapCommand.HIGHER);
        } else if (lowerValue.equals("reserved") || lowerValue.equals("3")) {
            tap.setTapCommand(BdaTapCommand.TapCommand.RESERVED);
        }
    }

    /**
     * Obtiene un atributo por referencia
     */
    public BasicDataAttribute getAttribute(String reference) {
        return attributeRefs.get(reference);
    }

    /**
     * Obtiene todas las referencias de atributos
     */
    public Set<String> getAttributeReferences() {
        return attributeRefs.keySet();
    }

    public int getPort() {
        return port;
    }

    // ServerEventListener implementation

    @Override
    public void serverStoppedListening(ServerSap serverSap) {
        System.out.println("[SERVER] Server stopped listening");
        running = false;
        if (listener != null) listener.onServerStopped();
    }

    @Override
    public List<ServiceError> write(List<BasicDataAttribute> bdas) {
        System.out.println("[SERVER] Client writing " + bdas.size() + " values");

        for (BasicDataAttribute bda : bdas) {
            String ref = bda.getReference().toString();
            String value = bda.getValueString();
            System.out.println("[SERVER] Write: " + ref + " = " + value);

            if (listener != null) {
                listener.onClientWrite(ref, value);
            }
        }

        return null; // No errors
    }

    /**
     * Imprime información detallada del modelo para debugging
     */
    private void debugPrintModelInfo(ServerModel model) {
        if (model == null) {
            System.out.println("[DEBUG] ServerModel is NULL!");
            return;
        }

        System.out.println("\n========== MODEL DEBUG INFO ==========");

        for (ModelNode ldNode : model.getChildren()) {
            LogicalDevice ld = (LogicalDevice) ldNode;
            System.out.println("[DEBUG] LD: " + ld.getName());

            int lnCount = 0;
            for (ModelNode lnNode : ld.getChildren()) {
                lnCount++;
                if (lnNode instanceof LogicalNode) {
                    LogicalNode ln = (LogicalNode) lnNode;
                    System.out.println("[DEBUG]   LN: " + ln.getName());

                    // Mostrar Reports (URCBs)
                    try {
                        Collection<Urcb> urcbs = ln.getUrcbs();
                        if (urcbs != null && !urcbs.isEmpty()) {
                            System.out.println("[DEBUG]     URCBs: " + urcbs.size());
                            for (Urcb urcb : urcbs) {
                                System.out.println("[DEBUG]       URCB: " + urcb.getName());
                            }
                        }
                    } catch (Exception e) {
                        // Ignorar si no hay URCBs
                    }

                    // Mostrar Reports (BRCBs)
                    try {
                        Collection<Brcb> brcbs = ln.getBrcbs();
                        if (brcbs != null && !brcbs.isEmpty()) {
                            System.out.println("[DEBUG]     BRCBs: " + brcbs.size());
                            for (Brcb brcb : brcbs) {
                                System.out.println("[DEBUG]       BRCB: " + brcb.getName());
                            }
                        }
                    } catch (Exception e) {
                        // Ignorar si no hay BRCBs
                    }
                }
            }
            System.out.println("[DEBUG]   Total LNs in LD: " + lnCount);
        }
        System.out.println("=======================================\n");
    }
}

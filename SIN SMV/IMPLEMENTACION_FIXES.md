# IEDNavigator — Plan Completo de Fixes e Implementaciones

> Generado: 2026-04-18
> Basado en: análisis de 22 archivos SCL (ANALISIS_SCL.md) + norma IEC 61850-6 Ed.2.0 (IEC61850_GAPS_NORMATIVO.md)
> Código fuente: `iec61850_java_explorer/src/main/java/com/iedexplorer/`

---

## Estado actual del código (diagnóstico)

| Archivo | Líneas | Estado |
|---------|--------|--------|
| `IEC61850Client.java` | 837 | `formatValue()` sin decodificación de enums. Sin soporte FC=DC, FC=SP lectura MMS, SV, BL |
| `IEDExplorerApp.java` | 6794 | Panel "Setting Groups" existe pero es stub. Panel "Dataset" existe pero sin valores FCDA. Sin nameplate panel |
| `IEC61850Server.java` | 643 | Funcional. Array de FCs incluye SP correctamente |

### Stubs confirmados en IEDExplorerApp.java

```java
// línea 6348 — activateSelectedSettingGroup()
JOptionPane.showMessageDialog(this, "Setting Group activado: " + name);  // ← stub vacío

// línea 6359 — editSelectedSettingGroup()
JOptionPane.showMessageDialog(this, "Editor de Setting Group: ... (Funcionalidad en desarrollo)");  // ← stub vacío

// línea 6307-6308 — findSettingGroups()
"1",  // Active group  ← hardcodeado
"1",  // Editable group ← hardcodeado
```

### formatValue() actual (línea 302)
```java
public String formatValue(ModelNode node) {
    if (node == null) return "null";
    try {
        if (node instanceof BasicDataAttribute) {
            BasicDataAttribute bda = (BasicDataAttribute) node;
            return bda.getValueString();  // ← devuelve "29" en vez de "V", "1" en vez de "direct-normal"
        }
        if (node instanceof BdaDoubleBitPos) {
            return formatDoubleBitPos((BdaDoubleBitPos) node);  // ← único enum ya decodificado
        }
        return node.toString();
    } catch (Exception e) {
        return "ERROR: " + e.getMessage();
    }
}
```

---

## Orden de implementación

| # | Fix | Archivo | Esfuerzo | Riesgo | Impacto |
|---|-----|---------|----------|--------|---------|
| **1** | Decodificación de enums en `formatValue()` | `IEC61850Client.java` | ~1h | Ninguno | Universal — afecta todas las vistas |
| **2** | Panel "Información del IED" (FC=DC nameplate) | `IEC61850Client.java` + `IEDExplorerApp.java` | ~2h | Ninguno | Visible al conectar |
| **3** | Setting Groups real (SGCB vía MMS) | `IEC61850Client.java` + `IEDExplorerApp.java` | ~4h | Medio — confirmar con usuario | Crítico para SIPROTEC5, REL, ABB |
| **4** | FC=SP — pestaña de ajustes de protección | `IEC61850Client.java` + `IEDExplorerApp.java` | ~4h | Bajo (lectura) / Medio (escritura) | MICOM P143/P545, ZIV, Siemens |
| **5** | Communication section — pre-llenar GOOSE | `IEDExplorerApp.java` | ~3h | Ninguno | GOOSE publisher más usable |
| **6** | Dataset browser — leer valores FCDA por MMS | `IEC61850Client.java` + `IEDExplorerApp.java` | ~3h | Ninguno | Diagnóstico GOOSE/reportes |
| **7** | FC=SV — sustitución supervisada | `IEC61850Client.java` + `IEDExplorerApp.java` | ~3h | Bajo (PICS_SUBST) | Mantenimiento en caliente |
| **8** | LGOS — mapa de suscripciones GOOSE | `IEDExplorerApp.java` (SCL parser) | ~2h | Ninguno | Diagnóstico de proyecto |
| **9** | FC=BL — bloqueo de actualizaciones | `IEC61850Client.java` + `IEDExplorerApp.java` | ~1h | Bajo (PICS_BLK) | Bajo impacto |

---

---

# FIX 1 — Decodificación de enums en `formatValue()`

## Archivo: `IEC61850Client.java` — línea 302

## Por qué
`bda.getValueString()` devuelve el entero raw para BdaInt8/BdaInt8U/BdaEnum.
El usuario ve `"29"` en vez de `"V"`, `"1"` en vez de `"direct-with-normal-security"`, `"2"` en vez de `"on"`.
Afecta TODAS las vistas simultáneamente: árbol, monitor, setting groups, reports.

## Qué IEDs lo necesitan
Todos. Especialmente: ION7400 (unidades SIUnit), SIPROTEC5 (ctlModel, Health), ABB RED670 (range, dir), SEL-487B (Mod/Beh).

## Código a reemplazar

**ANTES (línea 302–320):**
```java
public String formatValue(ModelNode node) {
    if (node == null) return "null";
    try {
        if (node instanceof BasicDataAttribute) {
            BasicDataAttribute bda = (BasicDataAttribute) node;
            return bda.getValueString();
        }
        if (node instanceof BdaDoubleBitPos) {
            return formatDoubleBitPos((BdaDoubleBitPos) node);
        }
        return node.toString();
    } catch (Exception e) {
        return "ERROR: " + e.getMessage();
    }
}
```

**DESPUÉS — reemplazar el método completo:**
```java
// ── Mapas estáticos de decodificación de enums ──────────────────────────────

private static final Map<Integer, String> SI_UNIT_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> CTL_MODEL_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> HEALTH_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> MOD_BEH_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> RANGE_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> DIR_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> OR_CATEGORY_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> AUTO_REC_ST_MAP = new LinkedHashMap<>();
private static final Map<Integer, String> FLT_LOOP_MAP = new LinkedHashMap<>();

static {
    // IEC 61850-7-3 — códigos de unidad SIUnit
    SI_UNIT_MAP.put(0,  "none");
    SI_UNIT_MAP.put(1,  "m");
    SI_UNIT_MAP.put(2,  "kg");
    SI_UNIT_MAP.put(3,  "s");
    SI_UNIT_MAP.put(4,  "A");
    SI_UNIT_MAP.put(8,  "K");
    SI_UNIT_MAP.put(11, "deg");
    SI_UNIT_MAP.put(21, "Gy");
    SI_UNIT_MAP.put(23, "°C");
    SI_UNIT_MAP.put(25, "F");
    SI_UNIT_MAP.put(26, "C");
    SI_UNIT_MAP.put(27, "S");
    SI_UNIT_MAP.put(28, "H");
    SI_UNIT_MAP.put(29, "V");
    SI_UNIT_MAP.put(30, "Ω");
    SI_UNIT_MAP.put(31, "J");
    SI_UNIT_MAP.put(32, "N");
    SI_UNIT_MAP.put(33, "Hz");
    SI_UNIT_MAP.put(35, "lm");
    SI_UNIT_MAP.put(36, "lx");
    SI_UNIT_MAP.put(37, "Wb");
    SI_UNIT_MAP.put(38, "T");
    SI_UNIT_MAP.put(61, "VA");
    SI_UNIT_MAP.put(62, "W");
    SI_UNIT_MAP.put(63, "VAr");
    SI_UNIT_MAP.put(64, "φ");
    SI_UNIT_MAP.put(65, "cos(φ)");
    SI_UNIT_MAP.put(66, "Vs");
    SI_UNIT_MAP.put(72, "Wh");
    SI_UNIT_MAP.put(73, "VAh");
    SI_UNIT_MAP.put(74, "VArh");
    SI_UNIT_MAP.put(75, "V²h");
    SI_UNIT_MAP.put(76, "A²h");
    SI_UNIT_MAP.put(77, "V²");
    SI_UNIT_MAP.put(78, "A²");

    // ctlModel
    CTL_MODEL_MAP.put(0, "status-only");
    CTL_MODEL_MAP.put(1, "direct-normal-security");
    CTL_MODEL_MAP.put(2, "sbo-normal-security");
    CTL_MODEL_MAP.put(3, "direct-enhanced-security");
    CTL_MODEL_MAP.put(4, "sbo-enhanced-security");

    // Health
    HEALTH_MAP.put(1, "Ok");
    HEALTH_MAP.put(2, "Warning");
    HEALTH_MAP.put(3, "Alarm");

    // Mod / Beh
    MOD_BEH_MAP.put(1, "on");
    MOD_BEH_MAP.put(2, "blocked");
    MOD_BEH_MAP.put(3, "test");
    MOD_BEH_MAP.put(4, "test/blocked");
    MOD_BEH_MAP.put(5, "off");

    // range
    RANGE_MAP.put(0, "normal");
    RANGE_MAP.put(1, "high");
    RANGE_MAP.put(2, "low");
    RANGE_MAP.put(3, "high-high");
    RANGE_MAP.put(4, "low-low");

    // dir (dirección de falta)
    DIR_MAP.put(0, "unknown");
    DIR_MAP.put(1, "forward");
    DIR_MAP.put(2, "backward");
    DIR_MAP.put(3, "both");

    // orCategory
    OR_CATEGORY_MAP.put(0, "not-supported");
    OR_CATEGORY_MAP.put(1, "bay-control");
    OR_CATEGORY_MAP.put(2, "station-control");
    OR_CATEGORY_MAP.put(3, "remote-control");
    OR_CATEGORY_MAP.put(4, "automatic-bay");
    OR_CATEGORY_MAP.put(5, "automatic-station");
    OR_CATEGORY_MAP.put(6, "automatic-remote");
    OR_CATEGORY_MAP.put(7, "maintenance");
    OR_CATEGORY_MAP.put(8, "process");

    // AutoRecSt
    AUTO_REC_ST_MAP.put(1, "Ready");
    AUTO_REC_ST_MAP.put(2, "InProgress");
    AUTO_REC_ST_MAP.put(3, "Successful");

    // FltLoop
    FLT_LOOP_MAP.put(1, "PhA-Gnd");
    FLT_LOOP_MAP.put(2, "PhB-Gnd");
    FLT_LOOP_MAP.put(3, "PhC-Gnd");
    FLT_LOOP_MAP.put(4, "PhA-PhB");
    FLT_LOOP_MAP.put(5, "PhB-PhC");
    FLT_LOOP_MAP.put(6, "PhA-PhC");
    FLT_LOOP_MAP.put(7, "Others");
}

public String formatValue(ModelNode node) {
    if (node == null) return "null";
    try {
        if (node instanceof BdaDoubleBitPos) {
            return formatDoubleBitPos((BdaDoubleBitPos) node);
        }
        if (node instanceof BasicDataAttribute) {
            BasicDataAttribute bda = (BasicDataAttribute) node;
            String name = node.getName().toLowerCase();

            // Decodificar enums por nombre del DA
            if (bda instanceof BdaInt8 || bda instanceof BdaInt8U
                    || bda instanceof BdaInt16 || bda instanceof BdaInt16U) {

                int intVal = getIntValue(bda);

                if (name.equals("unit") || name.equals("siunit")) {
                    return decodeEnum(intVal, SI_UNIT_MAP, bda);
                }
                if (name.equals("ctlmodel")) {
                    return decodeEnum(intVal, CTL_MODEL_MAP, bda);
                }
                if (name.equals("health")) {
                    return decodeEnum(intVal, HEALTH_MAP, bda);
                }
                if (name.equals("mod") || name.equals("beh")) {
                    return decodeEnum(intVal, MOD_BEH_MAP, bda);
                }
                if (name.equals("range")) {
                    return decodeEnum(intVal, RANGE_MAP, bda);
                }
                if (name.equals("dir") || name.equals("neut")) {
                    return decodeEnum(intVal, DIR_MAP, bda);
                }
                if (name.equals("orcategory") || name.equals("origin")) {
                    return decodeEnum(intVal, OR_CATEGORY_MAP, bda);
                }
                if (name.equals("autorecst")) {
                    return decodeEnum(intVal, AUTO_REC_ST_MAP, bda);
                }
                if (name.equals("fltloop")) {
                    return decodeEnum(intVal, FLT_LOOP_MAP, bda);
                }
            }
            return bda.getValueString();
        }
        return node.toString();
    } catch (Exception e) {
        return "ERROR: " + e.getMessage();
    }
}

private int getIntValue(BasicDataAttribute bda) {
    if (bda instanceof BdaInt8)   return ((BdaInt8) bda).getValue();
    if (bda instanceof BdaInt8U)  return ((BdaInt8U) bda).getValue();
    if (bda instanceof BdaInt16)  return ((BdaInt16) bda).getValue();
    if (bda instanceof BdaInt16U) return ((BdaInt16U) bda).getValue();
    if (bda instanceof BdaInt32)  return ((BdaInt32) bda).getValue();
    return 0;
}

private String decodeEnum(int value, Map<Integer, String> map, BasicDataAttribute bda) {
    String text = map.get(value);
    if (text != null) return text;
    return bda.getValueString() + " (?)";  // valor desconocido — muestra raw + indicador
}
```

## Nota de compilación
Los mapas son `static` — se inicializan una sola vez en el classloader. No hay impacto en rendimiento.
`getIntValue()` y `decodeEnum()` son métodos privados nuevos — agregar en el mismo archivo.

---

---

# FIX 2 — Panel "Información del IED" (FC=DC Nameplate)

## Archivos: `IEC61850Client.java` + `IEDExplorerApp.java`

## Por qué
Al conectar a un IED, el usuario no tiene forma de ver fabricante, modelo, versión de firmware ni revisión de configuración. Esta información vive en `LLN0.NamPlt` (CDC LPL) y `LPHD1.PhyNam` (CDC DPL) con FC=DC.

## Qué IEDs lo tienen
Todos. Confirmado en: ABB RED670, Siemens 7SL87/7UT85/7UM85, Schneider MICOM P143/P545, SEL-487B, ZIV.

## IEC 61850-6 §9.5.4.1 — CDC LPL (Logical node name Plate)
```
LLN0.NamPlt.vendor     [DC] VisString255  — fabricante
LLN0.NamPlt.swRev      [DC] VisString255  — versión firmware
LLN0.NamPlt.hwRev      [DC] VisString255  — versión hardware (opcional)
LLN0.NamPlt.configRev  [DC] VisString255  — revisión de configuración
LLN0.NamPlt.d          [DC] VisString255  — descripción textual
LPHD1.PhyNam.vendor    [DC] VisString255  — fabricante dispositivo físico
LPHD1.PhyNam.model     [DC] VisString255  — modelo
LPHD1.PhyNam.serNum    [DC] VisString255  — número de serie
```

## Paso 1 — Agregar método en `IEC61850Client.java`

Agregar después de `readValue()` (línea ~282):

```java
/**
 * Lee la placa de identificación del IED (FC=DC).
 * Retorna mapa con claves: vendor, swRev, hwRev, configRev, d, model, serNum
 * Las claves ausentes no están en el mapa (no lanzar excepción si no existe el nodo).
 */
public Map<String, String> readDeviceNameplate() {
    Map<String, String> result = new LinkedHashMap<>();
    if (!isConnected() || serverModel == null) return result;

    // Pares: referencia del DA → clave en el resultado
    String[][] refs = {
        {"LLN0.NamPlt.vendor",    "vendor"},
        {"LLN0.NamPlt.swRev",     "swRev"},
        {"LLN0.NamPlt.hwRev",     "hwRev"},
        {"LLN0.NamPlt.configRev", "configRev"},
        {"LLN0.NamPlt.d",         "d"},
        {"LPHD1.PhyNam.vendor",   "phy.vendor"},
        {"LPHD1.PhyNam.model",    "phy.model"},
        {"LPHD1.PhyNam.serNum",   "phy.serNum"},
    };

    // Buscar el primer LD del modelo para armar el prefijo
    String ldPrefix = "";
    if (serverModel.getChildren() != null && !serverModel.getChildren().isEmpty()) {
        ldPrefix = serverModel.getChildren().iterator().next().getName() + "/";
    }

    for (String[] ref : refs) {
        try {
            String fullRef = ldPrefix + ref[0];
            ModelNode node = serverModel.findModelNode(fullRef, Fc.DC);
            if (node instanceof FcModelNode) {
                association.getDataValues((FcModelNode) node);
                String val = formatValue(node);
                if (val != null && !val.isEmpty() && !val.equals("null")) {
                    result.put(ref[1], val);
                }
            }
        } catch (Exception e) {
            // Nodo no presente en este IED — continuar con el siguiente
        }
    }
    return result;
}
```

## Paso 2 — Agregar campo en `IEDExplorerApp.java`

### 2a. Declarar label en la sección de campos (cerca de línea 94, sección de variables)
```java
private JLabel lblIedInfo;  // ← agregar junto a los otros JLabel
```

### 2b. Crear el label en `createStatusBar()` o en el panel de conexión
Buscar `createStatusBar()` (línea ~865) y agregar dentro:
```java
lblIedInfo = new JLabel(" ");
lblIedInfo.setFont(new Font("Monospaced", Font.PLAIN, 11));
lblIedInfo.setForeground(new Color(60, 60, 180));
statusBar.add(lblIedInfo, BorderLayout.CENTER);
```

### 2c. Llamar a `readDeviceNameplate()` después de conectar
En `connect()` (línea 5246), después de `displayClientModel()`:
```java
log("Construyendo arbol del modelo...");
displayClientModel();
log("Conectado! Modelo recibido.");

// ── NUEVO: leer placa de identificación del IED ──
backgroundExecutor.submit(() -> {
    Map<String, String> plate = client.readDeviceNameplate();
    if (!plate.isEmpty()) {
        String info = String.format("IED: %s  |  FW: %s  |  Config: %s",
            plate.getOrDefault("vendor", "?"),
            plate.getOrDefault("swRev",  "?"),
            plate.getOrDefault("configRev", "?"));
        SwingUtilities.invokeLater(() -> {
            lblIedInfo.setText(info);
            log("Placa IED: " + info);
        });
    }
});
```

### 2d. Limpiar al desconectar
En `handleDisconnect()` (línea ~5278):
```java
lblIedInfo.setText(" ");
```

---

---

# FIX 3 — Setting Groups real (SGCB vía MMS)

## Archivos: `IEC61850Client.java` + `IEDExplorerApp.java`

## Por qué
El panel "Setting Groups" existe pero `activateSelectedSettingGroup()` (línea 6348) y `editSelectedSettingGroup()` (línea 6359) son stubs. Las filas muestran `"1"` hardcodeado para grupo activo y editable (líneas 6307-6308). Para relés Siemens SIPROTEC5 (7SL87, 7UT85, 7UM85) y ABB RED670 (numOfSGs=4), el panel es completamente inútil.

## IEC 61850-6 §9.3.12 — SGCB
- Nombre fijo: `SGCB` (uno solo por LN0, no puede haber más)
- Atributo `numOfSGs` (requerido, ≥1): número de grupos disponibles
- Atributo `actSG` (default=1): grupo activo al cargar configuración
- Los servicios MMS son de IEC 61850-7-2

## Servicios MMS (iec61850bean v1.9.0)
```java
// Buscar el SGCB en el modelo
SgControlBlock sgcb = (SgControlBlock) serverModel.findModelNode(ldName + "/LLN0.SGCB", null);

// Leer valores del SGCB
association.getSgCbValues(sgcb);
int actSg  = sgcb.getActSg().getValue();
int numSgs = sgcb.getNumSgs().getValue();

// Cambiar grupo activo (¡AFECTA LA PROTECCIÓN EN TIEMPO REAL!)
association.selectActiveSgValues(sgcb, groupNumber);
```

## IEDs afectados (confirmados en archivos CID analizados)
- Siemens 7UT85: ~65 DAs con FC=SE
- Siemens 7SL87: FC=SE, FC=SR activos
- Siprotec 7UM85: FC=SG activo
- ABB REF630: `<SettingControl numOfSGs="4"/>` en SCL

## Paso 1 — Agregar métodos en `IEC61850Client.java`

Agregar después de `readDeviceNameplate()`:

```java
/**
 * Busca el SGCB (Setting Group Control Block) en un LD dado.
 * El SGCB tiene nombre fijo "SGCB" según IEC 61850-7-2.
 * @param ldName nombre del LD, ej: "LD0"
 */
public SgControlBlock findSGCB(String ldName) {
    if (serverModel == null) return null;
    try {
        ModelNode node = serverModel.findModelNode(ldName + "/LLN0.SGCB", null);
        if (node instanceof SgControlBlock) return (SgControlBlock) node;
    } catch (Exception e) {
        System.err.println("[SGCB] No encontrado en " + ldName + ": " + e.getMessage());
    }
    return null;
}

/**
 * Lee los valores actuales del SGCB.
 * @return array [actSG, numOfSGs] o null si falla
 */
public int[] readSGCBValues(SgControlBlock sgcb) throws IOException {
    if (!isConnected() || sgcb == null) return null;
    try {
        association.getSgCbValues(sgcb);
        int actSg  = sgcb.getActSg()  != null ? sgcb.getActSg().getValue()  : 1;
        int numSgs = sgcb.getNumSgs() != null ? sgcb.getNumSgs().getValue() : 1;
        return new int[]{actSg, numSgs};
    } catch (ServiceError e) {
        throw new IOException("ServiceError SGCB: " + e.getErrorCode(), e);
    }
}

/**
 * Activa un grupo de ajuste.
 * ADVERTENCIA: cambia el comportamiento de la protección en tiempo real.
 * @param sgcb el SGCB encontrado con findSGCB()
 * @param groupNumber número de grupo a activar (1..numOfSGs)
 */
public void selectActiveSG(SgControlBlock sgcb, int groupNumber) throws IOException {
    if (!isConnected() || sgcb == null) throw new IOException("No conectado o SGCB nulo");
    try {
        association.selectActiveSgValues(sgcb, groupNumber);
        System.out.println("[SGCB] Grupo activo cambiado a: " + groupNumber);
    } catch (ServiceError e) {
        throw new IOException("ServiceError SelectActiveSG: " + e.getErrorCode(), e);
    }
}
```

## Paso 2 — Actualizar `refreshSettingGroups()` en `IEDExplorerApp.java`

Reemplazar completamente `refreshSettingGroups()` (línea 6275):

```java
private void refreshSettingGroups() {
    settingGroupsTableModel.setRowCount(0);

    ServerModel model = currentMode == AppMode.SERVER
        ? server.getServerModel()
        : client.getServerModel();

    if (model == null) {
        log("No hay modelo cargado para Setting Groups");
        return;
    }

    for (ModelNode ld : model.getChildren()) {
        for (ModelNode ln : ld.getChildren()) {
            if (ln instanceof LogicalNode) {
                LogicalNode logicalNode = (LogicalNode) ln;
                findSettingGroups(logicalNode, ld.getName() + "/" + ln.getName());
            }
        }
    }

    // ── NUEVO: si estamos en modo cliente, leer SGCB real ──
    if (currentMode == AppMode.CLIENT && isConnected && client != null) {
        backgroundExecutor.submit(() -> {
            for (ModelNode ld : model.getChildren()) {
                SgControlBlock sgcb = client.findSGCB(ld.getName());
                if (sgcb != null) {
                    try {
                        int[] vals = client.readSGCBValues(sgcb);
                        if (vals != null) {
                            final int actSg  = vals[0];
                            final int numSgs = vals[1];
                            final String ldName = ld.getName();
                            SwingUtilities.invokeLater(() -> {
                                log(String.format("[SGCB] %s: actSG=%d, numOfSGs=%d", ldName, actSg, numSgs));
                                // Actualizar las filas de ese LD con el valor real
                                for (int r = 0; r < settingGroupsTableModel.getRowCount(); r++) {
                                    String ref = (String) settingGroupsTableModel.getValueAt(r, 0);
                                    if (ref.startsWith(ldName)) {
                                        settingGroupsTableModel.setValueAt(String.valueOf(actSg),  r, 1);
                                        settingGroupsTableModel.setValueAt(String.valueOf(numSgs), r, 2);
                                    }
                                }
                            });
                        }
                    } catch (Exception ex) {
                        log("[SGCB] Error leyendo " + ld.getName() + ": " + ex.getMessage());
                    }
                }
            }
        });
    }

    log("Setting Groups actualizados: " + settingGroupsTableModel.getRowCount() + " encontrados");
}
```

## Paso 3 — Implementar `activateSelectedSettingGroup()` (línea 6348)

Reemplazar el stub completo:

```java
private void activateSelectedSettingGroup() {
    int row = settingGroupsTable.getSelectedRow();
    if (row < 0) {
        JOptionPane.showMessageDialog(this, "Seleccione un Setting Group");
        return;
    }

    if (currentMode != AppMode.CLIENT || !isConnected) {
        JOptionPane.showMessageDialog(this, "Función disponible solo en modo Cliente conectado");
        return;
    }

    String ref = (String) settingGroupsTableModel.getValueAt(row, 0);
    String numSgsStr = (String) settingGroupsTableModel.getValueAt(row, 2);
    int numSgs = 1;
    try { numSgs = Integer.parseInt(numSgsStr); } catch (Exception ignored) {}

    // Extraer el nombre del LD de la referencia "LD0/LLN0.DO"
    String ldName = ref.contains("/") ? ref.split("/")[0] : "LD0";

    // Pedir número de grupo
    String[] grupos = new String[numSgs];
    for (int i = 0; i < numSgs; i++) grupos[i] = "Grupo " + (i + 1);

    String sel = (String) JOptionPane.showInputDialog(this,
        "Seleccione el grupo de ajuste a activar:\n" +
        "⚠ ATENCIÓN: Esto cambia el comportamiento de la protección en tiempo real.",
        "Activar Setting Group — " + ldName,
        JOptionPane.WARNING_MESSAGE, null, grupos, grupos[0]);

    if (sel == null) return;
    int groupNum = Integer.parseInt(sel.replace("Grupo ", ""));

    int confirm = JOptionPane.showConfirmDialog(this,
        "¿Confirma activar el Grupo " + groupNum + " en " + ldName + "?\n" +
        "Esta acción modifica la configuración de protección activa.",
        "Confirmar cambio de grupo",
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

    if (confirm != JOptionPane.YES_OPTION) return;

    final String finalLdName = ldName;
    final int finalGroup = groupNum;
    backgroundExecutor.submit(() -> {
        try {
            SgControlBlock sgcb = client.findSGCB(finalLdName);
            if (sgcb == null) {
                SwingUtilities.invokeLater(() ->
                    log("[SGCB] SGCB no encontrado en " + finalLdName));
                return;
            }
            client.selectActiveSG(sgcb, finalGroup);
            SwingUtilities.invokeLater(() -> {
                log("[SGCB] Grupo " + finalGroup + " activado en " + finalLdName);
                JOptionPane.showMessageDialog(this,
                    "Grupo " + finalGroup + " activado correctamente en " + finalLdName,
                    "OK", JOptionPane.INFORMATION_MESSAGE);
                refreshSettingGroups();  // refrescar para mostrar nuevo actSG
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
```

---

---

# FIX 4 — FC=SP: Pestaña de ajustes de protección

## Archivos: `IEC61850Client.java` + `IEDExplorerApp.java`

## Por qué
Los ajustes de protección (corrientes de arranque, tiempos de actuación, umbrales de tensión) viven en FC=SP. Hoy están mezclados en la pestaña Setting Groups sin distinción. Fabricantes donde es crítico:
- Schneider MICOM P143/P545: `setMag`, `setTmms`
- ZIV MCVA N616M: ~34 DAs SP
- Siemens 7SL87: ajustes de distancia (PDIS), PTOC, RBRF

## IEC 61850-6 §9.5.4.4
`valKind="Set"` con `fc="SP"`: valor que puede ser escrito en el IED en operación mediante `SetDataValues`.

## Paso 1 — Método `readSPValues()` en `IEC61850Client.java`

```java
/**
 * Lee todos los DAs con FC=SP del modelo y los devuelve como lista.
 * Cada entrada: [referencia, valor formateado, tipo BDA]
 */
public List<String[]> readAllSPValues() throws IOException {
    List<String[]> results = new ArrayList<>();
    if (!isConnected() || serverModel == null) return results;

    collectFcNodes(serverModel, Fc.SP, results);
    return results;
}

private void collectFcNodes(ModelNode parent, Fc targetFc, List<String[]> results) {
    for (ModelNode child : parent.getChildren()) {
        if (child instanceof FcModelNode) {
            FcModelNode fcNode = (FcModelNode) child;
            if (fcNode.getFc() == targetFc && child instanceof BasicDataAttribute) {
                try {
                    association.getDataValues(fcNode);
                    results.add(new String[]{
                        child.getReference().toString(),
                        formatValue(child),
                        getValueType(child)
                    });
                } catch (Exception e) {
                    results.add(new String[]{
                        child.getReference().toString(),
                        "ERROR: " + e.getMessage(),
                        "?"
                    });
                }
            }
        }
        if (!child.getChildren().isEmpty()) {
            collectFcNodes(child, targetFc, results);
        }
    }
}

/**
 * Escribe un valor SP en el IED.
 */
public void writeSPValue(String reference, String value) throws IOException {
    writeValue(reference, Fc.SP, value);  // writeValue() ya existe en línea 369
}
```

## Paso 2 — Nueva pestaña "Ajustes SP" en `IEDExplorerApp.java`

### 2a. Variables de clase (agregar con las otras variables)
```java
private DefaultTableModel spTableModel;
private JTable spTable;
```

### 2b. Crear el panel (nuevo método)
```java
private JPanel createSettingsPanel() {
    JPanel panel = new JPanel(new BorderLayout(5, 5));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    JToolBar tb = new JToolBar();
    tb.setFloatable(false);
    JButton btnRefresh = new JButton("Leer ajustes (SP)");
    btnRefresh.addActionListener(e -> refreshSPValues());
    JButton btnWrite = new JButton("Escribir seleccionado");
    btnWrite.addActionListener(e -> writeSPValue());
    tb.add(btnRefresh);
    tb.addSeparator();
    tb.add(btnWrite);
    panel.add(tb, BorderLayout.NORTH);

    String[] cols = {"Referencia", "Valor actual", "Tipo"};
    spTableModel = new DefaultTableModel(cols, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    spTable = new JTable(spTableModel);
    spTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
    spTable.getColumnModel().getColumn(0).setPreferredWidth(380);
    spTable.getColumnModel().getColumn(1).setPreferredWidth(150);
    spTable.getColumnModel().getColumn(2).setPreferredWidth(80);

    panel.add(new JScrollPane(spTable), BorderLayout.CENTER);

    JLabel info = new JLabel("  FC=SP: ajustes de protección. Doble clic para editar.");
    info.setForeground(Color.GRAY);
    panel.add(info, BorderLayout.SOUTH);

    return panel;
}

private void refreshSPValues() {
    if (currentMode != AppMode.CLIENT || !isConnected) {
        log("Ajustes SP disponibles solo en modo Cliente conectado");
        return;
    }
    spTableModel.setRowCount(0);
    log("Leyendo ajustes SP...");
    backgroundExecutor.submit(() -> {
        try {
            List<String[]> vals = client.readAllSPValues();
            SwingUtilities.invokeLater(() -> {
                for (String[] row : vals) spTableModel.addRow(row);
                log("Ajustes SP: " + vals.size() + " encontrados");
            });
        } catch (Exception ex) {
            log("ERROR leyendo SP: " + ex.getMessage());
        }
    });
}

private void writeSPValue() {
    int row = spTable.getSelectedRow();
    if (row < 0) { JOptionPane.showMessageDialog(this, "Seleccione un ajuste"); return; }
    if (currentMode != AppMode.CLIENT || !isConnected) return;

    String ref = (String) spTableModel.getValueAt(row, 0);
    String cur = (String) spTableModel.getValueAt(row, 1);
    String newVal = JOptionPane.showInputDialog(this,
        "Nuevo valor para:\n" + ref, cur);
    if (newVal == null) return;

    backgroundExecutor.submit(() -> {
        try {
            client.writeSPValue(ref, newVal);
            SwingUtilities.invokeLater(() -> {
                spTableModel.setValueAt(newVal, row, 1);
                log("[SP] Escrito: " + ref + " = " + newVal);
            });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
        }
    });
}
```

### 2c. Agregar la pestaña en `createRightPanel()` (línea ~668)
```java
rightTabbedPane.addTab("Ajustes SP", createSettingsPanel());
```

---

---

# FIX 5 — Communication section: pre-llenar GOOSE desde SCL

## Archivo: `IEDExplorerApp.java`

## Por qué
Al cargar un CID para el modo GOOSE Publisher, el usuario ingresa manualmente MAC, APPID y VLAN.
Esta información ya está en el CID en la sección `<Communication>`. Afecta a todos los IEDs con GOOSE.

## IEC 61850-6 §9.4.4 — Estructura XML en `<Communication>`
```xml
<Communication>
  <SubNetwork name="ProcessBus">
    <ConnectedAP iedName="E1Q1SB1" apName="S1">
      <GSE ldInst="LD0" cbName="gcb1">
        <Address>
          <P type="MAC-Address">01-0C-CD-01-00-01</P>
          <P type="APPID">0001</P>
          <P type="VLAN-PRIORITY">4</P>
          <P type="VLAN-ID">000</P>
        </Address>
        <MinTime unit="s" multiplier="m">4</MinTime>
        <MaxTime unit="s" multiplier="m">10000</MaxTime>
      </GSE>
    </ConnectedAP>
  </SubNetwork>
</Communication>
```

## Estructura de datos para almacenar la info

```java
// Agregar como clase interna o inner class en IEDExplorerApp
static class GseAddress {
    String ldInst;
    String cbName;
    String mac;
    String appId;
    String vlanId;
    String vlanPriority;
    String minTime;
    String maxTime;
}
// En la clase: Map<String, GseAddress> gseAddressMap = new HashMap<>();
// Clave del mapa: ldInst + "/" + cbName
```

## Método de parseo (agregar en IEDExplorerApp)

```java
/**
 * Parsea la sección <Communication> del documento SCL ya cargado.
 * @param doc el Document XML del CID/SCD ya parseado
 * @param iedName nombre del IED cuyo ConnectedAP queremos
 */
private void parseCommunicationSection(Document doc, String iedName) {
    gseAddressMap.clear();
    NodeList connectedAPs = doc.getElementsByTagName("ConnectedAP");
    for (int i = 0; i < connectedAPs.getLength(); i++) {
        Element ap = (Element) connectedAPs.item(i);
        String apIed = ap.getAttribute("iedName");
        if (!apIed.equals(iedName)) continue;

        NodeList gseList = ap.getElementsByTagName("GSE");
        for (int j = 0; j < gseList.getLength(); j++) {
            Element gse = (Element) gseList.item(j);
            GseAddress addr = new GseAddress();
            addr.ldInst = gse.getAttribute("ldInst");
            addr.cbName = gse.getAttribute("cbName");

            NodeList pList = gse.getElementsByTagName("P");
            for (int k = 0; k < pList.getLength(); k++) {
                Element p = (Element) pList.item(k);
                String type = p.getAttribute("type");
                String val  = p.getTextContent().trim();
                switch (type) {
                    case "MAC-Address":    addr.mac          = val; break;
                    case "APPID":          addr.appId        = val; break;
                    case "VLAN-ID":        addr.vlanId       = val; break;
                    case "VLAN-PRIORITY":  addr.vlanPriority = val; break;
                }
            }
            NodeList minT = gse.getElementsByTagName("MinTime");
            NodeList maxT = gse.getElementsByTagName("MaxTime");
            if (minT.getLength() > 0) addr.minTime = minT.item(0).getTextContent().trim();
            if (maxT.getLength() > 0) addr.maxTime = maxT.item(0).getTextContent().trim();

            gseAddressMap.put(addr.ldInst + "/" + addr.cbName, addr);
        }
    }
    log("Communication section: " + gseAddressMap.size() + " GSE addresses cargadas");
}
```

## Dónde llamar `parseCommunicationSection()`
En `parseGoCBsFromScl()` (ya existe en IEDExplorerApp, buscar por nombre), después de parsear el Document:
```java
// Tras obtener el Document doc y el iedName:
parseCommunicationSection(doc, loadedIedName);
```

## Cómo usar la info en la tabla GOOSE
En `updateGoCBsTable()` o donde se llena la tabla de GoCBs, para cada gcb:
```java
GseAddress addr = gseAddressMap.get(gcb.ldInst + "/" + gcb.name);
if (addr != null) {
    // pre-llenar MAC destination con addr.mac
    // pre-llenar APPID con Integer.parseInt(addr.appId, 16)
    // mostrar VLAN-ID y VLAN-PRIORITY en columnas
    // mostrar MinTime/MaxTime para referencia
}
```

---

---

# FIX 6 — Dataset browser: leer valores FCDA por MMS

## Archivos: `IEC61850Client.java` + `IEDExplorerApp.java`

## Estado actual
El panel "Dataset" (línea 6371) ya existe y muestra nombre/conteo de miembros.
`showDatasetMembers()` (línea 6484) muestra los FCDAs del modelo local.
**Lo que falta**: leer los valores reales de los miembros via `GetDataSetValues` en modo cliente MMS.

## IEC 61850-6 §9.3.7 — Servicios MMS
```java
// iec61850bean: leer todos los valores de un DataSet en una operación
association.getDataSetValues(dataSet);  // dataSet es un objeto DataSet del serverModel

// Después de esta llamada, los FcModelNode miembros tienen sus valores actualizados
for (FcModelNode member : dataSet.getMembers()) {
    String ref = member.getReference().toString();
    String val = formatValue(member);  // usa el formatValue mejorado del Fix 1
}
```

## Paso 1 — Método en `IEC61850Client.java`

```java
/**
 * Lee todos los valores de un DataSet via GetDataSetValues.
 * Requiere que el DataSet sea encontrado en el serverModel.
 * @param dsReference referencia del DataSet, ej: "LD0/LLN0.StatNrml"
 * @return lista de [referencia FCDA, valor, FC, tipo]
 */
public List<String[]> readDataSetValues(String dsReference) throws IOException {
    if (!isConnected() || serverModel == null) throw new IOException("Not connected");
    List<String[]> results = new ArrayList<>();
    try {
        // Buscar el DataSet en el serverModel
        DataSet ds = null;
        for (DataSet candidate : serverModel.getDataSets()) {
            if (candidate.getReferenceStr().equals(dsReference)) {
                ds = candidate;
                break;
            }
        }
        if (ds == null) throw new IOException("DataSet no encontrado: " + dsReference);

        association.getDataSetValues(ds);

        for (FcModelNode member : ds.getMembers()) {
            results.add(new String[]{
                member.getReference().toString(),
                formatValue(member),
                member.getFc().toString(),
                getValueType(member)
            });
        }
    } catch (ServiceError e) {
        throw new IOException("ServiceError GetDataSetValues: " + e.getErrorCode(), e);
    }
    return results;
}
```

## Paso 2 — Botón "Leer valores" en panel Dataset (`IEDExplorerApp.java`)

En `createDatasetPanel()` (línea 6371), en la toolbar ya existente, agregar:
```java
JButton btnReadValues = new JButton("Leer valores FCDA");
btnReadValues.addActionListener(e -> readSelectedDatasetValues());
toolbar.add(btnReadValues);
```

Nuevo método:
```java
private void readSelectedDatasetValues() {
    int row = datasetTable.getSelectedRow();
    if (row < 0) { JOptionPane.showMessageDialog(this, "Seleccione un Dataset"); return; }
    if (currentMode != AppMode.CLIENT || !isConnected) {
        log("Leer valores FCDA disponible solo en modo Cliente conectado");
        return;
    }
    String dsRef = (String) datasetTableModel.getValueAt(row, 2);
    log("Leyendo valores de DataSet: " + dsRef);
    backgroundExecutor.submit(() -> {
        try {
            List<String[]> vals = client.readDataSetValues(dsRef);
            SwingUtilities.invokeLater(() -> {
                datasetMembersTableModel.setRowCount(0);
                for (String[] v : vals) datasetMembersTableModel.addRow(v);
                log("DataSet " + dsRef + ": " + vals.size() + " valores leídos");
            });
        } catch (Exception ex) {
            log("ERROR GetDataSetValues: " + ex.getMessage());
        }
    });
}
```

Asegurarse que `datasetMembersTableModel` tenga columnas: `{"Referencia FCDA", "Valor", "FC", "Tipo"}`.
Buscar `datasetMembersTableModel` en `createDatasetPanel()` y ajustar si solo tiene 3 columnas.

---

---

# FIX 7 — FC=SV: Sustitución supervisada

## Archivos: `IEC61850Client.java` + `IEDExplorerApp.java`

## Por qué
Permite a un operador "congelar" un valor de proceso con un valor artificial durante mantenimiento.
Cuando `subEna=true`, el IED usa `subVal`/`subQ` en lugar del valor real.
Presente en: ABB RED670, Siemens 7SL87/7UT85, NRR MU1.1.
**VERIFICAR PICS_SUBST antes de intentar escribir** — no todos los IEDs lo soportan.

## IEC 61850-6 §9.5.4.1 — atributos FC=SV en CDCs
```
subEna  [SV] BOOLEAN      — activa/desactiva la sustitución
subVal  [SV] <tipo DO>    — valor artificial a usar
subQ    [SV] Quality      — calidad del valor artificial
subID   [SV] VisString64  — identificador del operador que activó (opcional)
```

## Paso 1 — Métodos en `IEC61850Client.java`

```java
/**
 * Verifica si un DO tiene atributos de sustitución (subEna, subVal).
 */
public boolean supportsSubstitution(String doReference) {
    if (serverModel == null) return false;
    ModelNode subEna = serverModel.findModelNode(doReference + ".subEna", Fc.SV);
    return subEna != null;
}

/**
 * Activa la sustitución de un valor de proceso.
 * @param doReference referencia al DO, ej: "LD0/XCBR1.Pos"
 * @param subValue    valor como string (se convierte según el tipo del DO)
 * @param operatorId  identificador del operador (para auditoría)
 */
public void activateSubstitution(String doReference, String subValue, String operatorId)
        throws IOException {
    if (!isConnected() || serverModel == null) throw new IOException("Not connected");
    try {
        // 1. Escribir subVal
        FcModelNode subValNode = (FcModelNode) serverModel.findModelNode(doReference + ".subVal", Fc.SV);
        if (subValNode instanceof BasicDataAttribute) {
            setBasicDataAttributeValue((BasicDataAttribute) subValNode, subValue);
            association.setDataValues(subValNode);
        }
        // 2. Escribir subID si existe
        FcModelNode subIdNode = (FcModelNode) serverModel.findModelNode(doReference + ".subID", Fc.SV);
        if (subIdNode instanceof BdaVisibleString && operatorId != null) {
            ((BdaVisibleString) subIdNode).setValue(operatorId);
            association.setDataValues(subIdNode);
        }
        // 3. Activar subEna=true
        FcModelNode subEnaNode = (FcModelNode) serverModel.findModelNode(doReference + ".subEna", Fc.SV);
        if (subEnaNode instanceof BdaBoolean) {
            ((BdaBoolean) subEnaNode).setValue(true);
            association.setDataValues(subEnaNode);
        }
    } catch (ServiceError e) {
        throw new IOException("ServiceError SV activate: " + e.getErrorCode(), e);
    }
}

/**
 * Desactiva la sustitución (restaura el valor real del proceso).
 */
public void deactivateSubstitution(String doReference) throws IOException {
    if (!isConnected() || serverModel == null) throw new IOException("Not connected");
    try {
        FcModelNode subEnaNode = (FcModelNode) serverModel.findModelNode(doReference + ".subEna", Fc.SV);
        if (subEnaNode instanceof BdaBoolean) {
            ((BdaBoolean) subEnaNode).setValue(false);
            association.setDataValues(subEnaNode);
        }
    } catch (ServiceError e) {
        throw new IOException("ServiceError SV deactivate: " + e.getErrorCode(), e);
    }
}
```

## Paso 2 — Menú contextual en árbol (`IEDExplorerApp.java`)

En `createTreePopupMenu()` (línea ~3945), en el menú de cliente, agregar:

```java
treePopupMenu.addSeparator();
JMenuItem miSubstitute = new JMenuItem("Sustituir valor (SV)...");
miSubstitute.addActionListener(e -> substituteSelectedNode());
treePopupMenu.add(miSubstitute);
```

Nuevo método:
```java
private void substituteSelectedNode() {
    if (currentMode != AppMode.CLIENT || !isConnected) return;
    TreePath path = modelTree.getSelectionPath();
    if (path == null) return;
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
    Object userObj = treeNode.getUserObject();
    if (!(userObj instanceof FcModelNode)) return;

    FcModelNode node = (FcModelNode) userObj;
    String doRef = node.getReference().toString();
    // Subir al DO padre si el nodo es un DA
    if (node instanceof BasicDataAttribute) {
        ModelNode parent = node.getParent();
        if (parent != null) doRef = parent.getReference().toString();
    }

    if (!client.supportsSubstitution(doRef)) {
        JOptionPane.showMessageDialog(this,
            "Este DO no soporta sustitución (PICS_SUBST no declarado)",
            "No soportado", JOptionPane.WARNING_MESSAGE);
        return;
    }

    String curVal = formatValue(node);  // helper o usar client.formatValue
    String newVal = JOptionPane.showInputDialog(this,
        "Valor de sustitución para:\n" + doRef + "\nValor actual: " + curVal,
        curVal);
    if (newVal == null) return;

    String opId = System.getProperty("user.name", "IEDNavigator");
    final String finalDoRef = doRef;
    backgroundExecutor.submit(() -> {
        try {
            client.activateSubstitution(finalDoRef, newVal, opId);
            SwingUtilities.invokeLater(() ->
                log("[SV] Sustitución activada: " + finalDoRef + " = " + newVal));
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Error SV: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE));
        }
    });
}
```

---

---

# FIX 8 — LGOS: mapa de suscripciones GOOSE (solo SCL)

## Archivo: `IEDExplorerApp.java`

## Por qué
Los nodos `LGOS` en el SCL tienen una sección `<Inputs>` con `<ExtRef serviceType="GOOSE">` que dice
exactamente de qué publicadores GOOSE externos depende el IED. Es información puramente de ingeniería SCL.
No requiere servicios MMS adicionales.

## IEC 61850-6 §9.3.13 — Estructura ExtRef
```xml
<LN lnClass="LGOS" inst="1" lnType="LGOS_type">
  <Inputs>
    <ExtRef iedName="IED_Publisher"
            ldInst="LD0" lnClass="LLN0"
            doName="GoCB1" serviceType="GOOSE"
            srcLDInst="LD0" srcLNClass="LLN0" srcCBName="gcb1"/>
  </Inputs>
</LN>
```

## Estructura de datos

```java
static class GooseDependency {
    String subscriberIed;     // IED que suscribe
    String publisherIed;      // IED que publica
    String srcLdInst;         // LD del publisher
    String srcCbName;         // cbName del GoCB
    String doName;            // dato al que se suscribe
}
List<GooseDependency> gooseDependencies = new ArrayList<>();
```

## Método de parseo (en IEDExplorerApp)

```java
/**
 * Parsea los ExtRef de suscripción GOOSE de todos los IEDs en el documento SCL.
 */
private void parseGooseDependencies(Document doc) {
    gooseDependencies.clear();
    NodeList extRefs = doc.getElementsByTagName("ExtRef");
    for (int i = 0; i < extRefs.getLength(); i++) {
        Element extRef = (Element) extRefs.item(i);
        String serviceType = extRef.getAttribute("serviceType");
        if (!"GOOSE".equals(serviceType) && !"SMV".equals(serviceType)) continue;

        GooseDependency dep = new GooseDependency();
        dep.publisherIed = extRef.getAttribute("iedName");
        dep.srcLdInst    = extRef.getAttribute("srcLDInst");
        dep.srcCbName    = extRef.getAttribute("srcCBName");
        dep.doName       = extRef.getAttribute("doName");
        // El suscriptor es el IED al que pertenece este ExtRef
        // Subir en el DOM hasta encontrar el elemento <IED>
        Node parent = extRef.getParentNode();
        while (parent != null && !parent.getNodeName().equals("IED")) {
            parent = parent.getParentNode();
        }
        dep.subscriberIed = parent != null ? ((Element) parent).getAttribute("name") : "?";

        gooseDependencies.add(dep);
    }
    log("Dependencias GOOSE/SMV encontradas: " + gooseDependencies.size());
}
```

## Dónde llamar
En `parseGoCBsFromScl()`, después de obtener el Document:
```java
parseGooseDependencies(doc);
```

## Visualización (pestaña opcional)
Las dependencias pueden mostrarse en el panel GOOSE en una sub-tabla o en el log al cargar el SCL.
Para versión rápida: simplemente loguear las dependencias al cargar:
```java
for (GooseDependency dep : gooseDependencies) {
    log(String.format("[LGOS] %s ← %s:%s/%s",
        dep.subscriberIed, dep.publisherIed, dep.srcLdInst, dep.srcCbName));
}
```

---

---

# FIX 9 — FC=BL: Bloqueo de actualizaciones

## Archivos: `IEC61850Client.java` + `IEDExplorerApp.java`

## Por qué
FC=BL congela el valor actual de un atributo de medición para que el IED deje de actualizarlo.
Presente en: Siemens 7SL87/7UT85, NRR MU1.1.
**Verificar PICS_BLK antes de intentar escribir.**

## Paso 1 — Métodos en `IEC61850Client.java`

```java
public boolean supportsBlocking(String doReference) {
    if (serverModel == null) return false;
    return serverModel.findModelNode(doReference + ".blkEna", Fc.BL) != null;
}

public void setBlocking(String doReference, boolean block) throws IOException {
    if (!isConnected() || serverModel == null) throw new IOException("Not connected");
    try {
        FcModelNode blkEna = (FcModelNode) serverModel.findModelNode(doReference + ".blkEna", Fc.BL);
        if (blkEna instanceof BdaBoolean) {
            ((BdaBoolean) blkEna).setValue(block);
            association.setDataValues(blkEna);
        } else {
            throw new IOException("Nodo blkEna no encontrado o tipo incorrecto");
        }
    } catch (ServiceError e) {
        throw new IOException("ServiceError BL: " + e.getErrorCode(), e);
    }
}
```

## Paso 2 — Menú contextual

En `createTreePopupMenu()`, agregar (junto a la opción de sustitución del Fix 7):
```java
JMenuItem miBlock = new JMenuItem("Bloquear/Desbloquear (BL)");
miBlock.addActionListener(e -> toggleBlockingSelectedNode());
treePopupMenu.add(miBlock);
```

Nuevo método (similar al de sustitución — verificar soporte, confirmar, ejecutar en background).

---

---

# Resumen para retomar en un nuevo chat

## Archivos modificados
```
src/main/java/com/iedexplorer/
  IEC61850Client.java     — Fix 1 (enums), Fix 2 (nameplate), Fix 3 (SGCB),
                            Fix 4 (SP), Fix 6 (DataSetValues), Fix 7 (SV), Fix 9 (BL)
  IEDExplorerApp.java     — Fix 2 (UI nameplate), Fix 3 (Setting Groups real),
                            Fix 4 (pestaña Ajustes SP), Fix 5 (Communication section),
                            Fix 6 (btn leer valores DataSet), Fix 7 (menú SV),
                            Fix 8 (parseo LGOS), Fix 9 (menú BL)
```

## Checklist de implementación

- [ ] **Fix 1** — Reemplazar `formatValue()` + agregar mapas estáticos + `getIntValue()` + `decodeEnum()` en `IEC61850Client.java`
- [ ] **Fix 2** — Agregar `readDeviceNameplate()` en Client + `lblIedInfo` en App + llamada post-connect
- [ ] **Fix 3** — Agregar `findSGCB()`, `readSGCBValues()`, `selectActiveSG()` en Client + actualizar `refreshSettingGroups()` + reemplazar stub `activateSelectedSettingGroup()` en App
- [ ] **Fix 4** — Agregar `readAllSPValues()`, `collectFcNodes()` en Client + nueva pestaña `createSettingsPanel()` + `refreshSPValues()` + `writeSPValue()` en App
- [ ] **Fix 5** — Agregar clase `GseAddress` + `parseCommunicationSection()` + llamada en `parseGoCBsFromScl()` + usar en tabla GOOSE en App
- [ ] **Fix 6** — Agregar `readDataSetValues()` en Client + botón + `readSelectedDatasetValues()` en App
- [ ] **Fix 7** — Agregar `supportsSubstitution()`, `activateSubstitution()`, `deactivateSubstitution()` en Client + menú contextual `substituteSelectedNode()` en App
- [ ] **Fix 8** — Agregar clase `GooseDependency` + `parseGooseDependencies()` + llamada en parseGoCBsFromScl() en App
- [ ] **Fix 9** — Agregar `supportsBlocking()`, `setBlocking()` en Client + menú contextual en App

## Compilar después de cada fix

```bash
JAVAC="C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot/bin/javac.exe"
cd "C:/Users/admin/Documents/proyectos IA/iec61850_java_explorer"
find src/main/java -name "*.java" | sed 's|/c/|C:/|g' | sed 's/.*/"&"/' > /tmp/srclist.txt
"$JAVAC" -d classes -cp "lib/*" -encoding UTF-8 @srclist.txt
```

O con Maven:
```bash
mvn clean package -DskipTests
```

## Referencias de líneas actuales (pueden desplazarse al agregar código)

| Método | Archivo | Línea aprox. |
|--------|---------|--------------|
| `formatValue()` | IEC61850Client | 302 |
| `getValueType()` | IEC61850Client | 341 |
| `writeValue()` | IEC61850Client | 369 |
| `connect()` + `displayClientModel()` | IEDExplorerApp | 5196 / 5246 |
| `createTreePopupMenu()` | IEDExplorerApp | 3945 |
| `rightTabbedPane.addTab(...)` | IEDExplorerApp | 664-670 |
| `refreshSettingGroups()` | IEDExplorerApp | 6275 |
| `findSettingGroups()` | IEDExplorerApp | 6297 |
| `activateSelectedSettingGroup()` | IEDExplorerApp | 6348 (stub) |
| `editSelectedSettingGroup()` | IEDExplorerApp | 6359 (stub) |
| `createDatasetPanel()` | IEDExplorerApp | 6371 |
| `refreshDatasets()` | IEDExplorerApp | 6424 |
| `showDatasetMembers()` | IEDExplorerApp | 6484 |
| `parseGoCBsFromScl()` | IEDExplorerApp | ~3151 |
| `parseDataSetsFromIED()` | IEDExplorerApp | 3276 |

## Documentos de análisis relacionados

```
SIN SMV/CID/ANALISIS_SCL.md          — 22 archivos CID/ICD/SCD analizados
SIN SMV/CID/IEC61850_GAPS_NORMATIVO.md — referencia IEC 61850-6 por gap
SIN SMV/IEC61850_full.md              — norma completa IEC 61850-6 Ed.2.0 (44089 líneas)
```

---

*Fin del documento — IEDNavigator v3.0-edu | Generado 2026-04-18*

# Bitácora de Refactorización — IEDNavigator v3.0-edu

> Cada entrada documenta UN cambio atómico. En caso de fallo, revertir en orden inverso.

---

## FASE 0 — Demarcaciones y preparación

### [F0-001] Agregar comentarios de sección en IEDExplorerApp.java
- **Fecha**: 2026-04-22
- **Archivo**: src/main/java/com/iedexplorer/IEDExplorerApp.java
- **Tipo**: Anotación (sin cambio funcional)
- **Cambio**: Inserción de comentarios `// ═══ SECTION: xxx ═══` para delimitar subsistemas
- **Rollback**: Eliminar los comentarios agregados (búsqueda por `═══ SECTION:`)
- **Verificación**: Compilar — debe producir las mismas clases sin diferencia funcional
- **Estado**: ✅ Aplicado

---

## FASE 1 — Extracción de clases internas (POJOs y renderers autónomos)

### [F1-001] Extraer SclGoCB a archivo propio
- **Fecha**: 2026-04-22
- **Archivo origen**: IEDExplorerApp.java (línea ~71)
- **Archivo destino**: src/main/java/com/iedexplorer/SclGoCB.java
- **Tipo**: Mover clase interna a top-level (mismo paquete)
- **Rollback**: Eliminar SclGoCB.java y restaurar la definición interna en IEDExplorerApp.java
- **Verificación**: Compilar + abrir GUI + cargar archivo CID
- **Estado**: ✅ Aplicado

### [F1-002] Extraer SclDataSet a archivo propio
- **Fecha**: 2026-04-22
- **Archivo origen**: IEDExplorerApp.java (línea ~6059)
- **Archivo destino**: src/main/java/com/iedexplorer/SclDataSet.java
- **Tipo**: Mover clase interna a top-level (mismo paquete)
- **Rollback**: Eliminar SclDataSet.java y restaurar en IEDExplorerApp.java
- **Verificación**: Compilar + cargar CID + verificar GoCBs en pestaña GOOSE
- **Estado**: ✅ Aplicado

### [F1-003] Extraer SclReport a archivo propio
- **Fecha**: 2026-04-22
- **Archivo origen**: IEDExplorerApp.java (línea ~6067)
- **Archivo destino**: src/main/java/com/iedexplorer/SclReport.java
- **Tipo**: Mover clase interna a top-level (mismo paquete)
- **Rollback**: Eliminar SclReport.java y restaurar en IEDExplorerApp.java
- **Verificación**: Compilar + habilitar un RCB desde pestaña Reports
- **Estado**: ✅ Aplicado

### [F1-004] Extraer MonitorItem a archivo propio
- **Fecha**: 2026-04-22
- **Archivo origen**: IEDExplorerApp.java (línea ~187)
- **Archivo destino**: src/main/java/com/iedexplorer/MonitorItem.java
- **Tipo**: Mover clase interna a top-level (mismo paquete)
- **Rollback**: Eliminar MonitorItem.java y restaurar en IEDExplorerApp.java
- **Verificación**: Compilar + agregar nodo a monitor + verificar que aparece en tabla
- **Estado**: ✅ Aplicado

### [F1-005] Extraer NodeInfo a archivo propio
- **Fecha**: 2026-04-22
- **Archivo origen**: IEDExplorerApp.java (línea ~6444)
- **Archivo destino**: src/main/java/com/iedexplorer/NodeInfo.java
- **Tipo**: Mover clase interna a top-level (mismo paquete)
- **Rollback**: Eliminar NodeInfo.java y restaurar en IEDExplorerApp.java
- **Verificación**: Compilar + navegar árbol de modelo + verificar iconos y colores
- **Estado**: ✅ Aplicado

### [F1-006] Extraer MonitorTableRenderer a archivo propio
- **Fecha**: 2026-04-22
- **Archivo origen**: IEDExplorerApp.java (línea ~4073)
- **Archivo destino**: src/main/java/com/iedexplorer/MonitorTableRenderer.java
- **Tipo**: Mover clase interna a top-level (mismo paquete)
- **Rollback**: Eliminar MonitorTableRenderer.java y restaurar en IEDExplorerApp.java
- **Verificación**: Compilar + agregar nodo a monitor + verificar colores de celdas
- **Estado**: ✅ Aplicado

---

## FASE 2 — Extracción de lógica SCL
> (pendiente — comenzar tras verificar Fase 1)

---

## FASE 2 — Extracción de métodos SCL a SclFileProcessor

### [F2-INFRA] Infraestructura de verificación
- **Fecha**: 2026-04-22
- **Archivos nuevos**:
  - `src/main/java/com/iedexplorer/SmokeTest.java` — 15 tests headless (clases Fase 1, SclParser, class load)
  - `SIN SMV/CID/test_smoke.cid` — CID mínimo canónico para tests
  - `SIN SMV/verify.sh` — script: compilación + Xlint:all + SmokeTest
- **Baseline**: 0 errores, 33 warnings pre-existentes [serial]/Swing, 15/15 smoke tests PASS
- **Estado**: ✅ Aplicado

### [F2-001] Crear SclFileProcessor.java y reemplazar métodos en IEDExplorerApp.java
- **Fecha**: 2026-04-22
- **Archivo nuevo**: `src/main/java/com/iedexplorer/SclFileProcessor.java`
- **Archivo modificado**: `src/main/java/com/iedexplorer/IEDExplorerApp.java`
- **Cambio**: 5 métodos (~350 líneas) movidos a SclFileProcessor como métodos estáticos:
  - `parseSclDataTypeTemplates(Document)` → `SclFileProcessor.parseSclDataTypeTemplates(Document, SclParsingResult)` (privado)
  - `parseGoCBsFromScl(File)` → `SclFileProcessor.parseFirstIED(File, Consumer<String>)` (público)
  - `parseGoCBsFromScl(File, int)` → `SclFileProcessor.parseIEDByIndex(File, int, Consumer<String>)` (público)
  - `parseDataSetsFromIED(Element)` → `SclFileProcessor.parseDataSetsFromIED(Element, SclParsingResult)` (privado)
  - `parseReportsFromIED(Element)` → `SclFileProcessor.parseReportsFromIED(Element, SclParsingResult)` (privado)
- **Nuevo tipo**: `SclFileProcessor.SclParsingResult` (DTO con todos los outputs del parsing)
- **IEDExplorerApp**: métodos reemplazados por wrappers de ~10 líneas c/u
- **IEDExplorerApp líneas**: 7151 → 6774 (-377 líneas)
- **Rollback**:
  ```bash
  git checkout HEAD -- src/main/java/com/iedexplorer/IEDExplorerApp.java
  rm src/main/java/com/iedexplorer/SclFileProcessor.java
  ```
- **Verificación**: 0 errores compilación, 15/15 SmokeTest PASS
- **Estado**: ✅ Aplicado

---

## FASE 3 — Extracción de ModelTreeBuilder

### [F3-001] Crear ModelTreeBuilder.java y reemplazar métodos en IEDExplorerApp.java
- **Fecha**: 2026-04-22
- **Archivo nuevo**: `src/main/java/com/iedexplorer/ModelTreeBuilder.java`
- **Archivo modificado**: `src/main/java/com/iedexplorer/IEDExplorerApp.java`
- **Cambio**: 17 métodos (~420 líneas) movidos a ModelTreeBuilder como métodos estáticos:
  - `buildTree`, `buildTreeRecursive`, `createTreeNode`, `getNodePrefix`
  - `countNodes`, `countNodesRecursive`
  - `addDataSetsToLdNode`, `addReportsToLdNode`, `addReportNode`
  - `findInsertIndexAfterLLN0`, `addGoCBsToLdNode`, `addGocbProperty`, `addDataSetToGoCB`
  - `updateNodeValue`, `updateTreeValues`, `updateTreeValuesRecursive`, `clearModel`
- **Decisión de diseño**:
  - `formatEnumValue` pasa como `BiFunction<ModelNode,String,String>` (desacopla enum logic)
  - Logging pasa como `Consumer<String>`
  - `countNodes` expuesto como package-private (usado en DataModel panel, L6214)
- **IEDExplorerApp**: wrappers delgados; `displayServerModel/displayClientModel` sin cambios
- **IEDExplorerApp líneas**: 6774 → 6392 (−382 líneas)
- **Rollback**:
  ```bash
  git checkout HEAD -- src/main/java/com/iedexplorer/IEDExplorerApp.java
  rm src/main/java/com/iedexplorer/ModelTreeBuilder.java
  ```
- **Verificación**: 0 errores compilación, 15/15 SmokeTest PASS
- **Estado**: ✅ Aplicado

---

## FASE F1b — Extracción de Tree Cell Renderers

### [FF1b-001] Extraer ModelTreeCellRenderer, DataModelTreeCellRenderer, DataModelNodeInfo
- **Fecha**: 2026-04-22
- **Archivos nuevos**:
  - `ModelTreeCellRenderer.java` — colores/iconos para árbol principal; constructor(iconCache, watchlist)
  - `DataModelTreeCellRenderer.java` — iconos para árbol Data Model; constructor(iconCache)
  - `DataModelNodeInfo.java` — DTO de nodo (node + type + toString)
- **Archivo modificado**: `IEDExplorerApp.java`
  - L626: `new ModelTreeCellRenderer()` → `new ModelTreeCellRenderer(iconCache, watchlist)`
  - L6184: `new DataModelTreeCellRenderer()` → `new DataModelTreeCellRenderer(iconCache)`
  - Eliminadas 3 inner class definitions (~155 líneas)
- **IEDExplorerApp líneas**: 6392 → 6178 (−214 líneas)
- **Rollback**:
  ```bash
  git checkout HEAD -- src/main/java/com/iedexplorer/IEDExplorerApp.java
  rm src/main/java/com/iedexplorer/ModelTreeCellRenderer.java
  rm src/main/java/com/iedexplorer/DataModelTreeCellRenderer.java
  rm src/main/java/com/iedexplorer/DataModelNodeInfo.java
  ```
- **Verificación**: 0 errores compilación, 15/15 SmokeTest PASS
- **Estado**: ✅ Aplicado

---

## FASE 4 — Extracción de Paneles

### [F4-001] Extraer SettingGroupsPanel, DatasetPanel, DataModelPanel
- **Fecha**: 2026-04-22
- **Archivos nuevos**:
  - `src/main/java/com/iedexplorer/SettingGroupsPanel.java` — 7 métodos (~200 líneas)
  - `src/main/java/com/iedexplorer/DatasetPanel.java` — 5 métodos (~155 líneas)
  - `src/main/java/com/iedexplorer/DataModelPanel.java` — 8 métodos (~170 líneas)
- **Archivo modificado**: `IEDExplorerApp.java`
  - Eliminadas 18 declaraciones de campo (settingGroupsTable, datasetTable, dataModelTree, etc.)
  - L641-643: `addTab(...)` reemplazados por instanciación de los paneles con suppliers
  - Panel methods (~580 líneas) eliminados de IEDExplorerApp
  - Añadido `import java.util.function.Supplier`
- **Patrón DI**:
  - `Supplier<ServerModel> panelModelSupplier` — encapsula lógica `currentMode`/`server`/`client`
  - `Supplier<IEC61850Client> panelClientSupplier` — retorna null cuando no conectado
- **IEDExplorerApp líneas**: 6178 → 6510 (recuperado desde git HEAD + F4; inner classes F1-F1b siguen presentes en archivo)
- **Rollback**:
  ```bash
  git checkout HEAD -- src/main/java/com/iedexplorer/IEDExplorerApp.java
  rm src/main/java/com/iedexplorer/SettingGroupsPanel.java
  rm src/main/java/com/iedexplorer/DatasetPanel.java
  rm src/main/java/com/iedexplorer/DataModelPanel.java
  ```
- **Verificación**: 0 errores compilación, 15/15 SmokeTest PASS
- **Estado**: ✅ Aplicado

---

## FASE 5 — Extracción del Panel GOOSE

### [F5-001] Crear GoosePanel.java y reemplazar sección GOOSE en IEDExplorerApp.java
- **Fecha**: 2026-04-22
- **Archivo nuevo**: `src/main/java/com/iedexplorer/GoosePanel.java` (2135 líneas)
- **Archivo modificado**: `IEDExplorerApp.java`
- **Cambio**: 29 métodos (~1682 líneas) movidos a GoosePanel, incluyendo:
  - createGoosePanel, loadNetworkInterfaces, toggleGooseCapture, publishSelectedGoCB
  - createPublisherForGoCB, changeGoCBState, setPublisherDataValue, syncPublisherToServerModel
  - convertPublisherValueToString, publishAllGoCBs, stopAllPublishers, buildDataValuesFromDataSet
  - inferDataType, getDefaultValueForType, toggleGoosePublishing, updatePublisherState
  - publishGooseStateChange, handleGooseMessage, handleNativeGooseMessage
  - refreshGooseControlBlocks, cargarSclParaGoose, detectAndSelectIED
  - parseSclDataTypeTemplates, extractLnClass, getEnumOptionsForNode
  - formatEnumValue, showEnumDialog, parseGoCBsFromScl (×2), logGoose
- **Patrón DI**: `GoosePanel.Context` interface con 20+ métodos para inyectar:
  - logger, server/client suppliers, isServerMode(), backgroundExecutor()
  - updateSingleNodeInTree(), updateServerMonitorValues()
  - Estado SCL compartido: sclGoCBs, sclDataSets, loadedIedName, sclEnumTypes, etc.
  - onSclLoaded() callback para reconstruir árbol tras carga SCL
- **Métodos públicos expuestos**: publishGooseFromSelection(), autoSelectGooseInterface(),
  getGoosePublisher(), getActivePublishers(), inferDataType(), formatEnumValue(), etc.
- **IEDExplorerApp**: thin wrappers para los métodos llamados desde fuera del panel;
  GOOSE-MODEL SYNC section accede a goosePanel.getGoosePublisher() / getActivePublishers()
- **IEDExplorerApp líneas**: 6510 → 4792 (−1718 líneas)
- **Rollback**:
  ```bash
  git checkout HEAD -- src/main/java/com/iedexplorer/IEDExplorerApp.java
  rm src/main/java/com/iedexplorer/GoosePanel.java
  ```
- **Verificación**: 0 errores compilación, 15/15 SmokeTest PASS
- **Estado**: ✅ Aplicado

---

## FASE 7 — Extracción de CONNECTION MANAGEMENT a ConnectionManager.java

### [F7-001] Crear ConnectionManager.java y reemplazar sección CONNECTION MANAGEMENT
- **Fecha**: 2026-04-23
- **Archivo nuevo**: `src/main/java/com/iedexplorer/ConnectionManager.java` (713 líneas)
- **Archivo modificado**: `IEDExplorerApp.java`
- **Cambio**: 10 métodos (~638 líneas) movidos a ConnectionManager:
  - switchToServerMode, switchToClientMode
  - obtenerCidDelIed, autoDownloadCid, guardarCid, selectSclFile
  - toggleServer, toggleConnection, connect, disconnect
  - handleDisconnect (reconectado como thin wrapper en IEDExplorerApp para onConnectionClosed)
  - detectLocalInterface, getLocalIpAddress
- **Patrón DI**: `ConnectionManager.Context` interface con 35+ métodos para inyectar:
  - log, updateStatus, parentWindow, backgroundExecutor
  - getClient/setClient, getServer/setServer
  - isConnected/setConnected, isServerRunning/setServerRunning
  - SCL state: loadedSclFile, loadedIedName, loadedIedNameplate, sclGoCBs
  - UI callbacks: switchUiToServerMode/Client, setBtnConnectText, setCbPollingEnabled, etc.
  - Post-event: onConnected, onDisconnected, onServerStarted, onServerStopped
  - Delegates: displayServerModel/Client, refreshGooseControlBlocks, parseGoCBsFromScl
- **Campos eliminados de IEDExplorerApp**: currentHost, currentPort, connectedLocalIp,
  downloadedCidData, downloadedCidFilename (ahora en ConnectionManager)
- **Métodos mantenidos en IEDExplorerApp**: updateConnectionInfo (accede a lblConnectionInfo),
  showIEDSelectionDialog (usa `this` como parent), handleDisconnect (callback async de red)
- **IEDExplorerApp líneas**: 4386 → 3870 (−516 líneas)
- **Rollback**:
  ```bash
  git checkout HEAD -- src/main/java/com/iedexplorer/IEDExplorerApp.java
  rm src/main/java/com/iedexplorer/ConnectionManager.java
  ```
- **Verificación**: 0 errores compilación, 15/15 SmokeTest PASS
- **Estado**: ✅ Aplicado

---

## Registro de compilaciones

| Fecha | Fase | Resultado | Notas |
|-------|------|-----------|-------|
| 2026-04-22 | Base (antes de F0) | ✅ OK | Estado inicial de referencia |
| 2026-04-22 | F0-001 | ⏳ por verificar | — |
| 2026-04-22 | F1-001 a F1-006 | ✅ OK — 84 clases generadas | Eliminación de 6 clases internas de IEDExplorerApp.java |
| 2026-04-22 | F2-INFRA | ✅ OK — 15/15 SmokeTest PASS | SmokeTest.java + verify.sh + test_smoke.cid |
| 2026-04-22 | F2-001 | ✅ OK — 0 errores, 15/15 PASS | SclFileProcessor.java; IEDExplorerApp 7151→6774 líneas |
| 2026-04-22 | F3-001  | ✅ OK — 0 errores, 15/15 PASS | ModelTreeBuilder.java; IEDExplorerApp 6774→6392 líneas |
| 2026-04-22 | FF1b-001 | ✅ OK — 0 errores, 15/15 PASS | 3 renderers/DTOs; IEDExplorerApp 6392→6178 líneas |
| 2026-04-22 | F4-001   | ✅ OK — 0 errores, 15/15 PASS | 3 paneles; IEDExplorerApp →6510 líneas (recuperado git HEAD + F4) |
| 2026-04-22 | F5-001   | ✅ OK — 0 errores, 15/15 PASS | GoosePanel.java (2135 líneas); IEDExplorerApp 6510→4792 líneas (−1718) |
| 2026-04-22 | F6-001   | ✅ OK — 0 errores, 15/15 PASS | ReportsPanel.java (395 líneas); IEDExplorerApp 4792→4386 líneas (−406) |
| 2026-04-23 | F7-001   | ✅ OK — 0 errores, 15/15 PASS | ConnectionManager.java (713 líneas); IEDExplorerApp 4386→3870 líneas (−516) |
| 2026-04-23 | F8-001   | ✅ OK — 0 errores, 15/15 PASS | PollingManager.java (~180 líneas); IEDExplorerApp 3870→3730 líneas (−140) |
| 2026-04-23 | F9-001   | ✅ OK — 0 errores, 15/15 PASS | Delegación MODEL TREE BUILDING → ModelTreeBuilder.java; IEDExplorerApp 3730→3346 líneas (−384) |
| 2026-04-23 | F10-001  | ✅ OK — 0 errores, 15/15 PASS | Delegación SCL PARSING → SclFileProcessor.java; IEDExplorerApp 3346→2990 líneas (−356) |
| 2026-04-23 | F11-001  | ✅ OK — 0 errores, 15/15 PASS | IconFactory.java extraído; IEDExplorerApp 2990→2770 líneas (−220) |
| 2026-04-23 | F12-001  | ✅ OK — 0 errores, 15/15 PASS | MonitorManager.java extraído (MONITOR OPERATIONS); IEDExplorerApp 2770→2623 líneas (−147) |
| 2026-04-23 | F13-001  | ✅ OK — 0 errores, 15/15 PASS | ValueDialogs.java extraído (3 helpers de diálogo); IEDExplorerApp 2623→2562 líneas (−61) |
| 2026-04-23 | F14-001  | ✅ OK — 0 errores, 15/15 PASS | SclReferenceUtils.java extraído (4 métodos: 2 delegados + 2 código muerto eliminado); IEDExplorerApp 2562→2487 líneas (−75) |
| 2026-04-23 | F15-001  | ✅ OK — 0 errores, 15/15 PASS | Watchlist ops movidas a MonitorManager.java (addNodeToWatchlist, removeNodeFromWatchlist etc.); IEDExplorerApp 2487→2435 líneas (−52) |
| 2026-04-23 | F16-001  | ✅ OK — 0 errores, 15/15 PASS | updateServerMonitorValues() movido a MonitorManager; getServerModel() añadido al Context; IEDExplorerApp 2435→2402 líneas (−33) |
| 2026-04-23 | F17-001  | ✅ OK — 0 errores, 15/15 PASS | updateSingleNodeInTree() movido a ModelTreeBuilder (BiFunction formatFn); IEDExplorerApp 2402→2377 líneas (−25) |
| 2026-04-23 | F18-001  | ✅ OK — 0 errores, 15/15 PASS | Inner class ModelTreeCellRenderer eliminada; se usa ModelTreeCellRenderer.java (F1b) con parámetros; IEDExplorerApp 2377→2226 líneas (−151) |
| 2026-04-23 | F19-001  | ✅ OK — 0 errores, 15/15 PASS | SAMPLED VALUES PANEL (código muerto SIN-SMV) + 8 campos SV eliminados; IEDExplorerApp 2226→1960 líneas (−266) |
| 2026-04-23 | F20-001  | ✅ OK — 0 errores, 15/15 PASS | 5 stubs de iconos muertos + startPolling() muerto eliminados; IEDExplorerApp 1960→1954 líneas (−6) |
| 2026-04-23 | F21-001  | ✅ OK — 0 errores, 15/15 PASS | updateGoosePublisherValues()+propagateValueToPublishers() movidos a GoosePanel.java; IEDExplorerApp 1954→1875 líneas (−79) |
| 2026-04-23 | F22-001  | ✅ OK — 0 errores, 15/15 PASS | Eliminados 2 wrappers muertos (F14); navigateToFcdaInModel+findNodeInModel+searchTreeForFcda → ModelTreeBuilder.java; 1875→1782 (−93) |
| 2026-04-23 | F23-001  | ✅ OK — 0 errores, 15/15 PASS | updateServerTreeValues() muerto eliminado; updateTreeNodeRecursive → ModelTreeBuilder.java; 1782→1751 (−31) |
| 2026-04-23 | F24-001  | ✅ OK — 0 errores, 15/15 PASS | clearMonitor()+clearWatchlist() → MonitorManager.java; 1751→1737 (−14) |
| 2026-04-23 | F25-001  | ✅ OK — 0 errores, 15/15 PASS | applyServerValue() extrae bloque duplicado de setSelectedNodeValue+setSelectedNodeCustomValue; 1737→1707 (−30) |
| 2026-04-23 | F26-001  | ✅ OK — 0 errores, 15/15 PASS | handleDisconnect() → ConnectionManager.java (misma lógica, package-private); 1707→1696 (−11) |
| 2026-04-23 | F27-001  | ✅ OK — 0 errores, 15/15 PASS | readSelectedNode+getSelectedBlkEnaNode+toggleBlocking → PollingManager.java; 1696→1630 (−66) |
| 2026-04-23 | F28-001  | ✅ OK — 0 errores, 15/15 PASS | Eliminados 13 imports muertos/redundantes (pcap4j, XML, FileInputStream, geom, java.util duplicados); 1630→1614 (−16) |

---

## FASE GUI — Nuevas funcionalidades UI (IEDNavigatorApp.java)

### [GUI-001] Leyenda FC y CDC en diálogo de ayuda
- **Fecha**: 2026-04-30
- **Archivo**: `src/main/java/com/iednavigator/IEDNavigatorApp.java`
- **Cambio**: Añadidas dos secciones al diálogo de leyenda de colores:
  - "Functional Constraints (FC)": 14 entradas con badges de color (ST=azul, MX=teal, CO=rojo, etc.)
  - "Clases de Datos Comunes (CDC)": subcategorías por tipo (estado, medida, control, config, log)
  - ScrollPane ampliado de 500×520 → 560×580 px
  - Métodos helper nuevos: `legendFcRow()`, `legendCdcHeader()`, `legendCdcRow()`
- **Rollback**: Revertir commit `cde156a` (diccionario) → `f493b3b`
- **Estado**: ✅ Aplicado

### [GUI-002] Botones "Verificar Puerto" y "Liberar Puerto"
- **Fecha**: 2026-04-30
- **Archivo**: `src/main/java/com/iednavigator/IEDNavigatorApp.java`
- **Cambio**:
  - `btnCheckPort`: lee el campo "Puerto" del panel Servidor y ejecuta `diagnosePort(int)`
  - `btnReleasePort`: encuentra PIDs via `findPortPids()`, muestra confirmación, mata con `taskkill /F /PID` (Win) o `kill -9` (Linux)
  - `diagnosePort()`: intenta `new ServerSocket(port)`, distingue FREE / PERMISSION_DENIED / IN_USE
  - `getPortOwnerInfo()`: corre `netstat -ano` (Win) o `ss -tlnp` (Linux) para identificar proceso
  - `showPortDiagnosisDialog()`: diálogo modal con borde coloreado según resultado
  - Layout del panel Servidor: cardPanel ampliado a 210 px alto; botones en fila separada
- **Rollback**: Revertir commit `f493b3b`
- **Estado**: ✅ Aplicado

### [GUI-003] Display del nombre del IED en la barra superior
- **Fecha**: 2026-04-30
- **Archivo**: `src/main/java/com/iednavigator/IEDNavigatorApp.java`
- **Cambio**:
  - `lblIedDisplay` añadido a `topPanel` en posición `BorderLayout.EAST`
  - Panel con fondo `new Color(220,232,252)`, fuente bold 15px, borde azul
  - `updateIedDisplay(String)`: parsea nombre del IED del texto de `setLblIedInfo`
  - `setLblIedInfo` en Context ahora también llama `updateIedDisplay()`
- **Rollback**: Revertir commit `f493b3b`
- **Estado**: ✅ Aplicado

---

## FASE DIST — Distribución e instaladores

### [DIST-001] Installer Windows v3.1 — ZIP desatendido con UAC
- **Fecha**: 2026-04-30
- **Archivo nuevo**: `build_zip_installer_v31.ps1`
- **Salida**: `installer/output/IEDNavigator_v3.1_Setup.zip` (10 MB)
- **Contenido del ZIP**: `classes/`, `lib/*.jar`, `lib/iec61850.dll`, `IEDNavigator.bat`, `INSTALAR.bat`, `LEAME.txt`, `src/`, `compile.ps1`
- **INSTALAR.bat**: auto-detección/instalación de Java JDK21 (Adoptium API), Npcap, regla firewall TCP 102, acceso directo en Escritorio
- **IEDNavigator.bat**: auto-elevación UAC para puerto 102 y captura GOOSE
- **Estado**: ✅ Aplicado

### [DIST-002] Installer Linux Ubuntu Desktop v3.1 — ZIP desatendido con authbind
- **Fecha**: 2026-04-30
- **Archivo nuevo**: `build_linux_installer_v31.ps1`
- **Salida**: `installer/output/IEDNavigator_v3.1_Linux.zip` (8.12 MB)
- **instalar.sh**: instala Java, libpcap, authbind; configura `/etc/authbind/byport/102`; copia a `/opt/iednavigator/`; crea `.desktop` entry
- **iednavigator.sh**: detecta authbind y ejecuta `authbind --deep java ... IEDNavigatorApp`
- **Estado**: ✅ Aplicado

### [DIST-003] Fix UAC elevation — ventana se cerraba sola (v3.2)
- **Fecha**: 2026-04-30
- **Archivo**: `build_zip_installer_v31.ps1`
- **Bug**: `\"` dentro de string PS single-quoted es `\` + `"` literal, NO quote escapado. `Start-Process` pasaba args inválidos a `cmd.exe` → ventana elevada nunca aparecía → original cerraba con `exit /b`
- **Fix**: Reemplazado `IEDNavigator.bat` (90 líneas de batch+UAC) por:
  - `IEDNavigator.bat`: 2 líneas — wrapper que llama `IEDNavigator.ps1`
  - `IEDNavigator.ps1`: launcher PS1 con `ProcessStartInfo.Verb='runas'` (sin quoting issues), detección de Java via `Get-Item` wildcards, construcción de classpath, `Start-Process` del app
- **Fix INSTALAR.bat**: auto-eleva escribiendo PS1 temporal con `echo ... > %TEMP%\*.ps1` + `powershell -File`; ya no requiere clic derecho manual
- **Commit**: `5db30f2`
- **Estado**: ✅ Aplicado

### [DIST-004] Npcap — descargar en lugar de bundlear (cumplimiento de licencia)
- **Fecha**: 2026-04-30
- **Archivo**: `build_zip_installer_v31.ps1`
- **Bug de licencia**: Npcap prohíbe redistribución sin licencia OEM de pago. El ZIP incluía `npcap-1.79.exe` violando sus términos
- **Fix**: Eliminada copia de `npcap-1.79.exe` al ZIP; `INSTALAR.bat` ahora descarga `https://npcap.com/dist/npcap-1.79.exe` en tiempo de instalación (igual que Java)
- **Impacto**: ZIP bajó de 10 MB → 8.9 MB
- **Commit**: `8b42008`
- **Estado**: ✅ Aplicado

### [DIST-005] GitHub Release v3.2
- **Fecha**: 2026-04-30
- **Acción**: `gh release create v3.2` con `IEDNavigator_v3.2_Setup.zip` como asset
- **URL**: https://github.com/MancoQpa/IEDNavigator/releases/tag/v3.2
- **Estado**: ✅ Publicado

---

## Registro de compilaciones (continuación)

| Fecha | Cambio | Resultado | Notas |
|-------|--------|-----------|-------|
| 2026-04-30 | GUI-001/002/003 | ✅ OK | Leyenda FC/CDC, botones puerto, display IED; commit `f493b3b` |
| 2026-04-30 | DIST-001 | ✅ OK | v3.1 Windows ZIP generado (10 MB) |
| 2026-04-30 | DIST-002 | ✅ OK | v3.1 Linux ZIP generado (8.12 MB) |
| 2026-04-30 | DIST-003 | ✅ OK | v3.2 con launcher PS1; commit `5db30f2` |
| 2026-04-30 | DIST-004 | ✅ OK | Npcap descargado; ZIP 8.9 MB; commit `8b42008` |
| 2026-04-30 | DIST-005 | ✅ OK | Release v3.2 publicado en GitHub |

---

## Cómo hacer rollback de un paso

```bash
# Restaurar archivo original desde git
git checkout HEAD -- src/main/java/com/iedexplorer/IEDExplorerApp.java

# Eliminar archivo nuevo si fue creado
rm src/main/java/com/iedexplorer/NombreClase.java

# Recompilar para verificar
JAVAC="C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot/bin/javac.exe"
find src/main/java -name "*.java" > /tmp/srclist.txt
"$JAVAC" -d classes -cp "lib/*" -encoding UTF-8 @/tmp/srclist.txt
```

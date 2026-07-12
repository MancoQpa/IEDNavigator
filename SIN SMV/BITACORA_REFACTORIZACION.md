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

## FASE RENAME — Migración a com.iednavigator

### [RENAME-001] Renombrar paquete com.iedexplorer → com.iednavigator
- **Fecha**: 2026-04-30
- **Commit**: `8efc45e`
- **Cambio**: Todos los archivos de `com.iedexplorer` copiados/renombrados a `com.iednavigator`
  - Clase principal: `IEDExplorerApp.java` → `IEDNavigatorApp.java`
  - Todos los imports actualizados al nuevo paquete
  - `com.iedexplorer/` marcado como gitignoreado (paquete obsoleto)
- **Estado inicial post-rename**: `IEDNavigatorApp.java` 2043 líneas
- **Estado**: ✅ Aplicado

---

## FASE POST-RENAME — Nuevas funcionalidades (com.iednavigator)

### [PN-001] Diccionario educativo IEC 61850
- **Fecha**: 2026-04-30
- **Commit**: `cde156a`
- **Archivo nuevo**: `Iec61850Dictionary.java` (1102 líneas)
- **Cambio**: Clic derecho en nodo del árbol muestra descripción normativa del LN/DO/DA
- **Estado**: ✅ Aplicado

### [PN-002] Íconos diferenciados por grupo de LN e inferencia de clase
- **Fecha**: 2026-04-30
- **Commit**: `198e3e1`
- **Archivo**: `IEDNavigatorApp.java`, `IconFactory.java`
- **Cambio**: Íconos visuales por categoría LN (protección, medida, control, etc.); inferencia de clase con prefijo de fabricante
- **Estado**: ✅ Aplicado

### [PN-003] Dataset panel — clic en miembro navega al nodo
- **Fecha**: 2026-04-30
- **Commit**: `d99e66f`
- **Archivo**: `DatasetPanel.java`
- **Cambio**: Al hacer clic en un FCDA del Dataset panel, se navega al nodo correspondiente en el árbol de datos
- **Estado**: ✅ Aplicado

### [PN-004] Verificar/liberar puerto, display IED, leyenda FC/CDC
- **Fecha**: 2026-04-30
- **Commit**: `f493b3b`
- **Archivo**: `IEDNavigatorApp.java`
- **Cambios**:
  - `btnCheckPort` / `btnReleasePort`: diagnóstico y liberación de puerto TCP 102
  - `lblIedDisplay`: nombre del IED en barra superior
  - Leyenda FC y CDC en diálogo de ayuda
- **Estado**: ✅ Aplicado

---

## Estado actual — com.iednavigator (2026-05-18)

### Inventario de archivos

| Archivo | Líneas | Rol |
|---------|--------|-----|
| `IEDNavigatorApp.java` | 2043 | GUI principal — orquestador central |
| `GoosePanel.java` | 1918 | Panel GOOSE completo (pub/sub/bridge/sync) |
| `Iec61850Dictionary.java` | 1102 | Diccionario normativo IEC 61850 |
| `IEC61850Client.java` | 990 | Cliente MMS, polling, reportes |
| `IEC61850Server.java` | 642 | Servidor IED desde SCL |
| `ConnectionManager.java` | 615 | Conexión MMS, modos cliente/servidor |
| `ModelTreeBuilder.java` | 510 | Construcción y actualización del árbol del modelo |
| `GoosePublisher.java` | 412 | Publicación GOOSE Layer 2 (pcap4j) |
| `GooseSubscriber.java` | 407 | Suscripción GOOSE (pcap4j) |
| `NativeGooseSubscriber.java` | 403 | GOOSE nativo vía JNA/libiec61850 |
| `SclFileProcessor.java` | 397 | Parsing SCL/ICD/CID/SCD |
| `NativeSVSubscriber.java` | 378 | SV nativo vía JNA (código presente, UI desactivada) |
| `GooseUdpBridge.java` | 373 | Bridge GOOSE sobre UDP |
| `LibIec61850.java` | 369 | JNA bindings a iec61850.dll |
| `ReportsPanel.java` | 347 | Panel URCB/BRCB — habilitar/deshabilitar reportes |
| `IconFactory.java` | 316 | Fábrica de íconos Swing (breakers, LNs, estados) |
| `MonitorManager.java` | 267 | Monitor de valores, watchlist, CSV export |
| `PollingManager.java` | 233 | Polling periódico de atributos MMS |
| `SettingGroupsPanel.java` | 224 | Panel de grupos de configuración (SG) |
| `DatasetPanel.java` | 187 | Panel de DataSets con navegación al nodo |
| `SmokeTest.java` | 174 | Tests headless (15 casos, sin GUI) |
| `DataModelPanel.java` | 157 | Panel del árbol de modelo de datos |
| `ModelTreeCellRenderer.java` | 147 | Renderer del árbol principal (colores/iconos) |
| `SclParserTest.java` | 93 | Utilidad de prueba de parsing SCL |
| `SclReferenceUtils.java` | 87 | Helpers de conversión de referencias IEC 61850 |
| `MonitorTableRenderer.java` | 63 | Renderer de tabla del monitor |
| `ValueDialogs.java` | 58 | Diálogos de edición de valor (Boolean, Enum, genérico) |
| `DataModelTreeCellRenderer.java` | 44 | Renderer del árbol de Data Model |
| `NodeInfo.java` | 33 | DTO: nodo + tipo + texto para árbol |
| `MonitorItem.java` | 30 | DTO: ítem del monitor (ref, valor, timestamp) |
| `DataModelNodeInfo.java` | 27 | DTO: nodo Data Model (node + type) |
| `SclGoCB.java` | 22 | DTO: GoCB parseado desde SCL |
| `SclDataSet.java` | 17 | DTO: DataSet parseado desde SCL |
| `SclReport.java` | 17 | DTO: RCB parseado desde SCL |
| `ListFc.java` | 10 | Utilidad: listar FCs de un modelo |
| **native_lib/*** | 1150 | JNA bindings (3 archivos) |
| **TOTAL** | ~12.600 | 35 archivos + 3 native_lib |

### Secciones activas en IEDNavigatorApp.java (2043 líneas)

| Línea | Sección | Estado | Líneas aprox. |
|-------|---------|--------|---------------|
| 30 | ENUMS & INNER DATA CLASSES | En IEDNavigatorApp | ~83 |
| 113 | ICONOS PERSONALIZADOS | En IEDNavigatorApp (ref. IconFactory) | ~6 |
| 119 | DRAG AND DROP SIMPLIFICADO | En IEDNavigatorApp | ~30 |
| 264 | Construcción GUI (toolbar, panels, layout) | En IEDNavigatorApp | ~595 |
| 860 | PANEL CREATION METHODS | En IEDNavigatorApp | ~190 |
| 1050 | MONITOR PANEL | En IEDNavigatorApp | ~120 |
| 1173 | SCL PARSING | **Delegado → SclFileProcessor** | wrappers |
| 1227 | MONITOR OPERATIONS | **Delegado → MonitorManager** | wrappers |
| 1325 | TREE POPUP & VALUE EDITING | En IEDNavigatorApp | ~285 |
| 1611 | GOOSE-MODEL SYNC | En IEDNavigatorApp | ~112 |
| 1723 | CONNECTION MANAGEMENT | **Delegado → ConnectionManager** | wrappers |
| 2213 | POLLING | **Delegado → PollingManager** | wrappers |
| 2247 | MODEL TREE BUILDING | **Delegado → ModelTreeBuilder** | wrappers |
| 2301 | SETTING GROUPS / DATASET / DATA MODEL | **Delegado → paneles externos** | wrappers |
| 2304 | ENTRY POINT | En IEDNavigatorApp | ~10 |

---

## FASES PENDIENTES — com.iednavigator

### [FP-001] Extraer TREE POPUP & VALUE EDITING → NodeActionHandler.java
- **Prioridad**: Alta
- **Sección**: L1325–L1610 en IEDNavigatorApp.java (~285 líneas)
- **Contenido a mover**:
  - `buildTreePopupMenu()` / `buildServerTreePopupMenu()`
  - `setSelectedNodeValue()` / `setSelectedNodeCustomValue()`
  - `applyServerValue()` / `readSelectedNode()`
  - `showNodeDetailsDialog()`
- **Reducción estimada**: IEDNavigatorApp 2043 → ~1758 líneas
- **Estado**: ⏳ Pendiente

### [FP-002] Extraer MONITOR PANEL → MonitorPanel.java
- **Prioridad**: Media
- **Sección**: L1050–L1169 en IEDNavigatorApp.java (~120 líneas)
- **Contenido a mover**: `createMonitorPanel()` y métodos de refresh de tabla
- **Reducción estimada**: IEDNavigatorApp → ~1638 líneas
- **Estado**: ⏳ Pendiente

### [FP-003] Extraer GOOSE-MODEL SYNC → GoosePanel.java (ampliar)
- **Prioridad**: Media
- **Sección**: L1611–L1722 en IEDNavigatorApp.java (~112 líneas)
- **Contenido a mover**: `propagateValueToPublishers()`, `updateGoosePublisherValues()`
  (ya existen en GoosePanel; revisar si son duplicados o wrappers)
- **Reducción estimada**: IEDNavigatorApp → ~1526 líneas
- **Estado**: ⏳ Pendiente

### [FP-004] Extraer construcción GUI principal → métodos builder
- **Prioridad**: Baja (riesgo alto — código GUI entrelazado)
- **Sección**: L264–L859 (~595 líneas de layout/toolbar/panels)
- **Nota**: Refactorizar sin cambiar comportamiento es riesgoso; mejor dejar para último
- **Estado**: ⏳ Pendiente

---

## Registro de compilaciones — com.iednavigator

| Fecha | Cambio | Resultado | Notas |
|-------|--------|-----------|-------|
| 2026-04-30 | RENAME-001 | ✅ OK | Paquete renombrado; IEDNavigatorApp.java partiendo desde estado F28 |
| 2026-04-30 | PN-001/002/003/004 | ✅ OK | Diccionario, íconos, dataset nav, puerto, display IED |
| 2026-05-18 | Fix Java 11 compat | ✅ OK | `--release 11` en compile.ps1; `static Color` → `final Color` en IconFactory |
| 2026-05-18 | build_zip_installer_v32.ps1 | ✅ OK | Release v3.2 funcional; launcher PS1 corregido |
| 2026-05-20 | FIX-RCB-001 | ✅ OK | reserveUrcb + cancelUrcbReservation; URCB reservado antes de enable |
| 2026-05-21 | FIX-RCB-002 | ✅ OK | Cast explícito Urcb/Brcb en enableRcb; servidor muestra enabled=true |
| 2026-05-21 | FIX-RCB-003 | ✅ OK | Debug logs eliminados de IEC61850Client + IEC61850Server |
| 2026-05-21 | FIX-RCB-004 | ✅ OK | association.enableReporting(rcb) → chgRcbs=1 → reports llegan. Verificado en runtime |

---

---

## FASE FIX — Correcciones funcionales (com.iednavigator)

### [FIX-RCB-001] Fix reportes URCB — reserveUrcb antes de habilitar
- **Fecha**: 2026-05-20
- **Archivo**: `src/main/java/com/iednavigator/IEC61850Client.java`
- **Síntoma**: URCB aparecía "Habilitado" en el cliente pero el servidor lo tenía `enabled=false reserved=NULL`. `newReport()` nunca era llamado → tabla de reportes siempre vacía.
- **Causa raíz**: iec61850bean server exige que el URCB esté **reservado** (`RsvTms`) por la asociación cliente antes de aceptar `setRcbValues(rptEna=true)`. Sin `reserveUrcb()`, el servidor ignora silenciosamente el enable.
- **Diagnóstico**: bytecode analysis de `ServerAssociation.getWriteResult()` confirmó que el servidor verifica `reserved != null && reserved == currentAssociation` antes de llamar `urcb.enable()`. Debug con reflexión mostró `reserved=NULL` en el URCB del servidor.
- **Fix en `enableReporting()`**:
  ```java
  if (rcb instanceof Urcb) {
      Urcb urcb = (Urcb) rcb;
      association.reserveUrcb(urcb);  // ← NUEVO: reservar antes de habilitar
      enableRcb(urcb);
  }
  ```
- **Fix en `disableReporting()`** (después de setRcbValues para Urcb):
  ```java
  try { association.cancelUrcbReservation((Urcb) rcb); } catch (Exception ignore) {}
  ```
- **Fixes previos en esta sesión** (mismo bug, diferentes capas):
  - `dataRef="false"` en `SIN SMV/CID/test_smoke.cid` — `dataRef="true"` causaba que `processReport()` hiciera index misalignment → `ServiceError` → `ClientReceiver` moría silenciosamente
  - `extractNodeValue(ModelNode)` en `ReportsPanel.java` — los valores del DataSet son `FcDataObject`, no `BasicDataAttribute`; sin el helper recursive los valores salían vacíos
  - `findDataSetField(Class)` en `IEC61850Server.java` — `Rcb.class.getDeclaredField("dataSet")` lanzaba `NoSuchFieldException` porque `dataSet` está en `Rcb` pero el bytecode lo declara en `Urcb`; fix: buscar por tipo `DataSet.class` subiendo la jerarquía
- **Rollback**:
  ```java
  // Eliminar la línea: association.reserveUrcb(urcb);
  // Eliminar la línea: try { association.cancelUrcbReservation((Urcb) rcb); } catch (Exception ignore) {}
  ```
- **Verificación**: Compilar → levantar servidor (test_smoke.cid) → conectar cliente → habilitar URCB → cambiar valor en servidor → `[DBG-REPORT]` debe aparecer en consola y fila en tabla
- **Estado**: ✅ Aplicado (pendiente prueba en runtime)

### [FIX-RCB-002] Fix enableRcb — cast explícito a Urcb/Brcb
- **Fecha**: 2026-05-21
- **Archivo**: `src/main/java/com/iednavigator/IEC61850Client.java`
- **Síntoma**: Reports nunca llegaban al cliente. Log servidor mostraba `[DBG-URCB] urcb01 enabled=false reserved=ServerAssociation` y `[SERVER] Clients notified via reports` pero el cliente no recibía nada.
- **Causa raíz**: `enableRcb(Rcb rcb)` llamaba `association.setRcbValues(rcb, ...)` con tipo estático `Rcb` en vez de castear a `Urcb`/`Brcb`. iec61850bean tiene una sobrecarga genérica `setRcbValues(Rcb, ...)` que NO dispara `urcb.enable(serverModel)` en el servidor → `chgRcbs` queda vacío → `setValues()` ejecuta sin error pero no envía reports.
- **Diagnóstico**: Log `enabled=false` en servidor confirmó que el URCB nunca se habilitó a pesar de `reserved=ServerAssociation`. Comparar con `disableReporting()` que SÍ castea explícitamente.
- **Fix**: Refactorizar `enableRcb` con `instanceof` + cast explícito → `setRcbValues(Urcb, ...)` / `setRcbValues(Brcb, ...)` (idéntico a como `disableReporting` lo hace).
- **Fix adicional**: Clases del instalador estaban desactualizadas (2026-05-20 19:45). Se copiaron las clases recién compiladas al instalador.
- **Rollback**: Volver al `setRcbValues(rcb, ...)` sin cast (regresa el bug).
- **Verificación**: Compilar → servidor con test_smoke.cid → cliente habilita URCB → cambiar `GGIO1/Ind1.stVal` → debe aparecer `[OK] RCB urcb01 → rptEna local=true` en cliente y fila en tabla de Reports.
- **Estado**: ✅ Aplicado — pendiente prueba runtime

### [FIX-RCB-003] Limpieza de debug logs — producción
- **Fecha**: 2026-05-21
- **Archivos**: `IEC61850Client.java`, `IEC61850Server.java`
- **Logs eliminados**:
  - `IEC61850Client.java`: bloque `[DBG-SETRPT]` en `enableReporting()`, todos los `[REPORT]` debug prints en `newReport()`
  - `IEC61850Server.java`: bloque `[DBG-RELINK]` en `processRcbDataSet()`, bloque `[DBG-CHGRCBS]`/`[DBG-URCB]` de 40+ líneas con reflexión en `setDataValue()`
- **Estado**: ✅ Aplicado

### [FIX-RCB-004] Fix definitivo — usar association.enableReporting(rcb)
- **Fecha**: 2026-05-21
- **Archivo**: `src/main/java/com/iednavigator/IEC61850Client.java`
- **Síntoma**: Reports nunca llegaban al cliente. Log servidor mostraba `[DBG-URCB] chgRcbs=0` para todos los BDAs del DataSet, incluso con URCB `enabled=true` y `reserved=ServerAssociation`.
- **Causa raíz**: `enableRcb()` usaba `association.setRcbValues(urcb, false, false, true, ...)` que envía un Write MMS con bitmask. En `ServerAssociation`, el handler de `setRcbValues` procesa el campo `RptEna=true` pero **no llama** a `urcb.enable()` — solo actualiza el campo BDA del URCB. Sin `urcb.enable()`, `chgRcbs` queda vacío en todos los BDAs → `setValues()` no encuentra suscriptores → reports nunca se envían.
- **Diagnóstico**: Reflexión confirmó `chgRcbs.size=0` y `contents=[]` en todos los BDAs del DataSet. Comparar `setRcbValues` (bitmask) vs `enableReporting()` (directo a `setDataValues(rptEnaBda)`) via decompile de `ClientAssociation.class`: la segunda variante llama directamente al handler en `ServerAssociation` que sí invoca `urcb.enable()`.
- **Fix en `enableRcb()`**:
  ```java
  private void enableRcb(Rcb rcb) throws ServiceError, IOException {
      // Use the official enableReporting() API which calls setDataValues(rptEnaBda)
      // directly — this triggers the correct RptEna handler in ServerAssociation.
      association.enableReporting(rcb);
      try {
          association.getRcbValues(rcb);
      } catch (ServiceError e) {
          System.out.println("[WARN] getRcbValues post-enable: " + e.getMessage());
      }
      boolean enabled = rcb.getRptEna() != null && rcb.getRptEna().getValue();
      System.out.println("[" + (enabled ? "OK" : "INFO") + "] RCB " + rcb.getName()
          + " rptEna local=" + enabled);
  }
  ```
- **Verificación**: `[OK] RCB urcb01 rptEna local=true` en cliente + `urcb01 enabled=true chgRcbs=1` en servidor + `[REPORT] newReport() CALLED values=3` + filas en tabla de Reports. ✅ Confirmado en runtime.
- **Estado**: ✅ Aplicado y verificado en runtime

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

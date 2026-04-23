# IEDNavigator v3.0-edu — Informe de Estado del Proyecto
**Fecha:** 2026-04-23
**Autor:** Emilio Medina
**Versión analizada:** `SIN SMV` (rama `main`, commit `9a1029f`)

---

## 1. Resumen Ejecutivo

IEDNavigator es una aplicación de escritorio Java para exploración y simulación del protocolo IEC 61850, funcionalmente equivalente a OMICRON IEDScout. Nació como un archivo monolítico de ~7 150 líneas y fue refactorizado en 28 fases atómicas hasta alcanzar una arquitectura modular de 34 archivos fuente.

| Métrica | Antes (original) | Ahora (v3.0-edu) |
|---|---|---|
| Líneas en archivo principal (`IEDExplorerApp.java`) | 7 152 | 1 614 |
| Reducción del monolito | — | **−77,4 %** |
| Archivos fuente Java (paquete principal) | 9 | 31 |
| Archivos fuente Java (total incl. native_lib) | 12 | 34 |
| Total líneas del proyecto (src) | ~8 500 | ~12 971 |
| Tests de regresión automatizados | 0 | 15 (SmokeTest) |
| Compilación limpia (0 errores) | ✅ | ✅ |

---

## 2. Arquitectura Actual

### 2.1 Estructura de archivos fuente

```
src/main/java/com/iedexplorer/
│
├── ─ NÚCLEO DE APLICACIÓN ─────────────────────────────────
│   ├── IEDExplorerApp.java          1 614 lín  ← GUI principal (orquestador)
│   ├── IEC61850Client.java          1 131 lín  ← Cliente MMS / modelo / reportes
│   ├── IEC61850Server.java            756 lín  ← Servidor IED desde SCL
│   └── SmokeTest.java                 200 lín  ← Test de regresión (15 checks)
│
├── ─ SUBSISTEMAS EXTRAÍDOS (refactoring F1-F28) ───────────
│   ├── GoosePanel.java              2 203 lín  ← Panel GOOSE completo (pub/sub/bridge)
│   ├── ConnectionManager.java         713 lín  ← Conexión/desconexión cliente+servidor
│   ├── ModelTreeBuilder.java          575 lín  ← Construcción/actualización del árbol JTree
│   ├── SclFileProcessor.java          457 lín  ← Parsing de ICD/CID/SCD (SCL XML)
│   ├── ReportsPanel.java              395 lín  ← Panel de reportes RCB/URCB/BRCB
│   ├── MonitorManager.java            302 lín  ← Activity Monitor + watchlist
│   ├── PollingManager.java            269 lín  ← Polling periódico + operaciones de árbol
│   ├── SettingGroupsPanel.java        256 lín  ← Panel de Setting Groups (SGCB)
│   ├── IconFactory.java               196 lín  ← Caché de iconos (colores por FC/estado)
│   ├── DatasetPanel.java              192 lín  ← Panel de DataSets del modelo
│   ├── DataModelPanel.java            186 lín  ← Panel de navegación del modelo completo
│   ├── ModelTreeCellRenderer.java     148 lín  ← Renderer del árbol (colores/íconos)
│   ├── SclReferenceUtils.java          96 lín  ← Conversión de referencias FCDA↔modelo
│   ├── ValueDialogs.java               73 lín  ← Diálogos de edición de valor (bool, DPC, tap)
│   ├── MonitorTableRenderer.java       72 lín  ← Renderer de tabla Activity Monitor
│   └── DataModelTreeCellRenderer.java  57 lín  ← Renderer árbol DataModel
│
├── ─ MODELOS DE DATOS (POJOs) ─────────────────────────────
│   ├── NodeInfo.java                   36 lín  ← Nodo del JTree (nombre, FC, valor, ref)
│   ├── MonitorItem.java                33 lín  ← Ítem en Activity Monitor
│   ├── DataModelNodeInfo.java          32 lín  ← Nodo del árbol DataModel
│   ├── SclGoCB.java                    24 lín  ← GOOSE Control Block desde SCL
│   ├── SclDataSet.java                 19 lín  ← DataSet SCL (lista de FCDAs)
│   └── SclReport.java                  18 lín  ← Report Control Block desde SCL
│
├── ─ PROTOCOLO GOOSE / RED ────────────────────────────────
│   ├── GoosePublisher.java            490 lín  ← Publicador GOOSE (pcap4j / Layer 2)
│   ├── GooseSubscriber.java           468 lín  ← Suscriptor GOOSE (captura pcap4j)
│   └── GooseUdpBridge.java            451 lín  ← Bridge GOOSE sobre UDP (unicast/mcast)
│
├── ─ UTILIDADES / HERRAMIENTAS ────────────────────────────
│   ├── SclParserTest.java             112 lín  ← Herramienta CLI de test SCL
│   └── ListFc.java                     12 lín  ← Listado de FCs (debug)
│
└── native_lib/                                  ← Bindings JNA a iec61850.dll
    ├── LibIec61850.java               462 lín  ← Interfaz JNA completa
    ├── NativeGooseSubscriber.java     477 lín  ← Suscriptor GOOSE nativo
    └── NativeSVSubscriber.java        446 lín  ← SV nativo (compilado, NO expuesto en GUI)
```

**Total: 34 archivos fuente | ~12 971 líneas**

### 2.2 Diagrama de dependencias (simplificado)

```
IEDExplorerApp (orquestador)
├── IEC61850Client          ← MMS, modelo, polling, reportes
├── IEC61850Server          ← Simulación IED desde SCL
├── ConnectionManager       ← Ciclo de vida conexión
│     └── (delega a Client/Server)
├── PollingManager          ← Polling periódico
│     └── ModelTreeBuilder  ← Árbol JTree
├── MonitorManager          ← Activity Monitor + watchlist
├── SclFileProcessor        ← Parser SCL (XML → POJOs)
│     └── SclGoCB, SclDataSet, SclReport (POJOs)
├── GoosePanel              ← Panel GOOSE completo
│     ├── GoosePublisher    ← pcap4j / Layer 2
│     ├── GooseSubscriber
│     ├── GooseUdpBridge
│     └── SclReferenceUtils
├── ReportsPanel            ← RCB/URCB/BRCB
├── SettingGroupsPanel      ← SGCB
├── DatasetPanel            ← DataSets
├── DataModelPanel          ← Árbol modelo completo
├── IconFactory             ← Iconos/colores
├── ModelTreeCellRenderer   ← Renderer árbol principal
├── ValueDialogs            ← Diálogos edición
├── MonitorTableRenderer    ← Renderer tabla monitor
└── native_lib/*            ← JNA → iec61850.dll
```

---

## 3. Funcionalidades Implementadas

### 3.1 Modo Cliente (MMS/ACSE)
| Función | Estado |
|---|---|
| Conexión a IED real (host:port) | ✅ Operativo |
| Descubrimiento y visualización del modelo SCL | ✅ Operativo |
| Lectura de valores por FC (ST, MX, CF, DC…) | ✅ Operativo |
| Escritura de valores (DataAttributes) | ✅ Operativo |
| Polling periódico configurable (ms) | ✅ Operativo |
| Watchlist de nodos seleccionados | ✅ Operativo |
| Activity Monitor con filtro FC/nombre | ✅ Operativo |
| Suscripción a reportes (URCB/BRCB) | ✅ Operativo (fix aplicado) |
| Control de interruptores (SBO/direct) | ✅ Operativo |
| Bloqueo FC=BL (blkEna) | ✅ Operativo |
| Setting Groups (SGCB) | ✅ Operativo |
| DataSet browser | ✅ Operativo |
| Drag-and-drop al monitor | ✅ Operativo |
| Export CSV del monitor | ✅ Operativo |

### 3.2 Modo Servidor (Simulación IED)
| Función | Estado |
|---|---|
| Carga de ICD/CID/SCD (SCL parser) | ✅ Operativo |
| Selección de IED si hay varios en el SCL | ✅ Operativo |
| Respuesta a lecturas MMS | ✅ Operativo |
| Edición interactiva de valores desde GUI | ✅ Operativo |
| Diálogos especializados (bool, doble bit, tap) | ✅ Operativo |
| Enums desde SCL (EnumType) | ✅ Operativo |
| Sincronización Modelo → GOOSE | ✅ Operativo |
| Sincronización GOOSE → Modelo | ✅ Operativo |

### 3.3 GOOSE (IEC 61850-8-1)
| Función | Estado |
|---|---|
| Publicación GOOSE Layer 2 (pcap4j) | ✅ Operativo |
| Suscripción / captura GOOSE (pcap4j) | ✅ Operativo |
| Bridge GOOSE sobre UDP | ✅ Operativo |
| Multi-publishers (por GoCB) | ✅ Operativo |
| GOOSE nativo (JNA / iec61850.dll) | ✅ Compilado, disponible |
| Parsing GoCBs desde SCL | ✅ Operativo |

### 3.4 Sampled Values (SMV/SV)
| Función | Estado |
|---|---|
| NativeSVSubscriber | ⚠️ Compilado, NO expuesto en GUI (versión SIN SMV) |
| Panel SMV en GUI | ❌ Eliminado (F19) |

### 3.5 Reportes (RCB)
| Función | Estado |
|---|---|
| Listado URCB/BRCB del modelo | ✅ Operativo |
| Habilitación/deshabilitación | ✅ Fix aplicado (2026-03-10) |
| Recepción y display de informes | ✅ Operativo |
| setRcbValues correcto (no escribe datSet/optFlds/bufTm) | ✅ Fix aplicado |

---

## 4. Proceso de Refactorización — Cronología

### Fase de extracción de clases internas (F0–F1) — 2026-04-22
- **F0**: Demarcación con comentarios de sección en el monolito original (7 152 líneas)
- **F1**: Extracción de 6 POJOs/renderers: `SclGoCB`, `SclDataSet`, `SclReport`, `NodeInfo`, `MonitorItem`, `ModelTreeCellRenderer`

### Extracción de subsistemas grandes (F2–F8) — 2026-04-22/23
| Fase | Clase extraída | Reducción IEDExplorerApp |
|---|---|---|
| F2 | `SclFileProcessor` | 7 151 → 6 774 (−377) |
| F3 | `ModelTreeBuilder` | 6 774 → 6 392 (−382) |
| FF1b | Renderers/DTOs adicionales | 6 392 → 6 178 (−214) |
| F4 | 3 paneles auxiliares | +332 (reorganización) |
| F5 | `GoosePanel` (2 135 lín) | 6 510 → 4 792 (**−1 718**) |
| F6 | `ReportsPanel` | 4 792 → 4 386 (−406) |
| F7 | `ConnectionManager` | 4 386 → 3 870 (−516) |
| F8 | `PollingManager` | 3 870 → 3 730 (−140) |

### Delegaciones y limpieza (F9–F28) — 2026-04-23
| Fase | Descripción | Reducción |
|---|---|---|
| F9 | Delegación árbol → `ModelTreeBuilder` | −384 |
| F10 | Delegación SCL → `SclFileProcessor` | −356 |
| F11 | `IconFactory` extraído | −220 |
| F12 | `MonitorManager` extraído | −147 |
| F13 | `ValueDialogs` extraído | −61 |
| F14 | `SclReferenceUtils` extraído | −75 |
| F15 | Watchlist ops → `MonitorManager` | −52 |
| F16 | `updateServerMonitorValues` → `MonitorManager` | −33 |
| F17 | `updateSingleNodeInTree` → `ModelTreeBuilder` | −25 |
| F18 | Inner renderer eliminado → clase externa | −151 |
| F19 | Código SMV muerto eliminado | −266 |
| F20 | Stubs de iconos/métodos muertos | −6 |
| F21 | `updateGoosePublisherValues` → `GoosePanel` | −79 |
| F22 | Helpers muertos + navegación → `ModelTreeBuilder` | −93 |
| F23 | `updateServerTreeValues` muerto; delegación | −31 |
| F24 | `clearMonitor/Watchlist` → `MonitorManager` | −14 |
| F25 | `applyServerValue` extrae duplicado | −30 |
| F26 | `handleDisconnect` → `ConnectionManager` | −11 |
| F27 | `readSelectedNode`/`toggleBlocking` → `PollingManager` | −66 |
| F28 | 13 imports muertos eliminados + tombstones | −16 |

**Total reducción IEDExplorerApp: 7 152 → 1 614 (−77,4 %)**

---

## 5. Patrones de Diseño Aplicados

### 5.1 Context / Dependency Injection
Cada clase extraída define una interfaz interna `Context` que expone exactamente las dependencias que necesita. `IEDExplorerApp` provee implementaciones anónimas vía `createXxxContext()`:

```
IEDExplorerApp
  createConnectionContext() → ConnectionManager.Context (anon)
  createPollingContext()    → PollingManager.Context (anon)
  createMonitorContext()    → MonitorManager.Context (anon)
  createGooseContext()      → GoosePanel.Context (anon)
```

### 5.2 Delegación (Wrapper thin)
Los métodos privados de `IEDExplorerApp` son ahora stubs de una línea que delegan a la clase especializada:
```java
private void handleDisconnect()       { connectionManager.handleDisconnect(); }
private void clearWatchlist()         { monitorManager.clearWatchlist(); }
private void readSelectedNode()       { pollingManager.readSelectedNode(); }
```

### 5.3 Static Utility
`ModelTreeBuilder` y `SclReferenceUtils` exponen métodos estáticos sin estado, para operaciones funcionales sobre el árbol y las referencias SCL.

### 5.4 Package-private visibility
Clases del mismo paquete se comunican directamente cuando la interfaz Context sería innecesariamente compleja (ej: `ConnectionManager.handleDisconnect()` es package-private).

---

## 6. Infraestructura de Build y Despliegue

### 6.1 Compilación
```
compile.ps1          ← Script corregido (F28+): descubrimiento recursivo de *.java
                        @argfile con rutas con forward-slash (evita escape Java 9+)
```
- JDK: Eclipse Adoptium JDK 25.0.2.10-hotspot
- Classpath: 13 JARs en `lib/`
- Encoding: UTF-8
- Output: `classes/`

### 6.2 Ejecución
```batch
run.bat              ← Lanzador básico
START.bat            ← Lanzador inteligente con checks de dependencias
run.ps1              ← Alternativa PowerShell
```
Flags requeridos:
```
java --enable-native-access=ALL-UNNAMED -Djna.library.path=lib -jar target/...jar
```

### 6.3 Empaquetado
```
build_installer.ps1          ← Instalador NSIS Windows
build_linux_installer.ps1    ← Instalador Linux (sh)
build_zip_installer.ps1      ← ZIP portable
```
Versiones distribuidas: v2.1, v8, v9, v10, v11 (en `installer/output/`)

### 6.4 Test de Regresión
```
SmokeTest.java       ← 15 checks de carga de clases
test_smoke.cid       ← ICD/CID mínimo para pruebas
```
Ejecución:
```bash
java -Djava.awt.headless=true -cp "classes;lib/*" com.iedexplorer.SmokeTest "SIN SMV/CID/test_smoke.cid"
# → RESULTADO: 15 pasaron, 0 fallaron
```

---

## 7. Dependencias Externas

| Librería | Versión | Uso |
|---|---|---|
| iec61850bean | 1.9.0 (com.beanit) | Protocolo IEC 61850 / MMS / SCL parser |
| pcap4j | 1.8.2 | Captura de red Layer 2 para GOOSE |
| JNA | 5.14.0 | Binding nativo a iec61850.dll |
| FlatLaf | 3.2 | Look-and-feel moderno para Swing |
| asn1bean / jasn1 | 1.13.0 / 1.11.3 | Decodificación ASN.1 |
| SLF4J | 2.0.9 + simple | Logging |
| Npcap (externo) | ≥1.0 | Driver de captura en Windows (requerido por pcap4j) |

---

## 8. Deuda Técnica Identificada

### Alta prioridad
| ID | Descripción | Impacto |
|---|---|---|
| DT-01 | `GoosePanel.java` tiene 2 203 líneas — candidato a segunda fase de extracción | Mantenibilidad |
| DT-02 | `createTreePopupMenu()` + `buildServerPopupForNode()` + `handleTreePopup()` (~250 lín) aún en IEDExplorerApp | Cohesión |
| DT-03 | JNA duplicado en lib/ (jna-5.13.0 y jna-5.14.0) — posible conflicto de classpath | Estabilidad |

### Media prioridad
| ID | Descripción | Impacto |
|---|---|---|
| DT-04 | Timeout de conexión MMS hardcodeado (10 s) — no configurable desde GUI | UX |
| DT-05 | `setSelectedNodeCustomValue()` (~55 lín) con lógica de diálogos mezclada | Cohesión |
| DT-06 | No hay tests de integración reales — SmokeTest solo verifica carga de clases | Cobertura |
| DT-07 | Logging mezcla `SLF4J` con `log()` directo a JTextArea — dos canales sin unificar | Observabilidad |

### Baja prioridad
| ID | Descripción |
|---|---|
| DT-08 | TODO-REFACTOR F1 y F4 aún visibles como comentarios en IEDExplorerApp |
| DT-09 | `SclParserTest.java` y `ListFc.java` son herramientas CLI no documentadas |
| DT-10 | `NativeSVSubscriber` compilado pero no expuesto (versión SIN SMV) — acumula deuda |

---

## 9. Variantes y Proyectos Relacionados

| Proyecto | Ubicación | Descripción |
|---|---|---|
| **IEDNavigator** (este) | `SIN SMV/` | Versión principal sin SMV |
| **HarmonicMonitor** | `HarmonicMonitor/` | App JavaFX para análisis de armónicos en alimentadores MT 23kV |
| **IonSimServer** | `HarmonicMonitor/simulator/` | Simulador IEC 61850 de ION 7400 para HarmonicMonitor |
| **Android IED Navigator** | `android-ied-navigator/` | Cliente Android del protocolo |
| **Android IED Simulator** | `android_ied_simulator*/` | Simulador Android |
| **Android ION7400 Sim** | `android-ion7400-simulator/` | Simulador ION 7400 en Android |

---

## 10. Estado de Compilación y Tests (al 2026-04-23)

```
✅ Compilación: 0 errores, 0 advertencias (34 archivos, JDK 25)
✅ SmokeTest:   15/15 PASS
✅ compile.ps1: Corregido — descubrimiento recursivo de fuentes (fix F28)
```

---

## 11. Próximos Pasos Recomendados

1. **F29 — TreePopupBuilder**: Extraer `createTreePopupMenu()` + `buildServerPopupForNode()` + `handleTreePopup()` (~250 lín) a nueva clase `TreePopupBuilder.java` → llevaría IEDExplorerApp a ~1 360 líneas.
2. **Validación RCB con servidor real**: Probar el fix de `setRcbValues` (marzo 2026) contra un IED SIPROTEC5 real.
3. **Descomposición GoosePanel**: Separar la lógica de publicación/suscripción/bridge en subclases o helpers dentro del paquete.
4. **Tests de integración**: Agregar casos reales de lectura/escritura usando el simulador `test_smoke.cid`.
5. **Unified logging**: Canalizar todo el log por SLF4J + listener que actualice el JTextArea.

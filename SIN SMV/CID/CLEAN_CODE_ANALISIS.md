# Análisis de Código Limpio — IEDNavigator v3.0-edu

> Generado: 2026-04-21
> Alcance: análisis estructural del código fuente en `src/main/java/com/iedexplorer/`
> Objetivo: servir como referencia para refactorización futura

---

## Métricas de partida

| Archivo | Líneas | Responsabilidades detectadas |
|---------|--------|------------------------------|
| `IEDExplorerApp.java` | 7273 | ~12 (ver detalle abajo) |
| `IEC61850Client.java` | 1131 | 3 (conexión, lectura/escritura, formateo) |
| `IEC61850Server.java` | 756 | 3 (parseo SCL, expansión arrays, servidor MMS) |
| `GoosePublisher.java` | ~400 | 1 (publicación GOOSE) |
| `GooseSubscriber.java` | ~400 | 1 (captura GOOSE) |
| `GooseUdpBridge.java` | ~300 | 1 (bridge UDP) |
| **Total** | **~9160** | |

`IEDExplorerApp.java` tiene **244 métodos/campos declarados** y **3 clases internas**
(`MonitorTableRenderer`, `ModelTreeCellRenderer`, `DataModelTreeCellRenderer`).

---

## Problema 1: God Class (el más grave)

### Descripción

`IEDExplorerApp.java` concentra en una sola clase todo lo que debería estar distribuido
en múltiples módulos:

| Responsabilidad actual en IEDExplorerApp | Líneas aprox. |
|------------------------------------------|--------------|
| Layout principal y barra de estado | 574–900 |
| Panel servidor (SCL load, start/stop) | 902–948 |
| Panel cliente (conexión, watchlist) | 949–1026 |
| Panel Monitor (tabla, filtros, polling) | 1027–1143 |
| Panel Reports (RCBs, enable/disable) | 1154–1557 |
| Panel GOOSE (captura, publish, bridge) | 1558–3005 |
| Panel Sampled Values | 3636–3892 |
| Panel Setting Groups | ~6280–6370 |
| Panel Dataset | ~6900–6985 |
| Panel Data Model | ~6985–7110 |
| Parseo SCL (GoCBs, DataTypeTemplates, DataSets) | 3093–3595 |
| Lógica de árbol (build, update, render, popup) | 4130–4980 |
| Lógica de Monitor (add, remove, filter, refresh) | 3893–4130 |
| Coordinación cliente/servidor | disperso |
| Estado global de la aplicación | 38–210 |

### Principio violado

**SRP — Single Responsibility Principle**: una clase debería tener **una sola razón
para cambiar**. `IEDExplorerApp` tiene al menos 12 razones distintas para cambiar.

### Consecuencia práctica

Modificar cualquier funcionalidad implica navegar miles de líneas en el mismo archivo,
con riesgo de efectos laterales en partes no relacionadas.

---

## Problema 2: Ausencia de patrón MVC / MVP

### Estado actual

```
IEDExplorerApp
 ├── construye la GUI
 ├── maneja eventos de usuario
 ├── decide cuándo leer valores del IED
 ├── formatea los valores
 ├── actualiza el árbol
 └── gestiona el estado de conexión
```

### Lo que debería ser

```
View   (solo presentación)
 ├── MainWindow
 ├── MonitorPanel
 ├── GoosePanel
 └── ReportsPanel

Controller (coordinación, decisiones)
 ├── ClientController  → cuándo conectar, leer, escribir
 ├── ServerController  → cuándo cargar, iniciar, detener
 └── GooseController   → cuándo publicar, suscribir

Model (estado, datos)
 ├── AppState          → isConnected, currentMode, isServerRunning
 ├── IedSession        → serverModel, loadedIedName, nameplate
 └── MonitorModel      → monitorItems, filtros activos

Service (lógica de negocio reutilizable)
 ├── SclService        → parseo, expansión arrays, GoCBs, DataTypeTemplates
 ├── NameplateService  → lectura FC=DC
 └── EnumService       → mapas SIUnit, Health, CtlModel, etc.
```

### Principio violado

**Separación de responsabilidades**: la GUI no debería saber cómo se leen los valores
del IED ni cómo se parsea un archivo SCL.

---

## Problema 3: Métodos demasiado largos

### Métodos más largos identificados (estimación por posición en archivo)

| Método | Línea inicio | Tamaño aprox. |
|--------|-------------|---------------|
| `createGoosePanel()` | 1558 | ~415 líneas |
| `parseGoCBsFromScl(File, int)` | 3362 | ~134 líneas |
| `buildServerPopupForNode()` | 4229 | ~100 líneas |
| `enableSelectedReport()` | 1461 | ~45 líneas |
| `handleReportReceived()` | 1506 | ~52 líneas |
| `publishSelectedGoCB()` | 2190 | ~133 líneas |
| `parseSclDataTypeTemplates()` | 3093 | ~62 líneas |
| `refreshGooseControlBlocks()` | 2862 | ~140 líneas |
| `displayServerModel()` | ~5030 | ~120 líneas |
| `connect()` | 5482 | ~90 líneas |

`createGoosePanel()` con ~415 líneas construye la UI, registra listeners, define tablas,
crea popups y configura la lógica de captura — todo en un solo método.

### Regla violada

Un método debería **hacer una sola cosa** y caber en una pantalla (~30 líneas).
Métodos de 100–415 líneas hacen múltiples cosas y son imposibles de testear
unitariamente.

---

## Problema 4: Lógica de negocio dentro de listeners de eventos

### Ejemplo real (patrón repetido ~8 veces)

```java
btnSimulate.addActionListener(e -> {
    backgroundExecutor.submit(() -> {
        // 80 líneas de lógica: validar archivo, parsear SCL,
        // seleccionar IED, cargar modelo, actualizar UI...
    });
});
```

Los listeners deberían delegar inmediatamente a un controller o servicio:

```java
btnSimulate.addActionListener(e -> serverController.loadAndStart(selectedFile));
```

### Problema concreto

La lógica mezclada con la UI hace imposible:
- Testear el flujo de carga sin levantar una ventana Swing
- Reutilizar la misma lógica desde otro punto de entrada (CLI, tests)

---

## Problema 5: Estado global mutable no encapsulado

### Campos de estado sueltos en IEDExplorerApp (~40 campos)

```java
// Estado de conexión
private boolean isConnected = false;
private boolean isServerRunning = false;
private String currentHost;
private int currentPort;
private String connectedLocalIp;

// Estado del modelo cargado
private String loadedIedName;
private String[] loadedIedNameplate;
private AppMode currentMode;

// Mapas de lookup SCL (construidos al cargar CID)
private Map<String, LinkedHashMap<Integer, String>> sclEnumTypes;
private Map<String, String> sclDaEnumType;
private Map<String, Map<String, String>> sclLnTypeDoTypes;
private Map<String, String> sclLnClassToLnType;

// Estado del monitor
private List<MonitorItem> monitorItems;
private TableRowSorter<DefaultTableModel> monitorSorter;

// Estado GOOSE
private List<SclGoCB> sclGoCBs;
private GoosePublisher activePublisher;
private List<GoosePublisher> activePublishers;
// ... etc
```

Este estado es accedido y mutado desde cualquier método de la clase sin control.

### Solución estructural

```java
// Un objeto de sesión encapsula el estado con acceso controlado
class IedSession {
    private final String iedName;
    private final String[] nameplate;
    private final ServerModel model;
    // getters inmutables, sin setters públicos
}

class AppState {
    private volatile boolean connected;
    private volatile AppMode mode;
    // cambios solo a través de métodos explícitos con validación
}
```

---

## Problema 6: Lógica SCL dispersa en múltiples lugares

El parseo del SCL ocurre en al menos **6 lugares distintos**:

| Método | Archivo | Qué parsea |
|--------|---------|-----------|
| `getAvailableIEDs()` | IEC61850Server | Lista de IEDs + merge de AccessPoints |
| `expandSclArrays()` | IEC61850Server | Expansión de SDO/DA/BDA con count |
| `parseSclDataTypeTemplates()` | IEDExplorerApp | EnumTypes, DOTypes, LNodeTypes |
| `parseGoCBsFromScl(File)` | IEDExplorerApp | GoCBs, DataSets, Reports |
| `parseGoCBsFromScl(File, int)` | IEDExplorerApp | Ídem, filtrado por IED |
| `parseDataSetsFromIED()` | IEDExplorerApp | DataSets por IED element |
| `parseReportsFromIED()` | IEDExplorerApp | RCBs por IED element |

Hay dos overloads de `parseGoCBsFromScl` con lógica parcialmente duplicada.
Todo esto debería vivir en un `SclService` cohesivo que parsee el documento
**una sola vez** y exponga los datos estructurados.

### Violación

**DRY — Don't Repeat Yourself**: el mismo documento DOM se parsea múltiples veces
en llamadas separadas, con estructuras de datos similares duplicadas.

---

## Problema 7: Sin interfaces entre capas

### Estado actual

```java
// IEDExplorerApp llama directamente a la implementación concreta
IEC61850Client client = new IEC61850Client();
IEC61850Server server = new IEC61850Server();
client.connect(host, port);
server.loadSclFileWithIED(path, index);
```

### Problema

Si mañana se necesita:
- Cambiar la biblioteca IEC 61850 (de iec61850bean a otra)
- Agregar un mock para tests
- Soportar múltiples conexiones simultáneas

Habría que modificar la GUI directamente.

### Solución

```java
interface IedClientPort {
    void connect(String host, int port) throws IOException;
    Map<String, String> readDeviceNameplate();
    String readValue(String reference, Fc fc) throws IOException;
    void writeValue(String reference, Fc fc, String value) throws IOException;
}

interface IedServerPort {
    boolean loadModel(String sclPath, int iedIndex);
    boolean start(int port);
    void stop();
    ServerModel getModel();
}
```

### Principio violado

**DIP — Dependency Inversion Principle**: los módulos de alto nivel (GUI) no deberían
depender de los de bajo nivel (implementación MMS), sino de abstracciones.

---

## Problema 8: Sin tests

### Estado actual

```xml
<!-- pom.xml -->
<configuration>
    <skipTests>true</skipTests>
</configuration>
```

No existe ningún test unitario ni de integración. Todo se valida manualmente
conectando a un IED real o al simulador.

### Consecuencia

Cualquier refactorización es de alto riesgo porque no hay red de seguridad.
Los bugs de regresión solo se detectan en producción.

### Lo que sería testeable con la arquitectura correcta

```java
// Con SclService extraído:
@Test void debeExpandirArraysConCount() { ... }
@Test void debeDecodificarSIUnit29comoVoltios() { ... }
@Test void debeExtraerGoCBsDelLLN0() { ... }

// Con IedClientPort mockeado:
@Test void debeMostrarNameplateAlConectar() { ... }
@Test void debeActualizarArbolAlRecibirReporte() { ... }
```

---

## Resumen ejecutivo de deuda técnica

| # | Problema | Principio violado | Severidad | Esfuerzo de fix |
|---|----------|-------------------|-----------|-----------------|
| 1 | God Class (7273 líneas) | SRP | Crítico | Alto |
| 2 | Sin MVC/MVP | Separación capas | Alto | Alto |
| 3 | Métodos de 100–415 líneas | Una función = una cosa | Alto | Medio |
| 4 | Lógica de negocio en listeners | SRP, testabilidad | Alto | Medio |
| 5 | Estado global mutable suelto | Encapsulamiento | Medio | Medio |
| 6 | Parseo SCL disperso y duplicado | DRY, SRP | Medio | Medio |
| 7 | Sin interfaces entre capas | DIP | Medio | Bajo |
| 8 | Sin tests | — | Alto (futuro) | Alto |

---

## Plan de refactorización sugerido (por fases)

### Fase 1 — Sin romper nada (extraer paneles)

Extraer cada pestaña a su propia clase `JPanel`:

```
MonitorPanel.java       ← createMonitorPanel() + métodos monitor
ReportsPanel.java       ← createReportsPanel() + RCB logic
GoosePanel.java         ← createGoosePanel() + publish/subscribe
SampledValuesPanel.java ← createSampledValuesPanel()
SettingGroupsPanel.java ← setting groups tab
DatasetPanel.java       ← dataset tab
DataModelPanel.java     ← data model tab
```

**Impacto**: IEDExplorerApp pasa de 7273 a ~2500 líneas sin cambiar comportamiento.

### Fase 2 — Extraer servicios

```
SclService.java         ← todo el parseo XML/SCL
EnumService.java        ← mapas SIUnit, Health, CtlModel, etc.
NameplateService.java   ← lectura FC=DC
```

### Fase 3 — Introducir interfaces

```
IedClientPort.java      ← interfaz para IEC61850Client
IedServerPort.java      ← interfaz para IEC61850Server
```

### Fase 4 — Encapsular estado

```
AppState.java           ← isConnected, currentMode, etc.
IedSession.java         ← modelo cargado, nombre IED, nameplate
```

### Fase 5 — Agregar tests

Con las capas separadas, introducir JUnit 5 + Mockito para los servicios.

---

## Nota sobre el contexto del proyecto

El código es funcional y cubre un dominio complejo (IEC 61850 MMS + GOOSE + SCL).
La deuda técnica es **consecuencia natural del crecimiento iterativo**, no de
negligencia. El patrón "monolito primero, refactoriza después" es válido mientras
el producto no esté estabilizado. Este análisis sirve para cuando se decida
invertir en mantenibilidad a largo plazo.

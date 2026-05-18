# Informe Técnico — IEDNavigator
## Análisis estructural, funcional y de potencial

**Versión analizada**: com.iednavigator (post-refactorización, 2026-05-18)
**Líneas de código total**: ~15.170 (fuente) + ~1.385 (native_lib)
**Archivos fuente**: 35 clases Java + 3 clases JNA en subpaquete
**Desarrollador**: Emilio Medina

---

## 1. Descripción general

IEDNavigator es una aplicación de escritorio Java para ingeniería de protección y
automatización en subestaciones eléctricas bajo el estándar IEC 61850. Su propósito
central es explorar, simular y monitorear Intelligent Electronic Devices (IEDs) de
forma interactiva, siendo funcionalmente comparable a herramientas comerciales como
OMICRON IEDScout, aunque de código abierto.

Opera sobre tres capas del estándar IEC 61850 simultáneamente:
- **Capa de servicios MMS** (ISO 9506 sobre TCP): conexión cliente/servidor, lectura/escritura de valores, reportes.
- **Capa de mensajería GOOSE** (EtherType 0x88B8, Layer 2): publicación y suscripción de eventos de subestación.
- **Capa de Sampled Values** (capa 2, compilado pero no expuesto en GUI en versión actual).

---

## 2. Estructura del proyecto

```
iec61850_java_explorer/
├── src/main/java/com/iednavigator/   ← código fuente principal
│     ├── native_lib/                  ← bindings JNA a libiec61850
│     └── *.java                       ← 35 clases
├── lib/                               ← JARs y iec61850.dll (precompilados)
├── classes/                           ← salida de compilación
├── SIN SMV/                           ← distribución educativa v3.0
├── installer/output/                  ← releases empaquetados (zip)
├── compile.ps1                        ← compilación PowerShell
├── build_zip_installer_v32.ps1        ← build completo de release
├── pom.xml                            ← definición Maven
├── ARQUITECTURA.md                    ← interfaces entre componentes
└── SIN SMV/BITACORA_REFACTORIZACION.md ← historial de refactorización
```

### 2.1 Dependencias externas

| Biblioteca | Versión | Rol |
|---|---|---|
| iec61850bean (beanit) | 1.9.0 | Stack MMS/IEC 61850 completo (cliente y servidor) |
| pcap4j | 1.8.2 | Captura/inyección de paquetes Layer 2 (GOOSE) |
| FlatLaf | 3.2 | Look-and-Feel moderno para Swing |
| JNA | 5.14.0 | Acceso nativo a libiec61850.dll |
| asn1bean / jasn1 | 1.13.0 / 1.11.3 | Codificación ASN.1 BER (GOOSE PDU manual) |
| SLF4J Simple | 2.0.9 | Logging |
| iec61850.dll | — | Biblioteca C (open62541/libiec61850) para GOOSE/SV nativo |

**Dependencias de runtime en Windows**: Java 11+, Npcap (para pcap4j), permisos de Administrador (puerto 102, captura Layer 2).

---

## 3. Inventario de módulos

### 3.1 Núcleo de orquestación

#### `IEDNavigatorApp.java` — 2320 líneas
El `JFrame` principal. Es el punto de entrada, el orquestador y el tejido conectivo
de toda la aplicación. Gestiona el layout de tres paneles, el árbol de modelo central
(`JTree` con `ModelTreeBuilder`), los modos de operación (servidor/cliente), el
ciclo de vida de todos los managers, y las secciones que aún no se han extraído
(TREE POPUP & VALUE EDITING, GOOSE-MODEL SYNC, construcción de UI).

Estructura interna por secciones:
- **L1–263**: declaración de campos (modo, cliente, servidor, widgets Swing, estado SCL, caché de iconos)
- **L264–859**: construcción de la GUI (`initUI`, menú, toolbar, tabs, barra de estado)
- **L860–1049**: creación del GooseContext y panel de servidor
- **L1050–1226**: panel de cliente + watchlist
- **L1227–1324**: MONITOR PANEL (tabla Activity Monitor)
- **L1325–1610**: TREE POPUP & VALUE EDITING (pendiente de extracción — FP-001)
- **L1611–1722**: GOOSE-MODEL SYNC bidireccional
- **L1723–2143**: wrappers delegando a managers y métodos auxiliares
- **L2143–2304**: implementaciones de los cuatro Context (ConnectionManager, MonitorManager, PollingManager, GoosePanel)
- **L2304–2320**: `main()` (entrada, FlatLaf, `SwingUtilities.invokeLater`)

#### `ConnectionManager.java` — 713 líneas
Gestiona todo el ciclo de vida de conexiones. Encapsula:
- Conexión MMS cliente (con timeout de 10 segundos, pre-check de reachability)
- Arranque y parada del servidor IED
- Descarga de archivo CID del IED (MMS file transfer)
- Selección y parsing de archivos SCL locales
- Diálogo de selección de IED (cuando el SCD contiene múltiples IEDs)
- Diagnóstico y liberación de puertos TCP ocupados

Usa el patrón `Context` con 55+ métodos que cubren estado compartido, callbacks
post-operación, y acceso a widgets de la UI.

#### `PollingManager.java` — 269 líneas
Gestiona el loop de lectura periódica de valores del árbol. Utiliza un
`ScheduledExecutorService` dedicado, independiente del `backgroundExecutor` general.
Recorre todos los nodos en la watchlist, llama a `IEC61850Client.readNodeValues()`,
actualiza el árbol Swing vía `ModelTreeBuilder`, y dispara el refresco del monitor.
El intervalo es configurable en la UI (spinner, por defecto 1000 ms).

#### `MonitorManager.java` — 302 líneas
Gestiona la tabla de Activity Monitor. Mantiene el `Map<String, MonitorItem>` de
nodos monitoreados, detecta cambios de valor (resalta filas modificadas),
aplica filtros por FC y nombre, y soporta agregar nodos recursivamente desde el árbol
(incluyendo todos los hijos de un Data Object).

---

### 3.2 Protocolo MMS (cliente y servidor)

#### `IEC61850Client.java` — ~1450 líneas
Cliente MMS completo. Responsabilidades:

**Conexión**:
- `connect(host, port)`: negocia asociación ACSE/MMS, descarga el modelo del servidor
- Pre-check de reachability TCP antes de intentar el stack MMS completo
- Timeout de 10 segundos con `Future` cancelable
- Cuenta nodos del modelo descargado para feedback al usuario

**Lectura/escritura de valores**:
- `readValue(reference, fc)`: lectura puntual con `FcModelNode`
- `readNodeValues(node)`: lectura recursiva con caché en `valueCache`
- `writeValue(reference, fc, value)`: escritura con conversión de string a tipos BDA
- Soporte para: Boolean, Int8/16/32/64, Float32/64, VisibleString, DoubleBitPos, TapCommand, BdaCheck

**Formateo**:
- `formatValue(node)`: decodifica enums IEC 61850 (SIUnit → "V/A/W/Hz/etc.", CtlModel, Health, Beh)
- Mapas de decodificación estáticos para ~40 unidades SI y enumeraciones normativas

**Control** (IEC 61850-7-2 §20):
- `operateControl(operNode, ctlValStr, testFlag, orIdent, synchroCheck, interlockCheck)`: operación
  unificada que detecta `ctlModel` del DO y ejecuta el flujo correcto:
  - ctlModel=0 (status-only): rechazado inmediatamente, sin tráfico MMS
  - ctlModel=1/3 (direct): `operate()` directo con estructura Oper completa
  - ctlModel=2 (sbo-normal-security): `select()` → `operate()`
  - ctlModel=4 (sbo-enhanced-security): `select()` con Oper completo (incluye ctlVal) →
    `operate()`; equivale a SELECT-WITH-VALUE sobre nodo SBOw
- `cancelControl(operNode, orIdent)`: escribe al nodo `Cancel` (FC=CO, hermano de `Oper`)
  vía `setDataValues()` para liberar un SELECT pendiente; aplica a ctlModel=2 y 4
- `fillControlStructure(operNode, testFlag, orIdent, synchroCheck, interlockCheck)`: rellena
  todos los campos del Oper excepto ctlVal: `origin.orCat=3`, `origin.orIdent`, `ctlNum`
  (contador circular 0–255), `T` (timestamp actual), `Test`, `Check.synchroChk`,
  `Check.interlkChk`
- `readLastApplError(operNode)`: lee `DO.LastApplError[CO]` tras fallo para exponer la
  causa específica del IED (`error` + `addCause`)
- `ControlResult`: clase interna que encapsula éxito/fallo, ctlModel, ctlModelName,
  mensaje de error y LastApplError
- Soporte de tipos de `ctlVal`: Boolean (interruptores), DoubleBitPos (seccionadores
  con 4 estados), TapCommand (cambiadores de taps), Integer, Float (casos genéricos)

**Reportes (RCB)**:
- `enableReporting(rcb, listener)`: configura URCB/BRCB con enfoque de dos pasos
  (escribe trgOps primero con rptEna=false, luego activa rptEna=true)
- `disableReporting(rcb)`: escribe rptEna=false
- Implementa `ClientEventListener` de iec61850bean para recibir reportes y delegarlos

**Setting Groups**:
- `readSGCBValues(ldName)`: lee el número de grupos y el grupo activo
- `selectActiveSG(ldName, groupNumber)`: activa un grupo de ajustes

**File transfer (GetCID)**:
- `listFiles(directory)`, `findSclFiles()`, `downloadFile(filename)`, `downloadAndSaveSclFile()`

#### `IEC61850Server.java` — ~820 líneas
Servidor IED que simula un dispositivo real a partir de un archivo SCL.
- Parsea ICD/CID/SCD con iec61850bean `SclParser`
- Permite seleccionar el IED por índice (archivos SCD con múltiples IEDs)
- Inicia `ServerSap` en el puerto configurado (por defecto 102)
- Implementa `WriteValueHandler` de iec61850bean: cuando un cliente MMS escribe
  un valor, el servidor lo actualiza en el modelo en memoria
- Expone `setDataValue(ref, value)` para que la GUI modifique valores del modelo
  y el servidor los sirva a clientes conectados
- Notifica eventos vía `ServerListener` (arranque, parada, conexión/desconexión de clientes)
- **`patchMissingEnumOrdinals(doc)`** (pipeline de preprocesado DOM): corrige archivos SCL
  de fabricantes que exportan `EnumType` con ordinals incompletos (Siemens SIPROTEC5 omite
  `ord=2` "blocked" y `ord=4` "test/blocked" en `Behavior`). Agrega `EnumVal` sintéticos
  con texto igual al número (`"2"`, `"4"`) para que iec61850bean los resuelva por texto.
  Permite cargar archivos SCD/CID de SIPROTEC5 que de otro modo fallan con
  `SclParseException: unknown enum value: 2`.

---

### 3.3 GOOSE

#### `GoosePanel.java` — 2203 líneas
El módulo más extenso después de la clase principal. Concentra toda la funcionalidad GOOSE:

**Publicación**: configura `GoosePublisher` con parámetros del GoCB seleccionado
(APPID, MAC destino, GoCBRef, datSet, confRev, stNum, datos). Permite publicar
manualmente o en modo retransmisión automática.

**Suscripción**: gestiona `GooseSubscriber` (pcap4j) y `NativeGooseSubscriber` (JNA/libiec61850)
como backends alternativos. Muestra mensajes recibidos en tabla con stNum, sqNum, datos.

**Parsing SCL de GoCBs**: extrae GoCBs y DataSets del archivo SCL para poblar
los combos de selección, sin depender de la conexión MMS activa.

**Enumeraciones SCL**: mantiene los mapas de enums parseados del SCL y expone
`formatEnumValue(node, rawValue)` usado por PollingManager y MonitorManager.

**Sincronización bidireccional con el modelo**:
- `updateGoosePublisherValues(model)`: datos del servidor → publisher GOOSE (forward)
- `propagateValueToPublishers(ref, model)`: cambio puntual en modelo → actualiza GOOSE
- `syncPublisherToServerModel(gcbIndex, dataIndex)`: cambio en GOOSE → escribe en modelo servidor

**Inferencia de tipos**: `inferDataType(memberName)` deduce el tipo GOOSE
(BOOLEAN, FLOAT, INTEGER, BITSTRING, etc.) desde el nombre del atributo FCDA.

**UDP Bridge**: integra `GooseUdpBridge` para transportar GOOSE sobre redes IP
donde el multicast Layer 2 no funciona (WiFi, redes enrutadas).

#### `GoosePublisher.java` — 490 líneas
Construye y transmite frames Ethernet GOOSE usando pcap4j.
- Codifica el PDU GOOSE manualmente en ASN.1 BER (sin depender de iec61850bean para esto)
- Soporta tipos: BOOLEAN, INT32, UINT32, FLOAT32, BITSTRING, DBPOS, UTCTIME
- Maneja stNum/sqNum según el estándar (stNum incrementa en cambio de estado, sqNum en retransmisión)
- Modo test flag, ndsCom (needs commissioning), confRev
- Retransmisión automática con backoff (T1→T2→T3 según IEC 61850-8-1)

#### `GooseSubscriber.java` — 468 líneas
Captura y parsea frames GOOSE desde la red usando pcap4j.
- Filtro pcap: `ether proto 0x88B8`
- Decodifica el PDU ASN.1 BER manualmente
- Extrae: goCbRef, goId, datSet, appId, stNum, sqNum, test, ndsCom, confRev, timestamp, valores
- Entrega parsed `GooseMessage` al listener en hilo separado

#### `GooseUdpBridge.java` — 451 líneas
Puente GOOSE sobre UDP, inspirado en IEC 61850-90-5 R-GOOSE (implementación simplificada).
- Encapsula el frame GOOSE Ethernet raw en datagramas UDP
- Soporta tres modos: unicast, broadcast (255.255.255.255), multicast (239.255.88.184)
- Puerto por defecto: 62746
- Permite que IEDs en redes separadas intercambien GOOSE sin Capa 2 compartida

---

### 3.4 Capa nativa (JNA / libiec61850)

#### `native_lib/LibIec61850.java` — 462 líneas
Interfaz JNA que mapea las funciones C de libiec61850.dll:
- `GooseReceiver_*`: ciclo de vida del receptor GOOSE nativo
- `GooseSubscriber_*`: suscripción filtrada por GoCBRef/AppId
- `SVReceiver_*` / `SVSubscriber_*`: receptor de Sampled Values
- Callbacks nativos: `GooseListener` y `SVUpdateListener` como interfaces JNA con `Callback`

#### `native_lib/NativeGooseSubscriber.java` — 477 líneas
Capa Java sobre la interfaz JNA para recepción GOOSE nativa.
Ventaja frente a pcap4j: parseo de valores completo a nivel de biblioteca C,
soporte para VLAN, y mejor rendimiento en alta frecuencia de mensajes.
Provee la misma interfaz de callback que `GooseSubscriber` para intercambiabilidad.

#### `native_lib/NativeSVSubscriber.java` — 446 líneas
Suscriptor de Sampled Values via libiec61850. **Compilado pero no expuesto en la GUI**
(desactivado en versión actual por limitaciones del entorno de pruebas).
Decodifica ASDUs de Sampled Values: hasta 8 muestras por ASDU, con
instantes, calidad, y valores de corriente/tensión trifásicos.

---

### 3.5 Parsing SCL

#### `SclFileProcessor.java` — 457 líneas
Procesador XML de archivos SCL (ICD/CID/SCD) usando DOM parser estándar de Java.
Sin dependencias externas para el parsing (usa `javax.xml.parsers`).
Extrae:
- Nombre del IED y datos de placa (manufacturer, type, desc, configVersion)
- Lista de GoCBs (GSE Control Blocks) con APPID, MAC, datSet, confRev
- DataSets con sus FCDAs (referencias de datos)
- Report Control Blocks (URCB/BRCB)
- DataTypeTemplates completos: DOTypes, DATypes, EnumTypes → construye los mapas
  de decodificación de enums por DA (`daEnumType`, `lnTypeDoTypes`, `lnClassToLnType`)

#### `SclReferenceUtils.java` — 96 líneas
Utilidades estáticas para conversión de referencias FCDA al formato iec61850bean:
- `buildModelRefFromFCDA(member, iedName)`: convierte `"ldInst/LN.DO.DA [FC]"` → `"IEDNameldInst/LN.DO.DA"`
- `extractFcFromMember(member)`: extrae el FC del sufijo `[FC]`

---

### 3.6 Árbol de modelo

#### `ModelTreeBuilder.java` — 575 líneas
Constructor y actualizador del árbol Swing que representa el modelo IEC 61850.
Métodos estáticos (sin estado):
- `buildTree()`: construye `DefaultMutableTreeNode` recursivamente desde un `ServerModel`
- `updateTreeValues()`: recorre todos los nodos y refresca valores desde el cliente
- `updateSingleNodeInTree()`: actualiza un único nodo por referencia (eficiente para reportes)
- `navigateToFcdaInModel()`: expande y selecciona el nodo que corresponde a un FCDA
- Formato de nodos: `NodeInfo` con nombre, FC, valor, tipo, referencia al `ModelNode`

#### `ModelTreeCellRenderer.java` — 171 líneas
Renderizador personalizado de celdas del árbol principal.
Aplica colores según: FC (ST=azul, MX=verde, CO=rojo, etc.), valor de disyuntor
(ON/OFF/intermedio), estado de Health/Beh, y tipo de nodo (LD/LN/DO/DA).
Usa `IconFactory` para los íconos y copia los iconos de estado por referencia,
evitando crear objetos por cada celda.

#### `DataModelTreeCellRenderer.java` — 51 líneas
Renderizador simplificado para el árbol secundario del tab "Data Model"
(vista alternativa del modelo con atributos expandidos).

---

### 3.7 Paneles de la interfaz (tabs)

#### `ReportsPanel.java` — 395 líneas
Tab "Reports" — gestión de Report Control Blocks.
- `refreshReportControlBlocks()`: escanea el ServerModel en busca de URCB/BRCB
- Muestra tabla con: referencia del RCB, datSet, trgOps, estado (enabled/disabled)
- Habilita/deshabilita reportes individuales o todos a la vez
- Tabla de datos recibidos: timestamp, referencia, valor, razón del trigger (dchg/qchg/dupd/gi/intg)
- Fix implementado: escritura en dos pasos (trgOps primero, rptEna después) para
  compatibilidad con servidores IEC 61850 que rechazan escrituras simultáneas

#### `DatasetPanel.java` — 208 líneas
Tab "Dataset" — browser de DataSets del modelo.
- Lista todos los DataSets con nombre, LD/LN propietario, número de miembros
- Al seleccionar un dataset, muestra sus FCDAs con referencia y FC
- Doble-clic en un FCDA navega al nodo en el árbol principal (callback `onNavigate`)

#### `DataModelPanel.java` — 186 líneas
Tab "Data Model" — vista árbol detallada del modelo, independiente del árbol principal.
- Árbol expandible con todos los LDs, LNs, DOs, DAs del modelo
- Al seleccionar un nodo, muestra tabla de atributos (nombre, FC, valor, tipo)
- Funciones: expandir todo, colapsar todo, exportar a XML

#### `SettingGroupsPanel.java` — 256 líneas
Tab "Setting Groups" — control de grupos de ajuste (SGCB).
- Lista todos los logical nodes con Setting Group Control Blocks
- Muestra número de grupos y grupo activo
- Permite visualizar valores de cada grupo y activar un grupo diferente
- Edición de valores dentro de un Setting Group (requiere cliente conectado)

---

### 3.8 Componentes de soporte

#### `IconFactory.java` — 338 líneas
Genera programáticamente todos los íconos del árbol usando formas geométricas Java2D.
Sin dependencias de recursos de imagen (no hay archivos `.png` o `.ico`).
Iconos para: LD (cuadrado azul), LN (hexágono), DO (rombo), DA (círculo),
disyuntor ON/OFF/intermedio, GOOSE, nodo raíz, y varios estados.
Mantiene un `Map<String, Icon>` como caché para no recrear objetos en cada render.

#### `Iec61850Dictionary.java` — 1318 líneas
Diccionario educativo/normativo integrado a la aplicación.
Contiene ~200 entradas clasificadas en: Logical Nodes (IEC 61850-7-4),
Common Data Classes (IEC 61850-7-3), Functional Constraints (IEC 61850-7-2),
Data Objects y Data Attributes comunes.
Cada entrada tiene: nombre ES/EN, descripción técnica, referencia a la norma, ejemplo.
Se activa con doble-clic en cualquier nodo del árbol → muestra diálogo con la definición.

#### `ValueDialogs.java` — 73 líneas
Diálogos modales para lectura/escritura interactiva de valores:
- `showReadDialog()`: muestra valor actual con opción de refrescar
- `showWriteDialog()`: campo de entrada con conversión automática según tipo BDA

#### `MonitorTableRenderer.java` — 72 líneas
Renderizador de la tabla Activity Monitor: resalta en amarillo las filas
con valores cambiados recientemente, y en gris las filas sin valor aún.

#### `PortUtils.java` (dentro de ConnectionManager) —
Diagnóstico de puertos TCP: verifica si el puerto 102 (o el configurado) está ocupado,
identifica el PID del proceso que lo ocupa (usando `netstat /ano` + `tasklist /FI PID`),
y permite liberar el puerto terminando el proceso (requiere Administrador).

#### `SmokeTest.java` — 200 líneas
Test de humo headless para verificar integridad post-refactorización.
Prueba: instanciación de DTOs, parsing SCL, carga de clases `IEC61850Server` e `IEC61850Client`.
Usado para detectar regresiones sin necesidad de GUI ni IED físico.

---

### 3.9 DTOs (clases de datos)

| Clase | Líneas | Contenido |
|---|---|---|
| `SclGoCB` | 24 | GoCB parseado del SCL: ldInst, cbName, appID, datSet, confRev, MAC, goID |
| `SclDataSet` | 19 | DataSet del SCL: ldInst, lnClass, name, desc, members |
| `SclReport` | 18 | RCB del SCL: ldInst, lnClass, name, rptID, datSet, buffered, confRev |
| `NodeInfo` | 36 | Nodo del árbol: name, prefix, fc, value, type, ModelNode, SclGoCB |
| `MonitorItem` | 33 | Elemento del monitor: reference, name, fc, value, oldValue, type, FcModelNode, lastChangeTime |
| `DataModelNodeInfo` | 32 | Nodo del árbol secundario (Data Model tab) |

---

## 4. Modos de operación

### 4.1 Modo Servidor (simulación de IED)

El usuario carga un archivo SCL (ICD/CID/SCD) y el servidor IEC 61850 escucha conexiones
entrantes en el puerto configurado (por defecto 102). Cualquier cliente MMS compatible
puede conectarse y ver el modelo del IED simulado.

**Flujo de uso típico**:
1. Seleccionar modo "Servidor" → elegir archivo SCL
2. El parser extrae el modelo, GoCBs, DataSets, enums
3. Presionar "Iniciar Servidor" → `IEC61850Server.start(port)`
4. El árbol muestra el modelo completo con valores iniciales (desde el SCL)
5. El usuario puede modificar valores manualmente desde el árbol (click derecho → editar)
6. Los cambios se propagan al modelo del servidor y se publican via GOOSE si hay publisher activo

**Capacidades del servidor**:
- Responde a lecturas MMS de cualquier atributo del modelo
- Acepta escrituras de clientes MMS (`WriteValueHandler`)
- Publica GOOSE sincronizado con el estado del modelo
- Soporte para Setting Groups (si el SCL los define)

### 4.2 Modo Cliente (conexión a IED real o simulado)

El usuario introduce IP y puerto del IED destino. La aplicación establece la asociación
MMS, descarga el modelo completo, y lo presenta en el árbol.

**Flujo de uso típico**:
1. Seleccionar modo "Cliente" → ingresar IP:puerto
2. "Conectar" → `IEC61850Client.connect()` (10 segundos timeout)
3. El modelo se descarga y el árbol se construye con `ModelTreeBuilder`
4. El usuario puede leer valores puntualmente, agregar nodos a la watchlist, y activar polling
5. Los nodos de la watchlist se refrescan periódicamente (polling configurable 500ms–10s)
6. Cualquier cambio de valor se refleja en el árbol y en el Activity Monitor

### 4.3 Monitoreo (Activity Monitor / Watchlist)

Independiente del modo (cliente o servidor), el usuario puede:
- Arrastrar nodos del árbol a la tabla del monitor (Drag-and-Drop)
- Usar click derecho → "Agregar al Monitor"
- Filtrar por FC (ST/MX/CO/CF/...) o por nombre
- Detectar cambios de valor (fila resaltada en amarillo)
- Ver tipo de dato, valor actual, y timestamp del último cambio

### 4.4 Reportes URCB/BRCB

Con un cliente conectado, el tab "Reports":
- Escanea el modelo del IED descubierto y lista todos los RCBs disponibles
- Permite habilitar/deshabilitar reportes individuales o todos
- Los reportes recibidos se muestran en tabla con: timestamp, referencia del dato,
  valor, y razón del trigger (data-change, quality-change, data-update, GI, integrity)
- Los valores reportados se propagan automáticamente al árbol (nodo actualizado visualmente)

### 4.5 GOOSE Publisher

El usuario puede publicar mensajes GOOSE configurando:
- Interfaz de red (lista de interfaces del sistema)
- Selección de GoCB desde el SCL cargado (combo poblado por `SclFileProcessor`)
- Datos a publicar (tabla editable con tipo y valor por cada FCDA del DataSet)
- Modo retransmisión automática o publicación manual

Además, cuando el servidor IED está activo y el usuario modifica un valor en el árbol,
el cambio se propaga automáticamente al publisher GOOSE (sincronización bidireccional).

### 4.6 GOOSE Subscriber

Captura frames GOOSE de la red y los muestra en tabla: MAC origen, GoCBRef, goId,
stNum, sqNum, test flag, y lista de valores del DataSet. Soporta dos backends:
- **pcap4j** (Java puro): funcional en la mayoría de casos, parseo manual de ASN.1
- **libiec61850 nativo** (JNA): mayor fidelidad de parsing, mejor para debug avanzado

### 4.7 GOOSE-over-UDP Bridge

Para redes donde el multicast Layer 2 no está disponible (WiFi, redes enrutadas
entre subestaciones), el bridge encapsula los frames GOOSE en datagramas UDP.
Modos disponibles: unicast (punto a punto), broadcast (red local), multicast UDP.

### 4.8 Setting Groups

Permite al usuario cambiar el grupo de ajustes activo de un IED conectado.
Muestra todos los LNs con SGCB, el número de grupos disponibles, y permite
activar un grupo diferente via MMS (`selectActiveSG`).

### 4.9 Obtención de CID

Desde un IED conectado (cliente MMS), el usuario puede:
- Listar los archivos disponibles en el file system del IED
- Detectar archivos CID/ICD automáticamente
- Descargarlos como bytes y guardarlos en disco local
- Usarlos para reusar el modelo en modo servidor

---

## 5. Fortalezas

### 5.1 Cobertura funcional amplia
Implementa en una sola aplicación lo que normalmente requiere varias herramientas:
cliente MMS, servidor IED, publisher GOOSE, subscriber GOOSE, parser SCL, monitor
de valores y browser de reportes. La mayoría de herramientas comerciales comparables
(IEDScout, OMICRON Test Universe) son de pago y cerradas.

### 5.2 Doble estrategia GOOSE
El uso paralelo de pcap4j (Java puro, portable) y libiec61850 via JNA (nativo, más completo)
da resiliencia: si uno falla (driver Npcap no instalado, permisos insuficientes),
el otro puede usarse como fallback. Además, el bridge UDP es una solución práctica
para escenarios de laboratorio con redes enrutadas.

### 5.3 Parsing SCL propio con enums
`SclFileProcessor` extrae los `DataTypeTemplates` completos del SCL para construir
mapas de decodificación de enumeraciones por DA. Esto permite mostrar "off/on/intermediate"
en lugar de "1/2/3" para los estados de disyuntores, lo cual es crítico para
la usabilidad en ingeniería de protecciones.

### 5.4 Sincronización bidireccional GOOSE↔modelo
La arquitectura que mantiene consistencia entre el modelo del servidor IEC 61850 y
el publisher GOOSE es no trivial y está bien implementada: los cambios en el árbol
se propagan al GOOSE, y los cambios desde el panel GOOSE se escriben de vuelta
al modelo del servidor. Esto permite simular comportamiento realista de un IED.

### 5.5 Refactorización progresiva documentada
El proyecto pasó de ~7151 líneas en un solo archivo a 35 clases con responsabilidades
bien delimitadas. La bitácora registra cada fase con motivación, cambios y resultado.
El patrón `Context` (usado en 4 managers) permite pruebas unitarias de los managers
sin instanciar el `JFrame` completo.

### 5.6 Empaquetado autónomo
El instalador generado (`build_zip_installer_v32.ps1`) produce un ZIP portable que
contiene clases, JARs y DLL. El launcher PowerShell detecta Java automáticamente
(JAVA_HOME, rutas conocidas de Adoptium, Microsoft, Oracle) y el instalador
descarga automáticamente Java 21 y Npcap si no están presentes.

### 5.7 Diccionario normativo integrado
`Iec61850Dictionary` con ~200 entradas es un recurso educativo valioso que convierte
la aplicación en herramienta de aprendizaje además de herramienta de ingeniería.
El acceso con doble-clic en cualquier nodo reduce la fricción cognitiva al trabajar
con los abbreviaturas del estándar (XCBR, PTOC, MMXU, CSWI, etc.).

### 5.8 Diagnóstico integrado de puertos
La detección del proceso que ocupa el puerto 102 con capacidad de liberarlo directamente
desde la UI es una funcionalidad práctica que elimina fricción en el workflow
de laboratorio (no es necesario abrir el Administrador de Tareas o usar netstat).

---

## 6. Debilidades

### ~~6.1 Control: solo direct-operate (sin SBO completo)~~ — **RESUELTO**
El módulo de control fue completamente reimplementado. `operateControl()` cubre los cinco
modelos definidos en IEC 61850-7-3 (ctlModel 0–4), con los siguientes servicios operativos:

| ctlModel | Nombre | Flujo implementado |
|---|---|---|
| 0 | status-only | Rechazado inmediatamente (sin tráfico MMS) |
| 1 | direct-normal-security | `operate()` directo |
| 2 | sbo-normal-security | `select()` → `operate()` |
| 3 | direct-enhanced-security | `operate()` con estructura enhanced |
| 4 | sbo-enhanced-security | `select()` con Oper+ctlVal (≡ SELECT-WITH-VALUE) → `operate()` |

Adicionalmente implementados: **Cancel SELECT** (`cancelControl()`), **Test flag** (`Oper.Test`),
**Check field** (`Oper.Check.synchroChk` + `Oper.Check.interlkChk`) vía `BdaCheck`, y lectura
de `LastApplError` tras fallo para diagnóstico. El diálogo de control en la GUI expone todos
estos campos con controles tipados (combo para Boolean/DBPOS/TapCommand, texto para numéricos).

Pendiente menor: `TimeActivatedOperate` (operación con timestamp programado, IEC 61850-7-2 §20.7).

### 6.2 Thread safety parcial en la GUI
Los managers (`PollingManager`, `ConnectionManager`) operan en hilos de background
y llaman a callbacks de `Context`. Algunos de estos callbacks actualizan widgets
Swing directamente sin garantizar que estén en el EDT. Aunque en la práctica Java
es tolerante con esto, viola las reglas de Swing y puede causar intermitencias visuales
en condiciones de carga.

### 6.3 Dos versiones de JNA en el classpath
`lib/jna-5.13.0.jar` y `lib/jna-5.14.0.jar` coexisten. Si el orden de carga del
classloader es adverso, puede resultar en `ClassNotFoundException` o comportamiento
inesperado en la capa nativa. Debería usarse solo `jna-5.14.0.jar`.

### 6.4 Timeout de conexión hardcoded
Los 10 segundos de timeout en `IEC61850Client.connect()` no son configurables desde la UI.
Para IEDs con latencia alta (conexiones WAN, VPNs de subestación) este valor puede
ser insuficiente. Para pruebas locales, puede ser excesivo.

### 6.5 Sin persistencia de sesión
No hay forma de guardar y restaurar una sesión: lista de nodos monitoreados, filtros
activos, configuración de GOOSE publishers, ni historial de valores. Al cerrar la
aplicación se pierde todo el estado.

### 6.6 Sampled Values deshabilitados en GUI
`NativeSVSubscriber` está compilado y es funcional a nivel de clase, pero no está
expuesto en ninguna tab de la interfaz. Esta es la limitación más visible para
aplicaciones de medición (protecciones de distancia, PMUs, registradores).

### 6.7 Cero tests automatizados
`SmokeTest.java` verifica instanciación de clases y carga de SCL, pero no prueba
ningún flujo de red. No hay mocks de la capa MMS ni de pcap4j. Cualquier regresión
en el protocolo solo se detecta conectando a un IED real o al simulador.

### 6.8 `GoosePanel.java` es el nuevo monolito
Con 2203 líneas, `GoosePanel` es casi tan grande como la clase principal antes
de la refactorización. Internamente mezcla UI (tablas, combos, botones), lógica
de protocolo (ASN.1 parsing, retransmisión), parsing SCL, y sincronización con el modelo.
Candidato natural para la próxima fase de extracción.

### 6.9 Windows-only por diseño
La dependencia de Npcap (solo Windows), `iec61850.dll` (Win64), y los scripts `.bat`/`.ps1`
limitan la distribución a Windows. pcap4j en sí es multiplataforma (usa libpcap en Linux/macOS),
pero la cadena de packaging completa no lo es.

### 6.10 Formateo de valores incompleto
El mapa `SI_UNIT_MAP` en `IEC61850Client` tiene ~40 unidades, pero IEC 61850-7-3
define más de 100. Los valores con unidades no mapeadas muestran el código numérico
crudo. Similar con las enumeraciones: solo se decodifican automáticamente `SIUnit`,
`CtlModel`, `Health` y `Beh`; otras enumeraciones normativas requieren el SCL cargado.

---

## 7. Potencialidades

### ~~7.1 Completar Select-Before-Operate~~ — **IMPLEMENTADO**
SBO completo implementado: ctlModel=2 y 4, Cancel SELECT, Test flag, Check field.
Ver sección 6.1 para el detalle. La aplicación es ahora apta para pruebas de FAT/SAT
con IEDs reales configurados con seguridad enhanced (ABB REL670, Siemens SIPROTEC5,
GE L90, SEL-411L, etc.).

El único servicio de control pendiente es `TimeActivatedOperate` (baja prioridad operacional).

### 7.2 Habilitar Sampled Values en GUI
El código nativo (`NativeSVSubscriber`) ya está compilado. Crear un `SVPanel.java`
análogo a `GoosePanel` y añadir una tab permitiría monitorear corrientes y tensiones
muestreadas — base para aplicaciones de calidad de energía y verificación de PMUs.

### 7.3 Sistema de scripting / automatización
Una capa de scripting (Groovy, Jython, o un DSL propio) sobre `IEC61850Client` y
`IEC61850Server` permitiría automatizar secuencias de prueba: conectar → leer valor →
comparar con esperado → escribir control → verificar respuesta. Esto abre el camino
hacia un runner de pruebas de tipo OMICRON Relvac.

### 7.4 Persistencia de sesión (JSON/YAML)
Serializar el estado del monitor (watchlist, filtros), configuración de GOOSE publishers,
y perfiles de conexión a archivos JSON permitiría reproducir configuraciones de prueba
y compartirlas entre equipos.

### 7.5 Exportación de datos monitoreados
El Activity Monitor ya tiene todos los datos (referencia, FC, valor, timestamp).
Añadir exportación a CSV/Excel y una visualización de serie temporal (gráfico de valores
vs tiempo) lo convertiría en un registrador básico de eventos, útil para análisis post-mortem.

### 7.6 Comparación de modelos SCL
Con el parser SCL ya funcional, una vista de diff entre dos archivos SCL
(versión anterior vs nueva configuración de subestación) sería una funcionalidad
de alto valor para ingeniería de protecciones durante migraciones y comisionamientos.

### 7.7 Soporte multi-IED simultáneo
La arquitectura actual gestiona una sola conexión cliente a la vez. Añadir un
`List<IEC61850Client>` con tabs por conexión permitiría monitorear múltiples IEDs
en paralelo — caso de uso natural en bahías con IEDs de protección + medición + control.

### 7.8 Interfaz de línea de comandos (headless)
`SmokeTest` ya demuestra que las clases de protocolo funcionan sin GUI.
Un modo headless (`--headless --connect ip:port --read ref --write ref:value`)
permitiría integrar la aplicación en pipelines de CI/CD para pruebas automatizadas
de configuración de subestaciones, o usarla en servidores de laboratorio sin display.

### 7.9 Publicación de modelo OPC-UA sobre el modelo IEC 61850
Exponiendo el `ServerModel` de iec61850bean como un servidor OPC-UA (Eclipse Milo)
se crearía un gateway IEC 61850 → OPC-UA, habilitando la integración con SCADA
modernos y plataformas de IoT industrial sin modificar el IED original.

### 7.10 Reconocimiento de topología desde SCD
Los archivos SCD de subestación completa contienen la topología de la bahía
(qué IED publica GOOSE, cuáles lo suscriben, con qué datasets). Parsear las
secciones `<Communication>` y `<Substation>` del SCD permitiría generar automáticamente
la configuración de suscriptores GOOSE y visualizar la topología de protecciones.

---

## 8. Resumen ejecutivo

IEDNavigator es una herramienta sólida para ingeniería y laboratorio en IEC 61850.
Su cobertura funcional es notable para un proyecto individual: MMS cliente/servidor,
GOOSE pub/sub, parsing SCL con enums, reportes, setting groups, file transfer, y bridge UDP.

La refactorización completada (67% de reducción en la clase principal, 35 clases con
responsabilidades delimitadas) ha dado estabilidad estructural al proyecto.

**Módulo de control completado** (2026-05): el gap más crítico para uso en subestaciones
reales fue resuelto. La implementación ahora cubre los cinco ctlModel de IEC 61850-7-3,
SELECT-WITH-VALUE para seguridad enhanced, Cancel SELECT, Test flag y Check field.
Adicionalmente, el preprocesado DOM de SCL (`patchMissingEnumOrdinals`) permite cargar
archivos de fabricantes no conformes como Siemens SIPROTEC5.

El mayor riesgo operacional restante es la ausencia de tests de protocolo automatizados.

Las tres mejoras de mayor impacto inmediato son:
1. **Habilitar Sampled Values** (código ya existente en `NativeSVSubscriber`, solo falta la tab de UI)
2. **Persistencia de sesión** (watchlist, configuraciones GOOSE y perfiles de conexión en JSON)
3. **Tests de protocolo** (mocks de la capa MMS para detectar regresiones sin IED físico)

El diccionario normativo integrado y la documentación de arquitectura (`ARQUITECTURA.md`,
`BITACORA_REFACTORIZACION.md`) posicionan bien al proyecto para incorporar colaboradores
o crecer hacia un producto más completo.

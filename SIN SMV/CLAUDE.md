# CLAUDE.md - IEDNavigator v3.0-edu (SIN SMV)

## Estado del Proyecto

Versión "SIN SMV": la funcionalidad de Sampled Values fue desactivada (código comentado) por limitaciones
en el entorno de pruebas. El resto del sistema es funcional.

**Lo que funciona bien:**
- Modelado de estructura de datos desde ICD/CID (parsing SCL, árbol de modelo)
- Modo Servidor: simula IEDs desde archivos SCL
- Modo Cliente: conexión MMS, lectura/escritura de valores, polling
- Mensajería GOOSE (publicación y suscripción, nativo + pcap4j)
- GOOSE-over-UDP bridge
- Carga de RCBs desde el modelo del servidor

**Lo que se está corrigiendo:**
- Reportes (RCB/URCB/BRCB): el `setRcbValues` escribía campos no permitidos (`datSet`, `optFlds`, `bufTm`)
  causando ServiceError en servidores reales. Fix: solo escribir `rptEna` y `trgOps`.

## Estructura de Archivos

```
SIN SMV/                          ← git root de esta versión
  CLAUDE.md                       ← este archivo
  IEDNavigator_v3.0-edu_Setup/   ← distribución compilada (solo clases + lib)

../src/main/java/com/iedexplorer/ ← código fuente (en directorio padre)
  IEDExplorerApp.java             ← GUI principal (~6720 líneas), monolítico
  IEC61850Client.java             ← cliente MMS, suscripción a reportes
  IEC61850Server.java             ← servidor IED desde SCL
  GoosePublisher.java             ← publicación GOOSE (pcap4j)
  GooseSubscriber.java            ← captura GOOSE (pcap4j)
  GooseUdpBridge.java             ← bridge GOOSE sobre UDP
  native_lib/
    LibIec61850.java              ← JNA bindings a iec61850.dll
    NativeGooseSubscriber.java    ← GOOSE nativo vía JNA
    NativeSVSubscriber.java       ← SV nativo vía JNA (DESACTIVADO en GUI)
```

## Convenciones

- Java 11+, Maven 3.6+, Swing + FlatLaf
- Biblioteca IEC 61850: iec61850bean 1.9.0 (com.beanit)
- Captura de red: pcap4j 1.8.2 (requiere Npcap en Windows)
- JNA 5.14.0 + iec61850.dll para GOOSE/SV nativo
- Desarrollo Windows-first (bat/ps1, dll, Npcap)
- Sin tests unitarios (Maven skips tests)

## Arquitectura de Reportes (RCB)

### Flujo de suscripción:
1. `refreshReportControlBlocks()` → busca Urcb/Brcb en el ServerModel, llena `rcbMap`
2. Usuario selecciona RCB → `enableSelectedReport(true)`
3. `client.enableReporting(rcb, listener)` → configura y habilita en el servidor
4. Servidor envía reports → `IEC61850Client.newReport(Report)` → `externalReportListener`
5. `handleReportReceived(report)` → inserta fila en tabla con timestamp, ref, valor, razón

### Parámetros correctos de setRcbValues:
```java
// Solo escribir rptEna y trgOps (NO modificar datSet, optFlds, bufTm)
association.setRcbValues(urcb, false, false, true, false, false, false, true, false);
//                              rptId datSet rptEna optFlds bufTm sqNum trgOps intgPd
```

## Notas de Depuración

- GOOSE Layer 2 multicast puede no funcionar en todas las interfaces de red en Windows
- Timeout de conexión MMS: 10 segundos (hardcoded en IEC61850Client)
- Los RCBs del modo Servidor también se pueden cargar con "Cargar RCBs" (sin necesidad de cliente conectado)
- Si el servidor rechaza `setRcbValues` con ServiceError, revisar que el RCB no esté ya habilitado
  por otro cliente (para URCB solo un cliente puede tenerlo reservado a la vez)

## Tareas Comunes

- **Modificar GUI**: `IEDExplorerApp.java` (buscar por nombre del método o panel)
- **Modificar lógica de reportes**: `IEC61850Client.enableReporting()` / `handleReportReceived()`
- **Agregar formateo de tipos**: `IEC61850Client.formatValue()` / `getValueType()`
- **Modificar GOOSE**: `GoosePublisher.java` / `GooseSubscriber.java`
- **Compilar**: `mvn clean package -DskipTests` desde el directorio padre
- **Ejecutar**: `java --enable-native-access=ALL-UNNAMED -Djna.library.path=lib -jar target/iec61850-explorer-1.0.0-jar-with-dependencies.jar`

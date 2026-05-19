# IEDNavigator v3.3

Herramienta de escritorio Java para exploracion y simulacion del protocolo **IEC 61850**.
Funcionalmente similar a cualquier herramienta de exploración de ieds. De uso libre bajo licencia GPL v3.

Desarrollado por **Emilio Medina**.

---

## Caracteristicas

### Modo Cliente
- Conexion MMS/ACSE a cualquier IED IEC 61850
- Descubrimiento automatico del modelo (Server > LD > LN > DO > DA)
- Lectura y escritura de valores por Functional Constraint (ST, MX, CF, CO, BL)
- Polling periodico configurable
- Activity Monitor con filtros y exportacion CSV
- Suscripcion a reportes (URCB/BRCB)
- Control de interruptores y seccionadores: directo, SBO y SBOw (Select-Before-Operate)
- Cancel SELECT, flag Test, campos Check (synchroChk / interlkChk), identificador orIdent
- Bloqueo FC=BL (blkEna) y Setting Groups (SGCB)

### Modo Servidor
- Carga de archivos ICD / CID / SCD
- Responde a lecturas MMS de clientes externos
- Edicion interactiva de valores desde la GUI
- Enumeraciones (EnumType) leidas desde el SCL
- Compatibilidad con archivos SCD/CID de Siemens SIPROTEC5 (EnumTypes incompletos corregidos)

### GOOSE (IEC 61850-8-1)
- Publicacion y suscripcion Layer 2 (pcap4j)
- Bridge GOOSE sobre UDP para redes enrutadas
- Sincronizacion bidireccional modelo <-> GOOSE
- GOOSE nativo via libiec61850 (requiere instalacion separada)

---

## Requisitos previos

### 1. Java 11 o superior
Descarga gratuita: https://adoptium.net

### 2. Npcap (solo Windows, necesario para GOOSE Layer 2)

1. Ir a https://npcap.com
2. Descargar el instalador gratuito
3. Instalar con la opcion "WinPcap API-compatible Mode" activada
4. Reiniciar si se solicita

Sin Npcap, el modo Cliente MMS y el Servidor funcionan normalmente.
Solo la captura/publicacion GOOSE Layer 2 requiere Npcap.

### 3. libiec61850.dll (opcional, solo para GOOSE nativo avanzado)

IEDNavigator no incluye esta DLL por razones de licencia (GPL v3).
Para habilitarla:

1. Ir a https://github.com/mz-automation/libiec61850
2. Descargar los binarios precompilados para Windows
3. Copiar iec61850.dll a la carpeta lib/ del proyecto

Sin esta DLL todas las demas funciones funcionan con normalidad.

---

## Instalacion y ejecucion

### Opcion A - Script PowerShell

    .\compile.ps1
    .\run.ps1

### Opcion B - Maven

    mvn clean package -DskipTests
    java --enable-native-access=ALL-UNNAMED -Djna.library.path=lib -jar target/iec61850-explorer-1.0.0-jar-with-dependencies.jar

### Opcion C - Scripts batch

    build.bat
    run.bat

---

## Uso rapido

### Conectar a un IED real
1. Seleccionar pestana Cliente
2. Ingresar IP y puerto (puerto estandar IEC 61850: 102)
3. Click Connect
4. Navegar el arbol de modelo
5. Click derecho sobre un nodo: Read, Add to Monitor, Write

### Simular un IED propio
1. Seleccionar pestana Servidor
2. Click Cargar SCL -> seleccionar archivo .icd / .cid / .scd
3. Click Iniciar Servidor (puerto por defecto: 102)
4. Conectarse desde cualquier cliente MMS a 127.0.0.1:102

### Archivo de prueba incluido
    SIN SMV/CID/test_smoke.cid

---

## Estructura del proyecto

    src/main/java/com/iednavigator/
    IEDNavigatorApp.java       <- GUI principal
    IEC61850Client.java        <- Cliente MMS
    IEC61850Server.java        <- Servidor IED desde SCL
    GoosePublisher.java        <- Publicacion GOOSE
    GooseSubscriber.java       <- Suscripcion GOOSE
    GooseUdpBridge.java        <- Bridge GOOSE sobre UDP
    ConnectionManager.java     <- Gestion de conexion
    [+ clases auxiliares]
    native_lib/                <- Bindings JNA a iec61850.dll

    lib/                       <- Dependencias Java preempaquetadas
    SIN SMV/CID/test_smoke.cid <- ICD de prueba generico

---

## Dependencias

| Libreria        | Version | Licencia          |
|-----------------|---------|-------------------|
| iec61850bean    | 1.9.0   | Apache 2.0        |
| pcap4j          | 1.8.2   | MIT               |
| JNA             | 5.14.0  | LGPL 2.1          |
| FlatLaf         | 3.2     | Apache 2.0        |
| SLF4J           | 2.0.9   | MIT               |
| asn1bean        | 1.13.0  | Apache 2.0        |
| jasn1           | 1.11.3  | Apache 2.0        |
| ANTLR           | 2.7.7   | BSD               |
| libiec61850 *   | --      | GPL v3 (externo)  |
| Npcap *         | --      | Npcap (externo)   |

(*) No incluido. El usuario debe instalar por separado.

Ver THIRD_PARTY_LICENSES.md para los textos completos de cada licencia.

---

## Licencia

IEDNavigator se distribuye bajo la GNU General Public License v3 (GPL v3).

Puedes usar, modificar y redistribuir este software libremente, siempre que:
- Mantengas esta misma licencia en trabajos derivados
- Incluyas el codigo fuente en cualquier distribucion
- Conserves los avisos de copyright

Ver LICENSE para el texto completo.

Copyright (C) 2024 Emilio Medina

---

## Problemas conocidos

- GOOSE Layer 2 puede no funcionar en todas las interfaces de red en Windows
- Sampled Values (SMV) no tiene panel GUI en esta version

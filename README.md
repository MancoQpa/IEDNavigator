# IEC 61850 Explorer (Java)

Cliente y Servidor IEC 61850 usando **iec61850bean** 

## Requisitos

- Java 11 o superior
- Maven 3.6 o superior

## Compilacion

```bash
# Windows
build.bat

# O manualmente:
mvn clean package
```

## Ejecucion

```bash
# Windows
run.bat

# O manualmente:
java -jar target/iec61850-explorer-1.0.0-jar-with-dependencies.jar
```

## Caracteristicas

### Cliente
- Conexion/desconexion a IEDs
- Descubrimiento automatico del modelo
- Lectura de valores con formateo correcto:
  - **DoubleBitPos**: on/off/intermediate/bad
  - **Enums**: Mod, Beh, Health, ctlModel
  - **Quality**: Flags detallados
  - **Timestamps**: Formato legible
- Control de interruptores (Open/Close)
- Polling automatico

### Servidor
- Carga de archivos SCL (ICD/CID/SCD)
- Simulacion de IEDs
- Actualizacion de valores en tiempo real

### GUI
- Layout de 3 paneles 
- Arbol del modelo con colores de estado
- Data Monitor con historial
- Exportar a CSV


## Estructura

```
iec61850_java_explorer/
├── pom.xml                 # Configuracion Maven
├── build.bat               # Script de compilacion
├── run.bat                 # Script de ejecucion
├── README.md               # Esta documentacion
└── src/main/java/com/iedexplorer/
    ├── IEDExplorerApp.java # GUI principal
    ├── IEC61850Client.java # Cliente IEC 61850
    └── IEC61850Server.java # Servidor IEC 61850
```

## Uso

### Como Cliente
1. Ingresar IP y puerto del IED
2. Click "Connect"
3. Navegar el arbol y seleccionar nodos
4. Click "Read" o "Read All"
5. Usar polling para monitoreo continuo

### Como Servidor
1. File > Load SCL File (o Server > Start Server)
2. Seleccionar archivo .icd/.cid/.scd
3. Server > Start Server
4. Especificar puerto (default 102)
5. Conectarse con el cliente a 127.0.0.1

### Control de Interruptores
1. Seleccionar un XCBR, XSWI o similar
2. Click "Open" o "Close"
3. Confirmar la operacion

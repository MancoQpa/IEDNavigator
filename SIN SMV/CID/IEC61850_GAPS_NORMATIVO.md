# IEC 61850-6 vs Gaps IEDNavigator — Análisis Normativo

> Generado: 2026-04-18
> Fuente normativa: `IEC61850_full.md` (IEC 61850-6 Ed. 2.0, 44089 líneas)
> Basado en: análisis de 22 archivos SCL en `SIN SMV/CID/`

---

## Gap 1: FC=SP (Settings / Parameters)

### Definición normativa (IEC 61850-6 §9.5.4.1, §9.3.7)

FC=SP significa **Setting Parameter**: valores de ajuste configurables por el usuario (protecciones, umbrales de disparo, tiempos de retardo, límites de corriente). Definido en el esquema como valor de `tFCEnum`:

```xml
<xs:enumeration value="SP"/>
```

La Tabla 46 (valKind) del estándar (§9.5.4.4) dice:

> valKind="Set" con fc=SP: "A determined setting value. The value is/shall be set within the IED" — valor que se puede escribir en el IED en tiempo de operación.

### Estructura SCL

FC=SP aparece en los DataTypeTemplates como atributo `fc` de un `DA`. Ejemplo real del estándar:

```xml
<DataSet name="Example">
  <FCDA ldInst="C1" lnInst="1" lnClass="PVOC" doName="TmASt"
        fc="SP" daName="curvPts(2).xVal" ix="2"/>
</DataSet>
```

En DOTypes de protección (PVOC, PDIF, PSCH, etc.), los DAs con `fc="SP"` definen ajustes operativos:
corrientes de arranque (`setMag`), tiempos (`setTmms`), umbrales de tensión, etc.

### Servicio MMS para leer/escribir

- **Leer**: `GetDataValues` con `Fc.SP` → `association.getDataValues(fcModelNode)`
- **Escribir**: `SetDataValues` con `Fc.SP` → `association.setDataValues(fcModelNode)`

### Estado actual en IEDNavigator

`IEC61850Server.java` línea 381 ya incluye `Fc.SP` en el array del servidor.
El árbol del **cliente MMS** no muestra nodos SP de forma diferenciada. No existe panel dedicado.

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java` — método `readSettingParameters()`: filtra `Fc.SP` del serverModel.
2. `IEDExplorerApp.java` — pestaña "Ajustes SP": tabla editable [Referencia, Valor actual, Nuevo valor, Tipo] con botón "Aplicar" que llame a `association.setDataValues(node)`.
3. Marcar visualmente nodos SP en árbol (naranja).

---

## Gap 2: FC=SE / FC=SG (Setting Groups)

### Definición normativa (IEC 61850-6 §9.3.12, §9.5.4.4)

- **FC=SG** (Setting Group): valores visibles pero no editables directamente.
- **FC=SE** (Setting group Edit): subconjunto editable de SG; permite modificar valores del grupo en edición.

La norma dice:
> "fc=SE always also implies fc=SG; fc=SG means that the values are visible, but not editable"

Servicios asociados:
> "Setting group services: **SelectActiveSG** para activar un grupo; **SelectEditSG**, **ConfirmEditSGValues**, **SetSGValues** para edición online (decidido con el elemento **SGEdit**)."

### Estructura SCL — SettingControl (§9.3.12)

```xml
<!-- En LLN0: -->
<SettingControl numOfSGs="4" actSG="1" desc="Grupos de ajuste SIPROTEC5"/>
```

Los valores de DA con `fc="SG"` usan el atributo `sGroup` para indicar a qué grupo pertenece cada valor:
```xml
<xs:attribute name="sGroup" type="xs:unsignedInt" use="optional"/>
```

### Servicios MMS (IEC 61850-7-2)

| Servicio | Operación |
|----------|-----------|
| `GetSGCBValues` | Leer SGCB: numOfSGs, actSG, editSG |
| `SelectActiveSG(sgNum)` | Activar grupo de ajuste en operación |
| `SelectEditSG(sgNum)` | Seleccionar grupo para editar |
| `SetSGValues(sgNum, data)` | Escribir valores en el grupo en edición |
| `ConfirmEditSGValues` | Confirmar los cambios realizados |
| `GetDataValues(fc=SG)` | Leer todos los valores del grupo activo |

### Estado actual en IEDNavigator

Panel de Setting Groups (líneas ~6280-6315) detecta SE/SG/SP pero usa `"1"` hardcodeado.
No llama a `GetSGCBValues`. No implementa `SelectActiveSG`.

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java`: métodos `readSGCB()`, `selectActiveSG(int groupNum)`, `getSettingGroupValues(int sgNum)`.
2. `IEDExplorerApp.java`: leer `SettingControl` del LLN0 real, mostrar numOfSGs/actSG; JComboBox con grupos 1..N; botón "Activar grupo seleccionado".

---

## Gap 3: FC=DC (Descriptions / Descriptive Attributes)

### Definición normativa (IEC 61850-6 §9.5.4.1)

FC=DC significa **Description**: atributos puramente informativos, de sólo lectura en operación.

La Tabla 46 indica:
- `valKind="RO"` con `fc=DC/CF`: valor de sólo lectura en tiempo de configuración.

### Estructura SCL

```xml
<!-- En DOType del CDC LPL (Logical node name Plate): -->
<DA name="vendor"     fc="DC" bType="VisString255"><Val>Siemens AG</Val></DA>
<DA name="swRev"      fc="DC" bType="VisString255"><Val>V7.80</Val></DA>
<DA name="hwRev"      fc="DC" bType="VisString255"><Val>Rev 1.23</Val></DA>
<DA name="d"          fc="DC" bType="VisString255"/>   <!-- description text -->
<DA name="dU"         fc="DC" bType="Unicode255"/>     <!-- Unicode description -->
<DA name="configRev"  fc="DC" bType="VisString255"><Val>Rev 3.45</Val></DA>
```

CDC **LPL** (en LPHD/LLN0) contiene vendor, model, hwRev, swRev, serNum, configRev — todos FC=DC.
CDC **DPL** (Device Physical nameplate) tiene los datos del dispositivo físico.

### Servicio MMS

- **Leer**: `GetDataValues` con `Fc.DC` — `association.getDataValues(fcModelNode)`.
- De sólo lectura: no se escriben desde un cliente MMS.
- Referencias típicas: `IEDname/LLN0.NamPlt.vendor[DC]`, `IEDname/LPHD1.PhyNam.vendor[DC]`.

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java` — `readDeviceNameplate()`: busca `LLN0.NamPlt` y `LPHD1.PhyNam` con `Fc.DC`.
2. `IEDExplorerApp.java` — panel "Información del IED" al conectar: tabla vendor/model/hwRev/swRev/configRev.
3. Ícono "i" en árbol para nodos DC.

---

## Gap 4: FC=SV (Substitution / Sustitución supervisada)

### Definición normativa (IEC 61850-6 §9.5.4.1; IEC 61850-7-3 §6.2)

FC=SV (**Substituted Value**): permite a un operador reemplazar temporalmente un valor de proceso con un valor artificial para mantenimiento. Cuando `subEna=true`, el IED usa `subVal` y `subQ` como valor reportado.

La condición `PICS_SUBST` indica que es opcional según el PICS del IED:

```xml
<DA name="subEna"  bType="BOOLEAN"     fc="SV" cond="PICS_SUBST"/>
<DA name="subVal"  bType="Dbpos"       fc="SV" cond="PICS_SUBST"/>
<DA name="subQ"    bType="Quality"     fc="SV" cond="PICS_SUBST"/>
<DA name="subID"   bType="VisString64" fc="SV" cond="PICS_SUBST"/>
```

### Estructura SCL

```xml
<DOType cdc="DPC" id="DPC_switch">
  <DA name="stVal"  bType="Dbpos"    fc="ST" dchg="true"/>
  <DA name="q"      bType="Quality"  fc="ST" qchg="true"/>
  <!-- Sustitución (PICS_SUBST): -->
  <DA name="subEna" bType="BOOLEAN"     fc="SV" cond="PICS_SUBST"/>
  <DA name="subVal" bType="Dbpos"       fc="SV" cond="PICS_SUBST"/>
  <DA name="subQ"   bType="Quality"     fc="SV" cond="PICS_SUBST"/>
  <DA name="subID"  bType="VisString64" fc="SV" cond="PICS_SUBST"/>
</DOType>
```

### Servicio MMS

- **Activar**: Escribir `subVal`, `subQ`, luego `subEna=true` con `Fc.SV`.
- **Desactivar**: Escribir `subEna=false`.
- **Leer estado**: `GetDataValues` con `Fc.SV`.

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java`: `activateSubstitution(doRef, subValue, subId)`, `deactivateSubstitution(doRef)`.
2. `IEDExplorerApp.java`: menú contextual en nodo DO "Sustituir valor..."; diálogo valor+operadorID; nodos sustituidos con fondo amarillo.

---

## Gap 5: FC=BL (Blocking / Bloqueo de actualizaciones)

### Definición normativa (IEC 61850-6 §9.5.4.1; IEC 61850-7-3)

FC=BL (**Blocking**): congela el valor actual de un atributo, impidiendo que el IED lo actualice con nuevos valores del proceso. Condición `PICS_BLK`.

```xml
<xs:enumeration value="BL"/>
```

### Estructura SCL

```xml
<DOType cdc="MV" id="MV_measurement">
  <DA name="mag"    bType="Struct"  type="AnalogValue" fc="MX"/>
  <DA name="q"      bType="Quality" fc="MX" qchg="true"/>
  <DA name="blkEna" bType="BOOLEAN" fc="BL" cond="PICS_BLK"/>
</DOType>
```

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java`: `setBlocking(String doRef, boolean block)`.
2. `IEDExplorerApp.java`: menú contextual "Bloquear/Desbloquear actualización"; ícono de candado en nodos bloqueados.
3. Verificar PICS_BLK antes de intentar escribir.

---

## Gap 6: Communication Section — MAC, APPID, VLAN, MinTime/MaxTime

### Definición normativa (IEC 61850-6 §9.4)

La sección `Communication` define topología de red vía `SubNetwork > ConnectedAP`.

#### GSE (GOOSE) — §9.4.4

```xml
<ConnectedAP iedName="E1Q1SB1" apName="S1">
  <GSE ldInst="C1" cbName="Goose1">
    <Address>
      <P type="MAC-Address">01-0C-CD-01-00-01</P>
      <P type="APPID">3000</P>
      <P type="VLAN-PRIORITY">4</P>
      <P type="VLAN-ID">123</P>
    </Address>
    <MinTime unit="s" multiplier="m">4</MinTime>      <!-- ms: retardo primera repetición -->
    <MaxTime unit="s" multiplier="m">1000</MaxTime>   <!-- ms: heartbeat / supervisión -->
  </GSE>
</ConnectedAP>
```

#### SMV (Sampled Values) — §9.4.5

```xml
<SMV ldInst="C1" cbName="Volt">
  <Address>
    <P type="MAC-Address">01-0C-CD-04-00-01</P>
    <P type="APPID">4000</P>
    <P type="VLAN-ID">123</P>
    <P type="VLAN-PRIORITY">4</P>
  </Address>
</SMV>
```

P-types válidos: `MAC-Address`, `APPID`, `VLAN-PRIORITY`, `VLAN-ID`.

**Importante**: estos datos provienen **exclusivamente del SCL** — no se leen por MMS.

### Implementación concreta en IEDNavigator

1. Al cargar CID/SCD, parsear `<Communication>`:
   iterar `SubNetwork > ConnectedAP > GSE` y `ConnectedAP > SMV`.
2. Pre-poblar campos del publisher GOOSE (MAC dest, APPID, VLAN) con valores del SCL.
3. Columnas adicionales en tabla GoCBs: MAC Dest, APPID, VLAN-ID, MinTime, MaxTime.
4. El `SclModel` de iec61850bean expone `getCommunication()` → `SubNetwork` → `ConnectedAP`.

---

## Gap 7: LGOS / LSVS — Nodos de suscripción GOOSE/SMV

### Definición normativa (IEC 61850-6 §9.3.13 — ExtRef / Inputs)

En IEC 61850-6, la suscripción se modela mediante:

**1) IEDName en GSEControl** (§9.3.10):
```xml
<GSEControl name="GoCB1" appID="0x3000" datSet="GooseDS">
  <IEDName apRef="S1" ldInst="C1" lnClass="LGOS">IED_Subscriber1</IEDName>
</GSEControl>
```

**2) ExtRef en Inputs** (§9.3.13): suscripción desde el lado receptor:
```xml
<LN lnClass="LGOS" inst="1" lnType="LGOS_type">
  <Inputs>
    <ExtRef iedName="IED_Publisher" ldInst="C1" lnClass="LLN0"
            doName="GooseCB1" serviceType="GOOSE"
            srcLDInst="C1" srcLNClass="LLN0" srcCBName="GoCB1"/>
  </Inputs>
</LN>
```

Atributos clave de ExtRef: `iedName`, `ldInst`, `lnClass`, `doName`, `serviceType` (Poll|Report|GOOSE|SMV),
`srcLDInst`, `srcLNClass`, `srcCBName`.

**Nota**: LGOS/LSVS son clases de IEC 61850-7-4. En SCL aparecen como `LN lnClass="LGOS"` con sección `Inputs`.

### Implementación concreta en IEDNavigator

1. Al parsear SCL, extraer todos los `ExtRef` con `serviceType="GOOSE"` y `serviceType="SMV"`.
2. Construir mapa: `{IED suscriptor → lista de (IED fuente, cbName, dato)}`.
3. Panel "Dependencias GOOSE/SMV" con vistas:
   - Por publicador: qué IEDs suscriben mis GOOSE.
   - Por suscriptor: de qué GOOSE/SMV externos dependo.
4. Información puramente SCL — no requiere servicios MMS adicionales.

---

## Gap 8: Dataset Browser — Contenido FCDA de cada DataSet remoto

### Definición normativa (IEC 61850-6 §9.3.7; IEC 61850-7-2 `GetDataSetDirectory`)

El `DataSet` (§9.3.7) contiene una secuencia de `FCDA`. Tabla 22 define sus atributos:

```xml
<DataSet name="Positions">
  <FCDA ldInst="C1" prefix="" lnInst="1" lnClass="CSWI" doName="Pos" fc="ST"/>
  <FCDA ldInst="C1" prefix="" lnInst="2" lnClass="CSWI" doName="Pos" fc="ST"/>
  <FCDA ldInst="C1" prefix="" lnInst="1" lnClass="MMXU" doName="A"   fc="MX"/>
</DataSet>
```

Servicios MMS:
- `GetDataSetDirectory`: devuelve lista de FCD/FCDA de todos los miembros.
- `GetDataSetValues`: lee todos los valores en una sola operación.
- `SetDataSetValues`: escribe en bloque (si soportado).

Capacidades del IED (en Services):
```xml
<GetDataSetValue/>
<SetDataSetValue/>
<DataSetDirectory/>
```

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java`:
   - `getDataSetDirectory(String dsRef)` → `association.getDataSetDirectory(dsRef)`.
   - `getDataSetValues(String dsRef)` → `association.getDataSetValues(dataSet)`.
2. `IEDExplorerApp.java` — pestaña "DataSets":
   - Árbol izquierdo: todos los DataSets (agrupados por LN).
   - Panel derecho: tabla [ldInst, lnClass, lnInst, doName, daName, FC, Valor actual].
   - Botón "Leer todos los valores" (GetDataSetValues en lote).
   - Icono del tipo de control block que usa cada DataSet (RCB, GOOSE, Log).

---

## Gap 9: SettingControl — numOfSGs y SelectActiveSettingGroup

### Definición normativa (IEC 61850-6 §9.3.12)

El **SGCB** es el único control block definido en §9.3.12. Nombre fijo: `SGCB` (uno por LN0).

> "Note that the SGCB name, i.e. its name part within the LN0, is SGCB according to IEC 61850-7-2. Therefore, only one SGCB is allowed per LN0."

Tabla 32 — atributos:

| Atributo | Tipo | Descripción |
|----------|------|-------------|
| `numOfSGs` | xs:unsignedInt (≥1) | Número de grupos de ajuste disponibles |
| `actSG` | xs:unsignedInt (default=1) | Grupo activo al cargar configuración |
| `desc` | string | Descripción opcional |

Esquema XML:
```xml
<xs:complexType name="tSettingControl">
  <xs:attribute name="numOfSGs" use="required">
    <xs:restriction base="xs:unsignedInt">
      <xs:minInclusive value="1"/>
    </xs:restriction>
  </xs:attribute>
  <xs:attribute name="actSG" use="optional" default="1">
    <xs:restriction base="xs:unsignedInt">
      <xs:minInclusive value="1"/>
    </xs:restriction>
  </xs:attribute>
</xs:complexType>
```

### Servicios MMS (IEC 61850-7-2)

| Servicio MMS | Operación |
|-------------|-----------|
| `GetSGCBValues` | Leer actSG, numOfSGs, ResvTms, EditSG, ConfirmEdit |
| `SelectActiveSG(sgNum)` | Cambiar grupo activo — afecta protección en tiempo real |
| `SelectEditSG(sgNum)` | Abrir sesión de edición sobre un grupo |
| `SetSGValues(DA, value)` | Modificar valores del grupo en edición |
| `ConfirmEditSGValues` | Confirmar y aplicar cambios |

En iec61850bean: clase `SgControlBlock`, método `association.getSgCbValues(sgcb)`.

### Estado actual en IEDNavigator

Panel de Setting Groups (líneas ~6280-6315): valores `"1"` hardcodeados para actSG/numOfSGs.
No llama a `GetSGCBValues`. No implementa `SelectActiveSG`.

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java`:
   ```java
   public SgControlBlock getSGCB(String ldName) { ... }
   public void selectActiveSG(SgControlBlock sgcb, int groupNum) throws IOException {
       association.selectActiveSgValues(sgcb, groupNum);
   }
   ```
2. `IEDExplorerApp.java`:
   - Al conectar, leer `GetSGCBValues` → mostrar numOfSGs/actSG.
   - JComboBox "Grupo activo (1..N)" + botón "Seleccionar".
   - Advertencia: "Cambiar grupo activo afecta la protección".
   - Tabla con columna "Grupo" (sGroup) para ver valores por grupo.

---

## Gap 10: Enumeraciones extendidas — SIUnit y otros enums sin decodificar

### Definición normativa (IEC 61850-6 §9.5.6; §9.5.4.2)

Las enumeraciones se definen con `EnumType` (§9.5.6). Cada `EnumVal` tiene:
- `ord`: número ordinal — **el valor que viaja en MMS** como entero.
- Texto: cadena legible.

#### El problema de SIUnit

En IEC 61850-7-3, la unidad física se transporta como **entero** (código de unidad), no como string.
El código `29 = V (Voltios)`, `4 = A`, `33 = Hz`, etc.

#### Tabla de códigos SIUnit (IEC 61850-7-3)

| Código | Unidad | Código | Unidad |
|--------|--------|--------|--------|
| 0 | none | 29 | **V** |
| 4 | **A** | 30 | Ω (ohm) |
| 8 | K | 31 | J |
| 23 | °C | 32 | N |
| 25 | F | **33** | **Hz** |
| 26 | C | 38 | T |
| 28 | H | **61** | **VA** |
| **62** | **W** | **63** | **VAr** |
| **72** | **Wh** | **73** | **VAh** |
| **74** | **VArh** | 65 | cos(φ) |

#### Enumeraciones canónicas del estándar

| EnumType | Valores |
|----------|---------|
| `ctlModel` | 0=status-only, 1=direct-normal, 2=sbo-normal, 3=direct-enhanced, 4=sbo-enhanced |
| `Health` | 1=Ok, 2=Warning, 3=Alarm |
| `Mod`/`Beh` | 1=on, 2=blocked, 3=test, 4=test/blocked, 5=off |
| `range` | 0=normal, 1=high, 2=low, 3=high-high, 4=low-low |
| `dir` | 0=unknown, 1=forward, 2=backward, 3=both |
| `orCategory` | 0=not-supported, 1=bay-control, ... 8=process |
| `Dbpos` | 0=intermediate-state, 1=off, 2=on, 3=bad-state |
| `AutoRecSt` | 1=Ready, 2=InProgress, 3=Successful |

### Estado actual en IEDNavigator

`IEC61850Client.formatValue()` usa `bda.getValueString()` para todo → devuelve entero raw para BdaEnum/BdaInt8U. No existe mapa de decodificación.

### Implementación concreta en IEDNavigator

1. `IEC61850Client.java` — mapa estático `SI_UNIT_MAP`:
   ```java
   private static final Map<Integer, String> SI_UNIT_MAP = new HashMap<>();
   static {
       SI_UNIT_MAP.put(29, "V");    SI_UNIT_MAP.put(4, "A");
       SI_UNIT_MAP.put(33, "Hz");   SI_UNIT_MAP.put(62, "W");
       SI_UNIT_MAP.put(63, "VAr");  SI_UNIT_MAP.put(61, "VA");
       SI_UNIT_MAP.put(72, "Wh");   SI_UNIT_MAP.put(73, "VAh");
       SI_UNIT_MAP.put(74, "VArh"); SI_UNIT_MAP.put(23, "°C");
       // ... tabla completa
   }
   ```
2. Modificar `formatValue()`: cuando nombre del DA sea `unit`, usar `SI_UNIT_MAP.get(intValue)`.
3. Para BdaEnum con tipo conocido (ctlModel, Health, Mod): mapas estáticos con valores normativos.
4. Para enums del SCL: al cargar modelo, construir mapa dinámico desde `EnumType` del DataTypeTemplates.

---

## Conclusión: Mapa de prioridades

| # | Gap | Descripción | Complejidad | Impacto | Sección IEC 61850-6 | Prioridad |
|---|-----|-------------|-------------|---------|---------------------|-----------|
| **10** | Enumeraciones SIUnit | "29"→"V" en unidades | Baja | Alto | §9.5.6 | **1 — Inmediato** |
| **3** | FC=DC (nameplate) | vendor/swRev/model | Baja | Alto | §9.5.4.1 | **2 — Corto plazo** |
| **8** | DataSet Browser | FCDA + GetDataSetValues | Media | Alto | §9.3.7 | **3 — Corto plazo** |
| **1** | FC=SP (settings) | ajustes de protección | Media | Alto | §9.5.4.4 | **4 — Medio plazo** |
| **6** | Communication (MAC/VLAN) | configuración GOOSE auto | Media | Alto | §9.4.4-9.4.5 | **5 — Medio plazo** |
| **9** | SettingControl | numOfSGs/SelectActiveSG | Media | Muy alto | §9.3.12 | **6 — Medio plazo** |
| **2** | FC=SE/SG (grupos ajuste) | hasta 8 grupos SIPROTEC5 | Alta | Muy alto | §9.3.12, §9.5.4.4 | **7 — Largo plazo** |
| **7** | LGOS/LSVS (dependencias) | mapa suscripciones GOOSE | Media | Medio | §9.3.13 ExtRef | **8 — Largo plazo** |
| **4** | FC=SV (sustitución) | mantenimiento en caliente | Media | Medio | §9.5.4.1 | **9 — Largo plazo** |
| **5** | FC=BL (bloqueo) | congela actualizaciones | Baja | Bajo | §9.5.4.1 | **10 — Bajo impacto** |

### Observaciones finales

**Gap 10 (enumeraciones)** — mayor ROI: una tabla estática de ~50 entradas elimina la ilegibilidad de unidades físicas y estados de control. Afecta todos los IEDs.

**Gap 3 (FC=DC)** y **Gap 8 (DataSet browser)** — lectura pura: sin riesgo operativo, alta visibilidad para el usuario.

**Gap 9 (SettingControl / SelectActiveSG)** — mayor impacto en relés modernos (Siemens SIPROTEC5, SEL, ABB REL). **Requiere confirmación** porque cambia el comportamiento de la protección en tiempo real.

**Gap 6 (Communication section)** — valor especial para GOOSE Publisher: el SCL pre-llenaría MAC, APPID y VLAN automáticamente en vez de entrada manual.

**Gap 7 (LGOS/LSVS)** — puramente SCL, sin servicios MMS: cero riesgo, implementación más fácil de lo que parece.

---

## Archivos de código relevantes

| Archivo | Líneas clave | Relación con gaps |
|---------|-------------|-------------------|
| `IEC61850Client.java` | `formatValue()` ~302, `readValue()` ~249, `writeValue()` ~369 | Gaps 1, 4, 5, 10 |
| `IEDExplorerApp.java` | `findSettingGroups()` ~6297, `showSettingGroupValues()` ~6317 | Gaps 2, 9 |
| `IEC61850Server.java` | array FCs ~381 | Gap 1 (servidor) |
| `IEC61850_full.md` | §9.3.7, §9.3.12, §9.3.13, §9.4.4, §9.4.5, §9.5.4, §9.5.6 | Norma base |

---

*Fin del análisis normativo — IEC 61850-6 Ed. 2.0 vs IEDNavigator v3.0-edu*

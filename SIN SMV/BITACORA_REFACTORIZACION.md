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

## FASE 3 — Extracción de ModelTreeBuilder
> (pendiente — comenzar tras verificar Fase 2)

---

## FASE 4 — Extracción de Paneles
> (pendiente — comenzar tras verificar Fase 3)

---

## Registro de compilaciones

| Fecha | Fase | Resultado | Notas |
|-------|------|-----------|-------|
| 2026-04-22 | Base (antes de F0) | ✅ OK | Estado inicial de referencia |
| 2026-04-22 | F0-001 | ⏳ por verificar | — |
| 2026-04-22 | F1-001 a F1-006 | ✅ OK — 84 clases generadas | Eliminación de 6 clases internas de IEDExplorerApp.java |

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

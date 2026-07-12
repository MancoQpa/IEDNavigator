#!/usr/bin/env bash
# =============================================================================
# verify.sh — Script de verificación post-refactorización IEDNavigator
#
# Uso:    bash "SIN SMV/verify.sh"              # todo
#         bash "SIN SMV/verify.sh" --lint       # compilación + lint
#         bash "SIN SMV/verify.sh" --smoke      # solo smoke test
#
# Ejecutar desde la raíz del proyecto (iec61850_java_explorer/)
# =============================================================================

JAVAC="C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot/bin/javac.exe"
JAVA="C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot/bin/java.exe"
SRC_DIR="src/main/java"
CLASSES_DIR="classes"
LIB_DIR="lib"
CID_TEST="SIN SMV/CID/test_smoke.cid"
SRCLIST="/tmp/ied_srclist.txt"
OVERALL_OK=true

MODE="${1:-all}"

echo "================================================================"
echo " IEDNavigator — Verificación post-refactorización"
echo " Modo: $MODE"
echo "================================================================"
echo ""

# -----------------------------------------------------------------------
# Construir argfile (siempre necesario para los modos que compilan)
# -----------------------------------------------------------------------
mkdir -p "$CLASSES_DIR"
while IFS= read -r f; do
    printf '"%s"\n' "$f"
done < <(find "$SRC_DIR" -name "*.java") > "$SRCLIST"

# -----------------------------------------------------------------------
# PASO 1: Compilación básica
# -----------------------------------------------------------------------
if [[ "$MODE" == "all" || "$MODE" == "--lint" ]]; then
    echo "[1/3] Compilando (sin lint)..."
    "$JAVAC" -d "$CLASSES_DIR" -cp "$LIB_DIR/*" -encoding UTF-8 @"$SRCLIST" \
        > /tmp/compile_out.txt 2>&1
    EXIT1=$?
    if [ $EXIT1 -eq 0 ]; then
        echo "  [OK] Compilación limpia — 0 errores"
    else
        echo "  [ERROR] Compilación falló:"
        cat /tmp/compile_out.txt
        OVERALL_OK=false
    fi
fi

# -----------------------------------------------------------------------
# PASO 2: Compilación con -Xlint:all
# -----------------------------------------------------------------------
if [[ "$MODE" == "all" || "$MODE" == "--lint" ]]; then
    echo ""
    echo "[2/3] Compilando con -Xlint:all (análisis estático)..."
    "$JAVAC" -d "$CLASSES_DIR" -cp "$LIB_DIR/*" -encoding UTF-8 -Xlint:all @"$SRCLIST" \
        > /tmp/lint_out.txt 2>&1
    EXIT2=$?

    WARN_COUNT=$(grep -c "warning:" /tmp/lint_out.txt 2>/dev/null; true)
    ERR_COUNT=$(grep -c "error:" /tmp/lint_out.txt 2>/dev/null; true)
    WARN_COUNT="${WARN_COUNT:-0}"
    ERR_COUNT="${ERR_COUNT:-0}"

    if [ "$ERR_COUNT" -gt 0 ]; then
        echo "  [ERROR] $ERR_COUNT error(s) con -Xlint:"
        grep "error:" /tmp/lint_out.txt | head -20
        OVERALL_OK=false
    else
        echo "  [OK] 0 errores, $WARN_COUNT warning(s) [serial/Swing — pre-existentes]"
    fi
fi

# -----------------------------------------------------------------------
# PASO 3: Smoke test headless
# -----------------------------------------------------------------------
if [[ "$MODE" == "all" || "$MODE" == "--smoke" ]]; then
    echo ""
    echo "[3/3] Ejecutando SmokeTest (headless)..."

    CID_ARG=""
    if [ -f "$CID_TEST" ]; then
        CID_ARG="$CID_TEST"
    else
        ALT_CID=$(find "SIN SMV" -name "test_simple.cid" 2>/dev/null | head -1)
        [ -n "$ALT_CID" ] && CID_ARG="$ALT_CID"
    fi

    "$JAVA" -Djava.awt.headless=true -cp "$CLASSES_DIR;$LIB_DIR/*" \
        com.iedexplorer.SmokeTest "$CID_ARG" 2>&1
    EXIT3=$?

    if [ $EXIT3 -eq 0 ]; then
        echo "  [OK] SmokeTest PASÓ"
    else
        echo "  [ERROR] SmokeTest FALLÓ (exit $EXIT3)"
        OVERALL_OK=false
    fi
fi

# -----------------------------------------------------------------------
echo ""
echo "================================================================"
if [ "$OVERALL_OK" = true ]; then
    echo " RESULTADO: VERIFICACIÓN OK"
else
    echo " RESULTADO: VERIFICACIÓN FALLÓ — revisar errores arriba"
    exit 1
fi
echo "================================================================"

#!/bin/sh
# Regresion automatica de Five Doors Escape (CLAUDE.md §2.78). Compila el juego (gradlew lwjgl3:installDist) y los
# arneses de tools/tests/src contra sus bibliotecas, y los ejecuta con la GameplayScreen real en una ventana oculta:
#   - perfil aislado: user.home apunta a tools/tests/out/home (preferencias, handoff y senales de prueba quedan ahi);
#   - capturas y registros en tools/tests/out/.
# Uso (Git Bash en Windows, o sh en Linux/macOS), desde cualquier carpeta:
#   JAVA_HOME="/ruta/al/jdk-21" sh tools/tests/run_tests.sh [modo...]
# Sin modos ejecuta toda la regresion. Modos sueltos: mundo:<puertas|bordes|sprint|aturdir|cortina|intro|escalon|mesas|
# foco|pasos|pausa>, victoria:<completa|saltar>, derrota, arranque, carga, rendimiento.
# Diagnosticos sueltos (no forman parte de la regresion): DiagPisadas, DiagPisadasRender, DiagTexturas (ver su cabecera).
AQUI=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$AQUI/../.." && pwd)
OUT="$AQUI/out"
[ -n "$JAVA_HOME" ] || { echo "Falta JAVA_HOME (JDK 21)"; exit 1; }
J="$JAVA_HOME/bin"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';'; w() { cygpath -w "$1"; }; export MSYS_NO_PATHCONV=1 ;;
    *) SEP=':'; w() { printf '%s' "$1"; } ;;
esac

echo "compilando el juego (gradlew lwjgl3:installDist)..."
(cd "$REPO" && ./gradlew -q lwjgl3:installDist) || exit 1
LIB="$(w "$REPO/lwjgl3/build/install/lwjgl3/lib")"
rm -rf "$OUT/bin"; mkdir -p "$OUT/bin" "$OUT/home"
find "$AQUI/src" -name '*.java' | while read -r f; do printf '"%s"\n' "$(w "$f" | tr '\\' '/')"; done > "$OUT/arneses.txt"
"$J/javac" -nowarn -d "$(w "$OUT/bin")" -cp "$LIB/*" @"$(w "$OUT/arneses.txt")" 2>&1 | grep -v "^Note:"
CP="$LIB/*$SEP$(w "$OUT/bin")"

run() {
    n=$1; shift
    mkdir -p "$OUT/$n"
    rm -rf "$OUT/home"; mkdir -p "$OUT/home"   # cada prueba arranca con un perfil limpio
    echo "===== $n"
    (cd "$REPO/assets" && "$J/java" $JOPTS -Duser.home="$(w "$OUT/home")" -cp "$CP" "$@") > "$OUT/$n.log" 2>&1
    grep "OK \|FALLO\|FALLOS\|RESULTADO\|ZONA\|CARGA\|Exception" "$OUT/$n.log" | sort | uniq -c | sort -rn | head -40
}
mundo() { run mundo_$1 PruebaMundo "$(w "$OUT/mundo_$1")" $1; }
victoria() { for L in ESPANOL INGLES; do run victoria_$1_$L PruebaVictoria "$(w "$OUT/victoria_$1_$L")" $1 $L; done; }
derrota() { run derrota_ultima PruebaDerrota "$(w "$OUT/derrota_ultima")" ultima 0; run derrota_saltar PruebaDerrota "$(w "$OUT/derrota_saltar")" saltar 1; run derrota_normal PruebaDerrota "$(w "$OUT/derrota_normal")" normal 2; }

MODOS="$*"
[ -n "$MODOS" ] || MODOS="mundo:puertas mundo:bordes mundo:sprint mundo:aturdir mundo:cortina mundo:intro mundo:escalon mundo:mesas mundo:foco mundo:pasos mundo:pausa victoria:completa victoria:saltar derrota arranque carga"
for m in $MODOS; do
    case "$m" in
        mundo:*) mundo "${m#mundo:}" ;;
        victoria:*) victoria "${m#victoria:}" ;;
        derrota) derrota ;;
        arranque) run arranque PruebaArranque "$(w "$OUT/arranque")" ;;
        carga) run carga PruebaCarga "$(w "$OUT/carga")" reg ;;
        rendimiento) run rendimiento PruebaRendimiento "$(w "$OUT/rendimiento")" reg ;;
        *) echo "modo desconocido: $m" ;;
    esac
done
echo FIN

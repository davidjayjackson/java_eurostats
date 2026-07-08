#!/usr/bin/env bash
# Builds build/EurostatAddin.oxt using only the LibreOffice SDK's own toolchain
# (unoidl-write, javamaker, javac, jar, zip) -- no Maven, no external libraries.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

LO_HOME=/home/davidj/libreoffice26.2
SDK_BIN="$LO_HOME/sdk/bin"
PROGRAM="$LO_HOME/program"
UNO_CLASSES="$PROGRAM/classes"
JDK=/home/davidj/jdks/jdk8u492-b09
JAVAC="$JDK/bin/javac"
JAR="$JDK/bin/jar"

URE_TYPES="$PROGRAM/types.rdb"
OFFICE_TYPES="$PROGRAM/types/offapi.rdb"

BUILD="$REPO/build"
GEN="$BUILD/gen"
CLASSES_OUT="$BUILD/classes"
STAGE="$BUILD/oxt-stage"

IDL_FILE="$REPO/idl/org/libreoffice/eurostat/addin/XEurostatAddin.idl"
IDL_TYPE="org.libreoffice.eurostat.addin.XEurostatAddin"

echo "== Cleaning build directory =="
rm -rf "$BUILD"
mkdir -p "$GEN" "$CLASSES_OUT" "$STAGE/META-INF"

echo "== 1/6: unoidl-write (compile custom IDL to rdb) =="
"$SDK_BIN/unoidl-write" "$URE_TYPES" "$OFFICE_TYPES" "$IDL_FILE" "$GEN/EurostatAddin.uno.rdb"

echo "== 2/6: javamaker (generate Java stub for XEurostatAddin) =="
"$SDK_BIN/javamaker" -nD -T"$IDL_TYPE" -O"$CLASSES_OUT" "$GEN/EurostatAddin.uno.rdb" \
    -X"$URE_TYPES" -X"$OFFICE_TYPES"

echo "== 3/6: javac (compile our sources against the UNO jars + generated stub) =="
UNO_CP="$UNO_CLASSES/ridl.jar:$UNO_CLASSES/jurt.jar:$UNO_CLASSES/juh.jar:$UNO_CLASSES/unoil.jar:$UNO_CLASSES/java_uno.jar"
mapfile -t SOURCES < <(find "$REPO/src/main/java" -name '*.java')
"$JAVAC" -nowarn -classpath "$UNO_CP:$CLASSES_OUT" -d "$CLASSES_OUT" "${SOURCES[@]}"

echo "== 4/6: jar (pack EurostatAddin.uno.jar with UNO-Type-Path manifest) =="
MANIFEST="$GEN/EurostatAddin.uno.Manifest"
printf 'UNO-Type-Path: EurostatAddin.uno.jar\nRegistrationClassName: org.libreoffice.eurostat.addin.EurostatAddin\n' > "$MANIFEST"
"$JAR" cfm "$BUILD/EurostatAddin.uno.jar" "$MANIFEST" -C "$CLASSES_OUT" .

echo "== 5/6: assemble .oxt staging directory =="
cat > "$STAGE/META-INF/manifest.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE manifest:manifest PUBLIC "-//OpenOffice.org//DTD Manifest 1.0//EN" "Manifest.dtd">
<manifest:manifest xmlns:manifest="http://openoffice.org/2001/manifest">
 <manifest:file-entry manifest:media-type="application/vnd.sun.star.uno-typelibrary;type=RDB"
                       manifest:full-path="EurostatAddin.uno.rdb"/>
 <manifest:file-entry manifest:media-type="application/vnd.sun.star.uno-components"
                       manifest:full-path="EurostatAddin.components"/>
 <manifest:file-entry manifest:media-type="application/vnd.sun.star.configuration-data"
                       manifest:full-path="CalcAddIns.xcu"/>
</manifest:manifest>
EOF
cp "$REPO/packaging/description.xml" "$STAGE/description.xml"
cp "$REPO/packaging/EurostatAddin.components" "$STAGE/EurostatAddin.components"
cp "$REPO/packaging/CalcAddIns.xcu" "$STAGE/CalcAddIns.xcu"
cp "$GEN/EurostatAddin.uno.rdb" "$STAGE/EurostatAddin.uno.rdb"
cp "$BUILD/EurostatAddin.uno.jar" "$STAGE/EurostatAddin.uno.jar"

echo "== 6/6: zip .oxt =="
rm -f "$BUILD/EurostatAddin.oxt"
(cd "$STAGE" && zip -rq -X "$BUILD/EurostatAddin.oxt" .)

echo "Built $BUILD/EurostatAddin.oxt"

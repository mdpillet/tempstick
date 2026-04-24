#!/bin/bash
set -euo pipefail

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-34/android.jar
AAPT2=/usr/lib/android-sdk/build-tools/29.0.3/aapt2
APKSIGNER=/usr/lib/android-sdk/build-tools/29.0.3/apksigner
ZIPALIGN=/usr/lib/android-sdk/build-tools/29.0.3/zipalign
KOTLINC=/opt/kotlinc/bin/kotlinc
R8_JAR=/tmp/r8.jar
APP_SRC=/home/user/tempstick/app/src/main
BUILD=/home/user/tempstick/build/manual

rm -rf "$BUILD"
mkdir -p "$BUILD"/{gen,classes,dex,apk}

echo "=== Step 1: Compile resources ==="
"$AAPT2" compile --dir "$APP_SRC/res" -o "$BUILD/compiled.zip"

echo "=== Step 2: Link resources + generate R.java ==="
mkdir -p "$BUILD/gen"
"$AAPT2" link \
  -I "$ANDROID_JAR" \
  --manifest "$APP_SRC/AndroidManifest.xml" \
  "$BUILD/compiled.zip" \
  -o "$BUILD/apk/resources.ap_" \
  --java "$BUILD/gen/"

echo "=== Step 3: Compile R.java ==="
find "$BUILD/gen" -name "*.java" | xargs javac --release 8 -cp "$ANDROID_JAR" -d "$BUILD/classes"

echo "=== Step 4: Compile Kotlin sources ==="
KT_FILES=$(find "$APP_SRC/java" -name "*.kt" | tr '\n' ' ')
"$KOTLINC" \
  -cp "$ANDROID_JAR:$BUILD/classes" \
  -jvm-target 1.8 \
  $KT_FILES \
  -include-runtime \
  -d "$BUILD/app.jar"

echo "=== Step 5: Create R jar ==="
cd "$BUILD/classes" && jar cf "$BUILD/r.jar" . && cd -

echo "=== Step 6: Dex ==="
java -cp "$R8_JAR" com.android.tools.r8.D8 \
  --lib "$ANDROID_JAR" \
  --min-api 23 \
  --output "$BUILD/dex/" \
  "$BUILD/app.jar" \
  "$BUILD/r.jar"

echo "=== Step 7: Package APK ==="
cp "$BUILD/apk/resources.ap_" "$BUILD/apk/app-unaligned.apk"
cd "$BUILD/apk" && zip -j app-unaligned.apk "$BUILD/dex/classes.dex" && cd -

echo "=== Step 8: Zipalign ==="
"$ZIPALIGN" -f -v 4 "$BUILD/apk/app-unaligned.apk" "$BUILD/apk/app-unsigned.apk"

echo "=== Step 9: Generate debug key ==="
if [ ! -f "$BUILD/debug.jks" ]; then
  keytool -genkey -v \
    -keystore "$BUILD/debug.jks" \
    -alias debug -keyalg RSA -keysize 2048 -validity 365 \
    -storepass android -keypass android \
    -dname "CN=TempStick Widget Debug" -noprompt 2>&1
fi

echo "=== Step 10: Sign APK ==="
"$APKSIGNER" sign \
  --ks "$BUILD/debug.jks" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$BUILD/apk/tempstick-widget.apk" \
  "$BUILD/apk/app-unsigned.apk"

echo ""
echo "=== BUILD SUCCESSFUL ==="
echo "APK: $BUILD/apk/tempstick-widget.apk"
ls -lh "$BUILD/apk/tempstick-widget.apk"

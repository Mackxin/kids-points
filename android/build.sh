#!/bin/bash
# 一键构建积分银行 APK
#   用法：./build.sh
#   产物：app/build/outputs/apk/debug/app-debug.apk
set -e
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

# 优先用本机已解压的 Gradle 8.9（离线可用，不必联网下载 wrapper 包）
GRADLE_BIN=$(ls -d "$HOME/.gradle/wrapper/dists/gradle-8.9-bin/"*/gradle-8.9/bin/gradle 2>/dev/null | head -1)

if [ -n "$GRADLE_BIN" ]; then
  echo "使用本地 Gradle: $GRADLE_BIN"
  "$GRADLE_BIN" :app:assembleDebug --offline
else
  echo "使用 wrapper（首次会下载 Gradle 8.7）"
  ./gradlew :app:assembleDebug
fi

echo ""
echo "✅ APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"

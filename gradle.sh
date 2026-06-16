#!/bin/bash
# Gradle 9 Wrapper Helper Script for ICEpdf
# - Ensures JAVA_HOME is set to Java 17+ before running Gradle (Gradle 9 requirement).
# - Lets you pass JVM args (-X..., -D...) on the command line and forwards them to the
#   application JVM launched by run/test (JavaExec) tasks. Gradle itself rejects bare -X
#   options, so this script splits them out instead. Example:
#     ./gradle.sh :qa:viewer-jfx:run -Dsun.awt.disablegrab=true -Xmx4g \
#         -Dorg.icepdf.core.font.hinting=true -Dorg.apache.pdfbox.rendering.hinting=true

# Check if Java 17+ is available
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo "⚠️  Current Java version is $JAVA_VERSION, but Gradle 9 requires Java 17+"
        echo "Setting JAVA_HOME to Java 17..."

        # Try to find Java 17 or 21
        if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
            export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
            echo "✅ Using Java 17: $JAVA_HOME"
        elif [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
            export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
            echo "✅ Using Java 21: $JAVA_HOME"
        else
            echo "❌ ERROR: Java 17+ not found. Please install it:"
            echo "   sudo apt install openjdk-17-jdk"
            exit 1
        fi
    fi
fi

# Split JVM args (-X*, -D*, assertion/agent flags) from gradle args. The JVM args are meant for the
# launched application, not for gradle; -Dorg.gradle.* are kept as gradle options.
GRADLE_ARGS=()
JVM_ARGS=()
for arg in "$@"; do
    case "$arg" in
        -Dorg.gradle.*)
            GRADLE_ARGS+=("$arg")
            ;;
        -X*|-D*|-ea|-da|-enableassertions|-disableassertions|-agentlib:*|-javaagent:*|-verbose:*)
            JVM_ARGS+=("$arg")
            ;;
        *)
            GRADLE_ARGS+=("$arg")
            ;;
    esac
done

# Forward collected JVM args to the application JVM via JAVA_TOOL_OPTIONS, which every JVM that gradle
# forks for run/test honours. Use --no-daemon so a stale daemon's environment can't shadow them.
if [ ${#JVM_ARGS[@]} -gt 0 ]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }${JVM_ARGS[*]}"
    echo "➡️  Forwarding JVM args to application: ${JVM_ARGS[*]}"
    GRADLE_ARGS=("--no-daemon" "${GRADLE_ARGS[@]}")
fi

# Run gradlew with the remaining (gradle) arguments
./gradlew "${GRADLE_ARGS[@]}"


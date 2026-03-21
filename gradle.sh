#!/bin/bash
# Gradle 9 Wrapper Helper Script for ICEpdf
# This script ensures JAVA_HOME is set to Java 17+ before running Gradle

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

# Run gradlew with all arguments
./gradlew "$@"


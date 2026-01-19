#!/bin/bash

# 1. ESTABLISH CONTEXT
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 2. CONFIGURATION
# Update these variables if you upgrade the jar or change memory settings
JAR_FILE="./lib/emulinker-k-0.15.0.jar"
MAIN_CLASS="org.emulinker.kaillera.pico.ServerMainKt"
LOG_FILE="emulinker.log"
SEARCH_TERM="EmuLinker-K version"
JAVA_OPTS="-Xms64m -Xmx256m -XX:+UseSerialGC -XX:+AlwaysPreTouch"
CLASSPATH="./conf:$JAR_FILE"

# 3. PRE-FLIGHT CHECKS

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not found in your PATH."
    echo "   Please install a Java Runtime Environment (JRE) to continue."
    exit 1
fi

# Verify the jar file actually exists
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ Error: Jar file not found at: $JAR_FILE"
    echo "   Please check the path or filename in the script configuration."
    exit 1
fi

# Check for existing instance
if pgrep -f "$MAIN_CLASS" > /dev/null; then
    echo "❌ Aborted: An instance of EmuLinker-K is already running."
    exit 1
fi

# 3.b UPDATE CHECK
echo "🔍 Checking for updates..."
PROD_TXT=$(curl -fsSL --max-time 3 "https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/prod.txt" || true)

if [ -n "$PROD_TXT" ]; then
    LATEST_VERSION=$(echo "$PROD_TXT" | grep "^version=" | cut -d'=' -f2)
    RELEASE_NOTES=$(echo "$PROD_TXT" | grep "^releaseNotes=" | cut -d'=' -f2)
    
    # Extract local version from JAR filename
    # Assumes JAR_FILE format: .../emulinker-k-VERSION.jar
    LOCAL_VERSION=$(basename "$JAR_FILE" | sed -E 's/emulinker-k-(.+)\.jar/\1/')

    if [ -n "$LATEST_VERSION" ] && [ -n "$LOCAL_VERSION" ]; then
        if [ "$LATEST_VERSION" != "$LOCAL_VERSION" ]; then
            echo -e "\n⚠️  \033[1;33mUpdate Available!\033[0m"
            echo "   Current: $LOCAL_VERSION"
            echo "   Latest:  $LATEST_VERSION"
            if [ -n "$RELEASE_NOTES" ]; then
                echo "   Release Notes: $RELEASE_NOTES"
            fi
            echo "   To upgrade, run:"
            echo -e "   \033[1mcurl -fsSL https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/setup.sh | bash\033[0m\n"
            echo "   Starting server with current version in 5 seconds..."
            sleep 5
        else
            echo "✅ Server is up to date ($LOCAL_VERSION)."
        fi
    else
         echo "⚠️  Could not determine versions. Skipping check."
    fi
else
    echo "⚠️  Could not fetch update info. Skipping check."
fi

# 4. START THE SERVER
echo "🚀 Starting EmuLinker-K..."

# Run Java directly in the background
nohup java $JAVA_OPTS -cp "$CLASSPATH" $MAIN_CLASS > /dev/null 2>&1 &
SERVER_PID=$!

# 5. VERIFY AND INSPECT LOGS
sleep 3

if ! ps -p $SERVER_PID > /dev/null; then
    echo "❌ Error: The server process died immediately after starting."
    echo "Check $LOG_FILE for details."
    exit 1
fi

echo "✅ Server process running (PID: $SERVER_PID)."
echo "   Reading startup logs..."
echo "-------------------------------------------------------------------------------"

if [ -f "$LOG_FILE" ]; then
    # Find the line number of the LAST occurrence of the startup message
    START_LINE=$(grep -n "$SEARCH_TERM" "$LOG_FILE" | tail -n 1 | cut -d: -f1)
    if [ -n "$START_LINE" ]; then
        tail -n "+$START_LINE" "$LOG_FILE"
    else
        echo "   (Startup message not found yet. Showing last 10 lines:)"
        tail -n 10 "$LOG_FILE"
    fi
else
    echo "⚠️  Warning: $LOG_FILE was not found."
fi
echo "-------------------------------------------------------------------------------"

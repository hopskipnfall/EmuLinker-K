#!/bin/bash

# 1. ESTABLISH CONTEXT
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 2. CONFIGURATION
# Update these variables if you upgrade the jar or change memory settings
JAR_FILE="./lib/emulinker-k-0.15.1.jar"
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

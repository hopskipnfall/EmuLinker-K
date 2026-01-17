#!/bin/bash

# 1. ESTABLISH CONTEXT
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Define variables
LOG_FILE="emulinker.log"
SERVER_SCRIPT="./server.sh"
MAIN_CLASS="org.emulinker.kaillera.pico.ServerMainKt"
SEARCH_TERM="EmuLinker-K version"

# 2. ASSERT EXECUTABLE
# Check if the server script exists and is executable
if [ ! -x "$SERVER_SCRIPT" ]; then
    echo "❌ Error: $SERVER_SCRIPT is not executable or cannot be found."
    echo "   Please run the following command to fix it:"
    echo "   chmod +x $SERVER_SCRIPT"
    exit 1
fi

# 3. CHECK FOR EXISTING INSTANCE
if pgrep -f "$MAIN_CLASS" > /dev/null; then
    echo "❌ Aborted: An instance of EmuLinker-K is already running."
    exit 1
fi

# 4. START THE SERVER
echo "🚀 Starting EmuLinker-K..."
nohup "$SERVER_SCRIPT" > /dev/null 2>&1 &
SERVER_PID=$!

# 5. VERIFY AND INSPECT LOGS
sleep 3

# Check if the process is still alive
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

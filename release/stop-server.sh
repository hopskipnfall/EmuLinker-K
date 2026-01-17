#!/bin/bash

# 1. IDENTIFY THE PROCESS
# We match the same class name used in start-server.sh for precision.
MAIN_CLASS="org.emulinker.kaillera.pico.ServerMainKt"

# pgrep -f searches the full command line for the pattern
PID=$(pgrep -f "$MAIN_CLASS")

if [ -z "$PID" ]; then
    echo "⚠️  No running EmuLinker-K instance found."
    exit 0
fi

# 2. SEND TERMINATION SIGNAL
echo "🛑 Stopping EmuLinker-K (PID: $PID)..."
kill "$PID"

# 3. VERIFY SHUTDOWN
# Wait up to 10 seconds for the process to exit cleanly
for i in {1..10}; do
    if ! ps -p "$PID" > /dev/null; then
        echo "✅ Server stopped successfully."
        exit 0
    fi
    sleep 1
done

# 4. HANDLE STUCK PROCESS
echo "❌ Error: Server is still running after 10 seconds."
echo "   It might be stuck. You can force kill it with:"
echo "   kill -9 $PID"
exit 1

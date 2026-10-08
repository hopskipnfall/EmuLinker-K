#!/bin/bash

# 1. IDENTIFY THE PROCESS
# We match the same class name used in start-server.sh for precision.
MAIN_CLASS="org.emulinker.kaillera.pico.ServerMainKt"

# pgrep -f searches the full command line for the pattern
# There can be more than one match (several instances, or a wrapper process); handle each of them.
PIDS=$(pgrep -f "$MAIN_CLASS")

if [ -z "$PIDS" ]; then
    echo "⚠️  No running EmuLinker-K instance found."
    exit 0
fi

# 2. SEND TERMINATION SIGNAL
for PID in $PIDS; do
    echo "🛑 Stopping EmuLinker-K (PID: $PID)..."
    if ! kill "$PID" 2>/dev/null; then
        echo "⚠️  Operation not permitted. Attempting to stop EmuLinker-K with sudo..."
        sudo kill "$PID"
    fi
done

# 3. VERIFY SHUTDOWN
# Wait up to 10 seconds for every process to exit cleanly
for i in {1..10}; do
    STILL_RUNNING=""
    for PID in $PIDS; do
        if ps -p "$PID" > /dev/null; then
            STILL_RUNNING="$STILL_RUNNING $PID"
        fi
    done
    if [ -z "$STILL_RUNNING" ]; then
        echo "✅ Server stopped successfully."
        exit 0
    fi
    sleep 1
done

# 4. HANDLE STUCK PROCESS
echo "❌ Error: Server is still running after 10 seconds (PID:$STILL_RUNNING)."
echo "   It might be stuck. You can force kill it with:"
echo "   kill -9$STILL_RUNNING"
exit 1

#!/bin/bash

# EmuLinker-K Setup Script
# Usage: bash <(curl -s https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/setup.sh)

set -e

# --- ASCII Art & Welcome ---

echo -e "\033[1;36m"
cat << "EOF"
 _____                 _     _       _                  _  __
| ____|_ __ ___  _   _| |   (_)_ __ | | _____ _ __     | |/ /
|  _| | '_ ` _ \| | | | |   | | '_ \| |/ / _ \ '__|____| ' /
| |___| | | | | | |_| | |___| | | | |   <  __/ | |_____| . \
|_____|_| |_| |_|\__,_|_____|_|_| |_|_|\_\___|_|       |_|\_\

EOF
echo -e "\033[0m"
echo -e "👋 Welcome to the \033[1mEmuLinker-K\033[0m setup wizard!"
echo ""

# --- Detection Logic ---

INSTALL_DIR="EmuLinker-K"

if [ -d "$INSTALL_DIR" ]; then
    # Directory exists in current working directory
    MODE="UPGRADE"
elif [ "$(basename "$(pwd)")" == "$INSTALL_DIR" ]; then
    # Current directory is the install directory
    MODE="UPGRADE"
    INSTALL_DIR="."
else
    MODE="INSTALL"
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
  SED_CMD=(sed -i '')
else
  SED_CMD=(sed -i)
fi

echo -e "🔍 Detected mode: \033[1m$MODE\033[0m"

if [ "$MODE" == "UPGRADE" ]; then
    echo "❌ Error: Upgrade mode is not yet supported."
    exit 1
fi

# --- Install Logic ---

echo -e "\n🚀 \033[1mStarting installation...\033[0m"

# Fetch prod.txt
echo -e "📡 Fetching release information..."
#TODO: Revert
#PROD_TXT=$(curl -s "https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/prod.txt")
PROD_TXT=$(curl -s "https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/setup-script/release/prod.txt")

if [ -z "$PROD_TXT" ]; then
    echo "❌ Error: Could not fetch release information."
    exit 1
fi

# Parse tag, version and downloadUrl
TAG=$(echo "$PROD_TXT" | grep "^tag=" | cut -d'=' -f2)
VERSION=$(echo "$PROD_TXT" | grep "^version=" | cut -d'=' -f2)
DOWNLOAD_URL=$(echo "$PROD_TXT" | grep "^downloadUrl=" | cut -d'=' -f2)

if [ -z "$TAG" ] || [ -z "$VERSION" ] || [ -z "$DOWNLOAD_URL" ]; then
    echo "❌ Error: Invalid release information."
    exit 1
fi

echo -e "✅ Found version: \033[1;32m$VERSION\033[0m (Tag: $TAG)"

# Create directory structure
echo "📂 Creating directory structure..."
mkdir -p "$INSTALL_DIR/lib"
mkdir -p "$INSTALL_DIR/conf"

# Download files
#TODO undo
BASE_URL="https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/setup-script/release"
#BASE_URL="https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/$TAG/release"

echo "⬇️  Downloading configuration and scripts..."

download_file() {
    local file=$1
    local dest=$2
    local url="${BASE_URL}/${file}"
    echo "   📦 Downloading $file..."
    curl -s -o "$dest" "$url"
}

# Download JAR
echo "   📦 Downloading emulinker-k-$VERSION.jar..."
curl -s -L -o "$INSTALL_DIR/lib/emulinker-k-$VERSION.jar" "$DOWNLOAD_URL"

# Download other files
FILES=(
    "start-server.sh"
    "stop-server.sh"
    "README.md"
    "NOTICE.txt"
    "LICENSE.txt"
)

CONF_FILES=(
    "emulinker.cfg"
    "log4j2.properties"
    "language.properties"
    "access.cfg"
)

for f in "${FILES[@]}"; do
    # These go to root
    # However we need to be careful about 404s if I'm testing with a non-existent tag.
    # But assuming the tag exists:
    download_file "$f" "$INSTALL_DIR/$f"
done

for f in "${CONF_FILES[@]}"; do
    download_file "$f" "$INSTALL_DIR/conf/$f"
done

# Make scripts executable
chmod +x "$INSTALL_DIR/start-server.sh"
chmod +x "$INSTALL_DIR/stop-server.sh"

# --- Configuration ---

echo -e "\n⚙️  \033[1mConfiguration Setup\033[0m"
echo "==================="

read -p "📝 What is the name of the new server? " SERVER_NAME
read -p "🌍 Where is the server located? (e.g. Tokyo, Seattle) " SERVER_LOCATION
read -p "📡 Do you want your server to appear in server lists? (y/n) " PUBLIC_SERVER

# Update emulinker.cfg
CFG_FILE="$INSTALL_DIR/conf/emulinker.cfg"

# Escape special characters for sed if necessary, mainly slashes
SERVER_NAME_ESCAPED=$(printf '%s\n' "$SERVER_NAME" | sed -e 's/[]\/$*.^[]/\\&/g')
SERVER_LOCATION_ESCAPED=$(printf '%s\n' "$SERVER_LOCATION" | sed -e 's/[]\/$*.^[]/\\&/g')

"${SED_CMD[@]}" "s/^masterList.serverName=.*/masterList.serverName=$SERVER_NAME_ESCAPED/" "$CFG_FILE"
"${SED_CMD[@]}" "s/^masterList.serverLocation=.*/masterList.serverLocation=$SERVER_LOCATION_ESCAPED/" "$CFG_FILE"

if [[ "$PUBLIC_SERVER" =~ ^[Yy]$ ]]; then
    "${SED_CMD[@]}" "s/^masterList.touchKaillera=.*/masterList.touchKaillera=true/" "$CFG_FILE"
    "${SED_CMD[@]}" "s/^masterList.touchEmulinker=.*/masterList.touchEmulinker=true/" "$CFG_FILE"
else
    "${SED_CMD[@]}" "s/^masterList.touchKaillera=.*/masterList.touchKaillera=false/" "$CFG_FILE"
    "${SED_CMD[@]}" "s/^masterList.touchEmulinker=.*/masterList.touchEmulinker=false/" "$CFG_FILE"
fi

echo -e "\n🎉 \033[1;32mInstallation complete!\033[0m"
echo ""
echo -e "⚠️  \033[1;33mWARNING:\033[0m You need to open UDP port \033[1m27888\033[0m (or change the port in conf/emulinker.cfg)."
echo ""
echo "To start the server, run:"
echo -e "  \033[1mcd $INSTALL_DIR\033[0m"
echo -e "  \033[1m./start-server.sh\033[0m"
echo ""
echo "To stop the server, run:"
echo -e "  \033[1m./stop-server.sh\033[0m"
echo ""
echo "👋 Please join our Discord server (https://discord.gg/MqZEph388c) and ask for the \"Kaillera server owners\" role."

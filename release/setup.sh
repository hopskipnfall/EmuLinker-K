#!/bin/bash

# EmuLinker-K Setup Script
# Usage: curl -fsSL https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/master/release/setup.sh | bash

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
elif [ -f "start-server.sh" ] && [ -d "lib" ] && [ -d "conf" ]; then
    # Current directory appears to be an installation
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

# Pre-flight checks
if [ -f "$INSTALL_DIR/start-server.sh" ]; then
    echo "🗑️  Deleting obsolete server scripts..."
    rm -f "$INSTALL_DIR/start-server.sh"
    rm -f "$INSTALL_DIR/stop-server.sh"
fi
 if [ -f "$INSTALL_DIR/server.sh" ]; then
    echo "🗑️  Deleting obsolete server.sh..."
    rm "$INSTALL_DIR/server.sh"
fi

# Backup config
if [ -f "$INSTALL_DIR/conf/emulinker.cfg" ]; then
    echo "💾 Backing up emulinker.cfg to emulinker.cfg.old..."
    cp "$INSTALL_DIR/conf/emulinker.cfg" "$INSTALL_DIR/conf/emulinker.cfg.old"
fi


# --- Install / Upgrade Logic ---

if [ "$MODE" == "INSTALL" ]; then
    echo -e "\n🚀 \033[1mStarting installation...\033[0m"
fi


# --- Argument Parsing ---
# --- Argument Parsing ---
RELEASE_CHANNEL="prod"
TAG_ARG=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --beta)
            RELEASE_CHANNEL="beta"
            shift # Remove --beta
            ;;
        --tag)
            TAG_ARG="$2"
            shift # Remove --tag
            shift # Remove value
            ;;
        *)
            # Unknown option
            shift
            ;;
    esac
done

if [ -n "$TAG_ARG" ]; then
    echo -e "🏷️  \033[1;33mUsing specific tag: $TAG_ARG\033[0m"
    RELEASE_INFO_URL="https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/$TAG_ARG/release/prod.txt"
elif [ "$RELEASE_CHANNEL" == "beta" ]; then
    echo -e "🚧 \033[1;33mUsing BETA release channel\033[0m"
    RELEASE_INFO_URL="https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/beta/release/beta.txt"
else
    RELEASE_INFO_URL="https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/prod/release/prod.txt"
fi

# Fetch release info
echo -e "📡 Fetching release information from $RELEASE_CHANNEL..."
PROD_TXT=$(curl -s "$RELEASE_INFO_URL")

if [ -z "$PROD_TXT" ]; then
    echo "❌ Error: Could not fetch release information."
    exit 1
fi

# Parse tag, version, downloadUrl and releaseNotes
TAG=$(echo "$PROD_TXT" | grep "^tag=" | cut -d'=' -f2)
VERSION=$(echo "$PROD_TXT" | grep "^version=" | cut -d'=' -f2)
DOWNLOAD_URL=$(echo "$PROD_TXT" | grep "^downloadUrl=" | cut -d'=' -f2)
RELEASE_NOTES=$(echo "$PROD_TXT" | grep "^releaseNotes=" | cut -d'=' -f2)

if [ -z "$TAG" ] || [ -z "$VERSION" ] || [ -z "$DOWNLOAD_URL" ]; then
    echo "❌ Error: Invalid release information."
    exit 1
fi

echo -e "✅ Found version: \033[1;32m$VERSION\033[0m (Tag: $TAG)"

# Set BASE_URL using TAG
BASE_URL="https://raw.githubusercontent.com/hopskipnfall/EmuLinker-K/$TAG/release"

# Create directory structure
echo "📂 Creating directory structure..."
mkdir -p "$INSTALL_DIR/lib"
mkdir -p "$INSTALL_DIR/conf"

# Download files
echo "⬇️  Downloading configuration and scripts..."

download_file() {
    local file=$1
    local dest=$2
    local url="${BASE_URL}/${file}"
    echo "   📦 Downloading $file..."
    curl -s -o "$dest" "$url"
}

# Download JAR
JAR_PATH="$INSTALL_DIR/lib/emulinker-k-$VERSION.jar"
DOWNLOAD_JAR=true

if [ -f "$JAR_PATH" ]; then
    if [[ "$OSTYPE" == "darwin"* ]]; then
        FILE_SIZE=$(stat -f%z "$JAR_PATH")
    else
        FILE_SIZE=$(stat -c%s "$JAR_PATH")
    fi

    # 1MB = 1048576 bytes
    if [ "$FILE_SIZE" -ge 1048576 ]; then
        echo "   ✅ Found valid existing jar ($FILE_SIZE bytes). Skipping download."
        DOWNLOAD_JAR=false
    else
        echo "   ⚠️ Existing jar is too small ($FILE_SIZE bytes). Re-downloading..."
    fi
fi

if [ "$DOWNLOAD_JAR" = true ]; then
    echo "   📦 Downloading emulinker-k-$VERSION.jar..."
    curl -s -L -o "$JAR_PATH" "$DOWNLOAD_URL"
fi

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

if [ "$MODE" == "INSTALL" ]; then
    for f in "${CONF_FILES[@]}"; do
        download_file "$f" "$INSTALL_DIR/conf/$f"
    done
fi

# Make scripts executable
chmod +x "$INSTALL_DIR/start-server.sh"
chmod +x "$INSTALL_DIR/stop-server.sh"

# --- Configuration ---

echo -e "\n⚙️  \033[1mConfiguration Setup\033[0m"
echo "==================="

CFG_FILE="$INSTALL_DIR/conf/emulinker.cfg"

# Apply Questions.txt for both INSTALL and UPGRADE.
# Download questions.txt
QUESTIONS_FILE="$INSTALL_DIR/questions.txt"
download_file "questions.txt" "$QUESTIONS_FILE"

if [ -f "$QUESTIONS_FILE" ]; then
    while IFS='=' read -r key_type question || [ -n "$key_type" ]; do
            # Skip empty lines or comments
        [[ -z "$key_type" || "$key_type" == \#* ]] && continue
        
        # Parse Key and Type
        KEY=$(echo "$key_type" | cut -d':' -f1)
        TYPE=$(echo "$key_type" | cut -d':' -f2)
        
        # Default type to string if missing
        [[ "$KEY" == "$TYPE" ]] && TYPE="string"
        
        # Check if key exists in config
        IS_MISSING=false
        IS_EMPTY=false

        if ! grep -q "^$KEY=" "$CFG_FILE"; then
            IS_MISSING=true
        elif grep -q "^$KEY=$" "$CFG_FILE"; then
            IS_EMPTY=true
        fi

        if [ "$IS_MISSING" = true ] || [ "$IS_EMPTY" = true ]; then
            echo -e "\n❓ \033[1mConfig Update: $KEY ($TYPE)\033[0m"
            
            ATTEMPTS=0
            MAX_ATTEMPTS=2
            VALID_INPUT=false
            ANSWER=""

            while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
                read -p "  $question " INPUT < /dev/tty
                
                # Empty input skips
                if [ -z "$INPUT" ]; then
                    echo "   Skipping..."
                    break
                fi

                # Validation
                case "$TYPE" in
                    "boolean")
                        if [[ "$INPUT" =~ ^[Yy]([Ee][Ss])?$ ]] || [[ "$INPUT" =~ ^[Tt][Rr][Uu][Ee]$ ]]; then
                            ANSWER="true"
                            VALID_INPUT=true
                        elif [[ "$INPUT" =~ ^[Nn][Oo]?$ ]] || [[ "$INPUT" =~ ^[Ff][Aa][Ll][Ss][Ee]$ ]]; then
                            ANSWER="false"
                            VALID_INPUT=true
                        else
                            echo "   ❌ Invalid boolean. Please enter 'y', 'yes', 'true' or 'n', 'no', 'false'."
                        fi
                        ;;
                    "number")
                        if [[ "$INPUT" =~ ^-?[0-9]*\.?[0-9]+$ ]]; then
                            ANSWER="$INPUT"
                            VALID_INPUT=true
                        else
                            echo "   ❌ Invalid number."
                        fi
                        ;;
                    *) # string
                        ANSWER="$INPUT"
                        VALID_INPUT=true
                        ;;
                esac

                if [ "$VALID_INPUT" = true ]; then
                    if [ "$IS_EMPTY" = true ]; then
                        # Escape special characters for sed
                        ANSWER_ESCAPED=$(printf '%s\n' "$ANSWER" | sed -e 's/[]\/$*.^[]/\\&/g')
                        "${SED_CMD[@]}" "s/^$KEY=$/$KEY=$ANSWER_ESCAPED/" "$CFG_FILE"
                    else
                        echo "$KEY=$ANSWER" >> "$CFG_FILE"
                    fi
                    break
                fi
                
                ATTEMPTS=$((ATTEMPTS+1))
            done
            
            if [ "$VALID_INPUT" = false ] && [ -n "$INPUT" ]; then
                    echo "   ⚠️  Skipping $KEY due to invalid input."
            fi
        fi
    done < "$QUESTIONS_FILE"
    
    # Cleanup
    rm "$QUESTIONS_FILE"
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
if [ -n "$RELEASE_NOTES" ]; then
    echo -e "📜 \033[1mRelease Notes:\033[0m $RELEASE_NOTES"
    echo ""
fi
echo "👋 Please join our Discord server (https://discord.gg/MqZEph388c) and ask for the \"Kaillera server owners\" role."

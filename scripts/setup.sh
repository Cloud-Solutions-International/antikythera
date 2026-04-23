#!/usr/bin/env bash
#
# Setup script for antikythera local development.
# Clones and builds all sibling repositories required for building and testing.
#
# Usage: ./scripts/setup.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PARENT_DIR="$(dirname "$PROJECT_DIR")"

# Detect JAVA_HOME
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "/usr/lib/jvm/java-21-amazon-corretto" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21-amazon-corretto"
    elif [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
    else
        echo "ERROR: JAVA_HOME is not set and no Java 21 installation found."
        echo "       Please install Java 21 and set JAVA_HOME."
        exit 1
    fi
fi

echo "Using JAVA_HOME=$JAVA_HOME"
echo "Parent directory: $PARENT_DIR"
echo ""

SIBLING_REPOS=(
    "antikythera-common"
    "antikythera-agent"
    "antikythera-test-helper"
    "antikythera-sample-project"
    "antikythera-test-generator"
)

# Clone missing sibling repos
for repo in "${SIBLING_REPOS[@]}"; do
    if [ ! -d "$PARENT_DIR/$repo" ]; then
        echo "Cloning $repo..."
        git clone "https://github.com/Cloud-Solutions-International/$repo.git" "$PARENT_DIR/$repo"
    else
        echo "Found $repo"
    fi
done

echo ""
echo "Building dependencies..."

# Build in dependency order
echo "  [1/4] Building antikythera-common..."
(cd "$PARENT_DIR/antikythera-common" && mvn install -q -DskipTests)

echo "  [2/4] Building antikythera-agent..."
(cd "$PARENT_DIR/antikythera-agent" && mvn install -q -DskipTests)

echo "  [3/4] Building antikythera-sample-project..."
(cd "$PARENT_DIR/antikythera-sample-project" && mvn install -q -DskipTests)

echo "  [4/4] Building antikythera..."
(cd "$PROJECT_DIR" && mvn install -q -DskipTests)

echo ""
echo "Setup complete. Run tests with:"
echo "  mvn test"
echo ""
echo "To also verify antikythera-test-generator:"
echo "  (cd $PARENT_DIR/antikythera-test-generator && mvn test)"

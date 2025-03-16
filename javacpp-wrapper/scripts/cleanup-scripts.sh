#!/bin/bash

# Script to clean up temporary files and artifacts created during the build process
# This script is a wrapper around the main build.sh script

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Call the main build script with the --clean option
"$SCRIPT_DIR/build.sh" --clean --verbose "$@"

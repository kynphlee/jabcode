#!/bin/bash

# Script to build the Java wrapper for the JABCode library
# This script is a wrapper around the main build.sh script

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Call the main build script with the --java option
"$SCRIPT_DIR/build.sh" --java --verbose "$@"

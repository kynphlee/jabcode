#!/bin/bash

# Script to clean up log files in the javacpp-wrapper directory

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Find and delete all log files
echo "Deleting log files from $PROJECT_DIR..."
find "$PROJECT_DIR" -name "*.log" -type f -exec rm -v {} \;

echo "Log cleanup completed."

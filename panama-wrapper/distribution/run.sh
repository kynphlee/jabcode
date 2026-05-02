#!/bin/bash
# Run JABCode QuickStart example with proper library path

# Get the directory where this script is located
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set library path
export LD_LIBRARY_PATH="${DIR}/lib:${LD_LIBRARY_PATH}"

# Run the example
java --enable-preview \
     --enable-native-access=ALL-UNNAMED \
     -cp "${DIR}:${DIR}/jabcode-panama-1.0.0-SNAPSHOT.jar" \
     QuickStart

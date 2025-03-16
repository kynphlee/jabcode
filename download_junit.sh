#!/bin/bash

# download_junit.sh - Script to download JUnit and JavaCPP libraries

# Create lib directory if it doesn't exist
mkdir -p javacpp-wrapper/lib

# Download JUnit 4.13.2
echo "Downloading JUnit 4.13.2..."
wget -O javacpp-wrapper/lib/junit-4.13.2.jar https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar

# Download Hamcrest Core 1.3 (required by JUnit)
echo "Downloading Hamcrest Core 1.3..."
wget -O javacpp-wrapper/lib/hamcrest-core-1.3.jar https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar

# Download JavaCPP 1.5.9
echo "Downloading JavaCPP 1.5.9..."
wget -O javacpp-wrapper/lib/javacpp-1.5.9.jar https://repo1.maven.org/maven2/org/bytedeco/javacpp/1.5.9/javacpp-1.5.9.jar

echo "Libraries downloaded successfully."

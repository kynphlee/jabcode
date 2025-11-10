#!/bin/bash

# generate_jabcodes.sh - Script to generate JABCodes with different color modes

# Create output directory if it doesn't exist
mkdir -p output

# Sample text to encode
SAMPLE_TEXT="This is a test of JABCode color modes. JABCode (Just Another Barcode) is a color 2D matrix symbology made of color squares arranged in either square or rectangle grids."

# Save the sample text to a file
echo "$SAMPLE_TEXT" > output/sample_text.txt

# Set classpath to include the JABCode JAR file
CLASSPATH="javacpp-wrapper/target/jabcode-java-1.0.0.jar:javacpp-wrapper/target/test-classes"

# Set the java.library.path to include the lib directory
JAVA_OPTS="-Djava.library.path=./lib"

# Generate JABCodes with different color modes
echo "Generating JABCodes with different color modes..."

# 4 colors mode (Quaternary)
echo "Generating 4 colors JABCode..."
java $JAVA_OPTS -cp $CLASSPATH com.jabcode.test.ColorModeTest quaternary "$SAMPLE_TEXT" output/jabcode_4_colors.png

# 8 colors mode (Octal)
echo "Generating 8 colors JABCode..."
java $JAVA_OPTS -cp $CLASSPATH com.jabcode.test.ColorModeTest octal "$SAMPLE_TEXT" output/jabcode_8_colors.png

# Try to decode the generated JABCodes
echo "Decoding generated JABCodes..."

# Decode 4 colors JABCode
echo "Decoding 4 colors JABCode..."
java $JAVA_OPTS -cp $CLASSPATH com.jabcode.test.DecodeTest output/jabcode_4_colors.png > output/decoded_4_colors.txt

# Decode 8 colors JABCode
echo "Decoding 8 colors JABCode..."
java $JAVA_OPTS -cp $CLASSPATH com.jabcode.test.DecodeTest output/jabcode_8_colors.png > output/decoded_8_colors.txt

echo "JABCodes generation and decoding completed."
echo "Check the output directory for the generated JABCodes and decoded text."

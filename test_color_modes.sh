#!/bin/bash

# test_color_modes.sh - Script to run JABCode color mode unit tests

# Set classpath to include the JABCode JAR file, test classes, JUnit, and JavaCPP
CLASSPATH="output/libs/jabcode-java-1.0.0.jar:javacpp-wrapper/target/test-classes:javacpp-wrapper/lib/junit-4.13.2.jar:javacpp-wrapper/lib/hamcrest-core-1.3.jar:javacpp-wrapper/lib/javacpp-1.5.9.jar"

# Set the java.library.path to include the lib directory
JAVA_OPTS="-Djava.library.path=./lib"

# Run the JUnit tests
echo "Running JABCode color mode unit tests..."

# Run all tests in the ColorModeTest class
java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest

# Run individual tests if needed
# echo "Running Binary color mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#testBinaryColorMode

# echo "Running Quaternary color mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#testQuaternaryColorMode

# echo "Running Octal color mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#testOctalColorMode

# echo "Running Hexadecimal color mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#testHexadecimalColorMode

# echo "Running 32 colors mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#test32ColorMode

# echo "Running 64 colors mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#test64ColorMode

# echo "Running 128 colors mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#test128ColorMode

# echo "Running 256 colors mode test..."
# java $JAVA_OPTS -cp $CLASSPATH org.junit.runner.JUnitCore com.jabcode.test.ColorModeTest#test256ColorMode

echo "All tests completed."

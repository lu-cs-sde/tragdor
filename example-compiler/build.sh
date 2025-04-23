#!/bin/sh
set -e

java -jar jastadd2.jar --tracing=all --package=ast --cache=all --visitCheck=false addnum.ast attributes.jrag
javac Compiler.java

echo "Main-Class: Compiler" > MANIFEST.MF
jar cfm compiler.jar MANIFEST.MF Compiler.* ast/*
rm MANIFEST.MF

echo "Built compiler.jar"

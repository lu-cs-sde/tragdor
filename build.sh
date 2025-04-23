#!/bin/sh
set -e

rm -rf build_tmp/
mkdir -p build_tmp/tragdor/web
cp -Ra src/tragdor/web build_tmp/tragdor

echo "Gathering sources.."
find src -name "*.java" > sources.txt

echo "Building.."
# "Our own" class files
javac @sources.txt -cp libs/codeprober.jar -d build_tmp -source 8 -target 8 -g

cd build_tmp

echo "Generating jar.."
echo "Main-Class: tragdor.Tragdor" >> Manifest.txt
echo "Class-Path: libs/codeprober.jar" >> Manifest.txt

DST=../tragdor.jar
jar cfm $DST Manifest.txt **/*

cd ..

echo "Cleaning up.."
rm sources.txt

echo "Done! Built '$(basename $DST)'"

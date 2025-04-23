#!/bin/bash

set -e

if [ ! -e "testgen" ]; then
  echo "No 'testgen' directory. Have you generated any tests yet?"
  exit 1
fi

rm -rf testgen_dst
mkdir testgen_dst

LIBS=tragdor.jar:libs/codeprober.jar

echo "Compiling.."
javac -cp $LIBS -d testgen_dst testgen/*.java
cp testgen/*.json testgen_dst/tragdor


echo "Running.."

list_files_no_suffix() {
    result=""
    for file in testgen/*.java; do
        filename=$(basename "$file")
        name="${filename%.*}"  # strip suffix after last dot
        result="$result tragdor.$name "
    done
    echo "$result"
}

TESTFILES=$(list_files_no_suffix)

echo "testfiles: $TESTFILES"

echo "Test classes: $TEST_CLASSES"
java -cp $LIBS:testgen_dst org.junit.runner.JUnitCore $TESTFILES
echo "All Tests pass successfully"

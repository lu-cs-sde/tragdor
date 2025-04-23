# Tragdor - Testing RAG Dependency Orders & Remediation

This repository contains "Tragdor", a tool which performs dynamic dependency-based purity checking on compilers and static analyzers build using attribute grammars.

## Building

Build `tragdor.jar` using the script `build.sh`, like this:

```bash
sh build.sh
```

## Running

Tragdor will print usage information if started without extra arguments, like so:

```bash
java -jar tragdor.jar
```

See [example-compiler](example-compiler/README.md) for an example compiler to run Tragdor on, and more concrete instructions on what commands to use.

# FEUP-C

Group project for the C course unit at FEUP.

The goal of this project was to develop a compiler for a subset of Java, called Java--, or JMM. The compiler was developed in Java, using ANTLR as the parser, the Ollir intermediate language, and the Jasmin assembler.

## Running

This project uses [gradle](https://gradle.org/) as the build system. To compile and run the project, run `gradle run --args="<args>"`, where `<args>` are the arguments to be passed to the compiler. The compiler supports the following arguments:

- `-i`: Specifies the input file, required.
- `-o`: Enables optimizations.
- `-r`: Enables register allocation.
- `-d`: Enables debug mode.

## JMM

Java-- is (almost) a subset of Java, with some differences.
The following syntax is supported:

- A single class with methods;
- All primitive types, but all numeric types are treated as integers;
- If statements with mandatory else clause;
- For loops;
- For each loops (only for arrays);
- While loops;
- Do-while loops;
- Switch statements;
- All Java operators, except for the modulo operator;
- Return, break and continue statements;
- Arrays;

## Optimizations

When the `-o` flag is passed, the compiler will perform the following optimizations:

- Constant folding;
- Constant propagation;
- Dead code elimination;

## Register allocation

When the `-r` flag is passed, the compiler will perform register allocation.

## Unit info

- **Name**: Compiladores (Compilers)
- **Date**: Year 3, Semester 2, 2022/23
- [**More info**](https://sigarra.up.pt/feup/ucurr_geral.ficha_uc_view?pv_ocorrencia_id=501688)

## Disclaimer

This repository (and all others with the name format `feup-*`) are for archival and educational purposes only.

If you don't understand some part of the code or anything else in this repo, feel free to ask (although I may not understand it myself anymore).

Keep in mind that this repo is public. If you copy any code and use it in your school projects you may be flagged for plagiarism by automated tools.

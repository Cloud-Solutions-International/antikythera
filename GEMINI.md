# Gemini Agent Guide (GEMINI.md)

This document provides essential information for the Gemini agent to work efficiently and safely on this project.

## 1. High-Level Goal

A test generation tool for java projects. It will parse the source code of a target project and evaluate the expressions
with in it using the expression evaluation engine. The main class for the parser is 
`AbstractCompiler.java` located in the `src/main/java/sa/com/cloudsolutions/antikythera/parser/` directory. The main
class for the expression evaluation engine is `Evaluator.java` located in `src/main/java/sa/com/cloudsolutions/antikythera/evaluator/`.
The main class for the test generation is `TestGenerator.java` located in `src/main/java/sa/com/cloudsolutions/antikythera/generator/`.
All tehse classes have subclasses that extend their functionality.

When generating unit tests its required that you take both paths of a conditional statement where possible. This is
managed with the help of `sa.com.cloudsolutions.antikythera.evaluator.Branching.java` and `sa.com.cloudsolutions.antikythera.generator.TruthTable.java`

## 2. Core Commands

*   **Build:** `mvn clean install`
*   **Test:** `mvn test`
*   **Run Application:** mvn exec:java

## 4. Important File Locations

*   **Main Configuration:** `src/main/java/sa/com/cloudsolutions/antikythera/configuration/Settings.java`
*   **Locations of source code parsed by Java Parser for tests:** `../antikythera-sample-project/`, `../antikythera-test-helper/`

### Don't
*   (e.g., "Do not add new dependencies to `pom.xml` without discussion.")
*   (e.g., "Do not commit directly to the `main` branch.")

## 6. General Guidelines
*   Always consult the `README.md` file for high-level project information, setup instructions, and context before starting any task.

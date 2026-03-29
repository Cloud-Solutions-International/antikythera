Antikythera
===========

**Java AST Manipulation, Expression Evaluation, and Intelligent Code Analysis**

Antikythera is a core library that parses, evaluates, and transforms Java codebases.
It provides the AST parsing, symbolic execution, dependency solving, and query analysis
engine that powers tools built on top of it — including
[antikythera-test-generator](https://github.com/Cloud-Solutions-International/antikythera-test-generator) for automated
test generation.

---

## What Antikythera Can Do

### 🔍 Intelligent Dependency Analysis
Extract exactly what you need from complex codebases:

- **Isolated Code Migration**: Extract all dependencies for a target method or class at field and method granularity — only the code actually needed is copied, not entire classes
- **Microservice Extraction**: Pull out minimal, compilable codebases perfect for microservice architectures
- **Inheritance & Interface Tracking**: Automatically follow inheritance hierarchies, interface implementations, and method overrides
- **Clean Output**: Generate properly organized code with sorted members and optimized imports

Perfect for breaking apart monoliths or migrating functionality between projects.

### 📊 Advanced Query Parsing & Analysis
Deep understanding of your data access layer:

- **Multi-Language Query Support**: Parse and analyze SQL, HQL/JPQL, and Spring Expression Language (SpEL)
- **JPA Repository Intelligence**: Extract queries from @Query annotations and derive queries from method names
- **Query Optimization**: Identify performance issues and optimization opportunities in your queries
- **Dialect-Aware**: Supports multiple database dialects (PostgreSQL, Oracle, and more)

### 🔧 Large-Scale Refactoring
Transform your codebase with confidence:

- **Complete Code Analysis**: Build comprehensive parse trees and relationship graphs for entire codebases
- **Safe Transformations**: Understand all impacts before making changes
- **Framework Migrations**: Tools for Spring Boot upgrades, JUnit 4→5 migrations, and more
- **Pattern Detection**: Identify code duplication, usage patterns, and refactoring opportunities

---

## How It Works

Antikythera uses a sophisticated three-phase approach:

1. **Parse**: Leverages JavaParser to build complete ASTs of your codebase, including Java code, SQL/HQL queries, and SpEL expressions
2. **Analyze**: Evaluates expressions, builds dependency graphs, and tracks code paths using depth-first search algorithms
3. **Generate**: Creates tests, extracts dependencies, or performs refactoring based on the analysis

The expression evaluation engine uses reflection to execute code paths and discover all branches.

---

## Companion Modules

| Module | Purpose |
| :--- | :--- |
| [antikythera-test-generator](../antikythera-test-generator/README.md) | Automated unit, integration, and API test generation for Spring projects |
| [antikythera-examples](../antikythera-examples/) | Ready-to-use tools: schema normalisation, query analysis, framework migration utilities |

---

## Real-World Applications

**Legacy Modernization**: Analyse untested code and generate tests via `antikythera-test-generator`

**Microservice Migration**: Extract specific functionality with all dependencies into new services

**Framework Upgrades**: Use built-in tools for Spring Boot migrations, JUnit upgrades, and more

**Query Optimization**: Analyze your entire data access layer for performance improvements

**Code Quality**: Identify duplication, unused code, and refactoring opportunities

---

## Getting Started

### Requirements
- Java 21 or higher
- Maven (Gradle support coming soon)
- VM argument: `--add-opens java.base/java.util.stream=ALL-UNNAMED`

### Quick Example
```java
// Extract dependencies for a specific method
DepSolver solver = DepSolver.createSolver();
solver.processEntry("com.example.UserService#createUser");
// dfs() is called internally by processEntry

// Or symbolically execute a method to observe its behaviour
Evaluator eval = EvaluatorFactory.create("com.example.PricingService", SpringEvaluator.class);
Variable result = eval.executeMethod(methodNode);
System.out.println(result.getValue());

// To generate tests, use antikythera-test-generator:
// Antikythera.getInstance().generateUnitTests();
```

See the [documentation](docs/configurations.md) for detailed usage and configuration.

---

## Executables

The Antikythera core framework includes several standalone executables:

### DepSolver
Dependency analysis and extraction tool for microservice migration and code isolation.
```bash
mvn exec:java -Dexec.mainClass="sa.com.cloudsolutions.antikythera.depsolver.DepSolver"
```
Requires `depsolver.yml` configuration file. Given a target method or class, DepSolver resolves its full transitive dependency closure and generates a minimal compilable codebase. Crucially, dependencies are resolved at the **field and method level**: rather than copying entire classes, only the specific methods, fields, and inner classes that are actually required are extracted. For example, if `ClassA` uses `ClassB.doSomething()`, only that method (and its own dependencies) is copied — not all of `ClassB`.

### RepositoryParser
JPA Repository query analyzer and executor for visualization purposes.
```bash
mvn exec:java -Dexec.mainClass="sa.com.cloudsolutions.antikythera.parser.RepositoryParser" \
  -Dexec.args="com.example.repository.UserRepository"
```
Parses and executes queries from JPA repositories.

### TruthTable
Utility for generating truth tables from boolean expressions (testing/debugging tool).
```bash
mvn exec:java -Dexec.mainClass="sa.com.cloudsolutions.antikythera.generator.TruthTable"
```
Useful for understanding complex boolean logic in conditions.

---

## Tools & Examples

The **antikythera-examples** module includes ready-to-use tools for:
- Query performance analysis and optimization
- Code duplication detection
- Framework migration utilities (Spring Boot 2.x → 3.x, JUnit 4 → 5)
- Schema normalisation and DDL generation
- Usage pattern analysis
- And more...

The **antikythera-test-generator** module provides the test generation CLI and generators.
See [antikythera-test-generator/README.md](../antikythera-test-generator/README.md).

---

## Development & Testing

The core library is thoroughly tested with 786+ unit and integration tests. To run the test suite:

1. Clone the required test repositories:
   - https://github.com/Cloud-Solutions-International/antikythera-sample-project
   - https://github.com/Cloud-Solutions-International/antikythera-test-helper

2. Follow the folder structure described in antikythera-test-helper

3. Run with the required VM argument: `--add-opens java.base/java.util.stream=ALL-UNNAMED`

---

## Documentation

- **[AGENTS.md](AGENTS.md)** - AI agent guide: architecture, patterns, and critical rules
- **[docs/configurations.md](docs/configurations.md)** - Configuration reference
- **[PACKAGE.md](PACKAGE.md)** - Package and module overview

---

## License

See [LICENSE.txt](LICENSE.txt) for details.

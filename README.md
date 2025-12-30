Antikythera
===========

**Automated Test Generation, Code Refactoring, and Intelligent Analysis for Java Applications**

Antikythera is a powerful framework that automatically generates tests, analyzes dependencies, and enables large-scale refactoring of Java codebases.
Whether you're working on legacy code modernization, microservice extraction, or simply need comprehensive test coverage,
Antikythera accelerates your development workflow.

---

## What Antikythera Can Do

### 🧪 Automated Test Generation
Generate comprehensive unit tests and API tests with minimal effort:

- **Unit Tests**: Automatically generate JUnit tests for your service classes with proper assertions, mocks, and preconditions
- **API Tests**: Create RESTAssured tests for your REST controllers and endpoints
- **Smart Assertions**: Generate meaningful assertions based on return values, side effects, and logging statements
- **Branch Coverage**: Automatically explore all code paths and generate tests for each branch
- **Full Precondition Setup**: Tests include all necessary mocks, fixtures, and setup code

Currently supports Maven projects with Gradle support coming soon.

### 🔍 Intelligent Dependency Analysis
Extract exactly what you need from complex codebases:

- **Isolated Code Migration**: Identify and extract all dependencies for a specific class or method
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

The expression evaluation engine uses reflection to execute code paths and discover all branches, ensuring comprehensive coverage.

---

## Real-World Applications

**Legacy Modernization**: Generate tests for untested code before refactoring

**Microservice Migration**: Extract specific functionality with all dependencies into new services

**Framework Upgrades**: Use built-in tools for Spring Boot migrations, JUnit upgrades, and more

**Query Optimization**: Analyze your entire data access layer for performance improvements

**Code Quality**: Identify duplication, unused code, and refactoring opportunities

---

## Getting Started

### Requirements
- Java 11 or higher
- Maven (Gradle support coming soon)
- VM argument: `--add-opens java.base/java.util.stream=ALL-UNNAMED`

### Quick Example
```java
// Generate tests for a service class
Antikythera antk = Antikythera.getInstance();
antk.preProcess();
antk.generateUnitTests();

// Or extract dependencies for a specific method
DepSolver solver = DepSolver.createSolver();
solver.processMethod("com.example.UserService#createUser");
solver.dfs();
```

See the [documentation](WARP.md) for detailed usage and configuration.

---

## Tools & Examples

The **antikythera-examples** module includes ready-to-use tools for:
- Query performance analysis and optimization
- Code duplication detection
- Framework migration utilities (Spring Boot 2.x → 3.x, JUnit 4 → 5)
- Usage pattern analysis
- And more...

---

## Development & Testing

Antikythera itself is thoroughly tested with 660+ unit and integration tests. To run the test suite:

1. Clone the required test repositories:
   - https://github.com/Cloud-Solutions-International/antikythera-sample-project
   - https://github.com/Cloud-Solutions-International/antikythera-test-helper

2. Follow the folder structure described in antikythera-test-helper

3. Run with the required VM argument: `--add-opens java.base/java.util.stream=ALL-UNNAMED`

---

## Documentation

- **[WARP.md](WARP.md)** - Complete API documentation and AI agent guide
- **[AGENT.md](AGENT.md)** - Quick reference for common patterns
- **[docs/](docs/)** - Additional guides and specifications

---

## License

See [LICENSE.txt](LICENSE.txt) for details.

# Antikythera - Antigravity AI Agent Guide

**Comprehensive Documentation:** [WARP.md](WARP.md)

This file is optimized for the Antigravity AI agent. For other AI systems, see `AGENT.md` or `gemini.md`.

---

## Project Overview

Antikythera is an automated test generation framework for Java projects that:
- Generates JUnit tests for Spring services (with Mockito)
- Generates REST Assured tests for controllers
- Converts HQL/JPQL queries to native SQL
- Extracts minimal dependencies for microservice extraction

**Repository:** `antikythera/` (part of the Antikythera monorepo)

---

## Quick Start

```java
// Main generation flow
Antikythera antk = Antikythera.getInstance();
antk.preProcess();           // Parse all source files
antk.generateApiTests();     // Generate REST controller tests
antk.generateUnitTests();    // Generate service unit tests
```

---

## Architecture

### Three-Phase Pipeline

```
Parse → Evaluate → Generate
  │        │          │
  ├─ JavaParser    ├─ Reflection      ├─ JUnit Tests
  ├─ Type Cache    ├─ Branch Coverage ├─ Mockito Setup
  └─ Symbol Solver └─ Execution       └─ Assertions
```

### Key Modules

| Module | Package | Purpose |
|--------|---------|---------|
| Parser | `parser/` | JavaParser-based source parsing and type resolution |
| Evaluator | `evaluator/` | Symbolic execution engine with branching |
| Generator | `generator/` | Test code generation (JUnit/REST Assured) |
| Converter | `parser/converter/` | HQL/JPA to SQL conversion |
| DepSolver | `depsolver/` | Dependency graph and extraction |

---

## Essential Classes

### Entry Points
- `Antikythera` - Main orchestrator, singleton via `getInstance()`
- `Settings` - YAML configuration (`generator.yml`)
- `AntikytheraRunTime` - Global caches for CompilationUnits and types

### Parsing
- `AbstractCompiler` - Base parser, type resolution, symbol solving
- `RestControllerParser` - REST endpoints (`@RestController`, `@GetMapping`)
- `ServicesParser` - Business logic (`@Service`, `@Component`)
- `RepositoryParser` - JPA repositories with query extraction

### Evaluation
- `Evaluator` - Core expression evaluation engine
- `EvaluatorFactory` - Creates evaluator instances
- `SpringEvaluator` - Spring-aware evaluation
- `AKBuddy` - ByteBuddy dynamic class generation
- `MethodInterceptor` - Evaluator-instance bridge for dynamic classes

### Generation
- `TestGenerator` - Abstract base for test generation
- `SpringTestGenerator` - REST Assured API tests
- `UnitTestGenerator` - JUnit + Mockito unit tests

### Query Conversion
- `HQLParserAdapter` - HQL/JPQL → SQL (ANTLR4-based)
- `MethodToSQLConverter` - Method names → SQL (`findByUsername` → `WHERE username = ?`)
- `EntityMappingResolver` - JPA entity metadata extraction

---

## Common Patterns

### Type Resolution
```java
// Always check cache first
CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
if (cu == null) {
    AbstractCompiler compiler = new AbstractCompiler();
    compiler.compile(AbstractCompiler.classToPath(className));
    cu = compiler.getCompilationUnit();
}
TypeWrapper type = AbstractCompiler.findType(cu, "TypeName");
```

### Creating Dynamic Instances (AKBuddy)
```java
// For source-backed classes
Evaluator evaluator = EvaluatorFactory.create("com.example.User", SpringEvaluator.class);
MethodInterceptor interceptor = new MethodInterceptor(evaluator);
Class<?> dynamicClass = AKBuddy.createDynamicClass(interceptor);
Object instance = AKBuddy.createInstance(dynamicClass, interceptor);
// All method calls now route through the evaluator

// For mocking without source
MethodInterceptor mockInterceptor = new MethodInterceptor(SomeInterface.class);
Class<?> mockClass = AKBuddy.createDynamicClass(mockInterceptor);
```

### Query Conversion
```java
// HQL to SQL
HQLParserAdapter adapter = new HQLParserAdapter(cu, entityWrapper);
ConversionResult result = adapter.convertToNativeSQL("SELECT u FROM User u WHERE u.name = :name");

// Method name to SQL
List<String> components = MethodToSQLConverter.extractComponents("findByUsernameAndEmail");
StringBuilder sql = new StringBuilder();
MethodToSQLConverter.buildSelectAndWhereClauses(components, sql, "users");
```

---

## Configuration (generator.yml)

```yaml
base_path: /path/to/source/src/main/java
output_path: /path/to/output/src/test/java
base_package: com.example

controllers:
  - com.example.UserController

services:
  - com.example.UserService

database:
  url: jdbc:postgresql://localhost:5432/mydb
  username: user
  password: pass
  run_queries: true
  query_conversion:
    enabled: true
    fallback_on_failure: true
```

**Key Settings:**
- `base_path` - Source root (must include `src/main/java`)
- `output_path` - Generated test output
- `controllers` - REST controller classes
- `services` - Service classes
- `database` - For repository query execution

---

## Development Commands

### Build & Test
```bash
# Build
mvn compile

# Run tests (requires antikythera-sample-project and antikythera-test-helper cloned)
mvn test

# JVM flags required for tests
--add-opens java.base/java.util.stream=ALL-UNNAMED
```

### Run Project
```bash
# Build frontend (webpack)
webpack --watch

# Run backend (Django)
manage.py runserver
```

---

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21+ |
| JavaParser | 3.27.0 |
| ByteBuddy | 1.15.3 |
| Spring Boot | 2.7.14 |
| hql-parser | 0.0.15 (ANTLR4) |
| Maven | 3.x |

---

## Important Constraints

1. **Java 21 required** for compilation
2. **JVM flags required:** `--add-opens java.base/java.util.stream=ALL-UNNAMED`
3. **Test dependencies:** Clone `antikythera-sample-project` and `antikythera-test-helper`
4. **Configuration:** Always use `Settings` class, never hardcode paths
5. **Type resolution:** Always check `AntikytheraRunTime` cache before parsing

---

## Related Documentation

| File | Purpose |
|------|---------|
| [WARP.md](WARP.md) | Complete technical reference (primary) |
| [AGENT.md](AGENT.md) | Quick reference for AI agents |
| [gemini.md](gemini.md) | Gemini AI specific guide |
| [README.md](README.md) | Project overview |
| [PACKAGE.md](PACKAGE.md) | GitHub Packages instructions |

---

## Decision Guide

| Task | Use |
|------|-----|
| Generate tests for Spring project | `Antikythera.getInstance()` → `preProcess()` → `generateApiTests()`/`generateUnitTests()` |
| Parse a Java class | `AbstractCompiler.compile()` or `AntikytheraRunTime.getCompilationUnit()` |
| Resolve a type name | `AbstractCompiler.findType(cu, "TypeName")` |
| Convert method name to SQL | `MethodToSQLConverter.extractComponents()` + `buildSelectAndWhereClauses()` |
| Convert HQL to SQL | `HQLParserAdapter.convertToNativeSQL()` |
| Create dynamic mock instance | `AKBuddy.createDynamicClass()` + `createInstance()` |
| Load configuration | `Settings.loadConfigMap()` |
| Access type cache | `AntikytheraRunTime.getCompilationUnit(className)` |

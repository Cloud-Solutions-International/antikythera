# Antikythera Core — Agent Guide

Guidance for AI coding agents working on the **antikythera** core library.

Antikythera is a Java code intelligence engine: AST parsing, symbolic expression evaluation,
and dependency solving. Test generation is a **separate module** (`antikythera-test-generator`);
do not add test-generation logic here.

---

## Build & Test Commands

```bash
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn compile
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn test
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn install -Dmaven.test.skip=true
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn test -Dtest=TestEvaluator
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn test -Dtest=TestEvaluator#testReturnValue
```

> The system `/usr/bin/mvn` uses Java 8. Always prefix `mvn` with `JAVA_HOME=...`.

---

## Critical Rules

1. **No test generation logic here.** `TestGenerator`, `UnitTestGenerator`, `SpringTestGenerator`,
   `ServicesParser`, `RestControllerParser`, and `Antikythera` (CLI) live in `antikythera-test-generator`.

2. **`generator/` is shared model types only.** `TypeWrapper`, `MethodResponse`, `TruthTable`,
   `RepositoryQuery*`, `BaseRepositoryQuery`, `QueryMethod*`, `QueryType`,
   `AssertionConfidence`, `SerializationConfidence`, `CopyUtils` — nothing else.

3. **Do not move `TypeWrapper`.** Referenced by 90+ files across `parser/`, `evaluator/`, and `depsolver/`.

4. **`ITestGenerator` and `GeneratorState` are the module boundary.** `SpringEvaluator` communicates
   with `antikythera-test-generator` only through these two types.

5. **Always check `AntikytheraRunTime` before re-parsing.** All parsed `CompilationUnit`s are cached there.

6. **Thread safety.** `GeneratorState`, `AntikytheraRunTime`, and `Branching` hold static mutable state.
   Tests run sequentially — do not introduce parallel test execution.

---

## Package Overview

| Package | Key classes | Notes |
| :--- | :--- | :--- |
| `configuration/` | `Settings` | Loads `depsolver.yml` / app config |
| `parser/` | `AbstractCompiler`, `ImportUtils`, `MavenHelper`, `RepositoryParser`, `BaseRepositoryParser` | Entry point for all parsing; extend here for new frameworks |
| `evaluator/` | `AntikytheraRunTime`, `Evaluator`, `SpringEvaluator`, `ControlFlowEvaluator`, `MockingEvaluator`, `EvaluatorFactory`, `Branching`, `GeneratorState`, `ITestGenerator` | Core symbolic VM; `SpringEvaluator` is the branch-coverage driver |
| `exception/` | `AntikytheraException`, `EvaluatorException`, `GeneratorException`, `DepsolverException` | Shared exception hierarchy used across parser/evaluator/depsolver flows |
| `depsolver/` | `DepSolver`, `DependencyAnalyzer`, `Graph`, `CycleDetector`, `Resolver` | Dependency extraction; `DepSolver` is the entry point |
| `generator/` | `TypeWrapper`, `MethodResponse`, `TruthTable`, `RepositoryQuery`, `BaseRepositoryQuery`, `QueryMethod*`, `QueryType`, `AssertionConfidence`, `SerializationConfidence`, `CopyUtils` | Shared model types only — no test-generation code |
| `finch/` | `Finch` | Plugin/hook mechanism via ServiceLoader; must stay in core |

`SpringEvaluator` calls `ITestGenerator.createTests(md, response)` at each method exit — the concrete
implementation lives entirely in `antikythera-test-generator`.

---

## Test Setup

Tests require two repos cloned at the same level as this project:

- `antikythera-test-helper` — sample entities, services, controllers
- `antikythera-sample-project` — realistic Spring Boot integration fixtures

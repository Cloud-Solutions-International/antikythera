# Antikythera Architecture

## Overview

Antikythera is a Java symbolic execution engine that parses, evaluates, and
analyses Java source code at the AST level. Its primary use case is
branch-coverage-driven test generation for Spring Boot applications, powered
by the companion module **antikythera-test-generator**.

```
antikythera (core library)
    ^
    |  depends on
antikythera-test-generator (test generation)
    ^
    |  test fixtures
antikythera-test-helper
```

## Package Structure

```
sa.com.cloudsolutions.antikythera
 +-- configuration/     Settings and YAML config loading
 +-- depsolver/         Dependency graph construction and cycle resolution
 +-- evaluator/         Expression evaluation and symbolic execution engine
 |    +-- functional/   Functional interface proxy handling
 |    +-- logging/      Log recording during symbolic execution
 |    +-- mock/         Mockito stub generation and mock registry
 +-- exception/         Domain-specific exception hierarchy
 +-- finch/             External "finch" class loading and compilation
 +-- generator/         Shared model types for test generation
 +-- parser/            AST parsing, compilation, and import resolution
      +-- converter/    SQL/HQL query conversion and entity mapping
```

## Evaluator Hierarchy

The evaluator subsystem is the heart of antikythera. It forms a class
hierarchy where each level adds a specific concern:

```
Evaluator                     (expression evaluation, reflective invocation)
  +-- ControlFlowEvaluator    (branch-coverage-aware control flow, truth tables)
       +-- SpringEvaluator    (Spring/JPA awareness, repository handling, test orchestration)
       +-- MockingEvaluator   (mock return values, Mockito stub recording)
  +-- InnerClassEvaluator     (inner/nested class evaluation)
  +-- TestSuiteEvaluator      (test suite execution)
```

### Evaluator (~2700 lines)

Core expression evaluator. Handles:
- Literal, binary, unary, and conditional expression evaluation
- Variable declaration, assignment, and scope management
- Object creation (via evaluator or reflection)
- Method call dispatch (symbolic or reflective)
- Field access and scope chain resolution
- Loop execution (for, for-each, while, do-while)
- Try-catch-finally and exception handling
- Stream operation delegation (via `StreamEvaluator`)

### StreamEvaluator

Extracted from `Evaluator` to handle all `Stream`, `IntStream`, `LongStream`,
and `DoubleStream` operations. Adapts symbolic functional-interface
representations to their JDK counterparts and dispatches via reflection.

### ControlFlowEvaluator (~1970 lines)

Extends `Evaluator` with branch-coverage logic:
- Truth-table-driven condition setup
- Mock stubbing for conditional paths
- Precondition generation for targeting specific branches
- Setter/assignment-based condition materialization

### SpringEvaluator (~1800 lines)

Extends `ControlFlowEvaluator` with Spring framework awareness:
- JPA repository detection and query execution
- `@Autowired` field wiring (source code and bytecode)
- `ResponseEntity` handling and HTTP status extraction
- Test generation orchestration via `ITestGenerator`
- Branch iteration with safety limits

### MockingEvaluator (~870 lines)

Extends `ControlFlowEvaluator` for mock-based evaluation:
- Generates mock return values for autowired dependencies
- Records `Mockito.when().thenReturn()` stubs
- Handles repository method mocking (save, find, etc.)
- Optional handling with branching (present/empty paths)

## Key Subsystems

### Parser (`parser/`)

- **AbstractCompiler**: Central AST utilities -- type resolution, import
  lookup, method matching, and compilation unit management (~1800 lines).
- **DepsolvingParser**: Parses source files while resolving dependencies.
- **BaseRepositoryParser**: Parses JPA repository interfaces and derives
  query methods.
- **MavenHelper**: Maven POM parsing for dependency and property resolution.
- **ImportUtils**: Import statement analysis and resolution.

### Dependency Solver (`depsolver/`)

- **Graph / GraphNode**: Directed dependency graph with field/method
  granularity edges.
- **DepSolver**: Main entry point for dependency extraction and resolution.
- **DependencyAnalyzer**: Analyses class dependencies with hooks for
  custom processing.
- **CycleDetector / JohnsonCycleFinder**: Detect and report circular
  dependencies.
- **Extraction strategies**: `InterfaceExtractionStrategy`,
  `LazyAnnotationStrategy`, `SetterInjectionStrategy`,
  `MethodExtractionStrategy` for breaking cycles.

### Generator (`generator/`)

Shared model types used by both antikythera and antikythera-test-generator:

- **TypeWrapper**: Wraps a resolved type with its compilation unit, class,
  and metadata. Referenced by 90+ files -- do **not** move.
- **TruthTable**: Branch condition truth table for systematic path coverage.
- **MethodResponse**: Captures symbolic execution outcome (return value,
  HTTP status, exception context, captured output).
- **RepositoryQuery / BaseRepositoryQuery**: Query method representation.
- **CopyUtils**: Deep-copy utilities for AST nodes.
- **AssertionConfidence / SerializationConfidence**: Confidence levels for
  generated assertions and serialization.

### Configuration (`configuration/`)

- **Settings**: YAML-based configuration loading (`antikythera.yml`).
  Provides typed property access with defaults.

### Exception Hierarchy (`exception/`)

```
AntikytheraException (unchecked, base)
  +-- EvaluatorException    (symbolic evaluation failures)
  +-- AUTException          (application-under-test exceptions)
  +-- DepsolverException    (dependency resolution failures)
  +-- GeneratorException    (test generation failures)
```

### Finch (`finch/`)

- **Finch**: Loads and compiles external Java source files ("finches")
  that extend antikythera's evaluation capabilities at runtime.

## Module Boundary

Antikythera core communicates with antikythera-test-generator through
two interfaces:

- **ITestGenerator**: Contract for test generators. `SpringEvaluator`
  invokes generators through this interface.
- **GeneratorState**: Static shared state for the current generation
  session (when/then stubs, mock hints, etc.).

No test generation logic should exist in antikythera core. The
`generator/` package contains only shared model types.

## Static Mutable State

Several classes hold static mutable state that is shared across the
evaluation session. Tests run sequentially -- do **not** introduce
parallel test execution.

| Class | State | Purpose |
|-------|-------|---------|
| `AntikytheraRunTime` | Parsed compilation units, auto-wired variables, argument stack | Central runtime cache |
| `GeneratorState` | When/then stubs, mock hints, pending stubs | Test generation session state |
| `Branching` | Branch attempts, truth table entries | Branch coverage tracking |
| `MockingRegistry` | Mock targets, stub expressions | Mockito stub registry |
| `Reflect` | Class loading cache | Reflection utilities |

## Build and Test

```bash
# Build (requires Java 21)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn compile

# Test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test

# Test with antikythera-test-generator (clone as sibling)
cd ../antikythera-test-generator
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test
```

## Future Improvement Opportunities

- **Evaluator decomposition**: `Evaluator`, `ControlFlowEvaluator`, and
  `SpringEvaluator` remain large. The reflective invocation chain
  (`reflectiveMethodCall` -> `invokeReflectively` -> `invokeFallback` ->
  `invokeinAccessibleMethod` -> `invoke`) and scope chain resolution
  methods are tightly coupled to `returnValue` instance state, making
  extraction into separate classes non-trivial without an API redesign.
- **ControlFlowEvaluator**: Mixed concerns between condition setup,
  precondition generation, and branch materialization could be separated
  with a builder pattern.
- **Static mutable state**: Thread-local or scoped context objects would
  improve testability and enable future parallel execution.

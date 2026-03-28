# Antikythera Core — Agent Guide

Guidance for AI coding agents working on the **antikythera** core library.

Antikythera is a Java code intelligence engine: AST parsing, symbolic expression evaluation,
and dependency solving. Test generation is a **separate module** (`antikythera-test-generator`);
do not add test-generation logic here.

---

## Build & Test Commands

```bash
# Compile
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn compile

# Run tests
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn test

# Install locally (required before building antikythera-test-generator)
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn install -Dmaven.test.skip=true

# Single test class
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn test -Dtest=TestEvaluator

# Single test method
JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto mvn test -Dtest=TestEvaluator#testReturnValue
```

> The system `/usr/bin/mvn` uses Java 8. Always prefix `mvn` with
> `JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto` in shell sessions.

---

## ⚠️ Critical Rules

1. **No test generation logic here.** `TestGenerator`, `UnitTestGenerator`, `SpringTestGenerator`,
   `ServicesParser`, `RestControllerParser`, and `Antikythera` (CLI) live in `antikythera-test-generator`.
   Do not add them back or duplicate them.

2. **`generator/` is shared model types only.** The package contains `TypeWrapper`, `MethodResponse`,
   `TruthTable`, `RepositoryQuery*`, `CopyUtils` and nothing else. Do not add new generator classes
   that produce test code.

3. **Do not move `TypeWrapper`.** It is referenced by 90+ files across `parser/`, `evaluator/`, and
   `depsolver/`. Moving it would break all of those imports with no benefit.

4. **`ITestGenerator` and `GeneratorState` are the module boundary.** `SpringEvaluator` communicates
   with `antikythera-test-generator` only through these two types. Do not introduce direct imports of
   concrete generator classes (`TestGenerator` etc.) into the core.

5. **Always check `AntikytheraRunTime` before re-parsing.** All parsed `CompilationUnit`s are cached
   there. Re-parsing an already-loaded file wastes time and can introduce duplicate state.

6. **Thread safety.** `GeneratorState`, `AntikytheraRunTime`, and `Branching` hold static mutable state.
   Tests run sequentially (`<parallel>none</parallel>`) — do not introduce parallel test execution.

---

## Architecture

```
parser/
  AbstractCompiler       — parses .java files; smart classpath scanner and type resolver
  AntikytheraRunTime     — static cache: CompilationUnit map, TypeWrapper map
  MavenHelper            — reads pom.xml, resolves project dependencies onto classpath

evaluator/
  Evaluator              — symbolic VM: processes AST nodes, tracks Variable state, handles control flow
  SpringEvaluator        — extends Evaluator; simulates @Autowired, @Value, Spring context
  ControlFlowEvaluator   — handles if/else/switch/loop/try-catch
  MockingEvaluator       — intercepts method calls; builds Mockito when/then chains
  EvaluatorFactory       — creates SpringEvaluator (or subclass) for a given FQN
  Branching              — priority queue of conditional statements; tracks TRUE/FALSE/BOTH paths
  GeneratorState         — static holder for imports and when/then lists shared with test-generator
  ITestGenerator         — interface; SpringEvaluator holds List<ITestGenerator>
  AKBuddy                — ByteBuddy proxy factory for intercepting real object method calls
  ArgumentGenerator      — generates mock argument values for method parameters
  Variable               — wraps a value with its declared type

depsolver/
  DependencyAnalyzer     — depth-first traversal; collects transitive field/method dependencies
  DepSolver              — extends DependencyAnalyzer; clones only required members into output CUs
  Graph / GraphNode      — registry and node wrapper for the dependency graph

generator/  (shared model types only)
  TypeWrapper            — wraps a resolved Java type; generic parameter helpers
  MethodResponse         — captures return value or exception from an evaluated method
  TruthTable             — generates truth tables from boolean expressions
  RepositoryQuery et al. — models for JPA repository query analysis
  CopyUtils              — AST node copy helpers used by DepSolver

finch/
  Finch                  — plugin/hook mechanism; loaded by Evaluator at startup via ServiceLoader
```

---

## Key Integration Points with antikythera-test-generator

| Class | Package | Role |
| :--- | :--- | :--- |
| `ITestGenerator` | `evaluator` | Interface implemented by `TestGenerator` in test-generator |
| `GeneratorState` | `evaluator` | Static state (imports, when/then) written by evaluators, read by generators |
| `MethodResponse` | `generator` | Passed from `SpringEvaluator` to `ITestGenerator.createTests()` |
| `ArgumentGenerator` | `evaluator` | Injected into generators via `ITestGenerator.setArgumentGenerator()` |
| `Precondition` | `evaluator` | Passed via `ITestGenerator.setPreConditions()` |

When `SpringEvaluator` reaches a method exit point it calls:
```java
for (ITestGenerator gen : generators) {
    gen.createTests(md, response);
}
```
The concrete implementation of that call lives entirely in `antikythera-test-generator`.

---

## Common Patterns

### Parse and cache a class
```java
AbstractCompiler.preProcess();   // loads all classes in base_path
CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("com.example.UserService");
```

### Symbolically execute a method
```java
Evaluator eval = EvaluatorFactory.create("com.example.UserService", SpringEvaluator.class);
MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
    m -> m.getNameAsString().equals("getUser")).orElseThrow();
Variable result = eval.executeMethod(md);
```

### Resolve a type from within a CU
```java
TypeWrapper tw = AbstractCompiler.findType(cu, "UserDto");
```

### Extract minimal dependency closure
```java
Settings.loadConfigMap(new File("depsolver.yml"));
DepSolver solver = DepSolver.createSolver();
solver.processEntry("com.example.UserService#getUser");
```

---

## Test Setup

Tests require two external repositories cloned at the same level as this project:

- `antikythera-test-helper` — sample entities, services, controllers used as test fixtures
- `antikythera-sample-project` — realistic Spring Boot project for integration tests

VM argument required for tests (already set in `pom.xml`):
```
--add-opens java.base/java.util.stream=ALL-UNNAMED
```

---

## Key Files

| File | Purpose |
| :--- | :--- |
| `parser/AbstractCompiler.java` | Entry point for all parsing; extend here for new frameworks |
| `evaluator/Evaluator.java` | Core symbolic VM; add new expression handlers here |
| `evaluator/SpringEvaluator.java` | Spring-aware evaluation loop; branch-coverage driver |
| `evaluator/ControlFlowEvaluator.java` | if/else/switch/loop/try handling |
| `evaluator/MockingEvaluator.java` | Mock interception; writes to `GeneratorState` |
| `evaluator/GeneratorState.java` | Shared static state — clear between method executions |
| `evaluator/ITestGenerator.java` | Module boundary interface — keep minimal |
| `depsolver/DepSolver.java` | Dependency extraction entry point |
| `generator/TypeWrapper.java` | Do not move; imported everywhere |
| `finch/Finch.java` | Plugin hook; must stay in core (Evaluator calls it directly) |

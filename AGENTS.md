
# Antikythera - Java Code Intelligence Engine

**Current Version**: Agent Reference Guide
**Purpose**: A versatile platform for **Static Analysis**, **Symbolic Execution**, and **Automated Refactoring** of Java applications.

---

## 🚀 Core Capabilities

Antikythera is more than a test generator. It is a modular engine that understands, executes, and transforms Java code.

| Capability | Description | Modules |
| :--- | :--- | :--- |
| **🔎 Deep Analysis** | Parse source/bytecode, resolve complex types, and build dependency graphs. | `parser`, `depsolver` |
| **🧠 Symbolic Execution** | Execute code paths abstractly to understand behavior without running the app. | `evaluator` |
| **🔧 Transformation** | Refactor code, extract microservices, and convert queries (JPA/HQL → SQL). | `depsolver`, `generator` |
| **🧪 Test Generation** | Auto-generate Unit & API tests using the intelligence above. | `generator` |

---

## 🏗 Architecture: The "Engine" Concept

Think of Antikythera as a layered engine. You can invoke the lower layers directly for custom tasks.

```mermaid
graph TD
    A[Java Source / Bytecode] --> B[Parser Layer]
    B --> C{Core Intelligence}
    C --> D[Evaluator]
    C --> E[DepSolver]
    C --> F[Type Resolver]
    
    D --> G[Applications]
    E --> G
    F --> G
    
    G --> H[Test Generator]
    G --> I[Microservice Extractor]
    G --> J[Query Optimizer]
    G --> K[Custom Agent Tools]
```

### 1. The Parser Layer (`parser/`)
**Goal**: Turn code into a computable model.
*   **`AbstractCompiler`**: The foundation. Parses files and resolves types using a smart classpath scanner.
*   **`AntikytheraRunTime`**: Distributed cache for ASTs (`CompilationUnit`) and Type Metadata (`TypeWrapper`).

### 2. The Evaluator Layer (`evaluator/`)
**Goal**: "Run" the code safely to learn what it does.
*   **`Evaluator`**: A symbolic VM. It processes AST nodes, tracks variable states, and handles control flow (branches, loops).
*   **`SpringEvaluator`**: Aware of Spring Contexts. Can simulate `@Autowired` injection and `@Value` resolution.
*   **`AKBuddy`**: Creates dynamic proxies (`ByteBuddy`) so the Evaluator can intercept method calls on real objects.

### 3. The Dependency & Solver Layer (`depsolver/`)
**Goal**: Understand connections.
*   **`DepSolver`**: Builds directed graphs of class/method usage. Used for extracting slice-able code (e.g., "Extract this Service to a generated library").
*   **`Graph`**: Cyclic graph detection and resolution.

---

## 📦 TypeWrapper: The Universal Type Abstraction

`TypeWrapper` is the core type abstraction that bridges three representations of Java types:
- **AST-based** (`TypeDeclaration`) - from parsed source code
- **Reflection-based** (`Class<?>`) - from compiled bytecode/JARs
- **ResolvedType** - from JavaParser's symbol solver (preferred)

### Creating TypeWrapper Instances

**Factory Methods (Preferred)**
```java
// From AST TypeDeclaration
TypeWrapper wrapper = TypeWrapper.fromTypeDeclaration(classDecl);

// From reflection Class
TypeWrapper wrapper = TypeWrapper.fromClass(MyService.class);

// From enum constant (special case)
TypeWrapper wrapper = TypeWrapper.fromEnumConstant(enumConstant);

// From JavaParser's ResolvedType
TypeWrapper wrapper = TypeWrapper.fromResolvedType(resolvedType);

// For unresolved types, use the sentinel
if (cannotResolve) {
    return TypeWrapper.UNKNOWN;
}
```

**Via AbstractCompiler (Most Common)**
```java
// Resolve by name
TypeWrapper wrapper = AbstractCompiler.findType(cu, "MyService");

// Resolve by Type node
TypeWrapper wrapper = AbstractCompiler.findType(cu, fieldType);

// Get generic type arguments (returns [TypeArg1, ..., RawType])
List<TypeWrapper> types = AbstractCompiler.findWrappedTypes(cu, genericType);
```

### Key Methods

| Method | Description |
| :--- | :--- |
| `getResolvedType()` | Get underlying JavaParser ResolvedType (preferred) |
| `isResolved()` | Check if ResolvedType is available |
| `getFullyQualifiedName()` | Get FQN (uses ResolvedType, falls back to legacy) |
| `isPrimitive()` | Check if primitive type (int, boolean, etc.) |
| `isArray()` | Check if array type |
| `getComponentType()` | Get array component type |
| `getTypeArguments()` | Get generic type parameters |
| `getFields()` | Get fields via `ResolvedFieldAdapter` |

### Spring Annotation Detection

TypeWrapper provides dynamic annotation checking with reflection fallback:

```java
wrapper.isController()  // @Controller or @RestController
wrapper.isService()     // @Service
wrapper.isComponent()   // @Component
wrapper.isEntity()      // @Entity (jakarta or javax)
wrapper.isInterface()   // Interface check
```

### Type Compatibility

```java
// Check if thisType can be assigned from otherType
boolean compatible = thisWrapper.isAssignableFrom(otherWrapper);

// Works across boundaries:
// - Source type extending JAR type
// - JAR type implementing source interface
// - Generic type compatibility
```

### Field Access with ResolvedFieldAdapter

```java
// Get all declared fields (works with both AST and reflection)
List<ResolvedFieldAdapter> fields = wrapper.getFields();

for (ResolvedFieldAdapter field : fields) {
    String name = field.getName();
    TypeWrapper fieldType = field.getType();
    boolean isStatic = field.isStatic();
    boolean hasAnnotation = field.hasAnnotation("jakarta.persistence.Column");
}
```

### Legacy Methods (Backward Compatible)

These methods implement lazy derivation - they attempt to derive values from `ResolvedType` when the legacy field is null:

```java
// Returns TypeDeclaration (lazily derived from AntikytheraRunTime cache)
TypeDeclaration<?> type = wrapper.getType();

// Returns Class (lazily derived via Class.forName())
Class<?> clazz = wrapper.getClazz();
```

### Special Cases

| Case | Handling |
| :--- | :--- |
| **Enum Constants** | Use `fromEnumConstant()`. Cannot be represented as ResolvedType. |
| **Primitives** | Fully supported via `isPrimitive()` and `getComponentType()` for arrays. |
| **Generics** | Use `getTypeArguments()`. Ordering preserved: `[TypeArg1, ..., RawType]`. |
| **Unknown Types** | Use `TypeWrapper.UNKNOWN` sentinel instead of null. |

---

## 🛠 versatile Use Cases & Entry Points

### Use Case A: "I need to analyze code dependencies"
*Don't run the test generator. Use the Solver directly.*

```java
// 1. Configure the analyzer
DependencyAnalyzer analyzer = new DependencyAnalyzer();

// 2. Collect dependencies for a set of methods
Set<GraphNode> functionalitySlice = analyzer.collectDependencies(targetMethods);

// 3. Query the graph
boolean hasCycle = analyzer.hasCycles(functionalitySlice);
Set<GraphNode> externalDeps = analyzer.getCrossboundaryDependencies(functionalitySlice);
```

### Use Case B: "I need to understand what a method returns"
*Use the Evaluator to virtually execute the method.*

```java
// 1. Create an Evaluator for the class
Evaluator eval = EvaluatorFactory.create("com.example.PricingService", SpringEvaluator.class);

// 2. Setup inputs (optional)
eval.setField("basePrice", new Variable(100.0));

// 3. Execute a method AST node
Variable result = eval.executeMethod(calculateMethodNode);

// 4. Inspect the result
System.out.println("Returned: " + result.getValue()); // "120.0"
```

### Use Case C: "I need to translate JPA/HQL to SQL"
*Use the Converter subsystem.*

```java
// Method Name -> SQL
List<String> parts = MethodToSQLConverter.extractComponents("findActiveUsersByRegion");
// Output logic: WHERE status = 'ACTIVE' AND region = ?

// HQL -> SQL
HQLParserAdapter adapter = new HQLParserAdapter(cu, entityType);
ConversionResult res = adapter.convertToNativeSQL("FROM User u WHERE u.id = :id");
System.out.println(res.getNativeSql()); // "SELECT * FROM users WHERE id = ?"
```

### Use Case D: "I need to generate tests"
*The classic use case.*

```java
Antikythera.getInstance().generateUnitTests();
```

---

## 🔧 Key Configuration (`generator.yml`)

The engine is configured via YAML.

```yaml
base_path: /src/main/java       # Where to look for code
output_path: /src/test/java     # Where to put artifacts
database:
  url: jdbc:postgres://...      # For live query analysis (optional)
  run_queries: false            # Set true to execute queries during analysis
```

## 🧩 Extending the Engine

*   **New Language Feature?** Add a handler to `Evaluator.evaluateExpression`.
*   **New Framework?** Extend `AbstractCompiler` (e.g., `QuarkusCompiler`).
*   **New Output?** Implement a new Generator (e.g., `DocumentGenerator` using `DepSolver` data).

---

## 💡 Agent "Cheat Sheet"

| Task | Core Component | Method to Call |
| :--- | :--- | :--- |
| **Resolve Type** | `parser.AbstractCompiler` | `findType(cu, "Name")` |
| **Parse File** | `parser.AbstractCompiler` | `compile(path)` |
| **Execute Logic** | `evaluator.Evaluator` | `evaluateMethodCall(mce)` |
| **Find Dependencies** | `depsolver.DepSolver` | `processMethod(signature)` |
| **Convert Query** | `parser.converter` | `MethodToSQLConverter` |
| **Get Entity Info** | `parser.converter` | `EntityMappingResolver` |
| **Create TypeWrapper** | `generator.TypeWrapper` | `fromTypeDeclaration()`, `fromClass()` |
| **Check Type Compatibility** | `generator.TypeWrapper` | `isAssignableFrom(other)` |
| **Get Type Fields** | `generator.TypeWrapper` | `getFields()` → `ResolvedFieldAdapter` |
| **Check Spring Annotations** | `generator.TypeWrapper` | `isService()`, `isController()`, etc. |

**Rule of Thumb**: Always check `AntikytheraRunTime` caches before doing heavy lifting.

**TypeWrapper Tip**: Prefer factory methods (`TypeWrapper.fromXxx()`) over deprecated constructors. Use `TypeWrapper.UNKNOWN` instead of null for unresolved types.

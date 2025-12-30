# Antikythera - AI Agent Guide

**Technical reference for AI agents working with Antikythera**

For project overview and capabilities, see [README.md](README.md).

---

## Table of Contents
1. [Quick Start](#quick-start)
2. [Core Capabilities](#core-capabilities)
3. [Key Modules](#key-modules)
4. [Common Patterns](#common-patterns)
5. [Configuration](#configuration)
6. [Development Guide](#development-guide)

---

## Quick Start

### Entry Points

**Generate Tests:**
```java
Antikythera antk = Antikythera.getInstance();
antk.preProcess();           // Parse source files
antk.generateApiTests();     // REST controller tests
antk.generateUnitTests();    // Service class tests
```

**Extract Dependencies:**
```java
DepSolver solver = DepSolver.createSolver();
solver.processMethod("com.example.UserService#createUser");
solver.dfs();                // Build dependency graph
solver.writeFiles();         // Output minimal codebase
```

**Parse & Analyze Code:**
```java
// Controllers
RestControllerParser parser = new RestControllerParser("com.example.UserController");
parser.start();

// Services
ServicesParser serviceParser = new ServicesParser("com.example.UserService");
serviceParser.start();

// Repositories (with query extraction)
RepositoryParser repoParser = new RepositoryParser("com.example.UserRepository");
```

---

## Core Capabilities

### 1. Automated Test Generation
**Purpose:** Generate JUnit tests with assertions, mocks, and preconditions

**Key Classes:**
- `Antikythera` - Main entry point
- `SpringTestGenerator` - REST controller tests (RESTAssured)
- `UnitTestGenerator` - Service class tests (Mockito)
- `TestGenerator` - Base test generation logic

**What It Does:**
- Explores all code branches automatically
- Creates meaningful assertions from return values
- Generates mock setups for dependencies
- Handles void methods via logging/side effects

### 2. Dependency Analysis & Extraction
**Purpose:** Extract minimal, compilable code for migration or microservices

**Key Classes:**
- `DepSolver` - Dependency graph builder (DFS algorithm)
- `Graph` & `GraphNode` - Graph data structures
- `InterfaceSolver` - Interface implementation resolution
- `DTOHandler` - DTO/Entity handling

**What It Does:**
- Identifies all dependencies (fields, parameters, return types, annotations)
- Handles inheritance, interfaces, generics, nested classes
- Generates properly sorted, compilable code
- Organizes imports and member ordering

### 3. Query Parsing & Analysis
**Purpose:** Parse SQL, HQL/JPQL, and SpEL from JPA repositories

**Key Classes:**
- `BaseRepositoryParser` - Query extraction and parsing
- `HQLParserAdapter` - HQL/JPQL → SQL conversion
- `MethodToSQLConverter` - Derive SQL from method names
- `EntityMappingResolver` - JPA entity metadata

**What It Does:**
- Extracts @Query annotations (native SQL, HQL, JPQL)
- Derives queries from method names (findByUsernameAndEmail, etc.)
- Converts HQL/JPQL to native SQL
- Handles SpEL expressions (`:#{#variableName}`)
- Supports PostgreSQL and Oracle dialects

**Supported Patterns:**
- `findBy[Field]`, `countBy[Field]`, `deleteBy[Field]`
- `findBy[Field]And[Field2]`, `findBy[Field]Or[Field2]`
- `findBy[Field]GreaterThan`, `findBy[Field]Between`
- `findBy[Field]Containing`, `findBy[Field]StartingWith`
- `findBy[Field]OrderBy[Field2]Asc/Desc`
- And 20+ more patterns

### 4. Expression Evaluation
**Purpose:** Symbolically execute Java code to discover behavior

**Key Classes:**
- `Evaluator` - Base expression evaluation engine
- `SpringEvaluator` - Spring-aware evaluation
- `ControlFlowEvaluator` - Branch/loop handling
- `FPEvaluator` - Lambda/functional programming support

**What It Does:**
- Executes code paths using reflection
- Tracks variables, fields, scopes
- Handles branching (if/else, switch, loops)
- Supports functional programming (lambdas, streams)
- Generates test data from execution traces

---

## Key Modules

### Parser Module (`parser/`)
**Core:** `AbstractCompiler` - JavaParser wrapper with type resolution

**Specialized Parsers:**
- `RestControllerParser` - REST endpoints (@RestController, @GetMapping, etc.)
- `ServicesParser` - Business logic (@Service, @Component)
- `RepositoryParser` - JPA repositories (extends BaseRepositoryParser)
- `BaseRepositoryParser` - Query extraction & conversion

**Utilities:**
- `TypeWrapper` - Enhanced type information
- `ImportUtils` - Import management
- `Callable` - Method/constructor abstraction

### Evaluator Module (`evaluator/`)
**Core:** `Evaluator` - Expression evaluation engine

**Specialized Evaluators:**
- `SpringEvaluator` - Spring framework support
- `ControlFlowEvaluator` - Branching logic
- `MockingEvaluator` - Mocked dependencies
- `FPEvaluator` - Functional interfaces

**Support:**
- `Variable` - Variable state tracking
- `AntikytheraRunTime` - Global caches (compilation units, types)
- `ArgumentGenerator` - Test argument generation

### Generator Module (`generator/`)
**Core:** `TestGenerator` - Base test generation

**Specialized Generators:**
- `SpringTestGenerator` - API tests
- `UnitTestGenerator` - Unit tests
- `MethodResponse` - Captures method execution results

**Utilities:**
- `CopyUtils` - File/project structure creation
- `TestCaseWriter` - Test file writing

### DepSolver Module (`depsolver/`)
**Core:** 
- `DependencyAnalyzer` - Base class for dependency analysis (analysis-only, no code generation)
- `DepSolver` extends `DependencyAnalyzer` - Full dependency extraction with code generation

**Analysis API:**
```java
// Analysis-only mode (no code generation)
DependencyAnalyzer analyzer = new DependencyAnalyzer();
Set<GraphNode> deps = analyzer.collectDependencies(methods);
Set<GraphNode> filtered = analyzer.collectDependencies(methods, node -> !isCycleType(node));

// Code generation mode
DepSolver solver = DepSolver.createSolver();
solver.processMethod("com.example.Service#method");
solver.dfs();
```

**Query API:**
```java
// Query discovered dependencies
Set<MethodDeclaration> methods = DependencyQuery.getMethods(deps);
Set<FieldDeclaration> fields = DependencyQuery.getFields(deps);
Set<MethodDeclaration> refs = DependencyQuery.getMethodsReferencingType("FQN", deps);
```

**Support:**
- `Graph` & `GraphNode` - Dependency graph
- `DependencyQuery` - Query utilities for dependency results
- `InterfaceSolver` - Find implementations
- `Resolver` - Type resolution
- `DTOHandler` - DTO/Entity handling

### Configuration Module (`configuration/`)
**Core:** `Settings` - YAML configuration management

See [Configuration](#configuration) section for complete YAML structure and key properties.

---

## Common Patterns

### Pattern: Type Resolution
```java
// Always check cache first
CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);

// Resolve types
TypeWrapper type = AbstractCompiler.findType(cu, "TypeName");
```

### Pattern: Query Conversion
```java
// HQL → SQL
HQLParserAdapter adapter = new HQLParserAdapter(cu, entityWrapper);
ConversionResult result = adapter.convertToNativeSQL(hqlQuery);
String sql = result.getSql();

// Method name → SQL
List<String> components = MethodToSQLConverter.extractComponents("findByUsernameAndEmail");
StringBuilder sql = new StringBuilder();
MethodToSQLConverter.buildSelectAndWhereClauses(components, sql, "users");
```

### Pattern: Dependency Extraction
```java
// Extract dependencies for a method
DepSolver solver = DepSolver.createSolver();
solver.processMethod("com.example.UserService#createUser");
solver.dfs();

// Or for entire class
for (MethodDeclaration md : classDecl.getMethods()) {
    Graph.createGraphNode(md);
}
solver.dfs();
solver.writeFiles();
```

### Pattern: Expression Evaluation
```java
// Create evaluator
Evaluator evaluator = EvaluatorFactory.create(className, SpringEvaluator.class);

// Evaluate expression
Variable result = evaluator.evaluateExpression(expression);
Object value = result.getValue();
```

---

## Configuration

### YAML Structure

**generator.yml** - Main configuration file

```yaml
base_path: /path/to/source/code/src/main/java
output_path: /path/to/output/src/test/java
base_package: com.example
skip_getters_setters: true

controllers:
  - com.example.UserController
  - com.example.OrderController

services:
  - com.example.UserService
  - com.example.OrderService

database:
  url: jdbc:postgresql://localhost:5432/mydb
  username: user
  password: pass
  run_queries: true              # Execute queries during test generation
  
query_conversion:
  enabled: true                  # Enable HQL→SQL conversion
  fallback_on_failure: true      # Use original query if conversion fails
  cache_results: true
```

**depsolver.yml** - Dependency extraction configuration

```yaml
base_path: /path/to/source/src/main/java
output_path: /path/to/output/src/main/java
base_package: com.example

methods:
  - com.example.UserService#createUser
  - com.example.OrderService#processOrder
```

### Key Settings

- `base_path` - Source code location (must include `src/main/java`)
- `output_path` - Generated code output location
- `base_package` - Base Java package for generated code
- `controllers` - REST controller classes to process
- `services` - Service classes to process
- `repositories` - JPA repository interfaces
- `extra_exports` - Additional packages to include in classpath
- `skip_getters_setters` - Ignore simple getters/setters
- `test_privates` - Generate tests for private methods (default: false)

---

## Architecture Overview

### Three-Phase Pipeline for test generation

```
Parse → Analyze → Generate
  │        │         │
  ├─ JavaParser     ├─ Reflection    ├─ JUnit Tests
  ├─ Type Cache     ├─ Branching     ├─ Mockito Setup
  └─ Symbol Solver  └─ Execution     └─ Assertions
```

**Phase 1: Parse**
- JavaParser builds ASTs from source files
- Types are resolved and cached in `AntikytheraRunTime`
- Imports, inheritance, and references are tracked

**Phase 2: Analyze** 
- Expression evaluator executes code symbolically
- Branches are explored via truth tables
- Dependencies are tracked via `DepSolver`
- Return values and side effects are captured

**Phase 3: Generate**
- Test methods with proper assertions
- Mock setups for dependencies
- Preconditions and argument generation
- Organized file structure

### Key Design Patterns

**Factory Pattern:**
- `EvaluatorFactory` - Creates appropriate evaluator instances
- `Graph.createGraphNode()` - Graph node factory

**Visitor Pattern:**
- `VoidVisitorAdapter` - AST traversal (JavaParser)
- Custom visitors for dependency resolution

**Strategy Pattern:**
- Different test generators for different architectures
- Multiple evaluators for different contexts

**Singleton Pattern:**
- `Antikythera.getInstance()`
- `DepSolver.createSolver()`
- `AntikytheraRunTime` - Global caches

---

## Development Guide

### Adding New Capabilities

**New Expression Type:**
1. Add case in `Evaluator.evaluateExpression()`
2. Implement evaluation logic
3. Add test cases

**New Repository Method Pattern:**
1. Update `MethodToSQLConverter.extractComponents()`
2. Add keyword to component list
3. Test with various method names

**New Database Dialect:**
1. Add to `DatabaseDialect` enum
2. Update query conversion logic in `BaseRepositoryParser`
3. Handle dialect-specific syntax (LIMIT vs ROWNUM, etc.)

**New Parser Type:**
1. Extend `AbstractCompiler`
2. Implement parsing logic
3. Add to `Antikythera` workflow if needed

### Testing Requirements

**VM Arguments Required:**
```
--add-opens java.base/java.util.stream=ALL-UNNAMED
```

**Test Dependencies:**
- Clone `antikythera-sample-project` repository
- Clone `antikythera-test-helper` repository
- Maintain folder structure as documented

**Running Tests:**
```bash
mvn test
```

Currently 660+ unit and integration tests.

### Common Pitfalls

**Type Resolution:**
- Always check `AntikytheraRunTime` cache first
- Resolution order: Current CU → imports → same package → java.lang → classpath

**Query Conversion:**
- HQL/JPQL entities must map to database tables
- SpEL expressions are preserved during conversion
- Always enable fallback mode for robustness

**Dependency Extraction:**
- Cyclic dependencies handled via `visited` flag
- Abstract methods trigger subclass discovery
- Generic types require careful handling

**Evaluation:**
- Reflection requires proper VM flags
- Not all Java constructs can be evaluated
- Some expressions return null legitimately

---

## Quick Reference

### Most Used Classes

| Class | Purpose | Package |
|-------|---------|---------|
| `Antikythera` | Main entry point | (root) |
| `Settings` | Configuration | configuration |
| `AbstractCompiler` | Base parser | parser |
| `DepSolver` | Dependency extraction | depsolver |
| `Evaluator` | Expression evaluation | evaluator |
| `SpringEvaluator` | Spring-aware evaluation | evaluator |
| `BaseRepositoryParser` | Query parsing | parser |
| `HQLParserAdapter` | HQL conversion | parser.converter |
| `TestGenerator` | Test generation | generator |
| `AntikytheraRunTime` | Global caches | evaluator |

### Most Used Methods

| Method | Purpose |
|--------|---------|
| `AntikytheraRunTime.getCompilationUnit(String)` | Get cached AST |
| `AbstractCompiler.findType(CompilationUnit, String)` | Resolve type |
| `AbstractCompiler.findFullyQualifiedName(CompilationUnit, String)` | Get FQN |
| `Graph.createGraphNode(Node)` | Create dependency node |
| `DepSolver.dfs()` | Build dependency graph |
| `Evaluator.evaluateExpression(Expression)` | Evaluate code |
| `Settings.getProperty(String)` | Get configuration |

---

For additional resources and contributing guidelines, see the end of this document.

- [Additional Resources](#additional-resources)
- [Contributing](#contributing)

#### Parser Converter Subpackage
Located at `parser/converter`, this is a JPA/HQL to SQL conversion subsystem.

**Purpose:** Convert JPA/JPQL/HQL queries to native SQL with dialect support and entity metadata resolution.

##### Core Converter Classes

**`BasicConverter.java`**
Static utility class for converting Java field names to snake_case SQL columns.

**Key Features:**
- Converts camelCase field names to snake_case column names
- Processes SELECT statements (projections, WHERE, GROUP BY, ORDER BY, HAVING)
- Handles JOIN operations with entity metadata
- Normalizes SQL projections (replaces single-alias projections with *)

**Key Methods:**
- `convertFieldsToSnakeCase(Statement stmt, TypeWrapper entity)` - Main entry point for field conversion
- `convertPlainSelectToSnakeCase(PlainSelect, TypeWrapper)` - Converts all clauses to snake_case
- `normalizeProjection(PlainSelect)` - Optimizes SELECT projections
- `processJoins(TypeWrapper, PlainSelect)` - Handles JOIN clauses with entity metadata

**`HQLParserAdapter.java`**
Adapter that bridges Antikythera's converter interface with the external hql-parser library (ANTLR4-based). **This is the primary HQL converter used in production.**

**Key Features:**
- Uses `com.raditha.hql.parser.HQLParser` for HQL/JPQL parsing with ANTLR4 grammar
- Converts parsed HQL to PostgreSQL dialect using `HQLToPostgreSQLConverter`
- Registers entity metadata mappings from JavaParser annotations automatically
- Provides ConversionResult with native SQL, parameter mappings, and referenced tables
- Handles SpEL expressions (`:#{#variableName}` patterns)
- Supports CAST expressions and AS aliases
- Processes constructor expressions (SELECT NEW)

**Key Methods:**
- `convertToNativeSQL(String jpaQuery)` - Main entry point: parses HQL and converts to SQL
- `registerMappings(MetaData)` - Registers entity-to-table and field-to-column mappings
- Handles special HQL features like subqueries, aggregates, and complex joins

**Dependencies:**
- External library: `com.github.e4c5:hql-parser:0.0.15` (via JitPack)
- ANTLR4 runtime: `org.antlr:antlr4-runtime:4.13.1`
- Currently supports PostgreSQL dialect; Oracle support planned

**`MethodToSQLConverter.java`**
Utility class that converts Spring Data JPA repository method names to SQL fragments.

**Purpose:** Extract SQL generation logic from BaseRepositoryParser for better separation of concerns.

**Key Features:**
- Parses method names using JPA keywords (findBy, countBy, deleteBy, etc.)
- Handles complex method patterns:
  - `findByXAndY` → `WHERE x = ? AND y = ?`
  - `findByXOrY` → `WHERE x = ? OR y = ?`
  - `findByXBetween` → `WHERE x BETWEEN ? AND ?`
  - `findByXGreaterThan` → `WHERE x > ?`
  - `findByXIn` → `WHERE x IN (?)`
  - `findByXContaining` → `WHERE x LIKE ?`
  - `OrderByXDesc` → `ORDER BY x DESC`
- Normalizes method names (e.g., `findUserByEmail` → `findByEmail`)
- Handles edge cases: field names starting with lowercase, short keywords (In, Or, Not)

**Key Methods:**
- `extractComponents(String methodName)` - Parses method name into components (keywords and fields)
- `buildSelectAndWhereClauses(List<String> components, StringBuilder sql, String tableName)` - Builds SQL from components
- `handleQueryType()` - Handles query type keywords (findAll, findBy, countBy, etc.)
- `handleOperator()` - Handles comparison and logical operators
- `handleOrderBy()` - Handles ORDER BY clauses

**Supported Query Types:**
- `findAll`, `findAllById`, `findBy`, `findFirstBy`, `findTopBy`, `findDistinctBy`
- `countBy`, `deleteBy`, `removeBy`, `existsBy`
- `readBy`, `queryBy`, `searchBy`, `streamBy`, `get`

**Supported Operators:**
- Comparison: `GreaterThan`, `LessThan`, `Between`, `Before`, `After`
- Equality: `Equals`, `Is`, `IsNot`
- Null checks: `IsNull`, `IsNotNull`
- Collections: `In`, `NotIn`
- Strings: `Like`, `Containing`, `StartingWith`, `EndingWith`
- Boolean: `IsTrue`, `IsFalse`, `True`, `False`
- Logical: `And`, `Or`, `Not`

##### Dialect Support System

**`DatabaseDialect.java` (Enum)**
Defines supported database dialects with dialect-specific behaviors.

**Supported Dialects:**
- **ORACLE** - Oracle Database
- **POSTGRESQL** - PostgreSQL

**Key Features:**
- Dialect detection from JDBC URL
- Dialect-specific SQL transformations
- Used by BaseRepositoryParser and RepositoryParser for query conversion

##### Entity Metadata System

**`EntityMappingResolver.java`**
Extracts JPA entity metadata using reflection and annotations.

**Features:**
- Analyzes JPA @Entity, @Table, @Column annotations
- Resolves relationships (@OneToOne, @ManyToOne, etc.)
- Handles @JoinColumn mappings
- Automatic camelCase → snake_case conversion
- Metadata caching for performance

**Key Methods:**
- `resolveEntityMetadata(Class<?> entityClass)` - Build metadata for single entity
- `resolveEntityMetadata(Collection<Class<?>>)` - Build combined metadata
- `buildTableMapping(Class<?>, String)` - Extract @Table info
- `buildPropertyToColumnMappings(Class<?>, TableMapping)` - Map properties to columns
- `buildRelationshipMappings(Class<?>, TableMapping)` - Extract @JoinColumn info
- `determineJoinType(Field)` - Determine JOIN type from annotations
- `convertCamelCaseToSnakeCase(String)` - Naming conversion

**Supported Annotations:**
- @Entity, @Table (with schema support)
- @Column (name, nullable)
- @Id
- @OneToOne, @ManyToOne, @OneToMany, @ManyToMany
- @JoinColumn (name, referencedColumnName)
- @Transient (ignored fields)

**`EntityMetadata.java`**
Immutable container for entity mapping information.

**Contains:**
- Entity-to-table mappings: `Map<String, TableMapping>`
- Property-to-column mappings: `Map<String, ColumnMapping>` (via TableMapping)
- Relationship mappings: `Map<String, JoinMapping>` (via TableMapping)

**Methods:**
- `getTableMapping(String entityName)` - Get table for entity
- `hasEntityMetadata(String entityName)` - Check existence
- `getAllTableMappings()` - Get all tables

##### Mapping Data Structures

**`TableMapping.java` (Record)**
Maps JPA entity to database table.

**Fields:**
- `entityName` - JPA entity name
- `tableName` - Database table name
- `schema` - Database schema (optional)
- `propertyToColumnMap` - Map of property names to columns

**Methods:**
- `getColumnMapping(String propertyName)` - Get column for property (returns column name as String)
- `hasPropertyMapping(String)` - Check if property mapped

**`JoinMapping.java` (Record)**
Maps entity relationship to SQL join.

**Fields:**
- `propertyName` - Relationship property
- `targetEntity` - Target entity name
- `joinColumn` - Join column in source table
- `referencedColumn` - Referenced column in target table (usually 'id')
- `joinType` - JOIN type (INNER, LEFT, RIGHT, FULL)
- `sourceTable` - Source table name
- `targetTable` - Target table name

**Methods:**
- `toSqlJoinClause()` - Generate SQL JOIN statement

**`JoinType.java` (Enum)**
Defines SQL join types: INNER, LEFT, RIGHT, FULL

**`ParameterMapping.java`**
Maps named parameters to positional parameters.

**Fields:**
- Named parameter name
- Position in SQL (1-indexed)
- Parameter Java type
- Associated column name

##### Conversion Result Structures

**`ConversionResult.java`**
Encapsulates query conversion outcome.

**Success Case:**
- `nativeSql` - Converted SQL string
- `parameterMappings` - List of ParameterMapping
- `referencedTables` - Set of table names
- `successful` - true

**Failure Case:**
- `errorMessage` - Description of failure
- `failureReason` - ConversionFailureReason enum
- `successful` - false

**Methods:**
- `success(String nativeSql)` - Create successful result
- `failure(String error, ConversionFailureReason)` - Create failure result

**`ConversionFailureReason.java` (Enum)**
Categorizes conversion failures:
- DIALECT_INCOMPATIBILITY
- PARSER_ERROR
- UNSUPPORTED_FEATURE
- ENTITY_METADATA_MISSING
- INVALID_QUERY_SYNTAX

**`QueryConversionException.java`**
Exception thrown during conversion failures.

##### Usage Example

```java
// Setup
EntityMappingResolver resolver = new EntityMappingResolver();
EntityMetadata metadata = resolver.resolveEntityMetadata(User.class);

// Use BasicConverter for camelCase to snake_case conversion
Statement stmt = CCJSqlParserUtil.parse("SELECT u FROM User u WHERE u.userName = ?");
TypeWrapper userEntity = new TypeWrapper(User.class);
BasicConverter.convertFieldsToSnakeCase(stmt, userEntity);

// Or use HQLParserAdapter for full HQL to SQL conversion
HQLParserAdapter adapter = new HQLParserAdapter(compilationUnit, userEntity);
String hql = "SELECT u FROM User u WHERE u.userName = :name";
ConversionResult result = adapter.convertToNativeSQL(hql);

if (result.isSuccessful()) {
    String sql = result.getNativeSql();
    // "SELECT u.* FROM users u WHERE u.user_name = ?"
    List<ParameterMapping> params = result.getParameterMappings();
}
```

##### Configuration

```yaml
database:
  url: jdbc:postgresql://localhost:5432/db
  query_conversion:
    enabled: true
    fallback_on_failure: true
    log_conversion_failures: true
    cache_results: true
```

---

### 2. Evaluator Module (`sa.com.cloudsolutions.antikythera.evaluator`)

**Purpose:** Execute Java code symbolically to capture runtime behavior.

#### Key Classes

##### `Evaluator.java`
The core expression evaluation engine.

**Core Responsibilities:**
- Evaluate expressions (literals, method calls, binary ops, etc.)
- Maintain variable scopes (fields, locals, parameters)
- Execute methods (via reflection or sub-evaluators)
- Handle control flow (branching, loops, exceptions)
- Track return values and side effects

**Important Methods:**
- `evaluateExpression(Expression)` - Main entry point for expression evaluation
- `evaluateMethodCall(MethodCallExpr)` - Executes method calls
- `evaluateVariableDeclaration(Expression)` - Handles variable declarations
- `createObject(ObjectCreationExpr)` - Instantiates objects (reflection or evaluator)
- `getValue(Node, String)` - Retrieves variable value from scope
- `setLocal(Node, String, Symbol)` - Sets local variable in scope
- `setField(String, Symbol)` - Sets field value
- `executeMethod(MethodDeclaration)` - Executes a method body (implemented in subclasses)

**Symbol Management:**
- **Fields:** `Map<String, Symbol> fields` - Instance/static fields
- **Locals:** `Map<Integer, Map<String, Symbol>> locals` - Block-scoped variables
  - Key is `BlockStmt.hashCode()` to distinguish scopes
  - Includes method parameters

**Expression Types Handled:**
- Literals (String, int, boolean, double, long, null)
- Variable references (NameExpr)
- Method calls (MethodCallExpr)
- Binary expressions (+, -, *, /, &&, ||, ==, etc.)
- Unary expressions (!, ++, --, -)
- Assignments (=, +=, -=, etc.)
- Object creation (new)
- Field access (obj.field)
- Array operations (creation, access)
- Lambda expressions (converted to functional interfaces)
- Conditional expressions (ternary)
- instanceof checks

##### `EvaluatorFactory.java`
Factory for creating evaluator instances.

**Methods:**
- `create(String className, Class<? extends Evaluator> evalClass)` - Eager creation
- `createLazily(String className, Class<? extends Evaluator> evalClass)` - Lazy creation

**Evaluator Hierarchy:**
Different evaluators for different contexts (test generation, repository parsing, etc.).

##### `AntikytheraRunTime.java`
Global runtime state management.

**Manages:**
- Compilation unit cache: `Map<String, CompilationUnit>`
- Type declaration cache: `Map<String, TypeWrapper>`
- Static variable cache: `Map<String, Variable>`
- Variable stack: `Stack<Variable>` (for method arguments)
- Subclass mappings: `Map<String, Set<String>>`

##### `Variable.java`
Represents a runtime variable with type and value.

**Fields:**
- `Type type` - JavaParser type
- `Object value` - Runtime value (may be Evaluator, primitive, object)
- `Class<?> clazz` - Resolved Java class
- `List<Expression> initializer` - Original initialization expression

##### `Arithmetics.java`
Static utility class for arithmetic operations on Variables.

**Purpose:**
- Handles arithmetic operations (+, -, *, /, %) for numeric types
- Supports string concatenation
- Type promotion for numeric operations (int, long, float, double)

**Key Methods:**
- `operate(Variable left, Variable right, BinaryExpr.Operator)` - Perform arithmetic or string concatenation
- `performOperation(Number, Number, BinaryExpr.Operator)` - Core numeric operation

##### `BinaryOps.java`
Static utility class for binary operations and condition manipulation.

**Purpose:**
- Equality checking for Variables
- Condition negation for branching
- Numeric comparison support

**Key Methods:**
- `checkEquality(Variable left, Variable right)` - Compare two Variables for equality
- `negateCondition(Expression)` - Negate a conditional expression (used for branch coverage)

##### `ControlFlowEvaluator.java`
Handles control flow evaluation (if/else, switch, loops).

**Purpose:**
- Evaluates control flow statements
- Manages branching execution
- Tracks execution paths for test generation

##### Argument Generation Classes

**`ArgumentGenerator.java`**
Base class for generating test method arguments.

**`DatabaseArgumentGenerator.java`**
Generates test arguments by querying the database for real data.

**`DummyArgumentGenerator.java`**
Generates dummy/placeholder test arguments.

**`NullArgumentGenerator.java`**
Generates null arguments for test cases.

##### Supporting Evaluator Classes

**`AntikytheraGenerated.java`**
Marker annotation for generated classes.

**`ConditionVisitor.java`**
Visitor for analyzing conditional expressions.

**`InnerClassEvaluator.java`**
Specialized evaluator for inner and anonymous classes.

**`LineOfCode.java`**
Represents a single line of code during evaluation for debugging/tracking.

**`MockReturnValueHandler.java`**
Handles return values from mocked methods.

**`MockingEvaluator.java`**
Specialized evaluator for classes with mocked dependencies.

**`NumericComparator.java`**
Utility for comparing numeric values of different types.

**`Precondition.java`**
Represents preconditions for test execution.

**`ReturnConditionVisitor.java`**
Visitor for analyzing return statement conditions.

**`SpringEvaluator.java`**
Specialized evaluator for Spring framework classes.

**`TestSuiteEvaluator.java`**
Evaluator for test suite generation and execution.

##### `Scope.java` & `ScopeChain.java`
Handle scoped method calls like `obj.method1().method2()`.

**Purpose:**
- Track scope chain for chained method calls
- Maintain variable references across call chain
- Support fluent APIs

##### `Branching.java` & `TruthTable.java`
Support branch coverage in test generation.

**Purpose:**
- Track conditional expressions encountered during evaluation
- Generate truth tables for complex conditions
- Ensure both true and false paths are tested

#### Evaluator Subpackages

##### Functional Programming (`evaluator/functional`)
Handles lambda expressions and method references.

- `FPEvaluator.java` - Creates functional interface implementations
- `FunctionEvaluator.java` - Function<T, R> support
- `ConsumerEvaluator.java` - Consumer<T> support
- `BiConsumerEvaluator.java` - BiConsumer<T, U> support
- `SupplierEvaluator.java` - Supplier<T> support
- `BiFunctionEvaluator.java` - BiFunction<T, U, R> support
- `RunnableEvaluator.java` - Runnable support
- `FunctionalConverter.java` - Converts method references to lambdas
- `FunctionalInvocationHandler.java` - Dynamic proxy handler for functional interfaces

##### Mocking (`evaluator/mock`)
Mock configuration and registry.

- `MockingRegistry.java` - Registers custom mock expressions
- `MockConfigReader.java` - Reads mock configuration from YAML
- `MockedFieldDetector.java` - Detects and tracks mocked fields
- `MockingCall.java` - Represents a mocked method call

##### Logging (`evaluator/logging`)
Tracks logging statements for test assertions.

- `AKLogger.java` - Intercepts logging calls during evaluation
- `LogRecorder.java` - Records log statements for assertion generation

##### Dynamic Class Generation (`AKBuddy` & `MethodInterceptor`)

The AKBuddy subsystem uses Byte Buddy to generate dynamic classes at runtime, enabling the evaluation engine to intercept and execute code during symbolic execution.

**`AKBuddy.java`**

**Purpose:** Generate dynamic subclasses that mirror source code structure while routing all invocations through the evaluation engine.

**Key Responsibilities:**
- Generate dynamic classes from JavaParser source AST (fields, methods, constructors, Lombok accessors)
- Generate dynamic classes from bytecode when no source is available
- Cache generated classes by fully-qualified name to avoid regeneration
- Create instances with injected interceptors for method routing

**Generation Strategies:**

1. **Source-based** (`createDynamicClassBasedOnSourceCode`):
   - Synthesizes fields visible to reflection matching source declarations
   - Creates methods with signatures matching source MethodDeclarations
   - Intercepts constructors (explicit or implicit default)
   - Generates Lombok-derived accessors when `@Getter`, `@Setter`, or `@Data` annotations present
   - Implements interfaces declared in source
   
2. **Bytecode-based** (`createDynamicClassBasedOnByteCode`):
   - Subclasses the wrapped binary class directly
   - Delegates all methods to the interceptor
   - Preserves original constructors via `SuperMethodCall`

**Key Methods:**
- `createDynamicClass(MethodInterceptor)` - Build or retrieve cached dynamic class
- `createInstance(Class<?>, MethodInterceptor)` - Create instance with interceptor injection and field synchronization
- `addFields(List<FieldDeclaration>, ...)` - Define fields with annotations
- `addMethods(List<MethodDeclaration>, ...)` - Define methods with delegation
- `addConstructors(TypeDeclaration, ...)` - Intercept constructor invocations
- `addLombokAccessors(TypeDeclaration, ...)` - Synthesize getters/setters for Lombok annotations

**Caching:** The static `registry` map caches generated classes by fully-qualified name, preventing duplicate generation within the same JVM session.

**`MethodInterceptor.java`**

**Purpose:** Bridge between Byte Buddy–generated instances and the Antikythera evaluation engine, handling constructor/method invocation and bidirectional field synchronization.

**Key Responsibilities:**
- Forward constructor/method calls to `EvaluationEngine`
- Synchronize fields between generated instance and evaluator in both directions
- Optimize simple getter/setter invocations (direct field access)
- Provide fallback to `MockingRegistry` stubs or direct reflection when no evaluator present

**Interception Flow:**

```
┌────────────────────────────────────────────────────────────────────┐
│                     Constructor Interception                       │
├────────────────────────────────────────────────────────────────────┤
│ 1. Push args to AntikytheraRunTime stack (reverse order)          │
│ 2. Execute ConstructorDeclaration via evaluator                   │
│ 3. Sync instance fields → evaluator (seed with instance values)   │
│ 4. Sync evaluator fields → instance (write evaluator state back)  │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                       Method Interception                          │
├────────────────────────────────────────────────────────────────────┤
│ Simple getter (getX/isX):                                         │
│   → Read from evaluator field map, fallback to instance field     │
│                                                                    │
│ Simple setter (setX):                                              │
│   → Write to both instance field and evaluator field map          │
│   → Execute method body if MethodDeclaration available            │
│                                                                    │
│ General methods:                                                   │
│   → Push args to stack                                            │
│   → Execute MethodDeclaration via evaluator                       │
│   → Sync evaluator fields → instance                              │
│   → Wrap returned EvaluationEngine values as dynamic proxies      │
└────────────────────────────────────────────────────────────────────┘
```

**No-evaluator Fallback:**
When constructed with a `Class<?>` instead of an `EvaluationEngine`:
1. Check `MockingRegistry` for stubbed return value
2. Attempt reflective invocation on wrapped class 
3. Return type-appropriate default value

**Helper Classes:**
- `MethodDeclarationSupport` - Carries `MethodDeclaration` AST; handles setter field updates and delegates to parent interceptor
- `ConstructorDeclarationSupport` - Carries `ConstructorDeclaration` AST; creates fresh evaluator and wires interceptor on construction

**Usage Example:**

```java
// Create evaluator for a source class
Evaluator evaluator = EvaluatorFactory.create("com.example.User", SpringEvaluator.class);

// Create interceptor wrapping the evaluator
MethodInterceptor interceptor = new MethodInterceptor(evaluator);

// Generate and load dynamic class (cached)
Class<?> dynamicClass = AKBuddy.createDynamicClass(interceptor);

// Create instance with interceptor injected into INSTANCE_INTERCEPTOR field
Object user = AKBuddy.createInstance(dynamicClass, interceptor);

// All method calls now route through the evaluator:
// user.getName() → evaluator.executeMethod(getNameMethodDeclaration)
// user.setName("John") → evaluator.setField("name", new Variable("John"))

// For mock scenarios without source:
MethodInterceptor mockInterceptor = new MethodInterceptor(SomeInterface.class);
Class<?> mockClass = AKBuddy.createDynamicClass(mockInterceptor);
SomeInterface mock = (SomeInterface) mockClass.getDeclaredConstructor().newInstance();
// mock.someMethod() → returns default value or MockingRegistry stub
```

**Integration Points:**
- `Evaluator.createArray()` - Wraps evaluator array elements as dynamic instances
- `Reflect.dynamicProxy()` - Converts evaluators to dynamic instances for reflection
- `MockingEvaluator.optionalByteBuddy()` - Creates Optional-wrapped dynamic instances
- `MockingRegistry.createByteBuddyMockInstance()` - Creates mock instances for dependency injection

---


### 3. Generator Module (`sa.com.cloudsolutions.antikythera.generator`)

**Purpose:** Generate test code from evaluation results.

#### Key Classes

##### `TestGenerator.java`
Abstract base class for test generation.

**Core Responsibilities:**
- Generate test method signatures
- Create mock setups for dependencies
- Generate assertions based on return values or side effects
- Write test files to output directory

**Important Methods:**
- `generateTest(MethodDeclaration)` - Generates a test for a method
- `generateMocks()` - Generates mock declarations and setups
- `generateAssertions(Variable returnValue)` - Generates assertion statements
- `writeTestFile(String className, String content)` - Writes test file

##### `SpringTestGenerator.java`
Test generator specialized for Spring applications.

**Features:**
- Handles Spring annotations (@Service, @Component, @Autowired)
- Generates tests for REST controllers
- Manages Spring-specific test setup
- Supports transaction boundaries

**Key Methods:**
- Generates REST Assured tests for API endpoints
- Handles @RequestMapping and related annotations
- Creates authentication and authorization test setup

##### `UnitTestGenerator.java`
Test generator for unit tests with full mocking support.

**Features:**
- Generates JUnit tests with Mockito
- Creates mock declarations for dependencies
- Handles complex object initialization
- Supports dependency injection patterns
- Generates preconditions for test setup

**Key Methods:**
- Generates @Mock annotations
- Creates when().thenReturn() mock setups
- Handles dependency graphs
- Generates comprehensive test assertions

##### Assertion Classes

**`Asserter.java`**
Abstract base class for test assertion generation.

**Purpose:**
- Defines assertion API for test generators
- Supports multiple assertion frameworks
- Generates field assertions for complex objects

**Key Methods:**
- `assertNotNull(String variable)` - Generate not-null assertion
- `assertNull(String variable)` - Generate null assertion
- `assertEquals(String rhs, String lhs)` - Generate equality assertion
- `assertThrows(String invocation, MethodResponse)` - Generate exception assertion
- `addFieldAsserts(MethodResponse, BlockStmt)` - Add field-level assertions

**`JunitAsserter.java`**
JUnit-specific assertion generator.

**Features:**
- Generates JUnit 5 assertions
- Uses org.junit.jupiter.api.Assertions

**`TestNgAsserter.java`**
TestNG-specific assertion generator.

**Features:**
- Generates TestNG assertions
- Uses org.testng.Assert

##### Supporting Generator Classes

**`Factory.java`**
Factory for creating generator instances and test components.

**`MethodResponse.java`**
Encapsulates the response/result of a method execution.

**Fields:**
- Method return value
- Execution state
- Side effects
- Exceptions thrown

**`ControllerRequest.java`**
Represents an HTTP request for REST controller test generation.

**Fields:**
- HTTP method (GET, POST, PUT, DELETE)
- Endpoint path
- Request parameters
- Request body
- Headers

**`QueryType.java`**
Enum defining types of repository queries (SELECT, INSERT, UPDATE, DELETE).

**`QueryMethodArgument.java`**
Represents an argument for a repository query method.

**`QueryMethodParameter.java`**
Represents a parameter in a repository query.

##### `Antikythera.java`
Main entry point for test generation.

**Responsibilities:**
- Initialize configuration and parsers
- Coordinate API test generation (controllers)
- Coordinate unit test generation (services)
- Copy base test files and resources
- Manage output directory structure

**Main Flow:**
```java
1. getInstance() - Load configuration
2. preProcess() - Parse all source files, copy base files
3. generateApiTests() - For each controller, generate REST Assured tests
4. generateUnitTests() - For each service, generate JUnit tests
```

##### `TypeWrapper.java`
Wraps type information (TypeDeclaration or Class).

**Purpose:**
- Unified interface for source types and binary types
- Track type metadata (service, controller, component)
- Support both parsed and reflection-based type resolution

**Fields:**
- `TypeDeclaration<?> type` - JavaParser type declaration
- `Class<?> clazz` - Java reflection class
- `boolean isService, isController, isComponent, isInterface`

##### Supporting Classes
- `BaseRepositoryQuery.java` - Base query representation with SQL manipulation
- `RepositoryQuery.java` - Extended query with execution and simplification
- `CopyUtils.java` - Utilities for copying project structure
- `TruthTable.java` - Manages branch coverage tables

---

### 4. Depsolver Module (`sa.com.cloudsolutions.antikythera.depsolver`)

**Purpose:** Resolve and analyze dependencies between classes, manage dependency graphs, and handle DTO transformations.

#### Key Classes

##### `DepSolver.java`
Main dependency solver that analyzes class dependencies.

**Core Responsibilities:**
- Traverse AST to identify class dependencies
- Build dependency graphs
- Track imports, field types, method parameters
- Handle complex dependency chains

**Key Methods:**
- Analyzes field declarations for dependencies
- Processes method calls and constructor invocations
- Tracks type references throughout compilation units

##### `InterfaceSolver.java`
Resolves interface implementations and finds concrete classes.

**Purpose:**
- Maps interfaces to their implementations
- Supports polymorphic dependency resolution
- Used by test generators to instantiate concrete types

##### `ClassProcessor.java`
Processes individual classes for dependency extraction.

**Features:**
- Extracts class-level dependencies
- Identifies annotations and their impacts
- Processes inheritance hierarchies

##### `Graph.java` & `GraphNode.java`
Data structures for representing dependency graphs.

**`Graph.java`:**
- Manages collection of GraphNodes
- Supports graph traversal operations
- Detects circular dependencies

**`GraphNode.java`:**
- Represents a single node (class/interface) in dependency graph
- Tracks incoming and outgoing dependencies
- Contains compilation unit reference
- Manages destination compilation unit for code generation

**Fields:**
- `TypeDeclaration<?> typeDeclaration` - Source type
- `CompilationUnit compilationUnit` - Source CU
- `CompilationUnit destination` - Target CU for generation
- Dependencies: references to other GraphNodes

##### `AnnotationVisitor.java`
Visitor pattern implementation for processing annotations.

**Purpose:**
- Identifies Spring annotations (@Service, @Component, @Autowired, etc.)
- Extracts annotation parameters
- Used for dependency injection resolution

##### `DTOHandler.java`
Handles Data Transfer Objects (DTOs) in test generation.

**Features:**
- Identifies DTO classes
- Generates DTO initialization code
- Handles nested DTOs
- Supports builder patterns

##### `ClassDependency.java`
Represents a single class dependency relationship.

**Fields:**
- Source class
- Dependent class
- Dependency type (field, parameter, return type)

##### `Resolver.java`
Generic resolver utility for type resolution.

**Purpose:**
- Resolves types across packages
- Handles generic types
- Supports wildcard imports

---

### 5. Configuration Module (`sa.com.cloudsolutions.antikythera.configuration`)

**Purpose:** Manage project configuration.

##### `Settings.java`
Central configuration management.

**Configuration Sources:**
1. YAML file (default: `generator.yml`)
2. Environment variables
3. Programmatic settings

**Key Settings:** See [Configuration](#configuration) section for complete YAML configuration properties.

**Additional Settings (programmatic):**
- `controllers` - Can include `.java` extension or `#methodName` suffix for specific methods
- `services` - Can be class name or package path
- `extra_exports` or `extra_imports` - Additional imports to resolve for problematic types
- `finch` - External source directories to include in parsing

**Configuration Features:**
- Supports environment variable substitution: `${ENV_VAR_NAME}`
- Supports YAML variable substitution: `${variable_name}`
- Supports `${USERDIR}` replacement with user home directory
- Nested properties accessed via dot notation (e.g., `database.url`)
- List properties automatically converted to Collections

**Methods:**
- `loadConfigMap()` - Load configuration from YAML
- `getBasePath()` - Get target project root
- `getOutputPath()` - Get test output directory
- `getPropertyList(String key, Class<T>)` - Get list property
- `getProperty(String key, Class<T>)` - Get single property

---

## Package Structure

```
sa.com.cloudsolutions.antikythera/
│
├── configuration/
│   └── Settings.java                    # Configuration management
│
├── parser/
│   ├── AbstractCompiler.java            # Base parser, type resolution
│   ├── RestControllerParser.java        # REST controller parsing
│   ├── ServicesParser.java              # Service class parsing
│   ├── RepositoryParser.java            # JPA repository parsing
│   ├── BaseRepositoryParser.java        # Base repository parsing logic
│   ├── DepsolvingParser.java            # Dependency resolution
│   ├── Callable.java                    # Method/constructor wrapper
│   ├── ImportWrapper.java               # Import declaration wrapper
│   ├── ImportUtils.java                 # Import management utilities
│   ├── MCEWrapper.java                  # Method call expression wrapper
│   ├── MavenHelper.java                 # Maven POM parsing
│   ├── Stats.java                       # Statistics tracking
│   └── converter/                       # JPA query conversion (13 classes)
│       ├── BasicConverter.java          # Field name conversion utility
│       ├── HQLParserAdapter.java        # HQL to SQL adapter (ANTLR4-based)
│       ├── MethodToSQLConverter.java    # Spring Data method name to SQL
│       ├── EntityMappingResolver.java   # JPA entity metadata extraction
│       ├── EntityMetadata.java          # Entity metadata container
│       ├── TableMapping.java            # Entity-to-table mapping (Record)
│       ├── JoinMapping.java             # Relationship mapping (Record)
│       ├── JoinType.java                # JOIN type enum (INNER, LEFT, RIGHT, FULL)
│       ├── ParameterMapping.java        # Parameter mapping
│       ├── DatabaseDialect.java         # Database dialect enum (ORACLE, POSTGRESQL)
│       ├── ConversionResult.java        # Conversion result wrapper
│       ├── ConversionFailureReason.java # Failure reason enum
│       └── QueryConversionException.java # Conversion exception
│
├── evaluator/                           # Expression evaluation (29 classes)
│   ├── Evaluator.java                   # Core evaluation engine
│   ├── EvaluatorFactory.java            # Evaluator creation
│   ├── AntikytheraRunTime.java          # Global runtime state
│   ├── Variable.java                    # Runtime variable representation
│   ├── Scope.java                       # Method call scope
│   ├── ScopeChain.java                  # Chained method call tracking
│   ├── Branching.java                   # Branch tracking
│   ├── Arithmetics.java                 # Arithmetic operations
│   ├── BinaryOps.java                   # Binary operations
│   ├── ControlFlowEvaluator.java        # Control flow evaluation
│   ├── ArgumentGenerator.java           # Test argument generation
│   ├── DatabaseArgumentGenerator.java   # DB-based argument generation
│   ├── DummyArgumentGenerator.java      # Dummy argument generation
│   ├── NullArgumentGenerator.java       # Null argument generation
│   ├── AKBuddy.java                     # Dynamic class generation via ByteBuddy
│   ├── MethodInterceptor.java           # Evaluator-instance bridge, field sync
│   ├── Reflect.java                     # Reflection utilities
│   ├── ReflectionArguments.java         # Reflection argument handling
│   ├── AntikytheraGenerated.java        # Generated class marker
│   ├── ConditionVisitor.java            # Condition analysis visitor
│   ├── InnerClassEvaluator.java         # Inner class evaluation
│   ├── LineOfCode.java                  # Line tracking
│   ├── MockReturnValueHandler.java      # Mock return handling
│   ├── MockingEvaluator.java            # Mocking evaluator
│   ├── NumericComparator.java           # Numeric comparison
│   ├── Precondition.java                # Test preconditions
│   ├── ReturnConditionVisitor.java      # Return analysis visitor
│   ├── SpringEvaluator.java             # Spring-specific evaluator
│   ├── TestSuiteEvaluator.java          # Test suite evaluator
│   ├── functional/                      # Functional programming (9 classes)
│   │   ├── FPEvaluator.java
│   │   ├── FunctionEvaluator.java
│   │   ├── ConsumerEvaluator.java
│   │   ├── BiConsumerEvaluator.java
│   │   ├── SupplierEvaluator.java
│   │   ├── BiFunctionEvaluator.java
│   │   ├── FunctionalConverter.java
│   │   ├── FunctionalInvocationHandler.java
│   │   └── RunnableEvaluator.java
│   ├── logging/                         # Logging support (2 classes)
│   │   ├── AKLogger.java
│   │   └── LogRecorder.java
│   └── mock/                            # Mocking support (4 classes)
│       ├── MockingRegistry.java
│       ├── MockConfigReader.java
│       ├── MockedFieldDetector.java
│       └── MockingCall.java
│
├── generator/                           # Test generation (18 classes)
│   ├── Antikythera.java                 # Main entry point
│   ├── TestGenerator.java               # Base test generator
│   ├── SpringTestGenerator.java         # Spring test generator
│   ├── UnitTestGenerator.java           # Unit test generator
│   ├── Asserter.java                    # Base asserter
│   ├── JunitAsserter.java               # JUnit asserter
│   ├── TestNgAsserter.java              # TestNG asserter
│   ├── TypeWrapper.java                 # Type information wrapper
│   ├── BaseRepositoryQuery.java         # Query representation
│   ├── RepositoryQuery.java             # Query execution
│   ├── QueryType.java                   # Query type enum
│   ├── QueryMethodArgument.java         # Query method argument
│   ├── QueryMethodParameter.java        # Query method parameter
│   ├── MethodResponse.java              # Method response container
│   ├── ControllerRequest.java           # Controller request container
│   ├── Factory.java                     # Factory for generators
│   ├── CopyUtils.java                   # File copying utilities
│   └── TruthTable.java                  # Branch coverage support
│
├── depsolver/                           # Dependency resolution (9 classes)
│   ├── DepSolver.java                   # Main dependency solver
│   ├── InterfaceSolver.java             # Interface implementation resolution
│   ├── ClassProcessor.java              # Class dependency processing
│   ├── AnnotationVisitor.java           # Annotation processing
│   ├── DTOHandler.java                  # DTO handling
│   ├── ClassDependency.java             # Dependency representation
│   ├── Resolver.java                    # Type resolver
│   ├── Graph.java                       # Dependency graph
│   └── GraphNode.java                   # Graph node
│
├── finch/
│   └── Finch.java                       # External source integration
│
└── exception/                           # Exceptions (5 classes)
    ├── AntikytheraException.java        # Base exception
    ├── EvaluatorException.java          # Evaluation errors
    ├── GeneratorException.java          # Generation errors
    ├── DepsolverException.java          # Dependency resolution errors
    └── AUTException.java                # Application Under Test errors
```

**Total:** 103+ Java source files (exact count may vary with recent additions)

**Note:** The converter package now includes `MethodToSQLConverter` which was extracted from `BaseRepositoryParser` to improve code organization and maintainability.

---

## Key Workflows

### Workflow 1: Generating API Tests

```
1. User configures controllers in generator.yml:
   controllers:
     - com.example.UserController

2. Antikythera.main() calls generateApiTests()

3. For each controller:
   a. RestControllerParser.compile(controller)
   b. Parse @RequestMapping, @GetMapping, etc.
   c. For each endpoint method:
      - AbstractCompiler.findMethodDeclaration()
      - Evaluator.evaluateExpression() for each statement
      - Track method calls, return values
   d. TestGenerator generates REST Assured test:
      - given().auth(), headers()
      - when().get/post/put(endpoint)
      - then().statusCode(), body()
   e. Write test file to output directory

4. Report statistics (controllers, methods, tests)
```

### Workflow 2: Generating Unit Tests

```
1. User configures services in generator.yml:
   services:
     - com.example.UserService

2. Antikythera.main() calls generateUnitTests()

3. For each service:
   a. ServicesParser.compile(service)
   b. Identify public methods
   c. For each method:
      - Create Evaluator instance for service class
      - executeMethod(methodDeclaration)
      - Evaluator tracks:
        * Variable values
        * Method calls (mocked dependencies)
        * Return values
        * Branching conditions
   d. TestGenerator generates JUnit test:
      - @Mock annotations for dependencies
      - when().thenReturn() for mock behavior
      - Method invocation
      - Assertions (assertEquals, verify)
   e. Write test file to output directory

4. Repeat for all services
```

### Workflow 3: Type Resolution

```
Given: A type name "List" in UserService.java

1. AbstractCompiler.findType(cu, "List")
2. Check if "List" is declared in UserService.java → NO
3. Check imports for "List":
   - Find "import java.util.List;" → YES
4. Return TypeWrapper(List.class)

Given: A custom type "User" in same package

1. AbstractCompiler.findType(cu, "User")
2. Check if "User" in current compilation unit → NO
3. Check imports → NO
4. Check same package:
   - Package is "com.example"
   - Check if "com/example/User.java" exists → YES
5. AbstractCompiler.compile("com/example/User.java")
6. Return TypeWrapper(UserTypeDeclaration)
```

### Workflow 4: Method Call Evaluation

```
Given: userService.findById(123L)

1. Evaluator.evaluateMethodCall(methodCallExpr)
2. Evaluate scope: "userService"
   - Scope.getValue(node, "userService") → Variable(userService evaluator)
3. Evaluate arguments: 123L → Variable(Long, 123)
4. Find method declaration:
   - AbstractCompiler.findMethodDeclaration(MCEWrapper, UserService)
   - Match "findById" with parameter type Long
5. Execute method:
   - If UserService is source code:
     * Create new Evaluator for UserService
     * evaluator.executeMethod(findById method)
   - If UserService is binary (JAR):
     * Use reflection: method.invoke(userService, 123L)
6. Capture return value: Variable(User, userInstance)
7. Return Variable to caller
```

### Workflow 5: Branching and Coverage

```
Given: Method with if-else

public String process(int value) {
    if (value > 0) {
        return "positive";
    } else {
        return "negative";
    }
}

1. First evaluation run:
   - Evaluator encounters if (value > 0)
   - Branching.recordCondition(binaryExpr, currentValue=true)
   - Evaluates then-branch: return "positive"
   - TestGenerator generates test:
     * assertEquals("positive", service.process(1))

2. Second evaluation run:
   - Branching provides alternate value: false
   - Evaluator takes else-branch: return "negative"
   - TestGenerator generates test:
     * assertEquals("negative", service.process(-1))

3. Both paths covered!
```

---

## Repository Parser & Query System

### Overview
The RepositoryParser analyzes JPA Repository interfaces to extract and execute queries. It supports:
- `@Query` annotated methods (JPQL/HQL)
- Method-name-based queries (e.g., `findByUsername`)

### Key Components

#### BaseRepositoryParser
**Purpose:** Parses JPA Repository interfaces and converts queries to executable SQL.

**Key Features:**
- **Method Name Parsing:** `findByUsernameAndAge` → SQL query
- **Query Conversion:** JPA/HQL to native SQL with snake_case columns
- **Entity Resolution:** Resolves entity types from `JpaRepository<Entity, ID>`
- **Conversion Caching:** Caches query results to avoid re-processing
- **Join Processing:** Extracts table/column mappings from `@JoinColumn`

**Important Methods:**
- `getQueryFromRepositoryMethod(Callable)` - Get/create query for method
- `parseNonAnnotatedMethod(Callable)` - Derive SQL from method name
- `queryBuilder(String, boolean, Callable)` - Build RepositoryQuery with conversion
- `convertFieldsToSnakeCase(Statement, TypeWrapper)` - Convert camelCase to snake_case
- `processTypes()` - Identify entity types from repository declaration

**Configuration:** See [Configuration](#configuration) section for `database` and `query_conversion` YAML settings.

#### RepositoryParser
**Purpose:** Extends BaseRepositoryParser with database execution.

**Key Features:**
- **Database Connection:** JDBC connection management
- **Query Execution:** Execute parsed queries
- **Result Caching:** Cache query results
- **Parameter Binding:** Bind method arguments to SQL placeholders
- **Simplified Queries:** Create queries with fewer filters for test data discovery
- **Dialect Support:** Oracle and PostgreSQL differences

**Important Methods:**
- `executeQuery(Callable)` - Execute query for repository method
- `executeSimplifiedQuery(RepositoryQuery, MethodDeclaration, int)` - Execute with minimal filters
- `bindParameters(QueryMethodArgument, PreparedStatement, int)` - Bind Java values to SQL
- `createConnection()` - Establish database connection

**Supported Method Name Patterns:** See [MethodToSQLConverter](#parser-converter-subpackage) for the complete list of 20+ supported query patterns including `findBy`, `countBy`, `deleteBy`, comparison operators, and ORDER BY clauses.

#### BaseRepositoryQuery & RepositoryQuery
**Purpose:** Represent queries with SQL manipulation and execution.

**Key Features:**
- Query storage (original + parsed SQL)
- Parameter management
- Field conversion (camelCase → snake_case)
- HQL translation (entities → tables)
- Query simplification for test data discovery

**Query Simplification Strategy:**
When a query has many filters and returns no results, RepositoryQuery:
1. Replaces most placeholders with test values (`'1' = '1'`)
2. Keeps critical filters (e.g., hospital_id)
3. Executes simplified query to discover valid test data
4. Uses discovered data to populate test arguments

### Query Flow

```
1. Discovery: BaseRepositoryParser identifies repository methods
2. Parsing: Method names or @Query → SQL strings
3. Conversion: BaseRepositoryQuery converts JPA/HQL → SQL
4. Caching: Cache conversion results
5. Execution (optional): RepositoryParser executes against DB
6. Simplification: RepositoryQuery creates simplified version
7. Result Caching: Cache results to avoid redundant calls
```

### Common Pitfalls

- **Dialect Differences:** Oracle (ROWNUM) vs PostgreSQL (LIMIT)
- **Join Complexity:** Complex joins may need manual entity metadata
- **Conversion Failures:** Always enable fallback mode
- **Cache Invalidation:** Clear cache when entity metadata changes
- **Placeholder Indexing:** SQL placeholders are 1-indexed, not 0-indexed

---

## Development Guidelines

### Code Organization

1. **Parser classes** should extend `AbstractCompiler`
2. **Evaluator classes** should extend `Evaluator`
3. **Test generator classes** should extend `TestGenerator`
4. Use `AntikytheraRunTime` for global state (caches)
5. Use `Settings` for configuration, avoid hardcoding paths

### Adding New Expression Types

When adding support for a new expression type in Evaluator:

```java
// In Evaluator.evaluateExpression()
else if (expr.isYourNewExpr()) {
    return evaluateYourNewExpr(expr.asYourNewExpr());
}

// Add handler method
private Variable evaluateYourNewExpr(YourNewExpr expr) 
        throws ReflectiveOperationException {
    // Evaluate sub-expressions
    Variable left = evaluateExpression(expr.getLeft());
    Variable right = evaluateExpression(expr.getRight());
    
    // Perform operation
    Object result = ... // compute result
    
    // Return Variable
    return new Variable(resultType, result);
}
```

### Adding New Parser Types

To parse a new type of class (e.g., Kafka listeners):

```java
public class KafkaListenerParser extends AbstractCompiler {
    
    public KafkaListenerParser(String className) throws IOException {
        compile(AbstractCompiler.classToPath(className));
    }
    
    public void start() throws Exception {
        TypeDeclaration<?> type = AbstractCompiler.getPublicType(cu);
        
        for (MethodDeclaration method : type.getMethods()) {
            if (method.isAnnotationPresent("KafkaListener")) {
                processKafkaListener(method);
            }
        }
    }
    
    private void processKafkaListener(MethodDeclaration method) {
        // Extract @KafkaListener attributes
        // Generate test
    }
}
```

### Type Resolution Best Practices

1. **Always use `AbstractCompiler.findType()`** to resolve type names
2. **Check both TypeDeclaration and Class** in TypeWrapper
3. **Handle null returns** gracefully (type may be unresolvable)
4. **Use fully qualified names** when possible to avoid ambiguity
5. **Add to `extra_exports`** in config for problematic imports

### Evaluator Best Practices

1. **Check for null** before accessing Variable.getValue()
2. **Use `getValue(node, name)`** to respect scoping
3. **Set variable types** explicitly: `variable.setType(type)`
4. **Handle reflection exceptions** gracefully (log and continue)
5. **Use `AntikytheraRunTime.push/pop`** for method argument passing

### Testing Your Changes

Always run the full test suite:
```bash
mvn test
```

Requires:
- JVM flag: `--add-opens java.base/java.util.stream=ALL-UNNAMED`
- Cloned repos: `antikythera-sample-project`, `antikythera-test-helper`

See [Contributing](#contributing) section for complete testing requirements.

### Performance Considerations

1. **Cache aggressively:** Use AntikytheraRunTime caches
2. **Avoid re-parsing:** Check if compilation unit exists before compiling
3. **Lazy evaluation:** Don't evaluate methods unless needed
4. **Limit recursion depth:** Evaluator can recurse deeply on method calls

---

## Testing Strategy

### Antikythera's Own Tests

**Location:** `src/test/java/sa/com/cloudsolutions/antikythera/`

**Structure:**
- `base/` - Base test utilities
- `configurations/` - Test configuration
- `depsolver/` - Dependency resolution tests
- `evaluator/` - Evaluator engine tests
- `finch/` - Finch integration tests
- `generator/` - Test generation tests
- `parser/` - Parser tests

**Test Resources:**
- `src/test/resources/generator.yml` - Test configuration
- External repos: `antikythera-sample-project`, `antikythera-test-helper`

### Testing Parsers

```java
@Test
void testControllerParsing() throws Exception {
    RestControllerParser parser = new RestControllerParser(
        "com.example.UserController"
    );
    parser.start();
    
    Stats stats = RestControllerParser.getStats();
    assertTrue(stats.getMethods() > 0);
    assertTrue(stats.getTests() > 0);
}
```

### Testing Evaluators

```java
@Test
void testMethodEvaluation() throws Exception {
    Evaluator evaluator = EvaluatorFactory.create(
        "com.example.UserService",
        Evaluator.class
    );
    
    // Simulate method call
    MethodCallExpr mce = ...;
    Variable result = evaluator.evaluateMethodCall(mce);
    
    assertNotNull(result);
    assertEquals(expectedValue, result.getValue());
}
```

### Testing Type Resolution

```java
@Test
void testTypeResolution() throws Exception {
    AbstractCompiler compiler = new AbstractCompiler();
    compiler.compile("com/example/UserService.java");
    
    CompilationUnit cu = compiler.getCompilationUnit();
    TypeWrapper wrapper = AbstractCompiler.findType(cu, "User");
    
    assertNotNull(wrapper);
    assertEquals("com.example.User", wrapper.getFullyQualifiedName());
}
```

### Integration Testing

Integration tests should:
1. Use real source code from `antikythera-sample-project`
2. Generate tests to a temporary output directory
3. Verify generated test files compile
4. Optionally run generated tests

---

## API Entry Points

This section documents the main public APIs agents should use when working with Antikythera.

### Configuration API (`Settings`)

```java
// Load configuration
Settings.loadConfigMap();                                    // From default generator.yml
Settings.loadConfigMap(File configFile);                    // From custom file

// Get properties
String getBasePath();                                        // Target project root
String getOutputPath();                                      // Test output directory
String getBasePackage();                                     // Base package name
Collection<T> getPropertyList(String key, Class<T> cls);    // Get list property
Optional<T> getProperty(String key, Class<T> cls);          // Get single property
String[] getJarFiles();                                      // Additional JAR dependencies
String[] getArtifacts();                                     // Maven artifact IDs
```

### Main Generation API (`Antikythera`)

```java
// Singleton access
Antikythera getInstance();                                   // Initialize and get instance

// Generation workflow
void preProcess();                                           // Parse all files, copy base files
void generateApiTests();                                     // Generate REST controller tests
void generateUnitTests();                                    // Generate service unit tests

// File writing
void writeFilesToTest(String package, String filename, String content);
void writeFile(String filePath, String content);
```

### Parser API (`AbstractCompiler`)

```java
// Compilation
void compile(String relativePath);                          // Parse Java file
void preProcess();                                          // Pre-parse all Java files in project

// Type resolution
TypeWrapper findType(CompilationUnit cu, String className);
String findFullyQualifiedName(CompilationUnit cu, String name);
ImportWrapper findImport(CompilationUnit cu, String name);
MethodDeclaration findMethodDeclaration(MCEWrapper mce, TypeDeclaration<?> type);

// Utilities
CompilationUnit getCompilationUnit();
String classToPath(String className);                      // Convert class name to file path
```

### Parser Subclasses

**RestControllerParser:**
```java
RestControllerParser(String className);
void start();                                               // Parse and generate API tests
static Stats getStats();                                    // Get parsing statistics
```

**ServicesParser:**
```java
ServicesParser(String className);
void start();                                               // Parse all methods
void start(String methodName);                              // Parse specific method
void writeFiles();                                          // Write generated test files
```

**RepositoryParser / BaseRepositoryParser:**
```java
BaseRepositoryParser();
RepositoryQuery getQueryFromRepositoryMethod(Callable callable);
String findTableName(TypeWrapper entity);
String camelToSnake(String camelCase);
```

### Evaluator API (`Evaluator`)

```java
// Evaluation
Variable evaluateExpression(Expression expr);               // Main evaluation entry point
Variable evaluateMethodCall(MethodCallExpr mce);           // Evaluate method call
Variable createObject(ObjectCreationExpr expr);            // Create object instance

// Variable management
Variable getValue(Node node, String name);                 // Get variable from scope
void setLocal(Node node, String name, Symbol symbol);      // Set local variable
void setField(String name, Symbol symbol);                 // Set field value

// Method execution
Variable executeMethod(MethodDeclaration method);           // Execute method body
```

**EvaluatorFactory:**
```java
Evaluator create(String className, Class<? extends Evaluator> evalClass);
Evaluator createLazily(String className, Class<? extends Evaluator> evalClass);
```

### Runtime State API (`AntikytheraRunTime`)

```java
// Compilation unit cache
CompilationUnit getCompilationUnit(String className);
void putCompilationUnit(String className, CompilationUnit cu);

// Type cache
TypeWrapper getTypeDeclaration(String className);
void putTypeDeclaration(String className, TypeWrapper type);

// Variable stack (for method arguments)
void push(Variable var);
Variable pop();
```

### Converter API (`HQLParserAdapter`)

```java
HQLParserAdapter(CompilationUnit cu, TypeWrapper entity);
ConversionResult convertToNativeSQL(String jpaQuery);      // Convert HQL/JPQL to SQL
```

**MethodToSQLConverter:**
```java
static List<String> extractComponents(String methodName);  // Parse method name
static boolean buildSelectAndWhereClauses(List<String> components, StringBuilder sql, String tableName);
```

**EntityMappingResolver:**
```java
EntityMetadata resolveEntityMetadata(Class<?> entityClass);
EntityMetadata resolveEntityMetadata(Collection<Class<?>> entityClasses);
```

### Test Generator API (`TestGenerator`)

```java
// Abstract base - implement in subclasses
void generateTest(MethodDeclaration method);
void generateMocks();
void generateAssertions(Variable returnValue);
void writeTestFile(String className, String content);
```

**Subclasses:**
- `SpringTestGenerator` - For REST controller tests
- `UnitTestGenerator` - For service unit tests

---

## Decision Guide

Use this guide to decide which class/API to use for common tasks.

### "I need to..."

**...generate tests for a Spring Boot project**
→ Use `Antikythera.getInstance()` → `preProcess()` → `generateApiTests()` → `generateUnitTests()`

**...parse a specific Java class**
→ Use `AbstractCompiler.compile(path)` or check `AntikytheraRunTime.getCompilationUnit(className)`

**...resolve a type name to its TypeWrapper**
→ Use `AbstractCompiler.findType(cu, "TypeName")`

**...convert a Spring Data JPA method name to SQL**
→ Use `MethodToSQLConverter.extractComponents()` + `buildSelectAndWhereClauses()`

**...convert an HQL query to SQL**
→ Use `HQLParserAdapter.convertToNativeSQL()` (requires entity metadata)

**...extract entity metadata from a JPA entity class**
→ Use `EntityMappingResolver.resolveEntityMetadata(Class<?>)`

**...evaluate a Java expression during test generation**
→ Use `Evaluator.evaluateExpression()` or create evaluator via `EvaluatorFactory.create()`

**...generate tests for a REST controller**
→ Use `RestControllerParser` or `SpringTestGenerator`

**...generate tests for a service class**
→ Use `ServicesParser` or `UnitTestGenerator`

**...parse a JPA repository**
→ Use `RepositoryParser` or `BaseRepositoryParser`

**...access global compilation unit cache**
→ Use `AntikytheraRunTime.getCompilationUnit(className)`

**...load or access configuration**
→ Use `Settings.loadConfigMap()` then `Settings.getBasePath()`, `getOutputPath()`, etc.

**...handle branch coverage in test generation**
→ Use `Branching` class and `TruthTable` for condition tracking

**...generate mock setups**
→ Use `MockingRegistry` and `MockingEvaluator` for mock configuration

**...track logging statements for assertions**
→ Use `LogRecorder` and `AKLogger` for logging interception

---

## Best Practices Summary

### Do's ✅
- Always use `AbstractCompiler.findType()` for type resolution
- Cache compilation units and types in `AntikytheraRunTime`
- Handle null returns gracefully (many operations can fail)
- Use `Settings` for all configuration
- Run full test suite before committing
- Add javadoc for public methods

### Don'ts ❌
- Don't hardcode file paths or package names
- Don't add dependencies without discussion
- Don't commit directly to `main`
- Don't assume types are always resolvable
- Don't ignore null checks on Variable.getValue()
- Don't modify static fields (use Settings)
- Don't skip tests (they catch regressions)
- Don't forget `--add-opens` JVM flag for tests

### Code Quality
- Use Optional for nullable returns
- Prefer switch expressions over if-else chains
- Use records for simple data classes
- Keep methods short
- Extract complex logic into private methods
- Use meaningful variable names
- Add comments for complex algorithms

---

## Troubleshooting

### "Type not found" errors
1. Check if import exists in source file
2. Verify JAR is in classpath (pom.xml or jar_files config)
3. Try adding to `extra_exports` in config
4. Check if type name is fully qualified

### "Method not found" errors
1. Verify method signature matches (parameter types)
2. Check if method is in parent class
3. Ensure class is compiled (check AntikytheraRunTime cache)
4. Check for method overloading conflicts

### Evaluator infinite loops
1. Check for circular method calls
2. Add recursion depth limit
3. Use lazy evaluation for complex objects
4. Mock external dependencies

### Generated tests don't compile
1. Check import statements in generated code
2. Verify mock setup is correct
3. Ensure test helper classes are copied
4. Check for naming conflicts

---

## Related Projects & Tools

### Antikythera Examples Module
The `antikythera-examples` project provides practical tools built on Antikythera:

**Query Optimization Tools:**
- **QueryOptimizationChecker** - Analyzes JPA repository queries for optimization opportunities
- **QueryOptimizer** - Automatically applies query optimizations
- **CardinalityAnalyzer** - Analyzes column cardinality (HIGH/MEDIUM/LOW) for index recommendations
- **GeminiAIService** - AI-powered query optimization recommendations
- **LiquibaseGenerator** - Generates Liquibase changesets for index creation/drops

**Query Analysis:**
- **QueryOptimizationExtractor** - Extracts WHERE and JOIN conditions from queries
- **WhereClauseCollector** - Separates WHERE and JOIN ON conditions
- **QueryAnalysisEngine** - Core optimization rule engine

**Other Tools:**
- **Usage Finder** - Finds usages of classes/methods across codebase
- **Test Fixer** - Automated test repair and migration
- **Logger Tool** - Analyzes and optimizes logging statements
- **Hard Delete Tool** - Analyzes hard delete operations in codebase

See `antikythera-examples/README.md` and `antikythera-examples/docs/` for detailed documentation.

### HQL Parser Project
Standalone HQL/JPQL parser with PostgreSQL conversion:
- ANTLR4-based grammar for accurate parsing
- Supports SELECT, UPDATE, DELETE, INSERT statements
- Entity detection and field mapping
- Parameter extraction (named and positional)
- Join support (INNER, LEFT, RIGHT)
- Aggregate function support

Located in `hql-parser/` directory. See `hql-parser/README.md` for details.

### Integration Status
- ✅ **Phase 1 Complete:** HQL parser integration into Antikythera core
- ✅ **Production Ready:** HQLParserAdapter is default converter
- 🔄 **Future:** Oracle dialect support in hql-parser
- 🔄 **Future:** Enhanced QueryOptimizationChecker with hql-parser integration

---

## Additional Resources

- **README.md** - Project overview and setup instructions
- **GEMINI.md** - Quick reference guide for Gemini AI
- **PACKAGE.md** - GitHub Packages usage instructions
- **pom.xml** - Dependencies and build configuration
- **JavaParser docs** - https://javaparser.org/
- **ByteBuddy docs** - https://bytebuddy.net/
- **ANTLR4 docs** - https://www.antlr.org/
- **Spring Data JPA Reference** - https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- **HQL Parser Integration Docs** - See `docs/HQL_PARSER_INTEGRATION_STRATEGY.md` and `docs/INTEGRATION_PHASE1_COMPLETE.md`

---

## Contributing

When making changes:

1. Create a feature branch
2. Add tests for new functionality
3. Run full test suite (`mvn test`)
4. Ensure tests pass (requires cloned `antikythera-sample-project` and `antikythera-test-helper`)
5. Update this documentation if needed
6. Create a pull request

**Testing Requirements:**
- Must have `antikythera-sample-project` and `antikythera-test-helper` cloned
- JVM flags required: `--add-opens java.base/java.util.stream=ALL-UNNAMED`
- Java 21+ required for compilation
- ~620 unit and integration tests must pass

**Common Development Tasks:**
- Adding new Spring Data JPA method keywords → Update `MethodToSQLConverter`
- Supporting new database dialects → Extend `DatabaseDialect` enum and add conversion logic
- Adding new expression types → Extend `Evaluator.evaluateExpression()`
- Adding new parser types → Create new parser extending `AbstractCompiler`

For questions or issues, refer to the project repository or contact the maintainers.

# Antikythera - Warp AI Agent Guide

This document provides comprehensive technical information for AI agents (specifically Warp) working on the Antikythera project. For basic project information, see `README.md` and `GEMINI.md`.

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Core Modules](#core-modules)
4. [Package Structure](#package-structure)
5. [Key Workflows](#key-workflows)
6. [Development Guidelines](#development-guidelines)
7. [Testing Strategy](#testing-strategy)
8. [Common Tasks](#common-tasks)

---

## Project Overview

**Antikythera** is an automated test generator and refactoring tool for Java projects. It generates:
- **Unit tests** for service classes and business logic
- **API tests** for REST endpoints using REST Assured

### Technology Stack
- **Java 21** (requires `--add-opens` JVM flags for reflection)
- **Maven** for build management
- **JavaParser 3.27.0** for parsing source code
- **ByteBuddy 1.15.3** for dynamic class generation
- **Spring Boot 2.7.14** (for Spring integration support)
- **JPA/Hibernate** (for repository parsing)
- **JSQLParser 5.3** (for SQL query manipulation)

### Key Dependencies
- `javaparser-core` & `javaparser-symbol-solver-core` - Source code parsing
- `byte-buddy` - Runtime class generation for mocking
- `rest-assured` - API test generation
- `jsqlparser` - Query parsing and conversion
- `spring-boot-starter-data-jpa` - JPA entity metadata
- `hql-parser` (GitHub dependency from raditha) - HQL to SQL conversion
- `antikythera-common` (GitHub dependency) - Shared utilities

### Related Repositories
- `antikythera-sample-project` - Sample application for testing
- `antikythera-test-helper` - Test utilities and fixtures
- `antikythera-agent` - Java agent for bytecode instrumentation

---

## Architecture

### Three-Phase Pipeline

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   PARSING    │────▶│  EVALUATION  │────▶│  GENERATION  │
└──────────────┘     └──────────────┘     └──────────────┘
      ▲                     ▲                     ▲
      │                     │                     │
AbstractCompiler       Evaluator           TestGenerator
```

#### 1. Parser Phase (AbstractCompiler)
- **Input:** Java source files from target project
- **Process:**
  - Parses source code using JavaParser
  - Resolves types, imports, and dependencies
  - Builds compilation units and type cache
  - Identifies controllers, services, and repositories
- **Output:** Parsed AST (Abstract Syntax Tree) with resolved types

#### 2. Evaluation Phase (Evaluator)
- **Input:** Parsed AST nodes (methods, constructors)
- **Process:**
  - Executes code symbolically using an expression evaluation engine
  - Tracks variables, fields, and scopes
  - Handles branching (if/else, switch, loops)
  - Executes method calls (reflectively or via sub-evaluators)
  - Captures return values and side effects
- **Output:** Variable states, return values, execution traces

#### 3. Generation Phase (TestGenerator)
- **Input:** Evaluation results and execution traces
- **Process:**
  - Generates test methods with appropriate assertions
  - Creates mock setups for dependencies
  - Generates preconditions and argument values
  - Handles both void methods (logging/side effects) and non-void (return values)
- **Output:** JUnit test files written to output directory

### Data Flow

```
Target Project Source Files
        │
        ▼
   [AbstractCompiler]
        │
        ├─▶ CompilationUnit Cache (AntikytheraRunTime)
        ├─▶ Type Resolution (findType, findImport)
        └─▶ Symbol Resolution (JavaSymbolSolver)
        │
        ▼
   [Parser Subclasses]
        ├─▶ RestControllerParser (for API tests)
        ├─▶ ServicesParser (for unit tests)
        └─▶ RepositoryParser (for JPA repositories)
        │
        ▼
   [Evaluator & Subclasses]
        ├─▶ Variable tracking (locals, fields)
        ├─▶ Expression evaluation
        ├─▶ Method call execution
        ├─▶ Branching support (Branching.java)
        └─▶ Functional programming (FPEvaluator)
        │
        ▼
   [TestGenerator]
        ├─▶ SpringTestGenerator (for REST controllers)
        ├─▶ UnitTestGenerator (for services)
        ├─▶ Test method generation
        ├─▶ Mock setup generation (Mockito)
        ├─▶ Assertion generation (JUnit/TestNG)
        └─▶ File writing (Antikythera.writeFilesToTest)
        │
        ▼
Output Directory (JUnit/TestNG Test Files)
```

---

## Core Modules

### 1. Parser Module (`sa.com.cloudsolutions.antikythera.parser`)

**Purpose:** Parse Java source code and extract structural information.

#### Key Classes

##### `AbstractCompiler.java`
The foundational parser class that sets up JavaParser and maintains type caches.

**Core Responsibilities:**
- Initialize JavaParser with symbol resolver
- Load JAR files and external dependencies
- Parse Java files into CompilationUnits
- Resolve types, imports, and method declarations
- Cache compilation units and types

**Important Methods:**
- `setupParser()` - Initializes JavaParser, symbol solver, and class loaders
- `compile(String relativePath)` - Parses a Java file and caches it
- `findType(CompilationUnit, String)` - Resolves a type name to TypeWrapper
- `findFullyQualifiedName(CompilationUnit, String)` - Resolves short names to FQN
- `findMethodDeclaration(MCEWrapper, TypeDeclaration)` - Finds method matching a call
- `findConstructorDeclaration(MCEWrapper, TypeDeclaration)` - Finds constructor
- `preProcess()` - Pre-parses all Java files in the project

**Type Resolution Strategy:**
1. Check if type exists in current compilation unit
2. Check imports (non-wildcard, then wildcard)
3. Check same package
4. Check java.lang package
5. Try Class.forName with project class loader
6. Check `extra_exports` in configuration

##### `RestControllerParser.java`
Parses Spring REST controllers to generate API tests.

**Features:**
- Extracts `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc.
- Identifies path variables and request parameters
- Generates REST Assured test methods
- Handles authentication and authorization annotations

##### `ServicesParser.java`
Parses service classes to generate unit tests.

**Features:**
- Identifies `@Service` and `@Component` classes
- Extracts business logic methods
- Generates unit tests with mocks for dependencies
- Handles transaction boundaries

##### `RepositoryParser.java`
Parses JPA repositories to extract and execute queries.

**Features:**
- Parses `@Query` annotations (JPA/HQL)
- Derives queries from method names (`findByUsername`, etc.)
- Executes queries against database (optional)
- Converts JPA/HQL to native SQL
- Creates simplified queries for test data discovery

**See Also:** [Repository Parser & Query System](#repository-parser--query-system)

##### Supporting Classes
- `Callable.java` - Wraps method/constructor declarations with metadata
- `ImportWrapper.java` - Wraps import declarations with resolved types
- `ImportUtils.java` - Utilities for managing imports in generated code
- `MCEWrapper.java` - Wraps MethodCallExpr with argument type information
- `MavenHelper.java` - Parses pom.xml and builds classpath
- `Stats.java` - Tracks parsing statistics

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
Adapter that bridges Antikythera's converter interface with the external hql-parser library (ANTLR4-based).

**Key Features:**
- Uses `com.raditha.hql.parser.HQLParser` for HQL/JPQL parsing
- Converts parsed HQL to PostgreSQL dialect using `HQLToPostgreSQLConverter`
- Registers entity metadata mappings from JavaParser annotations
- Provides ConversionResult with native SQL and metadata

**Key Methods:**
- `convertToNativeSQL(String jpaQuery)` - Parses and converts HQL to SQL
- `registerMappings(MetaData)` - Registers entity-to-table mappings from HQL analysis

**Dependencies:**
- External library: `com.raditha:hql-parser` (GitHub dependency)
- Supports PostgreSQL dialect conversion

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

**Key Settings:**
- `base_path` - Root directory of target project
- `base_package` - Base package name of target project
- `output_path` - Where to write generated tests
- `controllers` - List of controller classes to process
- `services` - List of service classes to process
- `jar_files` - Additional JAR dependencies
- `skip` - Patterns for files to skip
- `extra_exports` - Additional imports to resolve
- `finch` - External source directories
- `database` - Database connection settings (for repository parsing)

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
│   └── converter/                       # JPA query conversion (12 classes)
│       ├── BasicConverter.java          # Field name conversion utility
│       ├── HQLParserAdapter.java        # HQL to SQL adapter
│       ├── EntityMappingResolver.java   # JPA entity metadata
│       ├── EntityMetadata.java          # Entity metadata container
│       ├── TableMapping.java            # Entity-to-table mapping
│       ├── JoinMapping.java             # Relationship mapping
│       ├── JoinType.java                # JOIN type enum
│       ├── ParameterMapping.java        # Parameter mapping
│       ├── DatabaseDialect.java         # Database dialect enum
│       ├── ConversionResult.java        # Conversion result
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
│   ├── AKBuddy.java                     # ByteBuddy integration
│   ├── MethodInterceptor.java           # Method call interception
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

**Total:** 102 Java source files

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

**Configuration (YAML):**
```yaml
database:
  url: jdbc:postgresql://localhost:5432/mydb
  username: user
  password: pass
  run_queries: true              # Execute queries during test generation
  query_conversion:
    enabled: true                # Enable JPA-to-SQL conversion
    fallback_on_failure: true    # Fallback to original query if conversion fails
    log_conversion_failures: true
    cache_results: true          # Cache conversion results
```

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

**Supported Method Name Patterns:**
- `findBy[Field]` → `SELECT * FROM table WHERE field = ?`
- `findBy[Field]And[Field2]` → `WHERE field = ? AND field2 = ?`
- `findFirstBy[Field]` → `... LIMIT 1`
- `findBy[Field]Between` → `WHERE field BETWEEN ? AND ?`
- `findBy[Field]GreaterThan` → `WHERE field > ?`
- `findBy[Field]In` → `WHERE field IN (?)`
- `findBy[Field]Containing` → `WHERE field LIKE ?`
- `findBy[Field]OrderBy[Field2]` → Adds ORDER BY clause

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

## Common Tasks

### Task 1: Add Support for New Annotation

**Example:** Add support for `@Scheduled` methods

```java
// In ServicesParser or new ScheduledParser
public void processScheduledMethods() {
    TypeDeclaration<?> type = AbstractCompiler.getPublicType(cu);
    
    for (MethodDeclaration method : type.getMethods()) {
        if (method.isAnnotationPresent("Scheduled")) {
            Optional<AnnotationExpr> ann = method.getAnnotationByName("Scheduled");
            if (ann.isPresent()) {
                String cron = extractCronExpression(ann.get());
                generateScheduledTest(method, cron);
            }
        }
    }
}
```

### Task 2: Improve Type Resolution

**Example:** Handle inner classes better

```java
// In AbstractCompiler.findType()
if (className.contains(".")) {
    // Might be OuterClass.InnerClass
    String[] parts = className.split("\\.");
    TypeWrapper outer = findType(cu, parts[0]);
    if (outer != null && outer.getType() != null) {
        for (TypeDeclaration<?> inner : outer.getType().findAll(TypeDeclaration.class)) {
            if (inner.getNameAsString().equals(parts[1])) {
                return new TypeWrapper(inner);
            }
        }
    }
}
```

### Task 3: Add New Mock Strategy

**Example:** Mock external API calls

```java
// In MockingRegistry
public static void registerExternalApiMock(String apiClass, String method) {
    String key = apiClass + "." + method;
    Expression mockExpr = StaticJavaParser.parseExpression(
        "return new ResponseEntity<>(mockData, HttpStatus.OK)"
    );
    customMockExpressions.put(key, List.of(mockExpr));
}
```

### Task 4: Extend Query Parser

**Example:** Support custom query methods

```java
// In BaseRepositoryParser
protected String parseCustomMethod(MethodDeclaration method) {
    String methodName = method.getNameAsString();
    
    if (methodName.startsWith("searchBy")) {
        String field = methodName.substring("searchBy".length());
        String column = camelToSnakeCase(field);
        return "SELECT * FROM " + tableName + 
               " WHERE " + column + " LIKE CONCAT('%', ?, '%')";
    }
    
    return null; // Fall back to standard parsing
}
```

### Task 5: Debug Type Resolution Issues

When type resolution fails:

1. **Enable debug logging:**
```java
logger.debug("Attempting to resolve type: {}", className);
logger.debug("Imports in CU: {}", cu.getImports());
```

2. **Check cache:**
```java
TypeWrapper cached = AntikytheraRunTime.getTypeDeclaration(className);
if (cached != null) {
    logger.debug("Found in cache: {}", cached);
}
```

3. **Verify imports:**
```java
ImportWrapper imp = AbstractCompiler.findImport(cu, className);
if (imp == null) {
    logger.warn("No import found for: {}", className);
}
```

4. **Add to `extra_exports`:**
```yaml
extra_exports:
  - com.problematic.UnresolvedClass
```

### Task 6: Handle New Database Dialect

**Example:** Add MySQL support

```java
// In RepositoryParser
private static String applyDialect(String sql) {
    if (Settings.getDialect().equals("mysql")) {
        sql = sql.replaceAll("ROWNUM <= (\\d+)", "LIMIT $1");
        sql = sql.replaceAll("(?i)FROM DUAL", "");
        return sql;
    }
    return sql;
}
```

---

## Best Practices Summary

### Do's ✅
- Always use `AbstractCompiler.findType()` for type resolution
- Cache compilation units and types in `AntikytheraRunTime`
- Handle null returns gracefully (many operations can fail)
- Use `Settings` for all configuration
- Run full test suite before committing
- Add javadoc for public methods
- Use try-catch for reflection operations
- Check `shouldSkip()` before processing files

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
- Keep methods under 50 lines where possible
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

## Additional Resources

- **README.md** - Project overview and setup
- **GEMINI.md** - Quick reference for Gemini
- **pom.xml** - Dependencies and build configuration
- **JavaParser docs** - https://javaparser.org/
- **ByteBuddy docs** - https://bytebuddy.net/

---

## Contributing

When making changes:

1. Create a feature branch
2. Add tests for new functionality
3. Run full test suite (`mvn test`)
4. Update this documentation if needed
5. Create a pull request

For questions or issues, refer to the project repository or contact the maintainers.

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

**Antikythera** is an automated test generator and refacoring tool for Java projects. It generates:
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
        ├─▶ Test method generation
        ├─▶ Mock setup generation
        ├─▶ Assertion generation
        └─▶ File writing (Antikythera.writeFilesToTest)
        │
        ▼
Output Directory (JUnit Test Files)
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
- `MCEWrapper.java` - Wraps MethodCallExpr with argument type information
- `MavenHelper.java` - Parses pom.xml and builds classpath
- `Stats.java` - Tracks parsing statistics

#### Parser Converter Subpackage
Located at `parser/converter`, this is a comprehensive JPA/HQL to SQL conversion subsystem.

**Purpose:** Convert JPA/JPQL/HQL queries to native SQL with full dialect support and entity metadata resolution.

##### Core Interfaces & Implementations

**`JpaQueryConverter.java` (Interface)**
Defines the contract for JPA query conversion.

**Methods:**
- `convertToNativeSQL(String jpaQuery, EntityMetadata, DatabaseDialect)` - Main conversion method
- `canConvert(String jpaQuery)` - Validates if query is convertible
- `supportsDialect(DatabaseDialect dialect)` - Checks dialect support

**`HibernateQueryConverter.java`**
Primary implementation of JpaQueryConverter using Hibernate's query parsing.

**Features:**
- Parses HQL/JPQL using pattern matching and JSQLParser
- Supports SELECT, UPDATE, DELETE queries
- Named parameter to positional parameter conversion
- Aggregate function handling (COUNT, SUM, AVG, MIN, MAX)
- Subquery support (EXISTS, IN subqueries)
- Dialect-specific transformations (Oracle, PostgreSQL)

**Key Methods:**
- `parseHqlQuery(String)` - Parses HQL into QueryNode AST
- `extractParameterMappings(String)` - Maps named params to positions
- `extractReferencedTables(EntityMetadata, QueryNode)` - Finds referenced tables
- `applyAdditionalDialectTransformations(String, DatabaseDialect)` - Dialect-specific SQL fixes

**Supported Patterns:**
- Entity references: `FROM User u` → `FROM users u`
- Property refs: `u.userName` → `u.user_name`
- Named params: `:paramName` → `?`
- Aggregate functions with DISTINCT
- Complex WHERE/HAVING clauses

##### SQL Generation & Transformation

**`SqlGenerationVisitor.java`**
Visitor pattern implementation for converting HQL AST to SQL.

**Multi-Phase Conversion Process:**
1. **SELECT Statement** - Entity to table mapping, property to column
2. **Entity References** - Convert FROM/JOIN entity names to tables
3. **Property References** - Convert alias.property to alias.column
4. **JOIN Operations** - Handle INNER/LEFT/RIGHT/FULL joins
5. **WHERE Clause** - Property-to-column mapping in conditions
6. **Aggregate Functions** - COUNT/SUM/AVG/MIN/MAX with column mapping
7. **GROUP BY Clause** - Property name conversion
8. **HAVING Clause** - Column mapping in aggregate conditions
9. **Subqueries** - Recursive processing of nested SELECTs
10. **Named Parameters** - Convert to positional (?)
11. **Dialect Transformations** - Apply dialect-specific rules

**Key Methods:**
- `convertToSql(QueryNode, SqlConversionContext)` - Main entry point
- `convertSelectStatement(Select, SqlConversionContext)` - AST-based SELECT conversion
- `convertUpdateStatement(Update, SqlConversionContext)` - AST-based UPDATE conversion
- `convertDeleteStatement(Delete, SqlConversionContext)` - AST-based DELETE conversion
- `convertExpressionToSnakeCase(Expression, SqlConversionContext)` - Recursive expression conversion
- `convertEntityReferences(String, SqlConversionContext)` - Entity to table mapping
- `convertPropertyReferences(String, SqlConversionContext)` - Property to column mapping
- `convertJoinOperations(String, SqlConversionContext)` - JOIN clause generation
- `convertAggregateFunctions(String, SqlConversionContext)` - Aggregate function handling
- `handleImplicitJoins(String, SqlConversionContext)` - Detect and add missing JOINs

**Supported Constructs:**
- SELECT/UPDATE/DELETE statements
- INNER/LEFT/RIGHT/FULL OUTER joins
- Aggregate functions: COUNT, SUM, AVG, MIN, MAX
- GROUP BY and HAVING clauses
- ORDER BY clauses
- Subqueries (SELECT in WHERE, EXISTS, IN)
- CASE expressions
- Binary expressions (AND, OR, =, <, >, etc.)
- Function calls with parameter conversion

##### Dialect Support System

**`DatabaseDialect.java` (Enum)**
Defines supported database dialects with dialect-specific behaviors.

**Supported Dialects:**
- **ORACLE** - Oracle Database with ROWNUM, boolean as 0/1, sequence.NEXTVAL
- **POSTGRESQL** - PostgreSQL with LIMIT, native boolean, NEXTVAL('sequence')

**Methods (per dialect):**
- `transformBooleanValue(String)` - true/false → 1/0 (Oracle) or unchanged (PostgreSQL)
- `applyLimitClause(String sql, int limit)` - LIMIT vs ROWNUM
- `getSequenceNextValueSyntax(String)` - Sequence next value syntax
- `getConcatenationOperator()` - String concatenation (|| for both)
- `supportsBoolean()` - Native boolean support check
- `transformSql(String)` - Apply all dialect transformations
- `fromJdbcUrl(String)` - Detect dialect from JDBC URL
- `fromString(String)` - Parse dialect from string identifier

**`DialectHandler.java`**
Integration layer between Settings and DatabaseDialect.

**Features:**
- Auto-detects dialect from configuration (JDBC URL)
- Provides convenience methods for dialect operations
- Backward compatibility with existing RepositoryParser

**Key Methods:**
- `detectDialectFromConfiguration()` - Read from Settings
- `detectDialectFromUrl(String jdbcUrl)` - Parse JDBC URL
- `transformSql(String)` - Apply current dialect transformations
- `isOracle()` / `isPostgreSQL()` - Dialect checks
- `fromRepositoryParser(String)` - Compatibility method

**`DialectTransformer.java`**
Comprehensive dialect-specific SQL transformations.

**Oracle Transformations:**
- LIMIT → ROWNUM (with OFFSET support)
- CONCAT(a, b) → (a || b)
- CURRENT_TIMESTAMP → SYSDATE
- NOW() → SYSDATE
- ILIKE → LIKE (with UPPER wrapping)
- COALESCE(a, b) → NVL(a, b) (2 args only)
- Date/time function conversions

**PostgreSQL Transformations:**
- SYSDATE → CURRENT_TIMESTAMP
- sequence.NEXTVAL → NEXTVAL('sequence')
- NVL(a, b) → COALESCE(a, b)
- ROWNUM → LIMIT
- Length → CHAR_LENGTH
- Oracle date functions to PostgreSQL equivalents

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
- Property-to-column mappings: `Map<String, ColumnMapping>`
- Relationship mappings: `Map<String, JoinMapping>`

**Methods:**
- `getTableMapping(String entityName)` - Get table for entity
- `getColumnMapping(String propertyName)` - Get column for property
- `getJoinMapping(String relationshipProperty)` - Get join info
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
- `getColumnMapping(String propertyName)` - Get column for property
- `hasPropertyMapping(String)` - Check if property mapped

**`ColumnMapping.java`**
Maps entity property to database column.

**Fields:**
- `propertyName` - Entity property name
- `columnName` - Database column name
- `tableName` - Containing table
- `javaType` - Java class type
- `sqlType` - SQL type (VARCHAR, INTEGER, etc.)
- `nullable` - NULL constraint

**Methods:**
- `getFullyQualifiedColumnName()` - Returns table.column

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

**`SqlConversionContext.java` (Record)**
Context object passed through conversion pipeline.

**Fields:**
- `entityMetadata` - EntityMetadata instance
- `dialect` - Target DatabaseDialect

**`QueryNode.java` (Interface)**
Represents nodes in HQL AST.

**Methods:**
- Tree navigation: getParent(), getChildren(), getFirstChild(), etc.
- Node properties: getType(), getText(), getLine(), getColumn()
- Tree manipulation: addChild(), removeChild(), replaceChild()

##### Usage Example

```java
// Setup
EntityMappingResolver resolver = new EntityMappingResolver();
EntityMetadata metadata = resolver.resolveEntityMetadata(User.class);
DatabaseDialect dialect = DatabaseDialect.POSTGRESQL;

// Create converter
HibernateQueryConverter converter = new HibernateQueryConverter(dialect);

// Convert query
String hql = "SELECT u FROM User u WHERE u.userName = :name";
ConversionResult result = converter.convertToNativeSQL(hql, metadata, dialect);

if (result.isSuccessful()) {
    String sql = result.getNativeSql();
    // "SELECT u.* FROM users u WHERE u.user_name = ?"
    List<ParameterMapping> params = result.getParameterMappings();
    // [{name="name", position=1, type=String.class, column="user_name"}]
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
- `SupplierEvaluator.java` - Supplier<T> support
- `BiFunctionEvaluator.java` - BiFunction<T, U, R> support
- `FunctionalConverter.java` - Converts method references to lambdas

##### Mocking (`evaluator/mock`)
Mock configuration and registry.

- `MockingRegistry.java` - Registers custom mock expressions
- `MockConfigReader.java` - Reads mock configuration from YAML

##### Logging (`evaluator/logging`)
Tracks logging statements for test assertions.

- `AKLogger.java` - Intercepts logging calls during evaluation

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

### 4. Configuration Module (`sa.com.cloudsolutions.antikythera.configuration`)

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
│   ├── MCEWrapper.java                  # Method call expression wrapper
│   ├── MavenHelper.java                 # Maven POM parsing
│   ├── Stats.java                       # Statistics tracking
│   └── converter/                       # JPA query conversion
│       ├── JpaQueryConverter.java
│       ├── HibernateQueryConverter.java
│       ├── EntityMappingResolver.java
│       ├── DialectHandler.java
│       └── ... (12 more classes)
│
├── evaluator/
│   ├── Evaluator.java                   # Core evaluation engine
│   ├── EvaluatorFactory.java            # Evaluator creation
│   ├── AntikytheraRunTime.java          # Global runtime state
│   ├── Variable.java                    # Runtime variable representation
│   ├── Scope.java                       # Method call scope
│   ├── ScopeChain.java                  # Chained method call tracking
│   ├── Branching.java                   # Branch tracking
│   ├── ArgumentGenerator.java           # Test argument generation
│   ├── AKBuddy.java                     # ByteBuddy integration
│   ├── MethodInterceptor.java           # Method call interception
│   ├── Reflect.java                     # Reflection utilities
│   ├── ReflectionArguments.java         # Reflection argument handling
│   ├── functional/                      # Functional programming support
│   │   ├── FPEvaluator.java
│   │   ├── FunctionEvaluator.java
│   │   ├── ConsumerEvaluator.java
│   │   ├── SupplierEvaluator.java
│   │   └── ... (5 more classes)
│   ├── logging/
│   │   └── AKLogger.java                # Logging interception
│   └── mock/
│       ├── MockingRegistry.java         # Mock management
│       └── MockConfigReader.java        # Mock configuration
│
├── generator/
│   ├── Antikythera.java                 # Main entry point
│   ├── TestGenerator.java               # Base test generator
│   ├── TypeWrapper.java                 # Type information wrapper
│   ├── BaseRepositoryQuery.java         # Query representation
│   ├── RepositoryQuery.java             # Query execution
│   ├── CopyUtils.java                   # File copying utilities
│   └── TruthTable.java                  # Branch coverage support
│
├── depsolver/
│   ├── InterfaceSolver.java             # Interface implementation resolution
│   └── ClassProcessor.java              # Class dependency processing
│
├── finch/
│   └── Finch.java                       # External source integration
│
└── exception/
    ├── AntikytheraException.java        # Base exception
    ├── EvaluatorException.java          # Evaluation errors
    ├── GeneratorException.java          # Generation errors
    └── AUTException.java                # Application Under Test errors
```

**Total:** 109 Java source files

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

# Antikythera - Gemini AI Instructions

## Project Purpose
Automated test generation framework for Java projects. Generates unit tests for services and API tests for REST controllers.

## Main Entry Point
```java
Antikythera antk = Antikythera.getInstance();
antk.preProcess();
antk.generateApiTests();    // REST controllers
antk.generateUnitTests();   // Services
```

## Architecture: Three-Phase Pipeline

1. **Parsing** (`AbstractCompiler`) - JavaParser-based source code parsing
2. **Evaluation** (`Evaluator`) - Symbolic execution engine
3. **Generation** (`TestGenerator`) - JUnit test code generation

## Key API Methods

### Configuration
- `Settings.loadConfigMap()` - Load from generator.yml
- `Settings.getBasePath()` - Project root
- `Settings.getPropertyList(Settings.CONTROLLERS, String.class)` - Get list config

### Parsing
- `AbstractCompiler.compile(String path)` - Parse Java file
- `AbstractCompiler.findType(CompilationUnit cu, String name)` - Resolve type
- `AntikytheraRunTime.getCompilationUnit(String className)` - Get cached CU

### Evaluation
- `EvaluatorFactory.create(String className, Class<Evaluator> cls)` - Create evaluator
- `Evaluator.evaluateExpression(Expression expr)` - Evaluate code
- `Evaluator.evaluateMethodCall(MethodCallExpr mce)` - Execute method call

### Query Conversion
- `HQLParserAdapter.convertToNativeSQL(String hql)` - HQL → SQL
- `MethodToSQLConverter.extractComponents(String methodName)` - Parse method name
- `EntityMappingResolver.resolveEntityMetadata(Class<?> entityClass)` - Get metadata

## Parser Classes
- `RestControllerParser` - Parse REST controllers
- `ServicesParser` - Parse service classes
- `RepositoryParser` - Parse JPA repositories
- All extend `AbstractCompiler`

## Common Workflows

**Workflow 1: Generate API Tests**
```
1. Configure controllers in generator.yml
2. Antikythera.getInstance()
3. preProcess() - Parse all files
4. generateApiTests() - For each controller
```

**Workflow 2: Resolve Type**
```
1. Get CompilationUnit from AntikytheraRunTime cache
2. If null, compile with AbstractCompiler.compile()
3. Use AbstractCompiler.findType(cu, "TypeName")
4. Check TypeWrapper.isService(), .isController(), etc.
```

**Workflow 3: Convert HQL Query**
```
1. Get entity metadata: EntityMappingResolver.resolveEntityMetadata()
2. Create adapter: new HQLParserAdapter(cu, entityWrapper)
3. Convert: adapter.convertToNativeSQL("SELECT u FROM User u")
4. Check ConversionResult.isSuccessful()
```

## Technology Stack
- Java 21 (requires --add-opens flags)
- JavaParser 3.27.0
- ByteBuddy 1.15.3
- Spring Boot 2.7.14
- hql-parser 0.0.15 (ANTLR4)

## Important Constraints
- Java 21 required
- Test requires: antikythera-sample-project and antikythera-test-helper repos
- JVM flag: `--add-opens java.base/java.util.stream=ALL-UNNAMED`
- Always use Settings for configuration, never hardcode

## Documentation Files
- **WARP.md** - Complete technical guide (primary)
- **AGENT.md** - Quick reference
- **README.md** - Project overview


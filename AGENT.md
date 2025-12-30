# Antikythera - AI Agent Quick Reference

**Primary Documentation:** See `WARP.md` for comprehensive technical details.

## Quick Entry Points

### Generate Tests
```java
Antikythera antk = Antikythera.getInstance();
antk.preProcess();
antk.generateApiTests();    // REST controllers
antk.generateUnitTests();   // Services
```

### Parse & Analyze Code
```java
// Parse a class
AbstractCompiler compiler = new AbstractCompiler();
compiler.compile("path/to/Class.java");

// Resolve types
CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
TypeWrapper type = AbstractCompiler.findType(cu, "TypeName");
```

### Convert Queries
```java
// HQL to SQL
HQLParserAdapter adapter = new HQLParserAdapter(cu, entityWrapper);
ConversionResult result = adapter.convertToNativeSQL("SELECT u FROM User u");

// Method name to SQL
List<String> components = MethodToSQLConverter.extractComponents("findByUsername");
```

## Key Classes

- **Main Entry:** `Antikythera` - Test generation orchestration
- **Parsing:** `AbstractCompiler`, `RestControllerParser`, `ServicesParser`, `RepositoryParser`
- **Evaluation:** `Evaluator`, `EvaluatorFactory`, `SpringEvaluator`
- **Generation:** `TestGenerator`, `SpringTestGenerator`, `UnitTestGenerator`
- **Configuration:** `Settings` - YAML config management
- **Runtime:** `AntikytheraRunTime` - Global caches

## Project Structure

- `parser/` - Source code parsing (JavaParser-based)
- `evaluator/` - Symbolic execution engine
- `generator/` - Test code generation
- `parser/converter/` - HQL/JPA query conversion
- `configuration/` - YAML config management

## Configuration

Load from `generator.yml`:
- `base_path` - Target project root
- `controllers` - Controllers to test
- `services` - Services to test
- `database.url` - For repository query execution

## Technology Stack

- Java 21, Maven
- JavaParser 3.27.0
- ByteBuddy 1.15.3
- Spring Boot 2.7.14
- hql-parser 0.0.15 (ANTLR4-based)

**For full details, see `WARP.md`.**


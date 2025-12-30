# GitHub Copilot Instructions for Antikythera

## Project Overview
Antikythera is an automated test generator for Java projects. It generates unit tests and API tests using a three-phase pipeline: Parsing → Evaluation → Generation.

## Quick Start for Copilot

When generating code for Antikythera, use these entry points:

### Test Generation
```java
Antikythera antk = Antikythera.getInstance();
antk.preProcess();
antk.generateApiTests();    // For REST controllers
antk.generateUnitTests();   // For services
```

### Code Parsing
```java
// Check cache first
CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
if (cu == null) {
    AbstractCompiler compiler = new AbstractCompiler();
    compiler.compile(AbstractCompiler.classToPath(className));
    cu = compiler.getCompilationUnit();
}

// Resolve types
TypeWrapper type = AbstractCompiler.findType(cu, "TypeName");
```

### Expression Evaluation
```java
Evaluator evaluator = EvaluatorFactory.create(className, Evaluator.class);
Variable result = evaluator.evaluateExpression(expression);
```

## Key Classes to Use

1. **Configuration:** `Settings` - Always use this for config, never hardcode paths
2. **Parsing:** `AbstractCompiler` - Base parser, use subclasses for specific types
3. **Type Resolution:** `AbstractCompiler.findType()` - Always use this method
4. **Runtime State:** `AntikytheraRunTime` - Global caches and state
5. **Query Conversion:** `HQLParserAdapter` for HQL, `MethodToSQLConverter` for method names

## Common Patterns

**Pattern: Parse and Generate**
```java
ServicesParser parser = new ServicesParser("com.example.UserService");
parser.start();
parser.writeFiles();
```

**Pattern: Type Resolution**
```java
CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
TypeWrapper type = AbstractCompiler.findType(cu, "User");
if (type != null && type.isService()) {
    // Handle service
}
```

**Pattern: Query Conversion**
```java
EntityMappingResolver resolver = new EntityMappingResolver();
EntityMetadata metadata = resolver.resolveEntityMetadata(User.class);
HQLParserAdapter adapter = new HQLParserAdapter(cu, entityWrapper);
ConversionResult result = adapter.convertToNativeSQL(hqlQuery);
```

## Important Notes for Copilot

- **Always check cache:** Use `AntikytheraRunTime.getCompilationUnit()` before parsing
- **Type resolution order:** Current CU → imports → same package → java.lang → classpath → extra_exports
- **Configuration:** Use `Settings.getBasePath()`, never hardcode paths
- **Error handling:** Types may not resolve - always check for null
- **Testing:** Requires JVM flag `--add-opens java.base/java.util.stream=ALL-UNNAMED`

## File Structure
- `src/main/java/sa/com/cloudsolutions/antikythera/`
  - `parser/` - Parsing logic
  - `evaluator/` - Expression evaluation
  - `generator/` - Test generation
  - `configuration/` - Config management

## Documentation
See `WARP.md` for complete API documentation and `AGENT.md` for quick reference.


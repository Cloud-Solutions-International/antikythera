# Strengthening Autowired Handling for Circular Dependency Detection

## Executive Summary

This document outlines a plan to strengthen the existing autowired handling infrastructure to support static dependency analysis for circular dependency detection. The goal is to extract and enhance the existing autowired detection logic to create a foundation for static dependency graph building.

## Current State Analysis

### Existing Infrastructure

#### 1. **SpringEvaluator.autoWire()** (Runtime Evaluation)
**Location**: `SpringEvaluator.java` (line 522-551)

**Current Purpose**: Runtime autowiring for test generation/evaluation
- Detects `@Autowired` fields
- Creates runtime `Variable` instances
- Stores in `AntikytheraRunTime.autowired` map (keyed by fully qualified class name)
- Integrates with `MockingRegistry` for mocked dependencies

**Limitations for Static Analysis**:
- Operates at runtime during code evaluation
- Creates `Variable` instances (runtime objects), not dependency metadata
- `autowired` map stores runtime state, not static dependency relationships

#### 2. **UnitTestGenerator Autowired Detection** (Code Generation)
**Location**: `UnitTestGenerator.java` (lines 977-1029)

**Current Capabilities**:
- `detectAutoWiringHelper()`: Detects `@Autowired` fields in classes
- `detectConstructorInjection()`: Detects constructor-based dependency injection
- `mapParamToFields()`: Maps constructor parameters to fields
- Uses `MockingRegistry.generateRegistryKey()` for type identification

**What's Good**:
- Already has logic to detect field injection (`@Autowired` annotation)
- Already has logic to detect constructor injection
- Already handles parameter-to-field mapping

**What's Missing**:
- This is tied to test generation context (requires `testSuite`)
- Doesn't collect dependency information for analysis
- Logic is spread across multiple private methods

#### 3. **MockingRegistry** (Type Registry)
**Location**: `MockingRegistry.java`

**Current Purpose**: Track mocked types for test generation
- `generateRegistryKey(List<TypeWrapper>)`: Creates consistent keys for types
- `markAsMocked(String)`: Marks types as mocked
- `isMockTarget(String)`: Checks if type should be mocked

**Value for Static Analysis**:
- Registry key generation could be reused for dependency tracking
- Consistent type identification mechanism

#### 4. **AbstractCompiler.findTypesInVariable()** (Type Resolution)
**Location**: `AbstractCompiler.java` (line 232)

**Current Purpose**: Resolve types from AST nodes (fields, parameters)
- Handles `VariableDeclarator`, `FieldDeclaration`, `Parameter`
- Resolves generic types
- Returns `List<TypeWrapper>`

**Value for Static Analysis**:
- **Perfect** for resolving dependency target types
- Already handles complex type scenarios (generics, imports, etc.)

#### 5. **Fields.java Example** (Reference Implementation)
**Location**: `antikythera-examples/src/main/java/sa/com/cloudsolutions/antikythera/examples/Fields.java`

**What It Shows**:
- Example of static field dependency collection
- Builds a dependency map from `TypeWrapper` instances
- Demonstrates the pattern we should follow

---

## Strengthening Plan

### Phase 1: Extract Static Dependency Analyzer (Priority: HIGH)

**Goal**: Create a reusable static dependency analyzer that extracts autowired dependencies without requiring runtime evaluation or test generation context.

**New Class**: `AutowiredDependencyAnalyzer`

**Package**: `sa.com.cloudsolutions.antikythera.depsolver`

**Responsibilities**:
1. Analyze Spring beans for autowired dependencies
2. Extract field injection dependencies
3. Extract constructor injection dependencies
4. Extract setter injection dependencies (NEW)
5. Return structured dependency information

**Key Methods**:
```java
public class AutowiredDependencyAnalyzer {
    
    /**
     * Analyze a Spring bean class and extract all autowired dependencies
     */
    public SpringBeanDependencies analyzeDependencies(TypeDeclaration<?> type, CompilationUnit cu) {
        // Detect field, constructor, and setter injection
    }
    
    /**
     * Extract field dependencies (annotated with @Autowired)
     */
    private List<FieldDependency> extractFieldDependencies(TypeDeclaration<?> type, CompilationUnit cu)
    
    /**
     * Extract constructor dependencies
     * Handles:
     * - Explicit @Autowired on constructor
     * - Single constructor (implicit autowiring in Spring 4.3+)
     * - Constructor parameters mapped to fields
     */
    private List<ConstructorDependency> extractConstructorDependencies(TypeDeclaration<?> type, CompilationUnit cu)
    
    /**
     * Extract setter method dependencies (annotated with @Autowired)
     */
    private List<SetterDependency> extractSetterDependencies(TypeDeclaration<?> type, CompilationUnit cu)
    
    /**
     * Resolve target type from field/parameter, handling:
     * - Generic types (e.g., List<ServiceA>)
     * - Interfaces (need to find implementations)
     * - Qualified vs simple names
     */
    private String resolveTargetType(Type type, CompilationUnit cu)
}
```

**Dependency Model Classes**:
```java
public class SpringBeanDependencies {
    private final String beanClassName;
    private final List<FieldDependency> fieldDependencies;
    private final List<ConstructorDependency> constructorDependencies;
    private final List<SetterDependency> setterDependencies;
    // getters...
}

public class FieldDependency {
    private final String fieldName;
    private final String targetType;  // Fully qualified name
    private final boolean hasAutowired;
    private final boolean hasLazy;  // @Lazy annotation present
    private final boolean hasQualifier;  // @Qualifier annotation present
    private final String qualifierValue;  // If @Qualifier present
}

public class ConstructorDependency {
    private final String paramName;
    private final String targetType;  // Fully qualified name
    private final int paramIndex;
    private final boolean hasAutowired;  // Explicit @Autowired
    private final boolean isImplicit;  // Single constructor (implicit autowiring)
}

public class SetterDependency {
    private final String methodName;
    private final String paramName;
    private final String targetType;  // Fully qualified name
    private final boolean hasAutowired;
}
```

### Phase 2: Enhance Type Resolution (Priority: HIGH)

**Goal**: Improve type resolution to handle edge cases needed for dependency analysis.

**Enhancements to `AbstractCompiler.findTypesInVariable()`**:

1. **Interface Resolution**
   - When target type is an interface, resolve to concrete implementations
   - Use `AntikytheraRunTime.findImplementations()`
   - Handle `@Qualifier` to select specific implementation

2. **Generic Type Handling**
   - Extract concrete types from generic collections
   - Example: `List<ServiceA>` → `ServiceA`
   - Filter out Spring beans from generic collections

3. **Qualifier Support**
   - Track `@Qualifier` annotations on fields/parameters
   - Use qualifier value to select specific bean when multiple implementations exist

**New Helper Method**:
```java
/**
 * Resolve Spring bean type from a field/parameter type.
 * Handles interfaces by finding implementations.
 * Returns the fully qualified name of the target bean type.
 */
public static String resolveSpringBeanType(Type type, CompilationUnit cu, 
                                           Optional<String> qualifier) {
    List<TypeWrapper> wrappers = findTypesInVariable(...);
    // If interface, find implementations
    // If qualifier present, filter by qualifier
    // Return FQN
}
```

### Phase 3: Integrate with TypeWrapper (Priority: MEDIUM)

**Goal**: Store dependency information in TypeWrapper for easy access during analysis.

**Enhancement to TypeWrapper**:
```java
public class TypeWrapper {
    // ... existing fields ...
    
    // NEW: Dependency information (lazy-loaded)
    private SpringBeanDependencies dependencies;
    
    /**
     * Get dependencies for this Spring bean.
     * Will analyze and cache if not already analyzed.
     */
    public Optional<SpringBeanDependencies> getDependencies() {
        if (!isService() && !isController() && !isComponent()) {
            return Optional.empty();
        }
        if (dependencies == null && type != null) {
            dependencies = AutowiredDependencyAnalyzer.analyzeDependencies(
                type, 
                AntikytheraRunTime.getCompilationUnit(getFullyQualifiedName())
            );
        }
        return Optional.ofNullable(dependencies);
    }
}
```

**Note**: Consider memory implications. For large codebases, we might want to:
- Lazy-load dependencies only when needed
- Store dependencies separately (e.g., in `AntikytheraRunTime`)

### Phase 4: Enhance MockingRegistry for Dependency Tracking (Priority: LOW)

**Goal**: Add dependency tracking capabilities to MockingRegistry without breaking existing functionality.

**New Methods**:
```java
public class MockingRegistry {
    // ... existing code ...
    
    /**
     * Generate a dependency key for tracking.
     * Similar to generateRegistryKey but specifically for dependency relationships.
     */
    public static String generateDependencyKey(String sourceBean, String targetBean, DependencyType type) {
        return sourceBean + "->" + targetBean + ":" + type.name();
    }
    
    /**
     * Track a dependency relationship.
     * Used for static analysis, separate from mocking.
     */
    public static void trackDependency(String sourceBean, String targetBean, DependencyType type) {
        // Store in separate map for dependency analysis
    }
}
```

**Note**: This might be overkill. The dependency analyzer should handle its own tracking.

### Phase 5: Refactor UnitTestGenerator to Use New Analyzer (Priority: MEDIUM)

**Goal**: Simplify `UnitTestGenerator` by using the new static analyzer.

**Changes**:
- Replace `detectAutoWiringHelper()` with calls to `AutowiredDependencyAnalyzer`
- Replace `detectConstructorInjection()` with calls to `AutowiredDependencyAnalyzer`
- Reduce code duplication
- Make test generation logic cleaner

**Before**:
```java
private void detectAutoWiringHelper(...) {
    for (FieldDeclaration fd : classUnderTest.getFields()) {
        // Manual annotation checking
        // Manual type resolution
        // Manual registry key generation
    }
}
```

**After**:
```java
private void detectAutoWiringHelper(...) {
    SpringBeanDependencies deps = AutowiredDependencyAnalyzer.analyzeDependencies(
        classUnderTest, cu
    );
    for (FieldDependency fieldDep : deps.getFieldDependencies()) {
        // Use structured dependency info
    }
}
```

---

## Implementation Details

### Field Dependency Detection

**Current Logic** (from `UnitTestGenerator.detectAutoWiringHelper()`):
```java
if (fd.getAnnotationByName("Autowired").isPresent()) {
    // Process field
}
```

**Enhanced Logic**:
```java
private List<FieldDependency> extractFieldDependencies(TypeDeclaration<?> type, CompilationUnit cu) {
    List<FieldDependency> deps = new ArrayList<>();
    
    for (FieldDeclaration field : type.getFields()) {
        boolean hasAutowired = field.getAnnotationByName("Autowired").isPresent();
        boolean hasLazy = field.getAnnotationByName("Lazy").isPresent();
        
        Optional<AnnotationExpr> qualifier = field.getAnnotationByName("Qualifier");
        String qualifierValue = qualifier
            .filter(a -> a.isNormalAnnotationExpr())
            .map(a -> extractQualifierValue(a.asNormalAnnotationExpr()))
            .orElse(null);
        
        if (hasAutowired || shouldAutoWireByDefault(field)) {
            for (VariableDeclarator var : field.getVariables()) {
                List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(var);
                if (!wrappers.isEmpty()) {
                    String targetType = wrappers.getLast().getFullyQualifiedName();
                    
                    // Resolve interface to implementation if needed
                    if (AntikytheraRunTime.isInterface(targetType)) {
                        targetType = resolveInterfaceImplementation(targetType, qualifierValue);
                    }
                    
                    deps.add(new FieldDependency(
                        var.getNameAsString(),
                        targetType,
                        hasAutowired,
                        hasLazy,
                        qualifierValue != null,
                        qualifierValue
                    ));
                }
            }
        }
    }
    return deps;
}
```

### Constructor Dependency Detection

**Current Logic** (from `UnitTestGenerator.detectConstructorInjection()`):
```java
for (ConstructorDeclaration constructor : decl.getConstructors()) {
    // Process parameters
}
```

**Enhanced Logic**:
```java
private List<ConstructorDependency> extractConstructorDependencies(TypeDeclaration<?> type, CompilationUnit cu) {
    List<ConstructorDependency> deps = new ArrayList<>();
    
    List<ConstructorDeclaration> constructors = type.getConstructors();
    boolean hasSingleConstructor = constructors.size() == 1;
    
    for (ConstructorDeclaration constructor : constructors) {
        boolean hasExplicitAutowired = constructor.getAnnotationByName("Autowired").isPresent();
        boolean isImplicit = !hasExplicitAutowired && hasSingleConstructor;
        
        if (hasExplicitAutowired || isImplicit) {
            int paramIndex = 0;
            for (Parameter param : constructor.getParameters()) {
                List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(param);
                if (!wrappers.isEmpty()) {
                    String targetType = wrappers.getLast().getFullyQualifiedName();
                    
                    // Check for @Qualifier on parameter
                    String qualifierValue = extractQualifierFromParameter(param);
                    
                    // Resolve interface if needed
                    if (AntikytheraRunTime.isInterface(targetType)) {
                        targetType = resolveInterfaceImplementation(targetType, qualifierValue);
                    }
                    
                    deps.add(new ConstructorDependency(
                        param.getNameAsString(),
                        targetType,
                        paramIndex++,
                        hasExplicitAutowired,
                        isImplicit
                    ));
                }
            }
        }
    }
    return deps;
}
```

### Setter Dependency Detection (NEW)

```java
private List<SetterDependency> extractSetterDependencies(TypeDeclaration<?> type, CompilationUnit cu) {
    List<SetterDependency> deps = new ArrayList<>();
    
    for (MethodDeclaration method : type.getMethods()) {
        if (method.isAnnotationPresent("Autowired") && method.getParameters().size() == 1) {
            Parameter param = method.getParameters().get(0);
            List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(param);
            
            if (!wrappers.isEmpty()) {
                String targetType = wrappers.getLast().getFullyQualifiedName();
                
                if (AntikytheraRunTime.isInterface(targetType)) {
                    targetType = resolveInterfaceImplementation(targetType, null);
                }
                
                deps.add(new SetterDependency(
                    method.getNameAsString(),
                    param.getNameAsString(),
                    targetType,
                    true
                ));
            }
        }
    }
    return deps;
}
```

### Interface Resolution Helper

```java
private String resolveInterfaceImplementation(String interfaceType, String qualifier) {
    Set<String> implementations = AntikytheraRunTime.findImplementations(interfaceType);
    
    if (implementations.isEmpty()) {
        // No implementations found - might be external dependency
        return interfaceType;
    }
    
    if (implementations.size() == 1) {
        return implementations.iterator().next();
    }
    
    // Multiple implementations - use qualifier if present
    if (qualifier != null) {
        // Try to find implementation with matching bean name or @Qualifier
        // This is complex and might require additional analysis
        // For now, return the interface type and let caller handle it
    }
    
    // Multiple implementations without qualifier
    // Return interface type - circular dependency detector should handle ambiguity
    return interfaceType;
}
```

---

## Testing Strategy

### Unit Tests

1. **AutowiredDependencyAnalyzer Tests**:
   - Field injection detection
   - Constructor injection detection
   - Setter injection detection
   - Interface resolution
   - Qualifier handling
   - Generic type handling

2. **Type Resolution Tests**:
   - Interface → Implementation resolution
   - Generic type extraction
   - Qualifier filtering

### Integration Tests

1. **Real-world Scenarios**:
   - Service with field injection
   - Service with constructor injection
   - Service with mixed injection types
   - Circular dependencies (should be detected)
   - Interface dependencies with multiple implementations

---

## Migration Path

### Step 1: Create AutowiredDependencyAnalyzer (Week 1)
- Create new class with basic structure
- Implement field dependency extraction
- Write unit tests

### Step 2: Enhance with Constructor Detection (Week 1-2)
- Add constructor dependency extraction
- Handle implicit autowiring (single constructor)
- Write tests

### Step 3: Add Setter Detection (Week 2)
- Add setter method detection
- Write tests

### Step 4: Integrate with Existing Code (Week 2-3)
- Refactor `UnitTestGenerator` to use new analyzer
- Verify test generation still works
- Write integration tests

### Step 5: Prepare for Circular Dependency Detection (Week 3)
- Add interface resolution
- Add qualifier support
- Optimize for large codebases (lazy loading, caching)

---

## Benefits

1. **Reusability**: Static dependency analysis can be used by:
   - Circular dependency detector
   - Test generators
   - Migration tools
   - Documentation generators

2. **Maintainability**: Centralized logic for dependency extraction
   - Single source of truth
   - Easier to test
   - Easier to extend

3. **Completeness**: Handles all injection types:
   - Field injection
   - Constructor injection
   - Setter injection (NEW)

4. **Accuracy**: Better type resolution:
   - Interface handling
   - Qualifier support
   - Generic type handling

5. **Foundation for Circular Detection**: Provides structured dependency data needed for cycle detection

---

## Open Questions

1. **Memory Considerations**: 
   - Should dependencies be stored in `TypeWrapper` or separately?
   - Should we lazy-load dependencies?
   - Should we cache dependency analysis results?

2. **Interface Resolution**:
   - How to handle multiple implementations without `@Qualifier`?
   - Should we return all possible implementations or just the interface?
   - How to handle `@Primary` annotations?

3. **Qualifier Resolution**:
   - How to match qualifier values to bean names?
   - Should we analyze bean names from class names or annotations?

4. **Generic Types**:
   - Should we extract Spring beans from generic collections?
   - Example: `List<ServiceA>` - should `ServiceA` be tracked as a dependency?

5. **Performance**:
   - Should we analyze dependencies on-demand or pre-compute?
   - How to handle incremental updates when files change?

---

## Next Steps

1. Review and approve this plan
2. Create initial `AutowiredDependencyAnalyzer` class structure
3. Implement field dependency extraction first (simplest case)
4. Add tests
5. Iterate with constructor and setter detection
6. Integrate with existing code
7. Prepare foundation for circular dependency detection


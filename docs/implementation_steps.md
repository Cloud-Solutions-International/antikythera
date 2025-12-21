# Implementation Steps: Strengthening Autowired Handling

## Overview

This document provides a step-by-step implementation guide for strengthening autowired handling to support circular dependency detection.

## Prerequisites

- Understanding of existing codebase (AbstractCompiler, TypeWrapper, AntikytheraRunTime)
- JavaParser knowledge
- Spring Framework dependency injection concepts
- Test-driven development approach

---

## Phase 1: Foundation - Create Core Classes (Week 1)

### Step 1.1: Create Dependency Model Classes

**Goal**: Define the data structures for representing dependencies.

**Files to Create**:
1. `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/dependency/FieldDependency.java`
2. `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/dependency/ConstructorDependency.java`
3. `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/dependency/SetterDependency.java`
4. `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/dependency/SpringBeanDependencies.java`
5. `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/dependency/DependencyType.java` (enum)

**Implementation Details**:

**FieldDependency.java**:
```java
package sa.com.cloudsolutions.antikythera.depsolver.dependency;

public class FieldDependency {
    private final String fieldName;
    private final String targetType;  // Fully qualified name
    private final boolean hasAutowired;
    private final boolean hasLazy;
    private final boolean hasQualifier;
    private final String qualifierValue;
    
    // Constructor, getters, equals, hashCode, toString
}
```

**ConstructorDependency.java**:
```java
package sa.com.cloudsolutions.antikythera.depsolver.dependency;

public class ConstructorDependency {
    private final String paramName;
    private final String targetType;  // Fully qualified name
    private final int paramIndex;
    private final boolean hasExplicitAutowired;
    private final boolean isImplicit;  // Single constructor (Spring 4.3+)
    
    // Constructor, getters, equals, hashCode, toString
}
```

**SetterDependency.java**:
```java
package sa.com.cloudsolutions.antikythera.depsolver.dependency;

public class SetterDependency {
    private final String methodName;
    private final String paramName;
    private final String targetType;  // Fully qualified name
    private final boolean hasAutowired;
    
    // Constructor, getters, equals, hashCode, toString
}
```

**DependencyType.java**:
```java
package sa.com.cloudsolutions.antikythera.depsolver.dependency;

public enum DependencyType {
    FIELD,
    CONSTRUCTOR,
    SETTER
}
```

**SpringBeanDependencies.java**:
```java
package sa.com.cloudsolutions.antikythera.depsolver.dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpringBeanDependencies {
    private final String beanClassName;
    private final List<FieldDependency> fieldDependencies;
    private final List<ConstructorDependency> constructorDependencies;
    private final List<SetterDependency> setterDependencies;
    
    public SpringBeanDependencies(String beanClassName) {
        this.beanClassName = beanClassName;
        this.fieldDependencies = new ArrayList<>();
        this.constructorDependencies = new ArrayList<>();
        this.setterDependencies = new ArrayList<>();
    }
    
    // Getters, adders, immutable views
    public List<FieldDependency> getFieldDependencies() {
        return Collections.unmodifiableList(fieldDependencies);
    }
    
    public void addFieldDependency(FieldDependency dep) {
        fieldDependencies.add(dep);
    }
    
    // Similar for constructor and setter dependencies
}
```

**Acceptance Criteria**:
- ✅ All classes compile
- ✅ Immutable data structures (use final fields)
- ✅ Proper equals/hashCode/toString implementations
- ✅ Unit tests for each class (test builders, getters, equals)

**Estimated Time**: 4-6 hours

---

### Step 1.2: Create AutowiredDependencyAnalyzer Skeleton

**Goal**: Create the main analyzer class with method stubs.

**File to Create**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Initial Structure**:
```java
package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import sa.com.cloudsolutions.antikythera.depsolver.dependency.*;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AutowiredDependencyAnalyzer {
    
    /**
     * Analyze a Spring bean class and extract all autowired dependencies
     */
    public SpringBeanDependencies analyzeDependencies(TypeDeclaration<?> type, CompilationUnit cu) {
        String className = type.getFullyQualifiedName()
            .orElseThrow(() -> new IllegalArgumentException("Type must have fully qualified name"));
        
        SpringBeanDependencies deps = new SpringBeanDependencies(className);
        
        if (!type.isClassOrInterfaceDeclaration()) {
            return deps;
        }
        
        ClassOrInterfaceDeclaration classDecl = type.asClassOrInterfaceDeclaration();
        
        // Extract different types of dependencies
        deps.getFieldDependencies().addAll(extractFieldDependencies(classDecl, cu));
        deps.getConstructorDependencies().addAll(extractConstructorDependencies(classDecl, cu));
        deps.getSetterDependencies().addAll(extractSetterDependencies(classDecl, cu));
        
        return deps;
    }
    
    private List<FieldDependency> extractFieldDependencies(ClassOrInterfaceDeclaration type, CompilationUnit cu) {
        // TODO: Implement in Step 2.1
        return new ArrayList<>();
    }
    
    private List<ConstructorDependency> extractConstructorDependencies(ClassOrInterfaceDeclaration type, CompilationUnit cu) {
        // TODO: Implement in Step 2.2
        return new ArrayList<>();
    }
    
    private List<SetterDependency> extractSetterDependencies(ClassOrInterfaceDeclaration type, CompilationUnit cu) {
        // TODO: Implement in Step 2.3
        return new ArrayList<>();
    }
}
```

**Acceptance Criteria**:
- ✅ Class compiles
- ✅ Method signatures are correct
- ✅ Returns empty lists for now (stubs)

**Estimated Time**: 1-2 hours

---

### Step 1.3: Create Unit Test Skeleton

**Goal**: Create test class with test data setup.

**File to Create**:
`antikythera/src/test/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzerTest.java`

**Initial Test Structure**:
```java
package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.depsolver.dependency.SpringBeanDependencies;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import static org.junit.jupiter.api.Assertions.*;

class AutowiredDependencyAnalyzerTest {
    
    private AutowiredDependencyAnalyzer analyzer;
    
    @BeforeEach
    void setUp() {
        analyzer = new AutowiredDependencyAnalyzer();
        AntikytheraRunTime.resetAll();
    }
    
    @Test
    void testAnalyzeDependencies_EmptyClass() {
        // Test with class that has no dependencies
        String code = "@Service\n" +
                     "public class EmptyService {\n" +
                     "}";
        
        CompilationUnit cu = StaticJavaParser.parse(code);
        TypeDeclaration<?> type = cu.getType(0);
        
        SpringBeanDependencies deps = analyzer.analyzeDependencies(type, cu);
        
        assertNotNull(deps);
        assertTrue(deps.getFieldDependencies().isEmpty());
        assertTrue(deps.getConstructorDependencies().isEmpty());
        assertTrue(deps.getSetterDependencies().isEmpty());
    }
}
```

**Acceptance Criteria**:
- ✅ Test class compiles
- ✅ Basic test setup works
- ✅ Can parse and analyze simple classes

**Estimated Time**: 2-3 hours

---

## Phase 2: Implement Core Functionality (Week 1-2)

### Step 2.1: Implement Field Dependency Extraction

**Goal**: Extract `@Autowired` field dependencies.

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Implementation**:
```java
private List<FieldDependency> extractFieldDependencies(ClassOrInterfaceDeclaration type, CompilationUnit cu) {
    List<FieldDependency> deps = new ArrayList<>();
    
    for (FieldDeclaration field : type.getFields()) {
        boolean hasAutowired = field.getAnnotationByName("Autowired").isPresent() ||
                              field.getAnnotationByName("org.springframework.beans.factory.annotation.Autowired").isPresent();
        
        if (!hasAutowired) {
            continue;
        }
        
        boolean hasLazy = field.getAnnotationByName("Lazy").isPresent() ||
                         field.getAnnotationByName("org.springframework.context.annotation.Lazy").isPresent();
        
        // Extract @Qualifier value if present
        Optional<String> qualifierValue = extractQualifierValue(field);
        
        for (VariableDeclarator var : field.getVariables()) {
            List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(var);
            if (wrappers.isEmpty()) {
                continue;
            }
            
            String targetType = wrappers.getLast().getFullyQualifiedName();
            
            // Resolve interface to implementation if needed
            targetType = resolveBeanType(targetType, qualifierValue, cu);
            
            deps.add(new FieldDependency(
                var.getNameAsString(),
                targetType,
                true,  // hasAutowired
                hasLazy,
                qualifierValue.isPresent(),
                qualifierValue.orElse(null)
            ));
        }
    }
    
    return deps;
}

private Optional<String> extractQualifierValue(FieldDeclaration field) {
    return field.getAnnotationByName("Qualifier")
        .or(() -> field.getAnnotationByName("org.springframework.beans.factory.annotation.Qualifier"))
        .filter(a -> a.isNormalAnnotationExpr())
        .map(a -> {
            // Extract value from @Qualifier("value")
            // Implementation details...
            return extractQualifierValueFromAnnotation(a.asNormalAnnotationExpr());
        });
}

private String resolveBeanType(String typeName, Optional<String> qualifier, CompilationUnit cu) {
    // If it's an interface, try to find implementations
    if (AntikytheraRunTime.isInterface(typeName)) {
        return resolveInterfaceImplementation(typeName, qualifier);
    }
    return typeName;
}

private String resolveInterfaceImplementation(String interfaceType, Optional<String> qualifier) {
    Set<String> implementations = AntikytheraRunTime.findImplementations(interfaceType);
    
    if (implementations.isEmpty()) {
        return interfaceType;  // No implementations found
    }
    
    if (implementations.size() == 1) {
        return implementations.iterator().next();
    }
    
    // Multiple implementations - use qualifier if present
    // For now, return interface type
    // TODO: Enhance qualifier matching in later step
    return interfaceType;
}
```

**Tests to Add**:
1. Field with `@Autowired` - should be detected
2. Field without `@Autowired` - should be ignored
3. Field with `@Autowired` and `@Lazy` - should detect both
4. Field with `@Qualifier` - should extract qualifier value
5. Interface field - should resolve to implementation if single
6. Generic field (e.g., `List<Service>`) - should handle correctly

**Acceptance Criteria**:
- ✅ All field dependency tests pass
- ✅ Handles `@Autowired` annotation (both simple and fully qualified)
- ✅ Extracts `@Lazy` annotation
- ✅ Extracts `@Qualifier` annotation value
- ✅ Resolves interfaces to implementations when possible

**Estimated Time**: 6-8 hours (including tests)

---

### Step 2.2: Implement Constructor Dependency Extraction

**Goal**: Extract constructor injection dependencies.

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Implementation**:
```java
private List<ConstructorDependency> extractConstructorDependencies(ClassOrInterfaceDeclaration type, CompilationUnit cu) {
    List<ConstructorDependency> deps = new ArrayList<>();
    
    List<ConstructorDeclaration> constructors = type.getConstructors();
    boolean hasSingleConstructor = constructors.size() == 1;
    
    for (ConstructorDeclaration constructor : constructors) {
        boolean hasExplicitAutowired = constructor.getAnnotationByName("Autowired").isPresent() ||
                                       constructor.getAnnotationByName("org.springframework.beans.factory.annotation.Autowired").isPresent();
        boolean isImplicit = !hasExplicitAutowired && hasSingleConstructor;
        
        // In Spring 4.3+, single constructor is implicitly autowired
        if (!hasExplicitAutowired && !isImplicit) {
            continue;
        }
        
        int paramIndex = 0;
        for (Parameter param : constructor.getParameters()) {
            List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(param);
            if (wrappers.isEmpty()) {
                paramIndex++;
                continue;
            }
            
            String targetType = wrappers.getLast().getFullyQualifiedName();
            
            // Extract qualifier from parameter if present
            Optional<String> qualifierValue = extractQualifierValueFromParameter(param);
            
            // Resolve interface to implementation if needed
            targetType = resolveBeanType(targetType, qualifierValue, cu);
            
            deps.add(new ConstructorDependency(
                param.getNameAsString(),
                targetType,
                paramIndex++,
                hasExplicitAutowired,
                isImplicit
            ));
        }
    }
    
    return deps;
}

private Optional<String> extractQualifierValueFromParameter(Parameter param) {
    return param.getAnnotationByName("Qualifier")
        .or(() -> param.getAnnotationByName("org.springframework.beans.factory.annotation.Qualifier"))
        .filter(a -> a.isNormalAnnotationExpr())
        .map(a -> extractQualifierValueFromAnnotation(a.asNormalAnnotationExpr()));
}
```

**Tests to Add**:
1. Constructor with explicit `@Autowired` - should be detected
2. Single constructor (implicit autowiring) - should be detected
3. Multiple constructors without `@Autowired` - should be ignored
4. Constructor with `@Qualifier` on parameters - should extract qualifier
5. Constructor with interface parameters - should resolve to implementation

**Acceptance Criteria**:
- ✅ All constructor dependency tests pass
- ✅ Handles explicit `@Autowired` on constructor
- ✅ Handles implicit autowiring (single constructor)
- ✅ Correctly ignores non-autowired constructors
- ✅ Extracts qualifier values from parameters

**Estimated Time**: 6-8 hours (including tests)

---

### Step 2.3: Implement Setter Dependency Extraction

**Goal**: Extract setter method injection dependencies.

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Implementation**:
```java
private List<SetterDependency> extractSetterDependencies(ClassOrInterfaceDeclaration type, CompilationUnit cu) {
    List<SetterDependency> deps = new ArrayList<>();
    
    for (MethodDeclaration method : type.getMethods()) {
        boolean hasAutowired = method.getAnnotationByName("Autowired").isPresent() ||
                              method.getAnnotationByName("org.springframework.beans.factory.annotation.Autowired").isPresent();
        
        // Setter injection: method annotated with @Autowired and single parameter
        if (!hasAutowired || method.getParameters().size() != 1) {
            continue;
        }
        
        Parameter param = method.getParameters().get(0);
        List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(param);
        
        if (wrappers.isEmpty()) {
            continue;
        }
        
        String targetType = wrappers.getLast().getFullyQualifiedName();
        
        Optional<String> qualifierValue = extractQualifierValueFromParameter(param);
        targetType = resolveBeanType(targetType, qualifierValue, cu);
        
        deps.add(new SetterDependency(
            method.getNameAsString(),
            param.getNameAsString(),
            targetType,
            true  // hasAutowired
        ));
    }
    
    return deps;
}
```

**Tests to Add**:
1. Setter method with `@Autowired` - should be detected
2. Method without `@Autowired` - should be ignored
3. Method with multiple parameters - should be ignored
4. Setter with `@Qualifier` - should extract qualifier

**Acceptance Criteria**:
- ✅ All setter dependency tests pass
- ✅ Only detects methods with `@Autowired` and single parameter
- ✅ Extracts method name and parameter type

**Estimated Time**: 3-4 hours (including tests)

---

### Step 2.4: Create Helper Utilities

**Goal**: Create reusable helper methods for common operations.

**File to Create/Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Helper Methods to Add**:
```java
/**
 * Extract qualifier value from a NormalAnnotationExpr representing @Qualifier
 */
private String extractQualifierValueFromAnnotation(NormalAnnotationExpr qualifierAnnotation) {
    return qualifierAnnotation.getPairs().stream()
        .filter(pair -> pair.getNameAsString().equals("value"))
        .findFirst()
        .map(pair -> {
            Expression value = pair.getValue();
            if (value.isStringLiteralExpr()) {
                return value.asStringLiteralExpr().asString();
            }
            return null;
        })
        .orElse(null);
}

/**
 * Check if a field should be autowired by default (future extension point)
 */
private boolean shouldAutoWireByDefault(FieldDeclaration field) {
    // For now, only explicit @Autowired
    // Could be extended for other frameworks or patterns
    return false;
}
```

**Acceptance Criteria**:
- ✅ Helper methods are well-tested
- ✅ Handle edge cases (missing values, non-string literals)
- ✅ Code is reusable and well-documented

**Estimated Time**: 2-3 hours

---

## Phase 3: Enhance Type Resolution (Week 2)

### Step 3.1: Enhance Interface Resolution

**Goal**: Better handling of interface dependencies with multiple implementations.

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Enhancement**:
```java
private String resolveInterfaceImplementation(String interfaceType, Optional<String> qualifier, CompilationUnit cu) {
    Set<String> implementations = AntikytheraRunTime.findImplementations(interfaceType);
    
    if (implementations.isEmpty()) {
        // No implementations - might be external dependency
        return interfaceType;
    }
    
    if (implementations.size() == 1) {
        return implementations.iterator().next();
    }
    
    // Multiple implementations
    if (qualifier.isPresent()) {
        // Try to match qualifier value to bean name
        String qualifierValue = qualifier.get();
        for (String impl : implementations) {
            if (matchesQualifier(impl, qualifierValue)) {
                return impl;
            }
        }
    }
    
    // Can't resolve - return interface type
    // Circular dependency detector should handle ambiguity
    return interfaceType;
}

private boolean matchesQualifier(String className, String qualifierValue) {
    // Simple bean name matching (class name decapitalized)
    String simpleName = className.substring(className.lastIndexOf('.') + 1);
    String beanName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    
    return beanName.equals(qualifierValue) || simpleName.equals(qualifierValue);
}
```

**Tests to Add**:
1. Interface with single implementation - should resolve
2. Interface with multiple implementations, no qualifier - should return interface
3. Interface with multiple implementations, matching qualifier - should resolve
4. Interface with no implementations - should return interface

**Acceptance Criteria**:
- ✅ Interface resolution tests pass
- ✅ Handles single implementation correctly
- ✅ Handles multiple implementations with qualifier
- ✅ Gracefully handles ambiguity

**Estimated Time**: 4-5 hours (including tests)

---

### Step 3.2: Handle Generic Types

**Goal**: Extract Spring bean types from generic collections (e.g., `List<ServiceA>`).

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Enhancement**:
```java
/**
 * Resolve target type, handling generics
 */
private String resolveBeanType(String typeName, Optional<String> qualifier, CompilationUnit cu) {
    // For now, use the last type from findTypesInVariable
    // This already handles generics to some extent
    // Could be enhanced to specifically extract Spring beans from collections
    
    if (AntikytheraRunTime.isInterface(typeName)) {
        return resolveInterfaceImplementation(typeName, qualifier, cu);
    }
    
    return typeName;
}
```

**Note**: Generic handling is partially done by `AbstractCompiler.findTypesInVariable()`. We may need to enhance this further based on requirements.

**Tests to Add**:
1. Field with generic type `List<ServiceA>` - should extract `ServiceA`
2. Field with `Map<String, ServiceB>` - should extract `ServiceB`
3. Constructor parameter with generic type

**Acceptance Criteria**:
- ✅ Generic type tests pass
- ✅ Spring beans extracted from collections
- ✅ Non-Spring types filtered out

**Estimated Time**: 3-4 hours (including tests)

---

## Phase 4: Integration (Week 2-3)

### Step 4.1: Create Integration Test Suite

**Goal**: Test analyzer with real-world Spring bean examples.

**File to Create**:
`antikythera/src/test/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzerIntegrationTest.java`

**Test Scenarios**:
1. Service with mixed injection types (field, constructor, setter)
2. Service with circular dependency (should be detected)
3. Service with interface dependencies
4. Service with `@Lazy` annotations
5. Service with `@Qualifier` annotations
6. Multiple implementations of interface

**Test Data**: Create sample Spring bean classes in test resources.

**Acceptance Criteria**:
- ✅ Integration tests pass
- ✅ Handles complex real-world scenarios
- ✅ Performance is acceptable

**Estimated Time**: 4-6 hours

---

### Step 4.2: Integrate with AbstractCompiler (Optional)

**Goal**: Make dependency analysis available during compilation phase.

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/parser/AbstractCompiler.java`

**Option 1**: Add method to trigger dependency analysis after `findContainedTypes()`:
```java
private void findContainedTypes(TypeDeclaration<?> declaration, CompilationUnit cu) {
    // ... existing code ...
    
    // NEW: Analyze dependencies for Spring beans
    if (typeWrapper.isService() || typeWrapper.isController() || typeWrapper.isComponent()) {
        AutowiredDependencyAnalyzer analyzer = new AutowiredDependencyAnalyzer();
        SpringBeanDependencies deps = analyzer.analyzeDependencies(type, cu);
        // Store dependencies somewhere (TypeWrapper or separate map)
    }
}
```

**Option 2**: Keep analyzer separate and call on-demand (recommended).

**Decision Point**: Should dependencies be pre-computed or computed on-demand?

**Acceptance Criteria**:
- ✅ Integration doesn't break existing functionality
- ✅ Dependencies are available when needed
- ✅ Performance impact is acceptable

**Estimated Time**: 3-4 hours

---

### Step 4.3: Refactor UnitTestGenerator to Use Analyzer

**Goal**: Replace duplicate logic in UnitTestGenerator with calls to AutowiredDependencyAnalyzer.

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/generator/UnitTestGenerator.java`

**Changes**:
```java
// Replace detectAutoWiringHelper()
private void detectAutoWiringHelper(CompilationUnit cu, TypeDeclaration<?> classUnderTest,
                                    ClassOrInterfaceDeclaration testSuite) {
    AutowiredDependencyAnalyzer analyzer = new AutowiredDependencyAnalyzer();
    SpringBeanDependencies deps = analyzer.analyzeDependencies(classUnderTest, cu);
    
    for (FieldDependency fieldDep : deps.getFieldDependencies()) {
        String registryKey = MockingRegistry.generateRegistryKey(
            List.of(new TypeWrapper(AntikytheraRunTime.getTypeDeclaration(fieldDep.getTargetType()).orElseThrow()))
        );
        
        if (!MockingRegistry.isMockTarget(registryKey)) {
            // Create mocked field in test suite
            FieldDeclaration field = testSuite.addField(
                fieldDep.getTargetType(), 
                fieldDep.getFieldName()
            );
            field.addAnnotation(MOCK);
        }
    }
}

// Similar refactoring for detectConstructorInjection()
```

**Acceptance Criteria**:
- ✅ UnitTestGenerator tests still pass
- ✅ Code duplication reduced
- ✅ Test generation still works correctly

**Estimated Time**: 4-6 hours (including testing)

---

## Phase 5: Prepare for Circular Dependency Detection (Week 3)

### Step 5.1: Add Dependency Query Methods

**Goal**: Add convenience methods for querying dependencies.

**File to Modify**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/AutowiredDependencyAnalyzer.java`

**Add Static Helper Methods**:
```java
/**
 * Get all dependencies for a Spring bean class
 */
public static SpringBeanDependencies getDependencies(String className) {
    TypeWrapper wrapper = AntikytheraRunTime.getResolvedTypes().get(className);
    if (wrapper == null || wrapper.getType() == null) {
        return new SpringBeanDependencies(className);
    }
    
    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
    if (cu == null) {
        return new SpringBeanDependencies(className);
    }
    
    AutowiredDependencyAnalyzer analyzer = new AutowiredDependencyAnalyzer();
    return analyzer.analyzeDependencies(wrapper.getType(), cu);
}

/**
 * Get all Spring beans that depend on the given class
 */
public static List<String> findDependents(String targetClassName) {
    List<String> dependents = new ArrayList<>();
    
    for (Map.Entry<String, TypeWrapper> entry : AntikytheraRunTime.getResolvedTypes().entrySet()) {
        String className = entry.getKey();
        TypeWrapper wrapper = entry.getValue();
        
        if (!wrapper.isService() && !wrapper.isController() && !wrapper.isComponent()) {
            continue;
        }
        
        SpringBeanDependencies deps = getDependencies(className);
        
        // Check if this bean depends on target
        boolean depends = deps.getFieldDependencies().stream()
                .anyMatch(fd -> fd.getTargetType().equals(targetClassName)) ||
            deps.getConstructorDependencies().stream()
                .anyMatch(cd -> cd.getTargetType().equals(targetClassName)) ||
            deps.getSetterDependencies().stream()
                .anyMatch(sd -> sd.getTargetType().equals(targetClassName));
        
        if (depends) {
            dependents.add(className);
        }
    }
    
    return dependents;
}
```

**Acceptance Criteria**:
- ✅ Helper methods work correctly
- ✅ Efficient implementation (consider caching)
- ✅ Tests for helper methods

**Estimated Time**: 3-4 hours

---

### Step 5.2: Build Dependency Graph

**Goal**: Create a method to build a dependency graph from all Spring beans.

**File to Create**:
`antikythera/src/main/java/sa/com/cloudsolutions/antikythera/depsolver/SpringBeanDependencyGraph.java`

**Implementation**:
```java
package sa.com.cloudsolutions.antikythera.depsolver;

import sa.com.cloudsolutions.antikythera.depsolver.dependency.SpringBeanDependencies;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.*;

public class SpringBeanDependencyGraph {
    private final Map<String, SpringBeanDependencies> dependencies;
    private final Map<String, Set<String>> dependencyGraph;  // bean -> set of dependencies
    
    public SpringBeanDependencyGraph() {
        this.dependencies = new HashMap<>();
        this.dependencyGraph = new HashMap<>();
        buildGraph();
    }
    
    private void buildGraph() {
        AutowiredDependencyAnalyzer analyzer = new AutowiredDependencyAnalyzer();
        
        for (Map.Entry<String, TypeWrapper> entry : AntikytheraRunTime.getResolvedTypes().entrySet()) {
            String className = entry.getKey();
            TypeWrapper wrapper = entry.getValue();
            
            if (!wrapper.isService() && !wrapper.isController() && !wrapper.isComponent()) {
                continue;
            }
            
            SpringBeanDependencies deps = analyzer.analyzeDependencies(
                wrapper.getType(),
                AntikytheraRunTime.getCompilationUnit(className)
            );
            
            dependencies.put(className, deps);
            
            Set<String> depsSet = new HashSet<>();
            // Add all field dependencies
            deps.getFieldDependencies().forEach(fd -> depsSet.add(fd.getTargetType()));
            // Add all constructor dependencies
            deps.getConstructorDependencies().forEach(cd -> depsSet.add(cd.getTargetType()));
            // Add all setter dependencies
            deps.getSetterDependencies().forEach(sd -> depsSet.add(sd.getTargetType()));
            
            dependencyGraph.put(className, depsSet);
        }
    }
    
    public Map<String, Set<String>> getGraph() {
        return Collections.unmodifiableMap(dependencyGraph);
    }
    
    public SpringBeanDependencies getDependencies(String className) {
        return dependencies.get(className);
    }
    
    public Set<String> getDependentBeans(String className) {
        return dependencyGraph.keySet();
    }
}
```

**Acceptance Criteria**:
- ✅ Graph is built correctly
- ✅ Contains all Spring beans
- ✅ Dependency relationships are accurate
- ✅ Tests for graph building

**Estimated Time**: 4-5 hours

---

## Testing Checklist

### Unit Tests
- [ ] FieldDependency class
- [ ] ConstructorDependency class
- [ ] SetterDependency class
- [ ] SpringBeanDependencies class
- [ ] Field dependency extraction
- [ ] Constructor dependency extraction
- [ ] Setter dependency extraction
- [ ] Interface resolution
- [ ] Qualifier extraction
- [ ] Generic type handling

### Integration Tests
- [ ] Real-world Spring bean scenarios
- [ ] Mixed injection types
- [ ] Circular dependencies
- [ ] Interface dependencies
- [ ] Performance with large codebase

### Regression Tests
- [ ] UnitTestGenerator still works after refactoring
- [ ] Existing functionality not broken

---

## Timeline Summary

**Week 1**:
- Phase 1: Foundation (Steps 1.1-1.3)
- Phase 2: Field dependencies (Step 2.1)

**Week 2**:
- Phase 2: Constructor and setter dependencies (Steps 2.2-2.4)
- Phase 3: Type resolution enhancements (Steps 3.1-3.2)
- Phase 4: Integration (Steps 4.1-4.2)

**Week 3**:
- Phase 4: Refactor UnitTestGenerator (Step 4.3)
- Phase 5: Prepare for circular detection (Steps 5.1-5.2)
- Final testing and documentation

**Total Estimated Time**: 60-80 hours

---

## Next Steps After Completion

Once this foundation is complete, you can proceed with:
1. Circular dependency detection (using the dependency graph)
2. Cycle reporting and visualization
3. Refactoring strategy implementation
4. Auto-fix capabilities (@Lazy annotation insertion)


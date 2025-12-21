# REVISED: Circular Dependency Detection - Leveraging Existing Infrastructure

## Key Changes from Original Plan

**❌ DON'T CREATE**: Separate `SpringBean` class  
**✅ USE INSTEAD**: Existing `TypeWrapper` (already tracks `isService`, `isController`, `isComponent`)

**❌ DON'T CREATE**: New graph infrastructure  
**✅ USE INSTEAD**: Existing `Graph`, `GraphNode`, `DepSolver` with DFS

**❌ DON'T CREATE**: Parallel dependency tracking  
**✅ EXTEND INSTEAD**: Existing `Resolver`, `AnnotationVisitor`

---

## Existing Infrastructure Assessment

### What We Already Have

#### 1. TypeWrapper (Lines 314-340 in AbstractCompiler)
```java
// ALREADY IDENTIFIES SPRING BEANS!
if (type.isAnnotationPresent("Service")) {
    typeWrapper.setService(true);
} else if (type.isAnnotationPresent("RestController") || type.isAnnotationPresent("Controller")) {
    typeWrapper.setController(true);
} else if (type.isAnnotationPresent("Component")) {
    typeWrapper.setComponent(true);
}

// ALREADY CACHED IN RUNTIME!
AntikytheraRunTime.addType(name, typeWrapper);
```

**What this means**:
- ✅ Spring bean detection: **ALREADY EXISTS**
- ✅ Bean caching: **ALREADY EXISTS**  
- ✅ Type metadata: **ALREADY EXISTS**

**What we need to ADD**:
```java
// Add to TypeWrapper.java
private List<FieldDependency> fieldDependencies = new ArrayList<>();
private List<ConstructorDependency> constructorDependencies = new ArrayList<>();

public void addFieldDependency(String fieldName, St ring targetType, boolean hasAutowired) {
    fieldDependencies.add(new FieldDependency(fieldName, targetType, hasAutowired));
}

public void addConstructorDependency(String paramName, String targetType) {
    constructorDependencies.add(new ConstructorDependency(paramName, targetType));
}
```

---

#### 2. Graph & GraphNode (Already has cycle detection primitives!)

```java
// In GraphNode.java
private boolean visited = false;  // ← ALREADY EXISTS!

public boolean isVisited() { return visited; }
public void setVisited(boolean visited) { this.visited = visited; }
```

**What this means**:
- ✅ Graph structure: **ALREADY EXISTS**
- ✅ Visited tracking: **ALREADY EXISTS**
- ✅ DFS traversal: **ALREADY EXISTS** in DepSolver

**What we need to ADD**:
- Spring-specific cycle detection logic on top of existing graph

---

#### 3. Resolver & AnnotationVisitor

`AnnotationVisitor` already processes annotations! We just need to:
1. Track `@Autowired` annotations
2. Record which field/constructor has them

**EXTEND**, don't replace:
```java
// Enhance AnnotationVisitor to also check for @Autowired
@Override
public void visit(final MarkerAnnotationExpr n, final GraphNode node) {
    if (n.getNameAsString().equals("Autowired")) {
        // Track this as an autowired dependency
        trackAutowiredDependency(node, n);
    }
    String[] fullName = n.getNameAsString().split("\\.");
    ImportUtils.addImport(node, fullName[0]);
    super.visit(n, node);
}
```

---

## REVISED Implementation Plan

### Phase 1: Extend TypeWrapper for Dependency Tracking (2-3 days)

**Goal**: Add dependency tracking to existing TypeWrapper

**Files to Modify**:
1. `/home/raditha/csi/Antikythera/antikythera/src/main/java/sa/com/cloudsolutions/antikythera/generator/TypeWrapper.java`

**Changes**:
```java
// Add new inner classes
public static class FieldDependency {
    private final String fieldName;
    private final String targetType;
    private final boolean hasAutowired;
    // constructor, getters
}

public static class ConstructorDependency {
    private final String paramName;
    private final String targetType;
    // constructor, getters
}

// Add fields
private List<FieldDependency> fieldDependencies = new ArrayList<>();
private List<ConstructorDependency> constructorDependencies = new ArrayList<>();

// Add methods
public void addFieldDependency(...) { }
public void addConstructorDependency(...) { }
public List<FieldDependency> getFieldDependencies() { }
public List<ConstructorDependency> getConstructorDependencies() { }
```

---

### Phase 2: Enhance Dependency Collection (3-4 days)

**Goal**: Collect `@Autowired` dependencies during existing parsing

**File 1: Extend AnnotationVisitor.java**
```java
// Add tracking for @Autowired
@Override
public void visit(MarkerAnnotationExpr n, GraphNode node) {
    if (n.getNameAsString().equals("Autowired")) {
        Node parent = n.getParentNode().orElse(null);
        if (parent instanceof FieldDeclaration field) {
            trackAutowiredField(node, field);
        } else if (parent instanceof ConstructorDeclaration constructor) {
            trackAutowiredConstructor(node, constructor);
        }
    }
    // existing logic...
}

private void trackAutowiredField(GraphNode node, FieldDeclaration field) {
    String fieldName = field.getVariable(0).getNameAsString();
    String targetType = field.getElementType().asString();
    
    // Get TypeWrapper for this class
    TypeWrapper wrapper = getTypeWrapperForNode(node);
    wrapper.addFieldDependency(fieldName, targetType, true);
}
```

**File 2: Modify AbstractCompiler.findContainedTypes()**
```java
private void findContainedTypes(TypeDeclaration<?> declaration, CompilationUnit cu) {
    for (TypeDeclaration<?> type : declaration.findAll(TypeDeclaration.class)) {
        TypeWrapper typeWrapper = new TypeWrapper(type);
        
        // EXISTING: Set Spring annotations
        if (type.isAnnotationPresent("Service")) {
            typeWrapper.setService(true);
        }
        // ...
        
        // NEW: Collect dependencies
        collectDependencies(type, typeWrapper);
        
        // EXISTING: Cache
        type.getFullyQualifiedName().ifPresent(name -> {
            AntikytheraRunTime.addType(name, typeWrapper);
            AntikytheraRunTime.addCompilationUnit(name, cu);
        });
    }
}

private void collectDependencies(TypeDeclaration<?> type, TypeWrapper wrapper) {
    // Collect @Autowired fields
    for (FieldDeclaration field : type.getFields()) {
        if (field.isAnnotationPresent("Autowired")) {
            String fieldType = findFullyQualifiedName(cu, field.getElementType());
            wrapper.addFieldDependency(
                field.getVariable(0).getNameAsString(),
                fieldType,
                true
            );
        }
    }
    
    // Collect constructor dependencies
    for (ConstructorDeclaration constructor : type.getConstructors()) {
        // Either @Autowired or single constructor (implicit autowiring)
        if (constructor.isAnnotationPresent("Autowired") || 
            type.getConstructors().size() == 1) {
            for (Parameter param : constructor.getParameters()) {
                String paramType = findFullyQualifiedName(cu, param.getType());
                wrapper.addConstructorDependency(
                    param.getNameAsString(),
                    paramType
                );
            }
        }
    }
}
```

---

### Phase 3: Add Circular Dependency Detection (2-3 days)

**New Package**: `sa.com.cloudsolutions.antikythera.circularref`

**File**: `CircularDependencyDetector.java`
```java
package sa.com.cloudsolutions.antikythera.circularref;

public class CircularDependencyDetector {
    private enum VisitState { WHITE, GRAY, BLACK }
    
    public List<CircularDependency> detectCycles() {
        // Get ALL Spring beans from AntikytheraRunTime
        Map<String, TypeWrapper> allTypes = AntikytheraRunTime.getResolvedTypes();
        
        // Filter to only Spring beans
        Map<String, TypeWrapper> springBeans = allTypes.entrySet().stream()
            .filter(e -> isSpringBean(e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        // Run DFS cycle detection
        Map<String, VisitState> state = new HashMap<>();
        List<CircularDependency> cycles = new ArrayList<>();
        
        for (String beanName : springBeans.keySet()) {
            if (state.getOrDefault(beanName, VisitState.WHITE) == VisitState.WHITE) {
                dfs(beanName, springBeans, state, new Stack<>(), cycles);
            }
        }
        
        return cycles;
    }
    
    private boolean isSpringBean(TypeWrapper wrapper) {
        return wrapper.isService() || wrapper.isController() || wrapper.isComponent();
    }
    
    private void dfs(String bean, Map<String, TypeWrapper> beans, 
                     Map<String, VisitState> state, Stack<String> path,
                     List<CircularDependency> cycles) {
        state.put(bean, VisitState.GRAY);
        path.push(bean);
        
        TypeWrapper wrapper = beans.get(bean);
        
        // Traverse field dependencies
        for (TypeWrapper.FieldDependency dep : wrapper.getFieldDependencies()) {
            String target = dep.getTargetType();
            if (beans.containsKey(target)) {
                if (state.get(target) == VisitState.GRAY) {
                    // CYCLE DETECTED!
                    cycles.add(extractCycle(path, target, DependencyType.FIELD));
                } else if (state.getOrDefault(target, VisitState.WHITE) == VisitState.WHITE) {
                    dfs(target, beans, state, path, cycles);
                }
            }
        }
        
        // Traverse constructor dependencies
        for (TypeWrapper.ConstructorDependency dep : wrapper.getConstructorDependencies()) {
            String target = dep.getTargetType();
            if (beans.containsKey(target)) {
                if (state.get(target) == VisitState.GRAY) {
                    // CRITICAL: Constructor cycle!
                    cycles.add(extractCycle(path, target, DependencyType.CONSTRUCTOR));
                } else if (state.getOrDefault(target, VisitState.WHITE) == VisitState.WHITE) {
                    dfs(target, beans, state, path, cycles);
                }
            }
        }
        
        path.pop();
        state.put(bean, VisitState.BLACK);
    }
}
```

---

### Phase 4: Refactoring Strategies (Reuse existing AST capabilities)

**Leverage**: Antikythera already has comprehensive AST manipulation!

**Example: Util Class Generator**
```java
public class UtilClassGenerator {
    
    public CompilationUnit generateUtilClass(CircularDependency cycle) {
        // Use existing AST tools!
        CompilationUnit cu = new CompilationUnit();
        
        // Get package from one of the beans
        String pkg = getPackageName(cycle.getPath().get(0));
        cu.setPackageDeclaration(pkg + ".util");
        
        // Generate class using StaticJavaParser
        ClassOrInterfaceDeclaration utilClass = cu.addClass(
            generateUtilClassName(cycle), Modifier.Keyword.PUBLIC
        );
        
        // Add @Component
        utilClass.addAnnotation("Component");
        
        // Extract shared methods
        for (MethodDeclaration method : identifySharedMethods(cycle)) {
            utilClass.addMember(method.clone());
        }
        
        return cu;
    }
}
```

---

## Simplified Project Structure

```
antikythera/
└── src/
main/java/sa/com/cloudsolutions/antikythera/
    ├── generator/
    │   └── TypeWrapper.java              # ENHANCE (add dependency tracking)
    ├── depsolver/
    │   ├── AnnotationVisitor.java        # ENHANCE (track @Autowired)
    │   ├── DepSolver.java                # USE AS-IS (DFS already exists)
    │   ├── Graph.java                    # USE AS-IS
    │   └── GraphNode.java                # USE AS-IS
    ├── parser/
    │   └── AbstractCompiler.java         # ENHANCE (collect dependencies)
    └── circularref/                      # NEW PACKAGE
        ├── CircularDependencyDetector.java
        ├── model/
        │   ├── CircularDependency.java
        │   └── DependencyType.java (enum)
        └── refactoring/
            ├── RefactoringStrategy.java
            ├── UtilClassGenerator.java
            ├── InterfaceExtractor.java
            └── IntermediateClassGenerator.java
```

---

## Implementation Estimate (REVISED)

### Original Estimate: ~500-650 lines NEW code
### Revised Estimate: ~300-400 lines NEW code

**Why?**
- ✅ Don't need `SpringBean` class (use `TypeWrapper`)
- ✅ Don't need graph infrastructure (use existing `Graph`/`GraphNode`)
- ✅ Don't need DFS implementation (use existing `DepSolver` pattern)
- ✅ Don't need Spring bean detection (already in `AbstractCompiler`)

**What we ACTUALLY need**:
1. **50-75 lines**: Add fields/methods to `TypeWrapper`
2. **75-100 lines**: Enhance `AnnotationVisitor` to track `@Autowired`
3. **50-75 lines**: Enhance `AbstractCompiler.findContainedTypes()`
4. **100-150 lines**: `CircularDependencyDetector` (DFS with Spring bean filter)
5. **Refactoring generators**: Same as before (~200 lines per strategy)

---

## Next Steps (REVISED)

### Week 1: Foundation
- [ ] Add dependency tracking fields to `TypeWrapper`
- [ ] Enhance `AbstractCompiler.findContainedTypes()` to collect dependencies
- [ ] Write tests to verify dependency collection works

### Week 2: Detection
- [ ] Create `CircularDependencyDetector`
- [ ] Use existing `AntikytheraRunTime.getResolvedTypes()` to get beans
- [ ] Implement DFS reusing `GraphNode.visited` pattern
- [ ] Test on sample circular dependency code

### Week 3-5: Same as original plan
- Refactoring strategies
- Code generation
- Integration

---

## Key Insight

**We were reinventing the wheel!** Antikythera ALREADY has:
- Spring bean detection
- Dependency graph
- Cycle detection primitives
- AST manipulation

We just need to:
1. **Enhance** existing classes to track `@Autowired`
2. **Add** Spring-specific cycle detection logic
3. **Reuse** existing graph traversal

This is **60% less new code** than the original plan! 🎉

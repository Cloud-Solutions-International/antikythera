# Performance Optimization Opportunities in Antikythera

This document outlines several performance optimization opportunities identified in the Antikythera codebase. These optimizations range from simple allocation reductions to more complex algorithmic improvements.

## 1. Avoid per-call allocations in getStaticVariable (IMPLEMENTED)

**Location**: `AntikytheraRunTime.getStaticVariable()`
**Issue**: The current implementation creates a new `TreeMap` on every cache miss:
```java
return statics.getOrDefault(fqn, new TreeMap<>()).get(field);
```

**Fix**: Use `Collections.emptyMap()` to avoid unnecessary allocations while preserving the same behavior.

**Impact**: Reduces object allocation overhead in static variable lookups.

## 2. Consider returning unmodifiable empty sets instead of new HashSet on misses

**Location**: `AntikytheraRunTime.findSubClasses()` and `findImplementations()`
**Issue**: These methods return `new HashSet<>()` on cache misses, creating unnecessary allocations.

**Suggested Fix**: Use `Collections.emptySet()` for read-only empty results.

**Impact**: Reduces allocation overhead when querying class hierarchies that don't exist.

## 3. Evaluator.getLocal hot-path scope lookup optimization

**Location**: `Evaluator.getLocal()` method (lines 809-842)
**Issue**: When no `BlockStmt` is found (hash == 0), the method scans all locals maps:
```java
for (Map<String, Variable> entry : locals.values()) {
    Variable v = entry.get(name);
    if (v != null) {
        return v;
    }
}
```

**Considerations**: This could be a performance bottleneck in expression evaluation. However, changes here require careful validation as they affect core evaluator semantics.

**Suggested Approach**: Profile to determine if this is actually a hotspot before optimizing, as the logic may be intentionally comprehensive for correctness.

## 4. TestGenerator.removeDuplicateTests fingerprint cost

**Location**: `TestGenerator.removeDuplicateTests()` method (lines 164-196)
**Issue**: Current approach clones methods and uses `toString()` for fingerprinting:
```java
MethodDeclaration m = method.clone();
m.setName("DUMMY");
String fingerprint = m.toString();
```

**Suggested Fix**: Consider a lighter structural fingerprint (e.g., hash from signature + body node kinds) if performance becomes an issue.

**Impact**: Could reduce memory and CPU overhead during test generation for large codebases.

## 5. Settings.getPropertyList allocation optimization

**Location**: `Settings.getPropertyList()` method (lines 250-259)
**Issue**: Creates a new `ArrayList` even when the property is missing or not a collection.

**Suggested Fix**: Return `Collections.emptyList()` when property is absent.

**Impact**: Minor allocation reduction in configuration property access.

## 6. RepositoryParser string building efficiency

**Location**: Various methods in `RepositoryParser`
**Current State**: Already uses `StringBuilder` appropriately in most places.

**Note**: String building patterns look generally efficient. Monitor regex usage in heavily-called flows for potential optimization opportunities.

## 7. Reflection hot paths caching

**Location**: Throughout the codebase, particularly in `Reflect` class
**Observation**: The `Reflect` class appears to implement some caching for method/constructor lookups.

**Suggested Review**: Validate and potentially extend caching for frequently invoked reflective operations if profiling indicates hotspots.

**Impact**: Could significantly improve performance in reflection-heavy code paths.

## Implementation Priority

1. **Low-hanging fruit** (implemented): Simple allocation reductions like `getStaticVariable`
2. **Medium effort**: Empty collection optimizations in runtime caches
3. **High effort/risk**: Core evaluator logic optimizations (require extensive testing)
4. **Profile-driven**: Reflection caching and fingerprinting optimizations (implement only if profiling shows benefit)

## Testing Considerations

- All optimizations should maintain existing API contracts
- Core evaluator changes require comprehensive test coverage
- Performance improvements should be validated with realistic workloads
- Consider adding performance regression tests for critical paths

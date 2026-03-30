# Stream Support — Implementation Plan

## Current State Summary

Stream support in Antikythera is partial. The `functional/` package provides solid lambda evaluation
infrastructure, and stream chains work end-to-end when the source collection is a real Java object
held as a class field or constructed inline. The failure mode is:

1. `.stream()` on a real `ArrayList` succeeds and returns an internal JDK class
   (e.g. `java.util.stream.ReferencePipeline$Head`).
2. Any subsequent intermediate operation (`.map()`, `.filter()`, etc.) attempts
   `method.setAccessible(true)` on that internal class, which throws `InaccessibleObjectException`.
3. `invokeinAccessibleMethod()` detects the `java.util.stream.*` package prefix and calls
   `handleStreamMethods()` as a fallback.
4. `handleStreamMethods()` only handles `forEach`; all other operations fall through without
   setting `returnValue`, leaving the chain broken.
5. The broken chain propagates through application `catch(Exception exp)` blocks and is
   misinterpreted by the test generator as an expected exception.

**The root cause of most stream failures is step 4**, not the stream source.

---

## What Already Works (do not regress)

All tests in `TestFunctional` must continue to pass throughout this work:

| Pattern | Test method(s) |
|---|---|
| `list.stream().map(lambda/methodRef).toList()` | `people1`, `people2`, `peopleArray1` |
| `list.stream().filter(lambda).map(methodRef).toList()` | `people4` |
| `list.stream().findFirst()` | `people5` |
| `list.stream().findFirst().ifPresent(lambda)` | `people6` |
| `list.forEach(lambda/methodRef)` | `people3`, `people8`, `sorting1`, `arraysAsList` |
| `list.stream().forEach(lambda)` | `streamForEach`, `streamForSet`, `streamLongs1`, `streamLongs2` |
| `list.stream().collect(Collectors.toList())` | `staticMethodReference1`, `staticMethodReference2`, `collectAgain` |
| `list.stream().collect(Collectors.toMap(keyFn, valueFn))` | `maps1` |
| `list.stream().collect(Collectors.joining(delim))` | `collectAgain` |
| `Arrays.stream(array).map(lambda).toList()` | `peopleArray1` |
| `Arrays.stream(intArray).boxed().toList()` | `array0` |
| Nested streams (stream inside forEach lambda) | `nestedStream` |
| Lambda with closure capture | `greet1..3`, `people7` |
| `Collections.sort(list, (a,b) -> b-a)` | `sorting2` |
| Static and instance method references | `valueOf`, `staticMethodReference1/2` |
| Stream over `Set<Long>` passed as method parameter | `streamLongs3`, `streamLongs4` |

---

## Implementation Plan

### P1 — Expand `handleStreamMethods` to cover all stream operations

**Priority:** Highest. Fixes ChiefComplainServiceImpl and the general case.

**Root cause:** `handleStreamMethods` in `Evaluator.java` (line 1241) only handles `forEach`.
All other stream operations on internal JDK stream classes fall through silently.

**Key insight:** The JDK stream classes implement the public `java.util.stream.Stream` interface.
Although `ReferencePipeline.Head.map()` (the concrete method) is inaccessible, `Stream.map()` (the
interface method) IS accessible. We can invoke via the interface method directly.

#### 1.1 Intermediate operations that return a new `Stream`

These operations take a functional argument and return another stream. They must:
- Execute the method via the public `Stream` interface (not the internal class)
- Store the resulting stream as the new `returnValue`

Operations: `map`, `filter`, `sorted`, `distinct`, `peek`, `flatMap`, `limit`, `skip`,
`takeWhile`, `dropWhile`

**Approach:** Call `Stream.class.getMethod(methodName, ...)` to obtain the interface method, then
invoke it with the already-proxied functional argument.

#### 1.2 Terminal operations that return a non-stream value

These consume the stream and return a concrete result (or void).

| Method | Return type | Notes |
|---|---|---|
| `forEach` | void | Already implemented |
| `collect` | Object (depends on Collector) | Most complex; see P3 |
| `findFirst` | `Optional<T>` | No functional arg |
| `findAny` | `Optional<T>` | No functional arg |
| `count` | long | No functional arg |
| `min` | `Optional<T>` | Takes `Comparator` |
| `max` | `Optional<T>` | Takes `Comparator` |
| `anyMatch` | boolean | Takes `Predicate` |
| `allMatch` | boolean | Takes `Predicate` |
| `noneMatch` | boolean | Takes `Predicate` |
| `reduce` | `Optional<T>` or T | Takes `BinaryOperator` or identity + accumulator |
| `toList` | `List<T>` | No-arg terminal (Java 16+) |
| `toArray` | `Object[]` | Optional `IntFunction` arg |

**Approach:** For each, obtain the interface method from `Stream.class`, invoke it, wrap result in
`Variable`.

#### 1.3 Suggested structure for the expanded `handleStreamMethods`

Break the method into two dispatchers:

```
handleStreamMethods(v, reflectionArguments)
  ├── dispatchIntermediateOp(stream, methodName, finalArgs)  → returns new Stream Variable
  └── dispatchTerminalOp(stream, methodName, finalArgs)      → returns result Variable
```

Intermediate ops set `returnValue` to a new `Variable` holding the returned stream, preserving
the chain.

Terminal ops set `returnValue` to a `Variable` holding the terminal result, with `clazz` set
appropriately.

#### 1.4 Files to change

- `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/evaluator/Evaluator.java`
  — expand `handleStreamMethods`

#### 1.5 Tests to add

New test methods in `TestFunctional` (new methods in `Functional.java` test helper):

| New test method | What it covers |
|---|---|
| `streamMap` — `list.stream().map(lambda).collect(Collectors.toList())` where list is a method parameter | P1 core case |
| `streamFilter` — `list.stream().filter(lambda)` | filter via handleStreamMethods |
| `streamCount` — `list.stream().count()` | count terminal |
| `streamFindFirst` — `list.stream().findFirst()` | findFirst terminal |
| `streamAnyMatch` — `list.stream().anyMatch(lambda)` | boolean terminal |
| `streamAllMatch` — `list.stream().allMatch(lambda)` | boolean terminal |
| `streamNoneMatch` — `list.stream().noneMatch(lambda)` | boolean terminal |
| `streamMin` / `streamMax` | Comparator-based terminals |
| `streamReduce` | BinaryOperator terminal |
| `streamLimit` / `streamSkip` | stateful intermediate ops |
| `streamDistinct` | stateful intermediate op |
| `streamFlatMap` | flatMap with lambda returning a stream |

Each test method should take its stream source as a **method parameter** (not a field) to exercise
the inaccessible-class path rather than the happy path.

---

### P2 — Fix `FPEvaluator.isReturning()` method name list

**Priority:** Medium. Causes silent wrong lambda body shapes for some operations.

**Location:** `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/evaluator/functional/FPEvaluator.java`, line 123.

**Current list:**
```java
case "map", "filter", "sorted", "reduce", "anyMatch", "allMatch", "noneMatch",
     "findFirst", "findAny" -> true;
```

**Missing methods:** `flatMap`, `mapToInt`, `mapToLong`, `mapToDouble`, `mapToObj`, `collect`,
`min`, `max`, `takeWhile`, `dropWhile`.

**Effect of the bug:** When a lambda is used in one of these missing positions and the body is an
expression statement (no explicit `return`), `isReturning()` returns `false`. The lambda body is
then treated as void (a `Consumer` instead of a `Function`), causing wrong runtime behaviour or
a `ClassCastException` in the proxy.

**Approach:** Add the missing names to the `switch` in `isReturning()`.

#### 2.1 Files to change

- `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/evaluator/functional/FPEvaluator.java`

#### 2.2 Tests to add

New parameterized cases in `TestFunctional.testBiFunction` that exercise `flatMap` and `collect`
with expression-statement (non-block) lambdas:

```java
"flatMapTest; [A, A, B, B]"   // List<List<Person>> flattened
"collectGroupBy; ..."          // covered by P3
```

---

### P3 — Additional Collector support (`groupingBy`, `partitioningBy`, `counting`, `toSet`)

**Priority:** Medium-low. Unblocks grouping/partitioning patterns common in service code.

**Background:** The existing working `Collectors` (`toList`, `toMap`, `joining`) work because:
- `Collectors.toList()` returns a concrete `Collector` that the real stream `.collect()` can
  execute once the stream chain is real Java objects.
- After P1 is done and `collect` is handled in `dispatchTerminalOp`, it will be invoked as
  `stream.collect(collector)` via the `Stream` interface method. The `Collector` argument is
  already a real Java object returned by the static `Collectors.*` factory methods.

**What needs adding:** Nothing structural — once P1's `collect` handling is in place, the
`Collectors.groupingBy`, `partitioningBy`, `counting`, and `toSet` factory calls are evaluated
by the existing `Reflect` machinery and produce real `Collector` objects. The terminal `collect`
dispatch added in P1 will execute them.

However, two things require attention:

#### 3.1 `Collectors.groupingBy` with a downstream collector

`Collectors.groupingBy(classifier, downstream)` takes two arguments; the downstream may itself be
a `Collector` such as `Collectors.counting()`. Verify that `Reflect.buildArgumentsCommon` handles
chained static calls like `Collectors.counting()` as a nested argument correctly.

#### 3.2 `Collectors.toSet`

Verify `Collectors.toSet()` is handled. It is a no-arg static call returning a concrete `Collector`
and should already work; add a test to confirm.

#### 3.3 Files to change

None new — these are covered by P1's `collect` terminal and the existing argument-building
machinery. May need minor fixes if argument evaluation of nested `Collectors.*` calls fails.

#### 3.4 Tests to add

New test methods in `Functional.java`:

| Test | Pattern |
|---|---|
| `groupByAge` | `.collect(Collectors.groupingBy(Person::getAge))` |
| `groupByAgeWithCount` | `.collect(Collectors.groupingBy(Person::getAge, Collectors.counting()))` |
| `partitionByPredicate` | `.collect(Collectors.partitioningBy(p -> p.getAge() > 25))` |
| `collectToSet` | `.collect(Collectors.toSet())` |

---

### P4 — Primitive specialised streams (`IntStream`, `LongStream`, `DoubleStream`)

**Priority:** Low. Affects `mapToInt`, `mapToLong`, `mapToDouble`, `range`, `rangeClosed`.

**Background:** These are separate interfaces from `Stream<T>`. When `stream.mapToInt(...)` is
called, the result is `java.util.stream.IntPipeline` (or similar), which has its own terminal ops
(`sum()`, `average()`, `boxed()`, etc.).

#### 4.1 Detection

After P1, if `mapToInt`/`mapToLong`/`mapToDouble` is dispatched as an intermediate op and its
result is stored, the subsequent method call will be on an `IntStream`/`LongStream`/`DoubleStream`
object. These also start with `java.util.stream.` so they will reach `handleStreamMethods`.

#### 4.2 Additional terminal ops required

| Method | Interface | Notes |
|---|---|---|
| `sum` | `IntStream`/`LongStream`/`DoubleStream` | No arg |
| `average` | all three | Returns `OptionalDouble` |
| `boxed` | all three | Returns `Stream<Integer/Long/Double>` |
| `summaryStatistics` | all three | Returns `IntSummaryStatistics` etc. |
| `asLongStream` / `asDoubleStream` | `IntStream` | Widening conversion |

**Approach:** Extend `dispatchTerminalOp` (added in P1) to check for primitive stream-specific
method names and invoke via `IntStream.class` / `LongStream.class` / `DoubleStream.class`
interface methods.

#### 4.3 Static factory `IntStream.range` / `rangeClosed`

These are static methods on `IntStream` (not on a stream instance). They should already work
through the existing static method invocation path in `Evaluator`. Add tests to confirm.

#### 4.4 Files to change

- `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/evaluator/Evaluator.java`
  — extend `handleStreamMethods` (or `dispatchTerminalOp`) with primitive stream ops

#### 4.5 Tests to add

| Test | Pattern |
|---|---|
| `intStreamRange` | `IntStream.range(1, 5).sum()` |
| `mapToIntSum` | `list.stream().mapToInt(Person::getAge).sum()` |
| `mapToLongSum` | `list.stream().mapToLong(Person::getId).sum()` |
| `mapToIntBoxed` | `list.stream().mapToInt(Person::getAge).boxed().toList()` |

---

## Checklist

### Pre-work

- [ ] Confirm all existing `TestFunctional` tests pass before starting (baseline)
- [ ] Confirm all existing `TestEvaluator`, `TestConditional`, `TestLoops`, `TestBunches` pass (regression baseline)

### P1 — Expand `handleStreamMethods`

- [ ] Identify the `Stream` interface methods for all intermediate ops (`map`, `filter`, `flatMap`,
      `distinct`, `limit`, `skip`, `peek`, `sorted`, `takeWhile`, `dropWhile`)
- [ ] Implement `dispatchIntermediateOp`: look up method on `Stream.class`, invoke on stream object,
      store result in `returnValue`
- [ ] Identify the `Stream` interface methods for all terminal ops (`collect`, `count`, `findFirst`,
      `findAny`, `min`, `max`, `anyMatch`, `allMatch`, `noneMatch`, `reduce`, `toList`, `toArray`,
      `forEach` — existing)
- [ ] Implement `dispatchTerminalOp`: look up method on `Stream.class`, invoke on stream object,
      wrap result in `Variable` with correct `clazz`
- [ ] Refactor `handleStreamMethods` to dispatch to `dispatchIntermediateOp` or `dispatchTerminalOp`
- [ ] Ensure `forEach` existing behaviour is preserved in the new dispatch structure
- [ ] Add `streamMap` test method to `Functional.java` (list param → map → collect)
- [ ] Add `streamFilter` test method
- [ ] Add `streamCount` test method
- [ ] Add `streamFindFirst` test method
- [ ] Add `streamAnyMatch`, `streamAllMatch`, `streamNoneMatch` test methods
- [ ] Add `streamMin`, `streamMax` test methods
- [ ] Add `streamReduce` test method
- [ ] Add `streamLimit`, `streamSkip` test methods
- [ ] Add `streamDistinct` test method
- [ ] Add `streamFlatMap` test method
- [ ] Add all new method names to `TestFunctional.testBiFunction` `@CsvSource`
- [ ] Run `TestFunctional` — all tests pass
- [ ] Run full `antikythera` test suite — no regressions
- [ ] Re-run EHR ChiefComplainServiceImpl generator → confirm no false assertThrows

### P2 — Fix `FPEvaluator.isReturning()`

- [ ] Add `flatMap`, `mapToInt`, `mapToLong`, `mapToDouble`, `mapToObj`, `collect`, `min`, `max`,
      `takeWhile`, `dropWhile` to the `switch` in `isReturning()`
- [ ] Add expression-lambda `flatMap` test to `Functional.java`
- [ ] Verify `TestFunctional` still passes
- [ ] Run full `antikythera` test suite — no regressions

### P3 — Additional Collectors

- [ ] Add `groupByAge` test to `Functional.java`
- [ ] Add `groupByAgeWithCount` test (nested downstream `Collectors.counting()`)
- [ ] Add `partitionByPredicate` test
- [ ] Add `collectToSet` test
- [ ] Run `TestFunctional` — all tests pass
- [ ] If any test fails, investigate argument evaluation of nested `Collectors.*` calls and fix

### P4 — Primitive specialised streams

- [ ] Extend `dispatchTerminalOp` (or add `dispatchPrimitiveStreamOp`) to handle `IntStream`,
      `LongStream`, `DoubleStream` terminal methods (`sum`, `average`, `boxed`,
      `summaryStatistics`)
- [ ] Add `intStreamRange` test to `Functional.java`
- [ ] Add `mapToIntSum` test
- [ ] Add `mapToLongSum` test
- [ ] Add `mapToIntBoxed` test
- [ ] Run `TestFunctional` — all tests pass
- [ ] Run full `antikythera` test suite — no regressions

### Post-work — EHR service validation

- [ ] Re-run generator on `ChiefComplainServiceImpl` — no error
- [ ] Run generated `ChiefComplainServiceImplAKTest` — all tests pass
- [ ] Continue service-by-service generation for remaining 8 EHR services

---

## Files Index

| File | Changed by |
|---|---|
| `antikythera/src/main/java/.../evaluator/Evaluator.java` | P1, P4 |
| `antikythera/src/main/java/.../evaluator/functional/FPEvaluator.java` | P2 |
| `antikythera-test-helper/src/main/java/.../testhelper/evaluator/Functional.java` | P1, P2, P3, P4 |
| `antikythera/src/test/java/.../evaluator/TestFunctional.java` | P1, P2, P3, P4 |

No new classes need to be created. All changes are additive expansions within existing methods.

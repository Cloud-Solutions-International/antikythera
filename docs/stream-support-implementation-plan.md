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

### Code locations (verified)

| Item | Location |
|---|---|
| `handleStreamMethods` | `Evaluator.java` line 1241 — only handles `forEach` |
| `invokeinAccessibleMethod` | `Evaluator.java` line 1210 — detects `java.util.stream.*` prefix |
| `isReturning()` | `FPEvaluator.java` line 104 — switch at line 123 |
| `FunctionEvaluator` | implements `java.util.function.Function<T,R>` |
| `ConsumerEvaluator` | implements `java.util.function.Consumer<T>` |

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
- Set `returnValue.clazz` to the **actual runtime class** of the returned stream (e.g. from
  `result.getClass()`), not `Stream.class`. This is critical: `invokeinAccessibleMethod` routes to
  `handleStreamMethods` by checking `v.getClazz().getName().startsWith("java.util.stream.")`. If
  the clazz is set incorrectly the chain silently breaks.

Operations: `map`, `filter`, `sorted`, `distinct`, `peek`, `flatMap`, `limit`, `skip`,
`takeWhile`, `dropWhile`

Operations returning a **primitive specialised stream** (to be dispatched onward to P4 terminal
handling): `mapToInt`, `mapToLong`, `mapToDouble`, `mapToObj`

> **Note — `sorted` has two overloads:** `sorted()` (no-arg, natural ordering) and
> `sorted(Comparator<? super T>)`. Both must be handled. For the no-arg form, look up
> `Stream.class.getMethod("sorted")` (no parameters); for the Comparator form use
> `Stream.class.getMethod("sorted", Comparator.class)`.

**Approach:** Call `Stream.class.getMethod(methodName, ...)` to obtain the interface method, then
invoke it with the already-proxied functional argument.

> **Critical implementation note — functional interface type adaptation:**
> Each Stream method expects a specific functional interface type:
>
> | Stream method | Expected parameter type |
> |---|---|
> | `filter` | `Predicate<? super T>` (raw: `Predicate`) |
> | `map` | `Function<? super T, ? extends R>` (raw: `Function`) |
> | `flatMap` | `Function<? super T, ? extends Stream<? extends R>>` (raw: `Function`) |
> | `sorted` | `Comparator<? super T>` (raw: `Comparator`) |
> | `peek` | `Consumer<? super T>` (raw: `Consumer`) |
> | `takeWhile` / `dropWhile` | `Predicate<? super T>` (raw: `Predicate`) |
> | `mapToInt/Long/Double` | `ToIntFunction` / `ToLongFunction` / `ToDoubleFunction` |
>
> The FPEvaluator proxy for a returning single-arg lambda is always a `FunctionEvaluator`
> (implements `java.util.function.Function`), regardless of context. Passing a `Function` where
> `Predicate` is expected will cause a `ClassCastException` inside the stream implementation.
> The dispatcher must **adapt** each proxy to the required interface. Recommended approach: for
> each intermediate operation, create a thin adapter lambda at dispatch time, e.g.:
> ```java
> // filter expects Predicate, but proxy is a Function<Object,Object> (return type erased to Object)
> Function<Object, Object> fn = (Function<Object, Object>) proxy;
> Predicate<Object> predicate = x -> Boolean.TRUE.equals(fn.apply(x));
> stream.filter(predicate);
> ```
> Using `Stream.class.getMethod("filter", Predicate.class).invoke(stream, predicate)` avoids
> the inaccessible-class problem while keeping type safety.

#### 1.2 Terminal operations that return a non-stream value

These consume the stream and return a concrete result (or void).

| Method | Return type | Notes |
|---|---|---|
| `forEach` | void | Already implemented |
| `collect` | Object (depends on Collector) | Most complex; see P3. Two overloads: `collect(Collector)` and `collect(Supplier, BiConsumer, BiConsumer)` — implement the Collector form first |
| `findFirst` | `Optional<T>` | No functional arg; wrap result in `Variable` with `clazz = Optional.class` |
| `findAny` | `Optional<T>` | No functional arg; wrap similarly |
| `count` | long | No functional arg |
| `min` | `Optional<T>` | Takes `Comparator`; see adaptation note in §1.1 |
| `max` | `Optional<T>` | Takes `Comparator` |
| `anyMatch` | boolean | Takes `Predicate`; see adaptation note in §1.1 |
| `allMatch` | boolean | Takes `Predicate` |
| `noneMatch` | boolean | Takes `Predicate` |
| `reduce` | `Optional<T>` or T | Three overloads: (a) `reduce(BinaryOperator<T>)` → `Optional<T>`; (b) `reduce(T identity, BinaryOperator<T>)` → `T`; (c) `reduce(U identity, BiFunction<U,T,U>, BinaryOperator<U>)` → `U`. Implement (a) and (b) first. |
| `toList` | `List<T>` | No-arg terminal (Java 16+) |
| `toArray` | `Object[]` | Optional `IntFunction` arg |

**Approach:** For each, obtain the interface method from `Stream.class`, invoke it, wrap result in
`Variable`.

> **Optional return types:** `findFirst`, `findAny`, `min`, `max`, and the single-arg `reduce` all
> return `Optional<T>`. Set `returnValue.clazz = Optional.class` so downstream code that calls
> `.isPresent()` / `.get()` / `.ifPresent()` resolves correctly through the existing reflection path.

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
| `streamFilter` — `list.stream().filter(lambda)` | filter via handleStreamMethods (Predicate adaptation) |
| `streamCount` — `list.stream().count()` | count terminal |
| `streamFindFirst` — `list.stream().findFirst()` | findFirst terminal → Optional |
| `streamAnyMatch` — `list.stream().anyMatch(lambda)` | boolean terminal (Predicate adaptation) |
| `streamAllMatch` — `list.stream().allMatch(lambda)` | boolean terminal |
| `streamNoneMatch` — `list.stream().noneMatch(lambda)` | boolean terminal |
| `streamMin` / `streamMax` | Comparator-based terminals (Comparator adaptation) |
| `streamReduce` | BinaryOperator terminal (single-arg form returning Optional) |
| `streamReduceWithIdentity` | two-arg reduce returning T |
| `streamLimit` / `streamSkip` | stateful intermediate ops |
| `streamDistinct` | stateful intermediate op |
| `streamFlatMap` | flatMap with lambda returning a stream (Function adaptation) |
| `streamSorted` | no-arg `sorted()` — natural ordering |
| `streamSortedWithComparator` | `sorted(Comparator)` — Comparator adaptation |

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

**What `isReturning()` actually does (verified against source):**

The method has three cases (lines 118–130):

1. **Non-block lambda** (`x -> expr`, no `{}`): always returns `true` — handled at line 118–119.
   These are *never* affected by the missing names.
2. **Block lambda with an explicit `return` statement** (`x -> { return …; }`): caught at line 54
   of `create()` before `isReturning()` is called. These are also *never* affected.
3. **Block lambda with no explicit `return`** (`x -> { expr; }`): reaches the `switch` at line 123.
   **This is the only case where the missing operation names matter.**

**Effect of the bug:** When a *block-statement* lambda that has no explicit `return` statement is
used in one of the missing operations (e.g. `stream.flatMap(x -> { return x.getItems().stream(); })`
written without `return`), `isReturning()` returns `false`. The lambda body is then treated as
void (a `ConsumerEvaluator` instead of a `FunctionEvaluator`), causing wrong runtime behaviour or
a `ClassCastException` in the proxy.

> **Interaction with P1:** `mapToInt/Long/Double/Obj` are listed here as missing from
> `isReturning()` and should be added in parallel with the P1 dispatcher changes.

**Approach:** Add the missing names to the `switch` in `isReturning()`.

#### 2.1 Files to change

- `antikythera/src/main/java/sa/com/cloudsolutions/antikythera/evaluator/functional/FPEvaluator.java`

#### 2.2 Tests to add

New parameterized cases in `TestFunctional.testBiFunction` that exercise `flatMap` and `collect`
with **block-body lambdas** (containing `{}`) that have no explicit `return` statement:

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

After P1, `mapToInt`/`mapToLong`/`mapToDouble` are dispatched as intermediate ops in
`dispatchIntermediateOp` (returning the primitive stream result). The subsequent method call will
be on an `IntStream`/`LongStream`/`DoubleStream` object. These also start with `java.util.stream.`
so they will reach `handleStreamMethods`. The dispatcher must then look up methods on the correct
primitive-stream interface class rather than `Stream.class`.

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
interface methods. Detect which primitive stream interface to use with
`instanceof IntStream` / `instanceof LongStream` / `instanceof DoubleStream` checks on the value.

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

## Known Limitations / Out of Scope

The following stream patterns are **not covered** by this plan and are deferred:

| Pattern | Reason |
|---|---|
| `Stream.generate(Supplier)` / `Stream.iterate(seed, fn)` | Infinite / generator streams — need lazy evaluation semantics |
| `Stream.of(...)` static factory | Usually works via the existing static method path; add a targeted test before declaring done |
| `String.chars()` / `CharSequence.chars()` | Returns `IntStream` — P4 terminal ops would handle once P4 lands, but the source method needs testing |
| `map.entrySet().stream()` | Common in service code; needs `Map.Entry` handling — test and verify |
| `Optional.stream()` (Java 9+) | Uncommon; defer until a real case is encountered |
| Parallel streams (`parallelStream()`, `.parallel()`) | `AntikytheraRunTime` and `Branching` hold shared mutable static state; parallel execution would corrupt state |
| `Collectors.teeing` (Java 12+) | Complex two-collector combinator; defer |
| `Stream.concat(a, b)` | Static method; likely works via existing path — add a test to confirm |

---

## Checklist

### Pre-work

- [x] Confirm all existing `TestFunctional` tests pass before starting (baseline)
- [x] Confirm all existing `TestEvaluator`, `TestConditional`, `TestLoops`, `TestBunches` pass (regression baseline)

### P1 — Expand `handleStreamMethods`

- [x] Identify the `Stream` interface methods for all intermediate ops (`map`, `filter`, `flatMap`,
      `distinct`, `limit`, `skip`, `peek`, `sorted` (both overloads), `takeWhile`, `dropWhile`,
      `mapToInt`, `mapToLong`, `mapToDouble`, `mapToObj`)
- [x] Implement functional-interface adapters for each intermediate op so that a `FunctionEvaluator`
      (implements `Function`) can be adapted to `Predicate` (for `filter`, `takeWhile`, `dropWhile`),
      `Comparator` (for `sorted`), `Consumer` (for `peek`), and the `ToInt/Long/DoubleFunction`
      specialisations (for `mapToInt/Long/Double`)
- [x] Implement `dispatchIntermediateOp`: look up method on `Stream.class` (or appropriate
      primitive-stream class), invoke with adapted argument, store result in `returnValue` with
      `clazz` set from `result.getClass()`
- [x] Identify the `Stream` interface methods for all terminal ops (`collect`, `count`, `findFirst`,
      `findAny`, `min`, `max`, `anyMatch`, `allMatch`, `noneMatch`, `reduce`, `toList`, `toArray`,
      `forEach` — existing)
- [x] Implement `dispatchTerminalOp`: look up method on `Stream.class`, invoke on stream object,
      wrap result in `Variable` with correct `clazz` (use `Optional.class` for Optional-returning ops)
- [x] Refactor `handleStreamMethods` to dispatch to `dispatchIntermediateOp` or `dispatchTerminalOp`
- [x] Ensure `forEach` existing behaviour is preserved in the new dispatch structure
- [x] Add `streamMap` test method to `FunctionalStream.java` (list → map → collect)
- [x] Add `streamFilter` test method (Predicate adaptation)
- [x] Add `streamCount` test method
- [x] Add `streamFindFirst` test method (Optional result)
- [x] Add `streamAnyMatch`, `streamAllMatch`, `streamNoneMatch` test methods
- [x] Add `streamMin`, `streamMax` test methods (Comparator adaptation)
- [x] Add `streamReduce` test method (single-arg, Optional result)
- [x] Add `streamReduceWithIdentity` test method (two-arg, T result)
- [x] Add `streamLimit`, `streamSkip` test methods
- [x] Add `streamDistinct` test method
- [x] Add `streamFlatMap` test method (Function adaptation returning Stream)
- [x] Add `streamSorted` test method (no-arg)
- [x] Add `streamSortedWithComparator` test method (Comparator adaptation)
- [x] Add all new method names to `TestFunctionalStream.testStreamOps` `@CsvSource`
      (Note: self-contained `FunctionalStream.java` + `TestFunctionalStream.java` added to
      `antikythera` instead of modifying external `antikythera-test-helper` repo)
- [x] Run `TestFunctional` — all 31 tests pass (unchanged)
- [x] Run `TestFunctionalStream` — all 25 new tests pass
- [x] Run full `antikythera` test suite — no regressions
- [ ] Re-run EHR ChiefComplainServiceImpl generator → confirm no false assertThrows

### P2 — Fix `FPEvaluator.isReturning()`

- [x] Add `flatMap`, `mapToInt`, `mapToLong`, `mapToDouble`, `mapToObj`, `collect`, `min`, `max`,
      `takeWhile`, `dropWhile` to the `switch` in `isReturning()` (only block-statement lambdas
      with no explicit `return` reach this code path)
- [x] Verify `TestFunctional` and `TestFunctionalStream` still pass
- [x] Run full `antikythera` test suite — no regressions

### P3 — Additional Collectors

- [x] Add `groupBy` test to `FunctionalStream.java`
- [x] Add `groupByWithCount` test (nested downstream `Collectors.counting()`)
- [x] Add `partitionByPredicate` test
- [x] Add `collectToSet` test
- [x] Run `TestFunctionalStream` — all tests pass

### P4 — Primitive specialised streams

- [x] Confirm `mapToInt/Long/Double` intermediate dispatch is handled in P1's
      `dispatchIntermediateOp` (routing, not terminal handling)
- [x] Add `dispatchPrimitiveStreamOp` to handle `IntStream`, `LongStream`, `DoubleStream`
      terminal methods (`sum`, `average`, `boxed`, `summaryStatistics`, `asLongStream`,
      `asDoubleStream`, `mapToObj`, `forEach`, `toList`, `toArray`);
      detects interface via `instanceof` checks on the stream value
- [x] Add `intStreamRange` test to `FunctionalStream.java`
- [x] Add `mapToIntSum` test
- [x] Add `mapToLongSum` test (via `mapToInt(...).asLongStream().sum()`)
- [x] Add `mapToIntBoxed` test
- [x] Run `TestFunctionalStream` — all tests pass
- [x] Run full `antikythera` test suite — no regressions

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

No new top-level classes need to be created. All changes are additive expansions within existing
methods. Small private helper methods (`dispatchIntermediateOp`, `dispatchTerminalOp`, and
functional-interface adapter utilities) will be added as private methods within `Evaluator.java`.

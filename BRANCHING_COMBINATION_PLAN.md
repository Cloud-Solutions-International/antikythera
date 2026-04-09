# Branching Combination Plan

This checklist captures the current plan for improving branch-combination coverage in the evaluator,
with specific focus on sequential conditionals such as:

- `ProblemServiceImpl.getPatientProblemsString(...)`
- `ProblemServiceImpl.getPatientProblems(...)`

The relevant core classes are:

- `src/main/java/sa/com/cloudsolutions/antikythera/evaluator/Branching.java`
- `src/main/java/sa/com/cloudsolutions/antikythera/evaluator/LineOfCode.java`
- `src/main/java/sa/com/cloudsolutions/antikythera/generator/TruthTable.java`

## Current Diagnosis

- [x] Confirm with targeted repros that the remaining gaps are branch-combination failures, not dedup failures.
- [ ] Capture a concrete failing matrix for `getPatientProblemsString(...)`.
Expected combinations:
  - diagnosis type empty + DAO result empty
  - diagnosis type empty + DAO result non-empty
  - diagnosis type non-empty + DAO result empty
  - diagnosis type non-empty + DAO result non-empty
- [ ] Capture a concrete failing matrix for `getPatientProblems(...)`.

## Phase 1: Observability

- [x] Add debug logging or trace capture for the branch currently being targeted by the evaluator.
- [x] Record the chosen truth-table row for each branch attempt.
- [x] Record emitted preconditions for each branch attempt.
- [x] Record the generated test fingerprint for each branch attempt.
- [x] Make the trace easy to compare against the generated test body.

## Phase 2: TruthTable Correctness

- [ ] Review `TruthTable.satisfiesConstraints(...)` and remove early-return behavior that skips remaining constraints.
- [ ] Make constraint evaluation require all applicable constraints to hold.
- [x] Add focused tests for `StringUtils.isEmpty(...)`.
- [ ] Add focused tests for sequential conditions.
- [ ] Add focused tests for `CollectionUtils.isEmpty(...)`.
- [ ] Add focused tests for null-sensitive comparisons.
- [ ] Verify that `findValuesForCondition(...)` returns all meaningful satisfying rows for the target repros.

## Phase 3: Branch Identity and State

- [ ] Define what a "logical branch point" is in `Branching`.
- [ ] Ensure one canonical `LineOfCode` instance represents one logical branch point.
- [ ] Separate branch path coverage state from branch-combination attempt history.
- [ ] Keep explicit knowledge of:
  - false side explored
  - true side explored
  - combinations attempted for each side
- [ ] Avoid flattening unrelated branch preconditions into one global list without attribution.

## Phase 4: Combination-Aware Exploration

- [ ] Change branch exploration so it does not always take the first satisfying truth-table row.
- [ ] Add a policy for requesting the next viable satisfying row for the same branch state.
- [ ] Skip combinations already attempted for the same branch side.
- [ ] Prefer combinations that materially change generated setup and assertions.
- [ ] Make sequential branch exploration preserve earlier branch choices when they are required to reach later branches.

## Phase 5: Integration in Evaluator Flow

- [ ] Update the evaluator to pass the targeted branch attempt, not just a flat list of preconditions, into test generation.
- [ ] Ensure branch transitions in `LineOfCode.transition()` still work for nested `if/else` structures after combination tracking is added.
- [ ] Re-check how `Branching.LineOfCodeComparator` prioritizes pending work once combination iteration exists.
- [ ] Confirm that the new flow does not regress simple one-branch methods such as `DoctorNameService.getDoctorName(...)`.

## Phase 5a: Return Type Resolution

- [x] Confirm whether later local-variable branch setup is blocked by missing local symbols or by failed return-type resolution.
- [x] Confirm that earlier collaborator assignments are now being found for `values` / `records`.
- [ ] Trace how `wrapCallExpression(...)` populates argument types for repository/interface calls.
- [ ] Trace how `AbstractCompiler.findMethodDeclaration(...)` selects a callable when exact matching fails.
- [ ] Audit arity-only fallback paths and identify where they can produce the wrong callable or wrong return type.
- [ ] Audit `MCEWrapper.getArgumentTypesAsClasses()` fallbacks to `Object.class` and measure where that breaks binary lookup.
- [x] Add focused tests for repository/interface return-type resolution, including generic collection returns.
- [x] Add a dedicated resolver path for method-call return types that does not depend only on the current symbolic argument types.
- [x] Prefer resolved callable metadata over `calculateResolvedType()` string parsing where possible.
- [x] Capture and compare the return-type-resolution path for:
  - source-declared methods
  - generic repository methods
  - collaborator-backed fixture methods
- [ ] Capture and compare the return-type-resolution path for:
  - inherited source methods
  - binary parent/interface methods

## Phase 6: Regression Coverage

- [x] Add dedicated fixture classes in `antikythera-test-helper` for sequential-branch combination scenarios.
- [x] Add one fixture for sequential string/collection branching similar to `getPatientProblemsString(...)`.
- [x] Add one fixture for mixed top-level plus nested branching similar to `getPatientProblems(...)`.
- [ ] Keep the fixtures minimal so failures are attributable to branching behavior, not Spring/JPA complexity.
- [ ] Add a regression test for `getPatientProblemsString(...)` covering all 4 combinations.
- [ ] Add a regression test for `getPatientProblems(...)` covering the missing `deletedBy` branch family.
- [ ] Add at least one nested-branch regression for parent/child `LineOfCode` behavior.
- [ ] Add at least one collection/map membership regression to protect the earlier DTO precondition fix.
- [x] Wire the new tests to use the `antikythera-test-helper` fixtures instead of relying only on large external project repros.

## Fixture Design

- [x] Add a fixture method with two sequential `if` statements where the second branch is reachable under multiple valid first-branch assignments.
- [x] Add a fixture method where a later branch depends on earlier object-shape setup, not just primitive parameters.
- [ ] Add a fixture with nested `if/else` blocks to validate parent/child `LineOfCode.transition()` behavior.
- [ ] Add expected generated-test assertions for each fixture so branch-combination loss is obvious.

## Implementation Order

- [x] Step 1: add traceability
- [ ] Step 2: fix `TruthTable` constraint evaluation
- [ ] Step 3: add branch-combination tracking to `LineOfCode`
- [ ] Step 4: update `Branching` to expose targeted branch-attempt context
- [ ] Step 5: update evaluator branch selection to iterate satisfying rows
- [x] Step 6: add `antikythera-test-helper` fixtures
- [ ] Step 7: add focused regression tests against those fixtures

## Notes

- [ ] Do not start by tuning comparator priority alone.
- [ ] Do not rely on deduplication to hide missing branch combinations.
- [ ] Keep changes incremental and measurable against the focused repro methods before running the full suite.
- [ ] Prefer fixture-backed tests for fast iteration, then validate against the external project repros.

## New Follow-Ups

- [ ] Make method-call trace values semantic for readability, e.g. show `NON_EMPTY_STRING` instead of raw `"T"` for `StringUtils.isEmpty(...)`.
- [ ] Diagnose why collaborator-backed fixtures fall straight into the plain `@Mock` NPE path before branch-specific collaborator stubs can form.
- [x] Fix generated test instantiation for constructor-injected fixtures/services; the current generator still emits no-arg construction plus reflection field injection for constructor-only classes.
- [x] Confirm that the current blocker for `values` / `records` is `reason=noReturnType`, not failed local-variable backtracking.

Current observation:

- The direct sequential fixture already reaches 4 observable combinations under evaluation.
- The new branch trace shows repeated branch-attempt selection for that fixture, so the remaining gap is more likely in precondition materialization and branch-attempt identity for collaborator/object-shaped scenarios than in the basic ability to revisit sequential branches.
- The original collaborator-backed failure at `reason=noReturnType` has been addressed by moving method-call resolution to a resolved-method-first path.
- The current resolver now prefers JavaParser `MethodUsage` / resolved method metadata, uses that to recover both return type and source callable when available, and keeps manual scope-based lookup only as fallback.
- The remaining collaborator-backed gap is no longer primarily return-type identification; it has shifted back to branch-consistent materialization of earlier assignment expressions.

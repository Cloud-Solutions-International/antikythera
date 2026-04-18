# Branching Combination Plan

This is the clean-start plan for branch `avurudu`, created from commit `2a5b7709`.

The goal of this branch is not to continue layering tactical fixes on top of the previous
implementation. The goal is to finish the remaining branch-combination problem with a smaller,
cleaner design centered on explicit path state.

## Problem Statement

We already know the evaluator can:

- revisit branches
- generate multiple truth-table rows
- handle many collaborator-backed and `TruthTable` correctness cases

What still remains incomplete is the last-mile cross-product exploration for sequential independent
branches, especially when a later branch must preserve an earlier chosen side.

The two primary proof targets are:

- `sequentialDirect(...)`
- `deletedByDirect(...)`

Expected combinations:

- `sequentialDirect(...)`
  - `TYPE_EMPTY|VALUES_EMPTY`
  - `TYPE_EMPTY|VALUES_PRESENT`
  - `TYPE_SET|VALUES_EMPTY`
  - `TYPE_SET|VALUES_PRESENT`

- `deletedByDirect(...)`
  - `OPEN|ACTIVE`
  - `OPEN|DELETED`
  - `ALL|ACTIVE`
  - `ALL|DELETED`

## Clean-Start Decision

This branch intentionally starts from `2a5b7709` because:

- it already has branch-attempt context and row iteration
- it is earlier than the later replay-index / precondition-preservation layering
- it gives us a better base for a cleaner implementation

This branch should avoid reintroducing:

- replay-index arithmetic as a surrogate for path identity
- broad scheduler changes just to force one more combination
- excessive state accumulation inside `LineOfCode`
- global precondition replay when only a targeted preserved earlier side is needed

## Architectural Direction

### Keep

- `TruthTable` for row generation only
- `LineOfCode` for branch-point identity and structural relationships
- `Branching` for queue/selection orchestration
- `SpringEvaluator` for materialization and execution

### Add

- `BranchSide`
  - `FALSE`
  - `TRUE`

- `PreservedPathState`
  - explicit record of earlier branch point -> chosen side

- `BranchSelection`
  - target `LineOfCode`
  - target side
  - chosen row fingerprint

- `BranchAttempt`
  - `BranchSelection`
  - `PreservedPathState`
  - target-local materialized preconditions

- `BranchAttemptPlanner`
  - chooses the next attempt for a target branch
  - decides what earlier branch-side choices must be preserved
  - owns the “next untried row for this path state” logic

### Responsibility Boundaries

- `TruthTable`
  - derive satisfying rows for a condition and its local constraints
  - no path-preservation policy

- `LineOfCode`
  - minimal branch identity
  - structural predecessor metadata only
  - no replay heuristics

- `Branching`
  - store branch points
  - expose candidate targets
  - delegate attempt construction to planner

- `BranchAttemptPlanner`
  - produce explicit attempts
  - maintain attempt history keyed by:
    - target branch
    - target side
    - preserved predecessor-side selections
    - selected row fingerprint

- `SpringEvaluator`
  - take a `BranchAttempt`
  - materialize it
  - execute it
  - report the result

## Non-Goals

- do not solve collaborator-backed 4-combination coverage first
- do not redesign `TruthTable` broadly unless a direct reproducer proves it is required
- do not use comparator tuning as the primary fix
- do not optimize tracing or diagnostics unless they block the work

## Execution Strategy

### Phase 0: Stabilize the Base

- [x] Confirm the branch state at `2a5b7709`
- [x] Record the exact baseline behavior of:
  - `sequentialDirect(...)`
  - `deletedByDirect(...)`
- [x] Record which focused tests are green before new implementation starts
- [x] Create a narrow working safety net command for this branch

Success criteria:

- we know exactly what the base does before new design work starts

### Phase 1: Recreate Only the Narrow Proven Fixes

- [x] Reapply the narrow enum fix for `OPEN.equals(s)` — `adjustForEnums` is present in `SpringEvaluator` and `enumEqualsVariableProducesTrueAndFalseRows` covers the `TruthTable` side
- [ ] Reapply the narrow concrete-object setter/materialization fix if `clarendon` needs it — **not yet verified; no targeted setter-materialization regression test exists**
- [x] Reapply only the direct `TruthTable` regression tests that proved:
  - enum true-row recovery (`enumEqualsVariableProducesTrueAndFalseRows` in `TestTruthTable`)
  - object/string setter materialization correctness — **partial; setter test still missing**

Success criteria:

- we keep previously validated local fixes
- we do not pull forward the later replay/preservation machinery

### Phase 2: Define Explicit Path State

- [x] Introduce `BranchSide`
- [x] Introduce `PreservedPathState`
- [x] Introduce `BranchSelection`
- [x] Introduce `BranchAttempt`
- [x] Introduce `BranchAttemptPlanner`
- [x] Keep these types small and explicit

Success criteria:

- path state is represented directly, not inferred from replay indexes or aggregate counters

### Phase 3: Reduce `LineOfCode` Responsibility

- [x] Audit current `LineOfCode` state on this branch
- [x] Keep only:
  - branch identity
  - current path coverage state
  - structural predecessors / parent / child links
- [x] Do not put preserved-path-state logic in `LineOfCode`
- [x] Do not put attempt-planning policy in `LineOfCode`

Success criteria:

- `LineOfCode` remains a branch point, not a planning engine

### Phase 4: Build Planner for Direct Fixtures Only

- [ ] Make `BranchAttemptPlanner` operate only on direct fixtures first — **this gate was never enforced; the planner is global and applies to all evaluations. Decide whether to gate by fixture type or accept the global scope and remove this item.**
- [x] For a chosen target branch, compute the relevant preserved predecessor sides
- [x] Ask `TruthTable` for satisfying rows for the target side — `SpringEvaluator` builds the `TruthTable`, computes `combinations`, and passes them into `selectTargetAttempt`
- [x] Track attempted combinations using:
  - target branch
  - target side
  - preserved predecessor-side state
  - row fingerprint
- [x] Return the next untried `BranchAttempt`

Success criteria:

- no replay-index arithmetic is needed to decide which earlier side is preserved

### Phase 5: Materialize a `BranchAttempt`

- [x] Update `SpringEvaluator` to materialize explicit `BranchAttempt`s — `setupIfCondition` calls `Branching.selectTargetAttempt`, stores `currentTargetAttempt`, and calls `applyPreservedPathState`
- [x] Materialize preserved predecessor-side choices before the target branch — `applyPreservedPathState` iterates preserved entries and calls `materializeBranchSide` per predecessor
- [x] Materialize only the target-local row for the current branch attempt
- [x] Avoid global replay of all previous preconditions

- [x] Fix cross-product exploration: `Branching.resetBranchesWithUntriedCombinations(cd)` scans all
      branches before break; any branch with an untried `(side, preservedState)` pair is reset to
      UNTRAVELLED and re-queued — `BranchAttemptPlanner.hasUntriedCombinations` drives this check
- [x] Gate `hasUntriedCombinations` on `candidateStates.size() > 1` — single-branch methods (no
      sibling predecessors) never trigger extra iterations
- [x] Only sibling predecessors (same `parent`) enter `candidatePreservedPathStates` — ancestor
      branches (else-if chains sharing the same variable) are excluded
- [x] `applyPreservedPathState` emits a `logger.trace` warning for null `Branching.get(hash)`

Success criteria:

- earlier branch choices are preserved because they are explicit
- later branch materialization is local and attributable
- predecessor row selection can vary across iterations — the full cross-product is explored

### Phase 6: Prove on `sequentialDirect(...)`

- [x] `sequentialDirectShouldCoverAllFourCombinations` enabled and passing

Success criteria:

- [x] `sequentialDirect(...)` reaches all 4 combinations with the new design

### Phase 7: Prove on `deletedByDirect(...)`

- [x] `deletedByDirectShouldCoverAllFourCombinations` enabled and passing

Success criteria:

- [x] `deletedByDirect(...)` reaches all 4 combinations with the same design

### Phase 8: Broaden Carefully

- [ ] Only after both direct fixtures are green, revisit collaborator-backed fixtures
- [ ] Decide whether collaborator-backed gaps need:
  - the same preserved-path-state model
  - or separate prior-local stub synthesis improvements
- [ ] Re-enable collaborator-backed 4-combination assertions only after direct proof is stable
- [ ] Run the generator against `csi-ehr-opd-patient-pomr-java-sev`; all generated tests must compile and pass
- [ ] Run the full `antikythera` and `antikythera-test-generator` module suites

Success criteria:

- the direct proof layer remains clean while broader scenarios are added
- the generator produces compilable, passing tests against the real target project
- no regressions in either module test suite

## Checklist of Concrete Deliverables

### Design

- [x] Write the Java types for:
  - `BranchSide`
  - `PreservedPathState`
  - `BranchSelection`
  - `BranchAttempt`
  - `BranchAttemptPlanner`

### Refactoring

- [x] Remove or avoid replay-index-based path selection in new code
- [x] Keep `LineOfCode` focused on identity and structure
- [ ] Keep `TruthTable` focused on row generation
- [ ] Keep `SpringEvaluator` focused on materialization
- [x] Consolidate row fingerprinting into one shared utility used by planner and evaluator

### Tests

- [x] `sequentialDirect(...)` direct 4-combination proof passes
- [x] `deletedByDirect(...)` direct 4-combination proof passes
- [ ] focused safety net passes:
  - `TestBranchingCombinations`
  - `TestConditionVisitor`
  - `TestLineOfCode`
  - `TestConditional`
  - `TestEnums`
  - `TestConditionalWithOptional`
  - `TestStatic`
  - `TestReturnTypeResolution`
  - `TestRepository`
  - `TestTruthTable`
- [ ] full `antikythera` module suite passes (`mvn test` in `antikythera/`)
- [ ] full `antikythera-test-generator` module suite passes (`mvn test` in `antikythera-test-generator/`)

### Validation Against Real Target

The primary end-to-end validation strategy is to run the generator against
`csi-ehr-opd-patient-pomr-java-sev` using the settings in
`antikythera-test-generator/src/main/resources/generator.yml` and verify the output.

- [ ] Run `mvn exec:java` in `antikythera-test-generator/` against `csi-ehr-opd-patient-pomr-java-sev`
- [ ] All generated test files compile without errors
- [ ] All generated tests execute successfully (zero failures, zero errors)
- [ ] No regressions in coverage or test count compared to the `core` branch baseline

This is the final gate before the branch is considered ready to merge. Unit-level proof tests
passing is necessary but not sufficient — the generator must produce correct, runnable tests
against a real production-scale codebase.

## Guardrails

- [ ] Do not import late-branch replay logic from `kitchen-sink`
- [ ] Do not copy over large chunks of the later `SpringEvaluator` state machinery without justification
- [ ] Prefer one explicit new abstraction over multiple small tactical flags
- [ ] Keep each phase separately verifiable
- [ ] If a new failure appears, decide first whether it is:
  - expected signal from the new design
  - collateral damage from overreach
  - a proof that the abstraction boundary is wrong

## Immediate Next Step

Phases 0–3 are complete. Phases 4 and 5 are structurally in place but have two identified gaps
(see Phase 5 above) that are the direct cause of the 4-combination proof tests remaining
`@Disabled`. The concrete next actions are:

- [ ] Fix `materializeBranchSide` to cycle over distinct predecessor rows rather than always
      calling `combinations.getFirst()` — this is the primary blocker for Phases 6 and 7
- [ ] Add a trace warning in `applyPreservedPathState` when `Branching.get(hash)` returns `null`
      so silent predecessor-lookup misses are visible during debugging
- [ ] Decide whether the Phase 4 "direct fixtures only" gate should be enforced or dropped
- [ ] Add a targeted setter-materialization regression test to cover the Phase 1 concrete-object fix

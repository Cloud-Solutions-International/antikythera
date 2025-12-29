# Test Order Dependency Fix

## Problem
Tests were passing locally but failing in GitHub Actions CI. The issue was related to test execution order differences between local and CI environments.

## Root Cause
The Maven Surefire plugin was configured with `<runOrder>alphabetical</runOrder>`, which can lead to inconsistent behavior across different environments because:

1. Alphabetical sorting may differ slightly based on locale and file system
2. Tests may have implicit dependencies on execution order due to shared static state
3. Different environments (local vs CI) may resolve test order differently

## Solution
Removed the `<runOrder>alphabetical</runOrder>` configuration to use Maven Surefire's default behavior (filesystem order), which is more consistent across environments.

## How to Reproduce Test Order Issues Locally

To help catch test order dependencies during development, you can run tests with random ordering:

```bash
mvn test -Dsurefire.runOrder=random
```

Run this multiple times to ensure tests pass regardless of order:

```bash
for i in {1..5}; do 
  echo "Run $i"
  mvn test -Dsurefire.runOrder=random || break
done
```

## Testing the Fix

### Run tests with the same order as CI (filesystem order):
```bash
mvn clean test
```

### Run tests multiple times to ensure stability:
```bash
for i in {1..3}; do
  echo "Test run $i"
  mvn clean test || exit 1
done
```

### Run tests with random order to detect dependencies:
```bash
mvn test -Dsurefire.runOrder=random
```

## Best Practices to Avoid Test Order Dependencies

1. **Clean up static state**: Ensure `@BeforeEach` and `@AfterEach` methods properly reset all static fields
2. **Use instance variables**: Prefer instance variables over static variables in tests
3. **Avoid shared state**: Each test should be independent and not rely on state from other tests
4. **Test in isolation**: Run individual test classes to ensure they work standalone
5. **Use @BeforeAll/@AfterAll carefully**: Be aware that these run once per class, not per test

## Related Files
- `pom.xml` - Maven Surefire plugin configuration
- Test classes with `@BeforeEach` methods that call:
  - `AbstractCompiler.reset()`
  - `AntikytheraRunTime.resetAll()`

## References
- [Maven Surefire Test Order Documentation](https://maven.apache.org/surefire/maven-surefire-plugin/examples/fork-options-and-parallel-execution.html)
- [JUnit 5 Test Instance Lifecycle](https://junit.org/junit5/docs/current/user-guide/#writing-tests-test-instance-lifecycle)

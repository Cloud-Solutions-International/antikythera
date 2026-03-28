# Antikythera Core Configuration Guide

Configuration is loaded from a `generator.yml` file on the classpath, or from a path passed
explicitly to `Settings.loadConfigMap(File)`.

---

## General Project Settings

| Property | Description |
| :--- | :--- |
| `base_package` | Base package of the application under test. Used to decide whether a class should be compiled and analysed or treated as an external dependency. |
| `base_path` | Absolute path to the root of the source tree (e.g. `.../src/main/java`). |
| `output_path` | Directory where generated files are written. |

---

## Symbolic Execution

| Property | Default | Description |
| :--- | :--- | :--- |
| `mock_with_internal` | — | Mocking framework used **internally** while Antikythera evaluates expressions (e.g. `Mockito`). Distinct from the framework used in the generated test source. |
| `strict_type_resolution` | `false` | When `true`, type-resolution failures during dynamic class generation throw an exception instead of falling back to `Object.class`. Useful for debugging complex type hierarchies; may cause generation to fail on unresolvable dependencies. |
| `skip_void_no_side_effects` | `true` | When `true`, no test is generated for a `void` method whose execution produces no detectable side effect (no `System.out` output, no log statements, no mock interactions, no branching conditions, no exceptions). Set to `false` to force a test for every `void` method. See the `antikythera-test-generator` documentation for the full list of detectable side effects. |

---

## Database

Used when Antikythera needs to connect to a live database during analysis (e.g. for HQL query
evaluation).  All sub-keys are nested under `database:`.

| Property | Default | Description |
| :--- | :--- | :--- |
| `database.run_queries` | `false` | When `true`, Antikythera connects to the database and executes queries during analysis. |
| `database.url` | — | JDBC connection URL. |
| `database.user` | — | Database username. |
| `database.password` | — | Database password. |
| `database.schema` | — | (Optional) Schema to set for the session. |
| `database.write_ops` | `false` | When `true`, write operations (INSERT / UPDATE / DELETE) may be executed during evaluation. Use with caution in non-disposable environments. |

---

## Dependencies

External JARs and Maven artifacts that Antikythera needs on its classpath to resolve types in the
application under test.  All sub-keys are nested under `dependencies:`.

| Property | Description |
| :--- | :--- |
| `dependencies.artifact_ids` | List of Maven artifact IDs (resolved from the local `.m2` repository). |
| `dependencies.jar_files` | List of absolute paths to local JAR files. |
| `dependencies.on_error` | What to do when a class cannot be processed: `log` records a warning and continues; any other value (or absent) re-throws the exception. |

Example:

```yaml
dependencies:
  artifact_ids:
    - com.example:my-library:1.0.0
  jar_files:
    - /opt/vendor/special.jar
  on_error: log
```

---

## Class Processing

| Property | Description |
| :--- | :--- |
| `skip` | List of fully qualified class names (or package prefixes) that Antikythera should not attempt to parse or compile. |
| `extra_exports` | List of module export directives added when the compiler opens the module graph for dynamic class generation. |
| `finch` | Plugin configuration block. Each entry names a `Finch` implementation class that extends the core evaluation pipeline. See the `Finch` API documentation for the expected structure. |

---

## Custom Method Names (DTO)

Some projects use non-standard getter/setter names for DTO fields.  These can be declared under the
`DTO` key:

```yaml
DTO:
  com.example.MyDto:
    someField:
      getter: retrieveSomeField
      setter: assignSomeField
```

`Settings.loadCustomMethodNames(className, fieldName)` returns the declared getter/setter names,
falling back to the JavaBean convention when no override is present.

---

## Variable Substitution

Values anywhere in the configuration file can reference variables using `${name}` syntax.

| Variable syntax | Resolved from |
| :--- | :--- |
| `${VARIABLE_NAME}` | `variables:` block in the same YAML file |
| `${ENV_VAR_NAME}` | Process environment variables |
| `${USERDIR}` | The current user's home directory (`user.home` system property) |

Example:

```yaml
variables:
  project_root: /home/dev/myproject

base_path: ${project_root}/src/main/java
output_path: ${project_root}/src/test/java
```

---

## Dependency Solver (`depsolver.yml`)

When using `DepSolver` in standalone mode, the following additional keys are recognised:

| Property | Description |
| :--- | :--- |
| `methods` | List of methods to resolve, formatted as `fully.qualified.ClassName#methodName`. |
| `target_class` | Fully qualified name of a single class to analyse. |

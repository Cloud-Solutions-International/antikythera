# Antikythera Configuration Guide

This document describes the various configuration settings available in Antikythera, typically managed through a `generator.yml` file (or `depsolver.yml` for the dependency solver).

## General Project Settings

| Property | Description |
| :--- | :--- |
| `base_package` | The base package of the Application Under Test (AUT). It helps identify classes that should be processed or compiled. |
| `base_path` | The absolute path to the root of the source code (e.g., `.../src/main/java`). |
| `output_path` | The directory where generated test files and other outputs will be saved. |

## Testing Configuration

| Property | Description |
| :--- | :--- |
| `mock_with` | The mocking framework to be used in the generated tests (e.g., `Mockito`). |
| `mock_with_internal` | The mocking framework used internally by Antikythera while evaluating expressions. |
| `base_test_class` | A fully qualified name of a class that every generated test class should extend. |
| `extra_imports` | A list of additional imports to be added to every generated test class. |
| `skip_void_no_side_effects` | Boolean (default: `true`). If enabled, Antikythera will skip creating tests for `void` methods that do not produce any detectable side effects (e.g., logging, console output, or state changes). |
| `log_appender` | The fully qualified name of the `LogAppender` class used to capture and verify log messages in tests. |

## Dependency Management

Dependencies can be specified under the `dependencies` key:

| Property | Description |
| :--- | :--- |
| `artifact_ids` | A list of Maven artifact IDs that the project depends on. |
| `jar_files` | A list of paths to local JAR files required by the project. |

## Variables and Environment Support

Antikythera supports variable substitution in configuration values.

- **Internal Variables**: Defined under a `variables` block in the YAML file. Usage: `${variable_name}`.
- **Environment Variables**: Can be accessed via `${ENV_VAR_NAME}`.
- **Special Variables**: `${USERDIR}` is automatically replaced with the user's home directory.

Example:
```yaml
variables:
  project_root: /path/to/project
base_path: ${project_root}/src/main/java
```

## Database Settings

Configuration for database-related operations using the `database` key. This requires a map of properties:

| Property | Description |
| :--- | :--- |
| `run_queries` | Boolean (default: `false`). If set to `true`, Antikythera will attempt to connect to the database and execute queries during analysis. |
| `url` | The JDBC URL for the database connection. |
| `user` | The database username. |
| `password` | The database password. |
| `schema` | (Optional) The database schema to set for the session. |
| `write_ops` | Boolean. If enabled, write operations (INSERT, UPDATE, DELETE) might be processed during evaluation. Use with caution. |

## Advanced Settings

### Dependency Solver (`depsolver.yml`)
When using `DepSolver`, the following property is used:

| Property | Description |
| :--- | :--- |
| `methods` | A list of methods to solve, formatted as `FullyQualifiedClassName#methodName`. |

## Application Info
- `application.host`: Defines the host URL for the application.
- `application.version`: The version of the application under test.

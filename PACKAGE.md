# Antikythera GitHub Package

This document describes how to use the Antikythera package published to GitHub Packages.

## Package Information

- **Group ID**: `sa.com.cloudsolutions`
- **Artifact ID**: `antikythera`
- **Version**: `0.1.0`
- **Package URL**: https://github.com/Cloud-Solutions-International/antikythera/packages

## Using the Package

### Maven

Add the following to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Cloud-Solutions-International/antikythera</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>sa.com.cloudsolutions</groupId>
        <artifactId>antikythera</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

### Gradle

Add the following to your `build.gradle`:

```gradle
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Cloud-Solutions-International/antikythera")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    implementation 'sa.com.cloudsolutions:antikythera:0.1.0'
}
```

## Authentication

To access GitHub Packages, you need to authenticate with a personal access token that has `read:packages` permission.

### Maven Authentication

Create or update `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

### Gradle Authentication

Set environment variables or gradle properties:

```bash
export USERNAME=YOUR_GITHUB_USERNAME
export TOKEN=YOUR_GITHUB_TOKEN
```

Or add to `gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
```

## Key Classes

- **Parser / type resolver**: `sa.com.cloudsolutions.antikythera.parser.AbstractCompiler`
- **Runtime cache**: `sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime`
- **Symbolic evaluator**: `sa.com.cloudsolutions.antikythera.evaluator.Evaluator`
- **Spring-aware evaluator**: `sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator`
- **Dependency solver**: `sa.com.cloudsolutions.antikythera.depsolver.DepSolver`
- **Test generator interface**: `sa.com.cloudsolutions.antikythera.evaluator.ITestGenerator`

> The test generation CLI (`Antikythera`) and concrete generators (`TestGenerator`, `UnitTestGenerator`,
> `SpringTestGenerator`) live in the companion module `antikythera-test-generator`.

## Requirements

- Java 21 or higher
- Maven 3.6+ or Gradle 6+

## More Information

For detailed usage instructions, examples, and API documentation, visit the [main repository](https://github.com/Cloud-Solutions-International/antikythera).
package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.HashMap;
import java.util.Map;

public class Factory {
    private final Map<String, TestGenerator> unit = new HashMap<>();
    private final Map<String, TestGenerator> integration = new HashMap<>();
    private final Map<String, TestGenerator> api = new HashMap<>();

    TestGenerator create(String type, CompilationUnit cu) {
        String className = AbstractCompiler.getPublicType(cu).getFullyQualifiedName().orElse(null);

        if (type.equals("unit")) {
            TestGenerator gen = unit.get(className);
            if (gen != null) {
                return gen;
            }
            else {
                return createUnitTestGenerator(cu);
            }
        }
        else if(type.equals("integration")) {
            TestGenerator gen = integration.get(cu);
            if (gen != null) {
                return gen;
            }
            else {
                return createIntegrationTestGenerator();
            }
        }
        else {
            TestGenerator gen = api.get(className);
            if (gen != null) {
                return gen;
            }
            else {
                return createApiTestGenerator(cu);
            }
        }
    }

    private TestGenerator createIntegrationTestGenerator() {
        return null;
    }

    private TestGenerator createApiTestGenerator(CompilationUnit cu) {
        String className = AbstractCompiler.getPublicType(cu).getFullyQualifiedName().orElse(null);
        SpringTestGenerator gen = new SpringTestGenerator();
        api.put(className, gen);
        return gen;
    }

    private TestGenerator createUnitTestGenerator(CompilationUnit cu) {
        String className = AbstractCompiler.getPublicType(cu).getFullyQualifiedName().orElse(null);
        UnitTestGenerator gen = new UnitTestGenerator(cu);
        unit.put(className, gen);
        return gen;
    }
}

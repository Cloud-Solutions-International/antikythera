package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.HashMap;
import java.util.Map;

public class Factory {
    private static final Map<String, TestGenerator> unit = new HashMap<>();
    private static final Map<String, TestGenerator> integration = new HashMap<>();
    private static final Map<String, TestGenerator> api = new HashMap<>();

    public static TestGenerator create(String type, CompilationUnit cu) {
        String className = AbstractCompiler.getPublicType(cu).getFullyQualifiedName().orElse(null);
        TestGenerator gen = null;
        if (type.equals("unit")) {
            gen = unit.get(className);
            if (gen != null) {
                return gen;
            }
            else {
                gen = createUnitTestGenerator(cu);
            }
        }
        else if(type.equals("integration")) {
            gen = integration.get(cu);
            if (gen != null) {
                return gen;
            }
            else {
                gen = createIntegrationTestGenerator();
            }
        }
        else {
            gen = api.get(className);
            if (gen != null) {
                return gen;
            }
            else {
                gen = createApiTestGenerator(cu);
            }
        }
        if (gen != null) {
            if (Settings.getProperty("test_framework", String.class).orElse(null).equals("junit")) {
                gen.setAsserter(new JunitAsserter());
            }
            else {
                gen.setAsserter(new TestNgAsserter());
            }
        }
        return gen;
    }

    private static TestGenerator createIntegrationTestGenerator() {
        return null;
    }

    private static TestGenerator createApiTestGenerator(CompilationUnit cu) {
        String className = AbstractCompiler.getPublicType(cu).getFullyQualifiedName().orElse(null);
        SpringTestGenerator gen = new SpringTestGenerator();
        api.put(className, gen);
        return gen;
    }

    private static TestGenerator createUnitTestGenerator(CompilationUnit cu) {
        String className = AbstractCompiler.getPublicType(cu).getFullyQualifiedName().orElse(null);
        UnitTestGenerator gen = new UnitTestGenerator(cu);
        unit.put(className, gen);
        return gen;
    }
}

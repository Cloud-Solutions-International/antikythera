package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestDummyArgumentGenerator {
    DummyArgumentGenerator dummy = new DummyArgumentGenerator();
    @BeforeAll
    static void setUP() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        AntikytheraRunTime.reset();
    }

    @ParameterizedTest
    @CsvSource({"String, Ibuprofen", "int, 0", "Boolean, false", "Double, 0.0", "Float, 0.0", "Long, -100"})
    void testGenerateArgument(String type, Object value) throws ReflectiveOperationException {
        MethodDeclaration md = new MethodDeclaration();
        Parameter parameter = new Parameter();
        helper(type, value, md, parameter);
    }

    @ParameterizedTest
    @CsvSource({"String, Ibuprofen", "int, 0", "Boolean, false", "Double, 0.0", "Float, 0.0", "Long, -100," +
            "List, java.util.ArrayList", "Map, {}", "Set, []"})
    void testGenerateArgumentBody(String type, Object value) throws ReflectiveOperationException, IOException {

        TestLocalsCompiler compiler = new TestLocalsCompiler();
        CompilationUnit cu = compiler.getCompilationUnit();
        cu.addImport("java.util.Map");
        cu.addImport("java.util.Set");
        MethodDeclaration md = cu.getClassByName("Hello").get().findFirst(MethodDeclaration.class).get();
        Parameter parameter = new Parameter();
        md.addParameter(parameter);
        parameter.addAnnotation("RequestBody");
        if (type.equals("String")) {
            value = "";
        }

        helper(type, value, md, parameter);
    }

    private void helper(String type, Object value, MethodDeclaration md, Parameter parameter) throws ReflectiveOperationException {
        md.setName("test");

        parameter.setName("First");
        parameter.setType(type);
        md.addParameter(parameter);

        dummy.generateArgument(parameter);
        Variable v = AntikytheraRunTime.pop();
        assertNotNull(v);
        assertEquals(value, v.getValue().toString());
    }

     class TestLocalsCompiler extends AbstractCompiler {

        protected TestLocalsCompiler() throws IOException {
            compile("sa/com/cloudsolutions/antikythera/evaluator/Hello.java");
        }
    }

}

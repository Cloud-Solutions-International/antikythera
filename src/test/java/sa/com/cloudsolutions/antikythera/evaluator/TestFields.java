package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.JunitAsserter;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFields extends TestHelper {

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Employee";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each()  {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, Evaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testPrintNumberField() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("toString")).orElseThrow();
        Variable v = evaluator.executeMethod(ts);
        assertTrue(v.getValue().toString().contains("Hornblower"));
    }

    @Test
    void testAccessor() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.getType(0).getMethodsByName("simpleAccess").getFirst();
        evaluator.executeMethod(ts);
        assertEquals("Hornblower\nnull\nColombo\n", outContent.toString() );
    }

    @Test
    void testPublic() throws  AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("publicAccess")).orElseThrow();
        evaluator.executeMethod(ts);
        assertEquals("Hornblower\n", outContent.toString() );
    }

    @Test
    void testChains() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("chained")).orElseThrow();
        Variable v = evaluator.executeMethod(ts);
        assertNull(v.getValue());
        assertEquals("false\n", outContent.toString() );
    }

    @Test
    void testJson() throws AntikytheraException, ReflectiveOperationException {
        evaluator.setupFields();
        evaluator.initializeFields();
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("jsonDump")).orElseThrow();
        Variable v = evaluator.executeMethod(ts);
        assertNull(v.getValue());
        assertEquals("""
            {"id":0,"name":"Hornblower","address":null,"phone":null,"email":null}
            """.trim(), outContent.toString().strip());
    }

    @Test
    void testLombok() throws ReflectiveOperationException {
        lombokHelper();
        assertEquals("""
                Tea Origin: Great Western
                Tea Quantity: 100
                Is Organic: true
                Is BOP: true
                Is Ceylon: true
                Is Green: false
                """, outContent.toString());

        assertTrue((boolean) evaluator.getField("isOrganic").getValue());
    }

    private void lombokHelper() throws ReflectiveOperationException {
        evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.antikythera.testhelper.evaluator.Tea", Evaluator.class);
        cu = evaluator.getCompilationUnit();
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("run")).orElseThrow();

        evaluator.executeMethod(md);
    }


    @Test
    void testGetterAndSetter() throws ReflectiveOperationException {
        lombokHelper();
        JunitAsserter asserter = new JunitAsserter();

        MethodResponse mr = new MethodResponse();
        mr.setBody(new Variable(evaluator));
        BlockStmt block = new BlockStmt();
        asserter.addFieldAsserts(mr, block);
        String s = block.toString();
        assertFalse(s.isEmpty());
        assertTrue(s.contains("assertEquals(\"Great Western\", resp.getOrigin())"));
        assertTrue(s.contains("assertEquals(100, resp.getQuantity())"));
        assertTrue(s.contains("assertEquals(true, resp.isOrganic())"));
        assertTrue(s.contains("assertEquals(true, resp.isBop())"));
        assertTrue(s.contains("assertEquals(true, resp.getCeylon())"));

    }
}

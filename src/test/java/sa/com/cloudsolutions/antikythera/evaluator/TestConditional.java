package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.expr.Expression;

class TestConditional extends TestHelper {

    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Conditional";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testExecuteMethod() throws ReflectiveOperationException {
        Person p = new Person("Hello");
        AntikytheraRunTime.push(new Variable(p));

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("conditional1")).orElseThrow();
        evaluator.executeMethod(method);
        assertEquals("Hello", outContent.toString());
    }


    @ParameterizedTest
    @CsvSource({"conditional1, The name is nullT", "conditional2, The name is nullT",
            "conditional3, ZERO!1",
    })
    void testVisit(String name, String value) throws ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(name)).orElseThrow();
        evaluator.visit(method);
        assertEquals(value, outContent.toString());
    }

    @ParameterizedTest
    @CsvSource({"conditional4, ZERO!Negative!Positive!", "conditional5, ZERO!One!Two!Three!",
            "conditional6, ZERO!One!Two!Three!","conditional7, ZERO!One!Two!Three!",
            "conditional8, ZERO!One!Two!Three!", "smallDiff, Nearly 2!One!", "booleanWorks, True!False!"
    })
    void testConditionals(String name, String value) throws ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(name)).orElseThrow();
        evaluator.visit(method);
        String s = outContent.toString();
        assertEquals(value.length(), s.length());
        String[] parts = value.split("!");
        for (String part : parts) {
            assertTrue(s.contains(part));
        }
    }

    @ParameterizedTest
    @CsvSource({"1, One!","2, Two!","3, Three!","4, Guess!"})
    void testSwitchCase(String key, String value) throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("switchCase1")).orElseThrow();

        AntikytheraRunTime.push(new Variable(Integer.parseInt(key)));
        evaluator.executeMethod(method);
        assertEquals(value, outContent.toString());
    }

    @Test
    void testCombinedConditionForNestedMethod() {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("nested")).orElseThrow();

        List<ReturnStmt> returnStatements = method.findAll(ReturnStmt.class);
        assertEquals(3, returnStatements.size(), "Expected 3 return statements in the nested method");

        // Test for the first return statement (return "Zero")
        ReturnConditionVisitor visitor1 = new ReturnConditionVisitor(returnStatements.get(0));
        method.accept(visitor1, null);
        Expression combinedCondition1 = visitor1.getCombinedCondition();
        assertEquals("a == 0 && a >= 0", combinedCondition1.toString(), "Incorrect combined condition for 'Zero'");

        // Test for the second return statement (return "Positive")
        ReturnConditionVisitor visitor2 = new ReturnConditionVisitor(returnStatements.get(1));
        method.accept(visitor2, null);
        Expression combinedCondition2 = visitor2.getCombinedCondition();
        assertEquals("a != 0 && a >= 0", combinedCondition2.toString(), "Incorrect combined condition for 'Positive'");

        // Test for the third return statement (return "Negative")
        ReturnConditionVisitor visitor3 = new ReturnConditionVisitor(returnStatements.get(2));
        method.accept(visitor3, null);
        Expression combinedCondition3 = visitor3.getCombinedCondition();
        assertEquals("a < 0", combinedCondition3.toString(), "Incorrect combined condition for 'Negative'");
    }
}

class TestConditionalWithOptional extends TestHelper {
    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Opt";
    CompilationUnit cu;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testVisit() throws ReflectiveOperationException {
        ((SpringEvaluator)evaluator).setArgumentGenerator(new DummyArgumentGenerator());

        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("ifEmpty")).orElseThrow();
        evaluator.visit(method);
        assertEquals("ID not found\nID: 1", outContent.toString().strip());
    }
}


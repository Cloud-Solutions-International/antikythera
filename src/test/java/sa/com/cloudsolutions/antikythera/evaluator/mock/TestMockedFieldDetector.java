package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestMockedFieldDetector {

    private JavaParser parser;
    private MockedFieldDetector detector;
    private Set<Expression> fieldExpressions;

    @BeforeEach
    void setUp() {
        parser = new JavaParser();
        detector = new MockedFieldDetector("mock");
        fieldExpressions = new HashSet<>();
    }

    @Test
    void testFieldAccessDetection() {
        String code = """
            class Test {
                void method() {
                    String name = mock.firstName;
                    int age = mock.age;
                    String other = otherVar.lastName;
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().get();
        detector.visit(cu, fieldExpressions);

        assertEquals(2, fieldExpressions.size());
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("firstName")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("age")));
        assertFalse(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("lastName")));
    }

    @Test
    void testGetterMethodDetection() {
        String code = """
            class Test {
                void method() {
                    String name = mock.getName();
                    int age = mock.getAge();
                    String email = mock.getEmailAddress();
                    String other = otherVar.getLastName();
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().get();
        detector.visit(cu, fieldExpressions);

        assertEquals(3, fieldExpressions.size());
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("name")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("age")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("emailAddress")));
        assertFalse(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("lastName")));
    }

    @Test
    void testBooleanGetterDetection() {
        String code = """
            class Test {
                void method() {
                    boolean active = mock.isActive();
                    boolean valid = mock.isValid();
                    boolean enabled = mock.isEnabled();
                    boolean other = otherVar.isDeleted();
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().get();
        detector.visit(cu, fieldExpressions);

        assertEquals(3, fieldExpressions.size());
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("active")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("valid")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("enabled")));
        assertFalse(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("deleted")));
    }

    @Test
    void testMethodsWithParametersIgnored() {
        String code = """
            class Test {
                void method() {
                    String result = mock.getName("param");
                    boolean flag = mock.isValid(true);
                    mock.setName("value");
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().get();
        detector.visit(cu, fieldExpressions);

        assertEquals(0, fieldExpressions.size());
    }

    @Test
    void testInvalidGetterNamesIgnored() {
        String code = """
            class Test {
                void method() {
                    mock.get();
                    mock.is();
                    mock.getA();
                    mock.isA();
                    mock.getName2();
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().get();
        detector.visit(cu, fieldExpressions);

        assertEquals(2, fieldExpressions.size());
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("a")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("name2")));
    }

    @Test
    void testMixedFieldAccessAndGetters() {
        String code = """
            class Test {
                void method() {
                    String direct = mock.firstName;
                    String getter = mock.getLastName();
                    boolean flag = mock.isActive();
                    int count = mock.count;
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().get();
        detector.visit(cu, fieldExpressions);

        assertEquals(4, fieldExpressions.size());
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("firstName")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("lastName")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("active")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("count")));
    }

    @Test
    void testDifferentVariableName() {
        detector = new MockedFieldDetector("service");

        String code = """
            class Test {
                void method() {
                    String name = service.getName();
                    boolean flag = service.isValid();
                    int age = service.age;
                    String other = mock.getOther();
                }
            }
            """;

        CompilationUnit cu = parser.parse(code).getResult().get();
        detector.visit(cu, fieldExpressions);

        assertEquals(3, fieldExpressions.size());
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("name")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("valid")));
        assertTrue(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("age")));
        assertFalse(fieldExpressions.stream().anyMatch(expr -> expr.toString().equals("other")));
    }
}


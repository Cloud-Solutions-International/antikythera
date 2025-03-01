package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.VoidType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMockingEvaluator {

    private MockingEvaluator mockingEvaluator;
    private MethodDeclaration voidMethod;
    private MethodDeclaration intMethod;
    private CompilationUnit cu;

    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
    }

    @BeforeEach
    void setUp() {
        mockingEvaluator = new MockingEvaluator("");
        cu = new CompilationUnit();
        voidMethod = new MethodDeclaration();
        voidMethod.setType(new VoidType());
        intMethod = new MethodDeclaration();
        intMethod.setType(PrimitiveType.intType());
    }

    @Test
    void executeMethodReturnsNullForVoidType() throws ReflectiveOperationException {
        assertNull(mockingEvaluator.executeMethod(voidMethod));
    }

    @Test
    void executeMethodReturnsDefaultForPrimitiveType() throws ReflectiveOperationException {
        Variable result = mockingEvaluator.executeMethod(intMethod);
        assertNotNull(result);
        assertEquals(0, result.getValue());
    }

    @Test
    void executeMethodReturnsDefaultForObjectType() throws ReflectiveOperationException {
        MethodDeclaration stringMethod = new MethodDeclaration();
        stringMethod.setType("String");
        stringMethod.setParentNode(cu);

        Variable result = mockingEvaluator.executeMethod(stringMethod);
        assertNotNull(result);
        assertEquals("Ibuprofen", result.getValue());
    }

    @Test
    void executeMethodReturnsNullForUnknownType() throws ReflectiveOperationException {
        MethodDeclaration unknownMethod = new MethodDeclaration();
        unknownMethod.setType("UnknownType");
        unknownMethod.setParentNode(cu);

        Variable result = mockingEvaluator.executeMethod(unknownMethod);
        assertNull(result);
    }
}

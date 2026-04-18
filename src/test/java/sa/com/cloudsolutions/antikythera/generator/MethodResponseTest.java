package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodResponseTest {

    @Test
    void confidence_listInterfaceIsLow() {
        CompilationUnit cu = new CompilationUnit();
        cu.addImport("java.util.List");
        Type listType = StaticJavaParser.parseType("List<String>");
        assertEquals(AssertionConfidence.LOW, MethodResponse.confidenceForDeclaredReturnType(cu, listType));
    }

    @Test
    void confidence_arrayListConcreteIsHigh() {
        CompilationUnit cu = new CompilationUnit();
        cu.addImport("java.util.ArrayList");
        Type t = StaticJavaParser.parseType("ArrayList<String>");
        assertEquals(AssertionConfidence.HIGH, MethodResponse.confidenceForDeclaredReturnType(cu, t));
    }

    @Test
    void confidence_voidIsHigh() {
        CompilationUnit cu = new CompilationUnit();
        assertEquals(AssertionConfidence.HIGH,
                MethodResponse.confidenceForDeclaredReturnType(cu, StaticJavaParser.parseType("void")));
    }

    @Test
    void inferConfidence_pageIsLow() {
        CompilationUnit cu = new CompilationUnit();
        cu.addImport("org.springframework.data.domain.Page");
        Type pageType = StaticJavaParser.parseType("Page<String>");
        MethodResponse mr = new MethodResponse();
        mr.inferReturnAssertionConfidence(cu, pageType);
        assertEquals(AssertionConfidence.LOW, mr.getReturnAssertionConfidence());
    }
}

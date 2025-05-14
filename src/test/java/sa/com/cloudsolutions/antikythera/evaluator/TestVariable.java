package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestVariable {

    @Test
    void testName() {
        String className = "HelloWorld";
        String name = Variable.generateVariableName(new ClassOrInterfaceType().setName(className));
        assertEquals(className.length(), name.length()-3);
        assertTrue(name.startsWith("helloWorld"));

        name = Variable.generateVariableName(java.util.List.class);
        assertTrue(name.startsWith("list"));
        assertEquals(7, name.length());
    }
}

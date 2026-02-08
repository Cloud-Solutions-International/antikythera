package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.HashMap;
import java.util.Map;

public abstract class ArgumentGenerator {
    protected Map<String, Variable> arguments = new HashMap<>();

    public abstract void generateArgument(Parameter param) throws ReflectiveOperationException;

    public Map<String, Variable> getArguments() {
        return arguments;
    }

    public static String instantiateClass(TypeDeclaration<?> classUnderTest, String instanceName) {

        ConstructorDeclaration matched = null;
        String className = resolveNestedName(classUnderTest);

        for (ConstructorDeclaration cd : classUnderTest.findAll(ConstructorDeclaration.class)) {
            if (matched == null) {
                matched = cd;
            }
            if (matched.getParameters().size() > cd.getParameters().size()) {
                matched = cd;
            }
        }
        if (matched != null) {
            StringBuilder b = new StringBuilder(className + " " + instanceName + " " + " = new " + className + "(");
            for (int i = 0; i < matched.getParameters().size(); i++) {
                b.append("null");
                if (i < matched.getParameters().size() - 1) {
                    b.append(", ");
                }
            }
            b.append(");");
            return b.toString();
        }
        return className + " " + instanceName + " = new " + className + "();";
    }

    private static String resolveNestedName(TypeDeclaration<?> type) {
        StringBuilder name = new StringBuilder(type.getNameAsString());
        TypeDeclaration<?> current = type;
        while (current.isNestedType()) {
            if (current.getParentNode().isPresent()) {
                Node parentNode = current.getParentNode().get();
                if (!(parentNode instanceof TypeDeclaration<?> parent)) {
                    break;
                }
                name.insert(0, parent.getNameAsString() + ".");
                current = parent;
            }
        }
        return name.toString();
    }
}

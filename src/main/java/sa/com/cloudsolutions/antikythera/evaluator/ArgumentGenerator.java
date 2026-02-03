package sa.com.cloudsolutions.antikythera.evaluator;

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
        String className = classUnderTest.getNameAsString();
        if (classUnderTest.isNestedType() && classUnderTest.getParentNode().isPresent() && classUnderTest.getParentNode().get() instanceof TypeDeclaration<?> parent) {
            className = parent.getNameAsString() + "." + className;
        }

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
        } else {
            return className + " " + instanceName + " = new " + className + "();";
        }
    }
}

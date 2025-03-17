package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.Map;

public class MockingEvaluator extends Evaluator {

    public MockingEvaluator(String fqdn) {
        super(fqdn);
    }

    @Override
    public void executeConstructor(CallableDeclaration<?> md) throws ReflectiveOperationException {
    }

    @Override
    public void setupFields(CompilationUnit cu) {
    }

    @Override
    public Map<String, Variable> getFields() {
        return null;
    }


    /**
     *
     * @param cd CallableDeclaration a method being executed.
     * @return null when the method defined by the callable declaration is of the type void.
     *   returns a reasonable value when the method is not void.
     * @throws ReflectiveOperationException
     */
    @Override
    public Variable executeMethod(CallableDeclaration<?> cd) throws ReflectiveOperationException {
        if (cd instanceof MethodDeclaration md) {
            setupParameters(md);
            Type returnType = md.getType();
            if (returnType.isVoidType()) {
                return null;
            }
            if (returnType.isClassOrInterfaceType()) {
                Variable v = Reflect.variableFactory(returnType.asClassOrInterfaceType().getNameAsString());
                if (v != null) {
                    return v;
                }
            }
            if (returnType.isPrimitiveType()) {
                return Reflect.variableFactory(returnType.toString());
            }
            if (cd.findCompilationUnit().isPresent()) {
                CompilationUnit cu1 = cd.findCompilationUnit().get();
                if (returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                    String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.asClassOrInterfaceType().getNameAsString());
                    return Reflect.variableFactory(fqdn);
                }
                String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.toString());
                return Reflect.variableFactory(fqdn);
            }
        }
        return null;
    }
}

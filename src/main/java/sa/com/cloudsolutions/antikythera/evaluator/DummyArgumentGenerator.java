package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.List;
import java.util.Optional;

public class DummyArgumentGenerator extends ArgumentGenerator {

    public DummyArgumentGenerator() {
        super();
    }

    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        Variable v = mockParameter(param);
        if (v.getValue() == null) {
            v = mockNonPrimitiveParameter(param);
        }

        /*
         * Pushed to be popped later in the callee
         */
        arguments.put(param.getNameAsString(), v);
        AntikytheraRunTime.push(v);
    }

    private Variable mockNonPrimitiveParameter(Parameter param) throws ReflectiveOperationException {
        Variable v = null;
        Type t = param.getType();

        if (t.isClassOrInterfaceType() && param.findCompilationUnit().isPresent()) {
            String fullClassName = AbstractCompiler.findFullyQualifiedName(param.findCompilationUnit().orElseThrow(), t.asClassOrInterfaceType().getNameAsString());
            if (fullClassName.startsWith("java")) {
                /*
                 * However, you can't rule out the possibility that this is a Map or a List or even a
                 * boxed type.
                 */
                if (t.asClassOrInterfaceType().isBoxedType()) {
                    v = mockParameter(param);
                } else {
                    if (fullClassName.startsWith("java.util")) {
                        v = Reflect.variableFactory(fullClassName);
                    } else {
                        Class<?> clazz = Class.forName(fullClassName);
                        v = new Variable(clazz.getDeclaredConstructor().newInstance());
                    }
                }
            } else {
                Evaluator o = EvaluatorFactory.create(fullClassName, SpringEvaluator.class);
                v = new Variable(o);
                v.setType(t);
                Optional<TypeDeclaration<?>> opt = AntikytheraRunTime.getTypeDeclaration(fullClassName);
                if (opt.isPresent()) {
                    String init = ArgumentGenerator.instantiateClass(
                            opt.get().asClassOrInterfaceDeclaration(),
                            param.getNameAsString()
                    ).replace(";","");
                    String[] parts = init.split("=");
                    v.setInitializer(List.of(StaticJavaParser.parseExpression(parts[1])));
                }
            }
        }
        return v;
    }

    protected Variable mockParameter(Parameter param) {
        Type t = param.getType();
        if (t.isClassOrInterfaceType()) {
            return Reflect.variableFactory(t.asClassOrInterfaceType().getName().asString());
        }
        return Reflect.variableFactory(param.getType().asString());
    }
}

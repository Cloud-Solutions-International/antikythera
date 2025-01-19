package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyArgumentGenerator extends ArgumentGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DummyArgumentGenerator.class);

    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        String paramString = String.valueOf(param);
        Variable v = null;

        if (paramString.startsWith("@RequestBody")) {
            /*
             * Request body on the other hand will be more complex and will most likely be a DTO.
             */
            Type t = param.getType();

            if (t.isClassOrInterfaceType()) {
                String fullClassName = t.asClassOrInterfaceType().resolve().asReferenceType().getQualifiedName();
                if (fullClassName.startsWith("java")) {
                    /*
                     * However you can't rule out the possibility that this is a Map or a List or even a
                     * boxed type.
                     */
                    if (t.asClassOrInterfaceType().isBoxedType()) {
                        v = mockParameter(param);
                    } else {
                        if (fullClassName.startsWith("java.util")) {
                            v = Reflect.variableFactory(fullClassName);
                        } else {
                            Class<?> clazz = Class.forName(fullClassName);
                            v = new Variable(clazz.newInstance());
                        }
                    }
                } else {
                    Evaluator o = new SpringEvaluator(fullClassName);
                    o.setupFields(AntikytheraRunTime.getCompilationUnit(fullClassName));
                    v = new Variable(o);
                }
            } else {
                v = mockParameter(param);
            }
        } else {
            /*
             * Request parameters are typically strings or numbers or booleans.
             */
            v = mockParameter(param);
        }
        /*
         * Pushed to be popped later in the callee
         */
        arguments.put(param.getNameAsString(), v);
        AntikytheraRunTime.push(v);
    }

     public Variable mockParameter(Parameter param) {
        return new Variable(switch (param.getType().asString()) {
            case "Boolean", "boolean" -> false;
            case "float", "Float", "double", "Double" -> 0.0;
            case "Integer", "int" -> 0;
            case "Long", "long" -> -100L;
            case "String" -> "Ibuprofen";
            default -> "0";
        });
    }
}

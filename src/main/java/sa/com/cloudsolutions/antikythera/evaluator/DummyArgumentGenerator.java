package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyArgumentGenerator extends ArgumentGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ArgumentGenerator.class);


    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        String paramString = String.valueOf(param);

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
                        Variable v = mockParameter(param.getTypeAsString());
                        /*
                         * We need to push this variable to the stack so that it can be used later.
                         */
                        AntikytheraRunTime.push(v);
                    } else {
                        if (fullClassName.startsWith("java.util")) {
                            Variable v = Reflect.variableFactory(fullClassName);
                            /*
                             * Pushed to be popped later in the callee
                             */
                            AntikytheraRunTime.push(v);
                        } else {
                            Class<?> clazz = Class.forName(fullClassName);
                            Variable v = new Variable(clazz.newInstance());
                            /*
                             * PUsh arguments
                             */
                            AntikytheraRunTime.push(v);
                        }
                    }
                } else {

                    Evaluator o = new SpringEvaluator(fullClassName);
                    o.setupFields(AntikytheraRunTime.getCompilationUnit(fullClassName));
                    Variable v = new Variable(o);
                    /*
                     * Args to be popped by the callee
                     */
                    AntikytheraRunTime.push(v);
                }
            } else {
                logger.warn("Unhandled {}", t);
            }
        } else {
            /*
             * Request parameters are typically strings or numbers and these are pushed into the stack
             * to be popped in the callee
             */
            Variable v = mockParameter(param.getTypeAsString());
            AntikytheraRunTime.push(v);
        }
    }

     public Variable mockParameter(String typeName) {
        return new Variable(switch (typeName) {
            case "Boolean" -> false;
            case "float", "Float", "double", "Double" -> 0.0;
            case "Integer", "int" -> 0;
            case "Long", "long" -> 0L;
            case "String" -> "Ibuprofen";
            default -> "0";
        });
    }
}

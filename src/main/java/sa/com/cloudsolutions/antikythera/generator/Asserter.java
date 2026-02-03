package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.lang.reflect.Method;
import java.util.Collection;

public abstract class Asserter {
    private static final Logger logger = LoggerFactory.getLogger(Asserter.class);

    public abstract Expression assertNotNull(String variable);
    public abstract Expression assertNull(String variable);
    public abstract void setupImports(CompilationUnit gen);
    public abstract Expression assertEquals(String rhs, String lhs);
    public abstract Expression assertThrows(String invocation, MethodResponse response);
    public abstract Expression assertDoesNotThrow(String invocation);
    public abstract Expression assertOutput(String expected);

    public void addFieldAsserts(MethodResponse resp, BlockStmt body) {
        if (resp.getBody() != null && resp.getBody().getValue() instanceof Evaluator ev) {
            addFieldAsserts(body, ev);
        }
    }

    private void addFieldAsserts(BlockStmt body, Evaluator ev) {
        int i = 0;
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(ev.getClassName()).orElseThrow();
        for(FieldDeclaration field : type.getFields()) {
            VariableDeclarator fieldVariable = field.getVariable(0);
            try {
                String fieldName = fieldVariable.getNameAsString();
                Variable value = ev.getField(fieldName);

                if (value != null && !fieldName.equals("serialVersionUID")
                        && value.getValue() != null) {

                    String getter = findGetter(value, fieldName, type);
                    if (getter != null) {
                        body.addStatement(fieldAssertion(getter, value));
                        i++;
                    }
                }
            } catch (Exception pex) {
                logger.error("Error asserting {}", fieldVariable.getNameAsString(), pex);
            }
            if (i == 5) {
                break;
            }
        }
    }

    private String findGetter(Variable value, String fieldName, TypeDeclaration<?> type) {

        /*
         * For `boolean` fields that start with `is` immediately followed by a title-case
         * letter, nothing is prefixed to generate the getter name.
         * So if you have a field boolean isOrganic the getter will be isOrganic()
         */
        String getter;
        if (value.getType() != null && value.getType().isPrimitiveType()
                && value.getType().asString().equals("boolean") && fieldName.startsWith("is")) {
            getter = fieldName;
        }
        else {
            getter = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        }

        /*
         * A method that's explicitly private is not a getter, so we skip it.
         */
        for (MethodDeclaration md : type.getMethodsByName(getter)) {
            if (md.getParameters().isEmpty() && md.isPrivate()) {
                return null;
            }
        }

        return getter;
    }

    public Expression fieldAssertion(String getter, Variable v) {
        Object value = v.getValue();

        String getterCall = "resp." + getter + "()";
        if (value instanceof String) {
            return assertEquals("\"" + v.getValue() + "\"", getterCall);
        }
        if (value instanceof Collection<?>) {
            try {
                Method m = v.getClazz().getMethod("size");
                Object result = m.invoke(value);
                return assertEquals(result.toString(), getterCall + ".size()");
            } catch (ReflectiveOperationException e) {
                logger.warn("Could not use reflection for assertion");
            }
        }
        return assertEquals(v.getValue().toString(), getterCall);

    }

    public Expression assertEmpty(String variable) {
        MethodCallExpr mce = new MethodCallExpr("assertTrue");
        mce.addArgument(variable + ".isEmpty()");
        return mce;
    }

    public Expression assertNotEmpty(String variable) {
        MethodCallExpr mce = new MethodCallExpr("assertFalse");
        mce.addArgument(variable + ".isEmpty()");
        return mce;
    }

}

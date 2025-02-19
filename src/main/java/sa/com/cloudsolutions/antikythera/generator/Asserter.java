package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.lang.reflect.Method;
import java.util.Map;

public abstract class Asserter {
    private static final Logger logger = LoggerFactory.getLogger(Asserter.class);

    public abstract Expression assertNotNull(String variable);
    public abstract void setupImports(CompilationUnit gen);
    public abstract Expression assertEquals(String lhs, String rhs);

    public void addFieldAsserts(MethodResponse resp, BlockStmt body) {
        if (resp.getBody() != null && resp.getBody().getValue() instanceof Evaluator ev) {
            int i = 0;
            for(Map.Entry<String, Variable> field : ev.getFields().entrySet()) {
                try {
                    if (field.getValue() != null && field.getValue().getValue() != null) {
                        Variable v = field.getValue();
                        String getter = "get" + field.getKey().substring(0, 1).toUpperCase() + field.getKey().substring(1);
                        body.addStatement(fieldAssertion(getter, v));
                        i++;
                    }
                } catch (Exception pex) {
                    logger.error("Error asserting {}", field.getKey());
                }
                if (i == 5) {
                    break;
                }
            }
        }
    }


    public Expression fieldAssertion(String getter, Variable v) {

        Object value = v.getValue();

        if (value instanceof String) {
            return assertEquals("resp." + getter + "()", "\"" + v.getValue() + "\"");
        } if (v.getClazz() != null) {
            try {
                Method m = v.getClazz().getMethod("size");
                Object result = m.invoke(value);
                return assertEquals("resp." + getter + "().size()", result.toString());
            } catch (ReflectiveOperationException e) {
                logger.warn("Could not use reflection for assertion");
            }
        }
        return assertEquals("resp." + getter + "()",v.getValue().toString());

    }
}

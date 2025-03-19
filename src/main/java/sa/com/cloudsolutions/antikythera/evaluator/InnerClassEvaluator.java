package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;

import java.util.Map;

public class InnerClassEvaluator extends Evaluator {
    protected Evaluator enclosure;

    public InnerClassEvaluator(String className) {
        super(className);
    }


    @Override
    public Variable getValue(Node n, String name) {
        Variable v = super.getValue(n, name);
        if (v == null) {
            for (Map<String, Variable> local : enclosure.getLocals().values()) {
                v = local.get(name);
                if (v != null) {
                    return v;
                }
            }
            return enclosure.getFields().get(name);
        }
        return v;
    }

    public void setEnclosure(Evaluator eval) {
        this.enclosure = eval;
    }
}

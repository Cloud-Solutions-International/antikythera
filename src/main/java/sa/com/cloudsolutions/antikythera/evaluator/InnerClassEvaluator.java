package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.Map;
import java.util.Optional;

public class InnerClassEvaluator extends Evaluator {
    protected Evaluator enclosure;

    protected InnerClassEvaluator(EvaluatorFactory.Context context) {
        super(context);
        this.enclosure = context.getEnclosure();
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

    @Override
    public Variable executeLocalMethod(MCEWrapper methodCall) throws ReflectiveOperationException {
        Variable v = super.executeLocalMethod(methodCall);
        if (v == null && methodCall.getMethodCallExpr() instanceof MethodCallExpr mce) {
            Optional<TypeDeclaration> t = mce.findAncestor(TypeDeclaration.class);
            if (t.isPresent()) {
                Optional<TypeDeclaration> parent = t.get().findAncestor(TypeDeclaration.class);
                if (parent.isPresent() && parent.get().isClassOrInterfaceDeclaration()) {
                    ClassOrInterfaceDeclaration cdecl = parent.get().asClassOrInterfaceDeclaration();
                    Optional<Callable> mdecl = AbstractCompiler.findMethodDeclaration(methodCall, cdecl);
                    if (mdecl.isPresent()) {
                        return enclosure.executeMethod(mdecl.get().getCallableDeclaration());
                    }
                }
            }
        }
        return null;
    }

}

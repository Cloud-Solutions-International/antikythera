package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
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
    public Symbol getValue(Node n, String name) {
        Symbol v = super.getValue(n, name);
        if (v == null) {
            for (Map<String, Symbol> local : enclosure.getLocals().values()) {
                v = local.get(name);
                if (v != null) {
                    return v;
                }
            }
            return enclosure.getField(name);
        }
        return v;
    }

    public void setEnclosure(Evaluator eval) {
        this.enclosure = eval;
    }

    @Override
    Variable resolveExpressionAsUtilityClass(NameExpr expr) {
        Variable v = super.resolveExpressionAsUtilityClass(expr);
        if (v == null && enclosure != null && enclosure.getCompilationUnit() != null) {
            cu = enclosure.getCompilationUnit();
            v = super.resolveExpressionAsUtilityClass(expr);
            cu = null;
        }
        return v;
    }

    @SuppressWarnings("unchecked")
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

    @Override
    Variable createObject(ObjectCreationExpr oce) throws ReflectiveOperationException {
        ClassOrInterfaceType type = oce.getType();
        TypeWrapper wrapper = AbstractCompiler.findType(cu, type.getNameAsString());
        if (wrapper == null && enclosure != null && enclosure.getCompilationUnit() != null) {
            wrapper = AbstractCompiler.findType(enclosure.getCompilationUnit(), type.getNameAsString());
        }
        if (wrapper == null) {
            return null;
        }
        return super.createObject(oce, wrapper);
    }

    @Override
    Variable evaluateClassExpression(ClassExpr classExpr) throws ClassNotFoundException {
        TypeWrapper wrapper = AbstractCompiler.findType(cu, classExpr.getType().asString());
        if (wrapper == null && enclosure != null && enclosure.getCompilationUnit() != null) {
            wrapper = AbstractCompiler.findType(enclosure.getCompilationUnit(),  classExpr.getType().asString());
            if (wrapper != null) {
                return super.evaluateClassExpression(wrapper);
            }
        }
        return null;
    }
}

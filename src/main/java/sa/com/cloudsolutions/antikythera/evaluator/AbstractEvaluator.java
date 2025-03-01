package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.Optional;

public abstract class AbstractEvaluator implements ExpressionEvaluator {

    /**
     * The fully qualified name of the class for which we created this evaluator.
     */
    protected String className;

    /**
     * The compilation unit that is being processed by the expression engine
     */
    protected CompilationUnit cu;

    public AbstractEvaluator(String className) {
        this.className = className;
        cu = AntikytheraRunTime.getCompilationUnit(className);
    }

    /**
     * Execute a method call.
     * @param wrapper the method call expression wrapped so that the argument types are available
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
    public Variable executeMethod(MCEWrapper wrapper) throws ReflectiveOperationException {

        Optional<Callable> n = AbstractCompiler.findCallableDeclaration(wrapper, cu.getType(0).asClassOrInterfaceDeclaration());
        if (n.isPresent() && n.get().isMethodDeclaration()) {
            Variable v = executeMethod(n.get().asMethodDeclaration());
            if (v != null && v.getValue() == null) {
                v.setType(n.get().asMethodDeclaration().getType());
            }
            return v;
        }

        return null;
    }

}

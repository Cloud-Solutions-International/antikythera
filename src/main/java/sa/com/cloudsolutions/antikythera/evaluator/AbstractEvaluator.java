package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.HashMap;
import java.util.Map;
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

    /**
     * Local variables.
     *
     * These are specific to a block statement. A block statement may also be an
     * entire method. The primary key will be the hashcode of the block statement.
     */
    protected final Map<Integer, Map<String, Variable>> locals ;


    public AbstractEvaluator(String className) {
        this.className = className;
        cu = AntikytheraRunTime.getCompilationUnit(className);
        locals = new HashMap<>();
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


    /**
     * Sets a local variable
     * @param node An expression representing the code being currently executed. It will be used to identify the
     *             encapsulating block.
     * @param nameAsString the variable name.
     *                     If the variable is already available as a local it's value will be replaced.
     * @param v The value to be set for the variable.
     */
    public void setLocal(Node node, String nameAsString, Variable v) {
        Variable old = getLocal(node, nameAsString);
        if (old != null) {
            old.setValue(v.getValue());
        }
        else {
            BlockStmt block = findBlockStatement(node);
            int hash = (block != null) ? block.hashCode() : 0;

            Map<String, Variable> localVars = this.locals.computeIfAbsent(hash, k -> new HashMap<>());
            localVars.put(nameAsString, v);
        }
    }

    /**
     * Recursively traverse parents to find a block statement.
     * @param expr the expression to start from
     * @return the block statement that contains expr
     */
    private static BlockStmt findBlockStatement(Node expr) {
        Node currentNode = expr;
        while (currentNode != null) {
            if (currentNode instanceof BlockStmt blockStmt) {
                return blockStmt;
            }
            if (currentNode instanceof MethodDeclaration md) {
                return md.getBody().orElse(null);
            }
            currentNode = currentNode.getParentNode().orElse(null);
        }
        return null; // No block statement found
    }

    /**
     * Find local variable
     * Does not look at fields. You probably want to call getValue() instead.
     *
     * @param node the node representing the current expresion.
     *             It's primary purpose is to help identify the current block
     * @param name the name of the variable to look up
     * @return the Variable if it's found or null.
     */
    public Variable getLocal(Node node, String name) {
        Variable v = null;
        Node n = node;

        while (true) {
            BlockStmt block = findBlockStatement(n);
            int hash = (block != null) ? block.hashCode() : 0;
            if (hash == 0) {
                for(Map.Entry<Integer, Map<String, Variable>> entry : locals.entrySet()) {
                    v = entry.getValue().get(name);
                    if (v != null) {
                        return v;
                    }
                }
                break;
            }
            else {
                Map<String, Variable> localsVars = this.locals.get(hash);

                if (localsVars != null) {
                    v = localsVars.get(name);
                    if (v != null)
                        return v;
                }
                if (n instanceof MethodDeclaration) {
                    localsVars = this.locals.get(hash);
                    if (localsVars != null) {
                        v = localsVars.get(name);
                        return v;
                    }
                    break;
                }
                if (block == null) {
                    break;
                }
                n = block.getParentNode().orElse(null);
                if (n == null) {
                    break;
                }
            }
        }
        return null;
    }

}

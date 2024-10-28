package sa.com.cloudsolutions.antikythera.generator;


import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

/**
 * Represents an argument in a call to a query method.
 * Remember an argument is what's being passed in at the time of execution.
 */
public class QueryMethodArgument {
    /**
     * The name of the argument as defined in the repository function
     */
    private Expression argument;

    private int index;

    private Variable variable;

    public QueryMethodArgument(Expression argument, int index, Variable variable) {
        this.argument = argument;
        this.index = index;
        this.variable = variable;
    }

    public Expression getArgument() {
        return argument;
    }

    public Variable getVariable() {
        return variable;
    }

    public void setVariable(Variable variable) {
        this.variable = variable;
    }

    @Override
    public String toString() {
        return argument.toString() + " : " + variable.toString();
    }
}

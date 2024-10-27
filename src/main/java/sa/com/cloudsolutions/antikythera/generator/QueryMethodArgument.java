package sa.com.cloudsolutions.antikythera.generator;


import com.github.javaparser.ast.expr.Expression;

/**
 * Represents an argument in a call to a query method.
 * Remember an argument is what's being passed in at the time of execution.
 */
public class QueryMethodArgument {
    /**
     * The name of the argument as defined in the respository function
     */
    private Expression argument;

    private int index;

    public QueryMethodArgument(Expression argument, int index) {
        this.argument = argument;
        this.index = index;
    }

    public Expression getArgument() {
        return argument;
    }
}

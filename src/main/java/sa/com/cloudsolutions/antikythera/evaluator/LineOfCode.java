package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;

import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

/**
 * Represents a line of code with a condition in a method.
 */
public class LineOfCode {

    /**
     * The state of the variables required such that an if condition evaluates to true.
     * This will be applicable only for IF statement expressions.
     */
    private final List<Expression> trueState = new ArrayList<>();

    /**
     * The state of the variables required such that an if condition evaluates to false.
     * This will be applicable only for IF statement expressions.
     */
    private final List<Expression> falseState = new ArrayList<>();

    /**
     * Represents the state where the node has not been visited at all.
     * This is the default value for color for all nodes.
     */
    public static final int UNTRAVELLED = 0;
    /**
     * Represents the stage where we have traversed the false path
     */
    public static final int FALSE_PATH = 1;
    /**
     * Represents the stage where we have traversed the true path
     */
    public static final int TRUE_PATH = 2;

    /**
     * This state is achieved by the logical or operation of the FALSE_PATH and TRUE_PATH
     */
    public static final int BOTH_PATHS = 3;

    /**
     * The current state of the
     */
    private int pathTaken;

    /**
     * The statement that this line of code represents.
     */
    private final Statement statement;

    /**
     * A non-null value if this statement represents a JPA Query
     */
    private RepositoryQuery repositoryQuery;

    public LineOfCode(Statement statement) {
        this.statement = statement;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof LineOfCode b) {
            return b.statement.equals(statement);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return statement.hashCode();
    }

    public int getPathTaken() {
        return pathTaken;
    }

    public void setPathTaken(int pathTaken) {
        this.pathTaken = pathTaken;
    }

    public void addPrecondition(Expression precondition, boolean state) {
        if(state) {
            trueState.add(precondition);
        }
        else {
            falseState.add(precondition);
        }
    }

    public List<Expression> getPrecondition(boolean state) {
        return state ? trueState : falseState;
    }

    public Statement getStatement() {
        return statement;
    }

    public RepositoryQuery getRepositoryQuery() {
        return repositoryQuery;
    }

    public void setRepositoryQuery(RepositoryQuery repositoryQuery) {
        this.repositoryQuery = repositoryQuery;
    }

    @Override
    public String toString() {
        return statement.toString();
    }
}

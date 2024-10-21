package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;

import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

/**
 * Represents a line of code in a method.
 * The term is used rather loosely, a line of code may span several lines on paper.
 */
public class LineOfCode {

    /**
     * The state of the variables required such that an if condition evaluates to true.
     * This will be applicable only for IF statement expressions.
     */
    private List<Expression> trueState = new ArrayList<>();

    /**
     * The state of the variables required such that an if condition evaluates to false.
     * This will be applicable only for IF statement expressions.
     */
    private List<Expression> falseState = new ArrayList<>();

    /**
     * Represents the state where the node has not been visited at all.
     * This is the default value for color for all nodes.
     */
    public static final int WHITE = 0;
    /**
     * Represents the stage where we have encountered a node but not traversed all edges.
     */
    public static final int GREY = 1;
    /**
     * Represents the stage where we have traversed all edges of a node.
     */
    public static final int BLACK = 2;
    /**
     * Shows the current state of the node.
     * Can be one of WHITE, GREY or BLACK.
     */
    private int color;

    /**
     * The statement that this line of code represents.
     */
    private Statement statement;

    /**
     * A non null value if this statement represents a JPA Query
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

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
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

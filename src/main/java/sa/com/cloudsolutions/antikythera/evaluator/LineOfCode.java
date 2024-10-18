package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a line of code in a method.
 * The term is used rather loosely, a line of code may span several lines on paper.
 */
public class LineOfCode {
    /*
     * If the LOC is a conditional statement will hold a condition
     * when the loc is not a conditional statement it will be null
     */
    Expression condition;

    /**
     * The conditions that need to be met to reach this line of code.
     * For each if/then/else statement there will be two of these. Neither will be directly attached
     * to the if statement. However one will be attached to the then statement and the other to the else statement.
     */
    List<Expression> preConditions = new ArrayList<>();

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
    int color;
    Statement statement;

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
}

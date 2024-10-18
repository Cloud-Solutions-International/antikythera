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

    boolean visited;
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
}

package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.stmt.Statement;

/**
 * Context information about a loop when an exception occurs during evaluation.
 * This helps determine if an exception is conditional on loop iteration.
 */
public class LoopContext {
    private Statement loopStatement;          // The ForEachStmt/ForStmt/WhileStmt
    private String iteratorVariable;          // Variable being iterated (for foreach)
    private Variable collectionVariable;      // The collection being iterated
    private boolean emptyCollection;          // Was collection empty when exception occurred?
    private int iterationWhenThrown;          // Which iteration threw (0-based)
    private Variable currentElement;          // Element being processed when thrown

    public LoopContext() {
    }

    public Statement getLoopStatement() {
        return loopStatement;
    }

    public void setLoopStatement(Statement loopStatement) {
        this.loopStatement = loopStatement;
    }

    public String getIteratorVariable() {
        return iteratorVariable;
    }

    public void setIteratorVariable(String iteratorVariable) {
        this.iteratorVariable = iteratorVariable;
    }

    public Variable getCollectionVariable() {
        return collectionVariable;
    }

    public void setCollectionVariable(Variable collectionVariable) {
        this.collectionVariable = collectionVariable;
    }

    public boolean isEmptyCollection() {
        return emptyCollection;
    }

    public void setEmptyCollection(boolean emptyCollection) {
        this.emptyCollection = emptyCollection;
    }

    public int getIterationWhenThrown() {
        return iterationWhenThrown;
    }

    public void setIterationWhenThrown(int iterationWhenThrown) {
        this.iterationWhenThrown = iterationWhenThrown;
    }

    public Variable getCurrentElement() {
        return currentElement;
    }

    public void setCurrentElement(Variable currentElement) {
        this.currentElement = currentElement;
    }

    @Override
    public String toString() {
        return "LoopContext{" +
                "loopStatement=" + (loopStatement != null ? loopStatement.getClass().getSimpleName() : "null") +
                ", iteratorVariable='" + iteratorVariable + '\'' +
                ", emptyCollection=" + emptyCollection +
                ", iterationWhenThrown=" + iterationWhenThrown +
                '}';
    }
}

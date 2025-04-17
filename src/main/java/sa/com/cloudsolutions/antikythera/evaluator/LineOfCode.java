package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
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
    private final List<Precondition> trueState = new ArrayList<>();

    /**
     * The state of the variables required such that an if condition evaluates to false.
     * This will be applicable only for IF statement expressions.
     */
    private final List<Precondition> falseState = new ArrayList<>();

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

    private final MethodDeclaration methodDeclaration;

    private LineOfCode parent;
    private List<LineOfCode> children = new ArrayList<>();

    /**
     * A non-null value if this statement represents a JPA Query
     */
    private RepositoryQuery repositoryQuery;

    @SuppressWarnings("unchecked")
    public LineOfCode(Statement statement) {
        this.methodDeclaration = statement.findAncestor(MethodDeclaration.class).orElseThrow();
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
        if (children.isEmpty() || children.stream().allMatch(LineOfCode::isBothPaths)) {
            this.pathTaken = pathTaken;
            if (parent != null) {
                parent.updatePaths(this);
            }
        }
    }

    private void updatePaths(LineOfCode eventSource) {
        if (children.stream().allMatch(child -> child.getPathTaken() == BOTH_PATHS)) {
            if (isTruePath() || isFalsePath()) {
                this.pathTaken = BOTH_PATHS;
                if (parent != null) {
                    parent.updatePaths(this);
                }
            }
            else if (statement instanceof IfStmt ifStmt) {
                if(IfConditionVisitor.isNodeInStatement(eventSource.getStatement(), ifStmt.getThenStmt())) {
                    this.pathTaken = TRUE_PATH;
                }
                else {
                    this.pathTaken = FALSE_PATH;
                }
            }
        }
    }

    public void addPrecondition(Precondition precondition, boolean state) {
        if(state) {
            trueState.add(precondition);
        }
        else {
            falseState.add(precondition);
        }
    }

    public List<Precondition> getPrecondition(boolean state) {
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

    public MethodDeclaration getMethodDeclaration() {
        return methodDeclaration;
    }

    public void addChild(LineOfCode child) {
        children.add(child);
    }

    public void setParent(LineOfCode parent) {
        if (parent != null) {
            this.parent = parent;
            parent.addChild(this);
        }
    }

    public LineOfCode getParent() {
        return parent;
    }

    public boolean isUntravelled() {
        return pathTaken == UNTRAVELLED;
    }
    public boolean isFalsePath() {
        return pathTaken == FALSE_PATH;
    }
    public boolean isTruePath() {
        return pathTaken == TRUE_PATH;
    }
    public boolean isBothPaths() {
        return pathTaken == BOTH_PATHS;
    }
}

package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;

import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

/**
 * Represents a line of code within a method, including its associated conditions and execution paths.
 *
 */
public class LineOfCode {

    /**
     * The list of preconditions to be applied before executing this line.
     */
    private final List<Precondition> preconditions = new ArrayList<>();

    /**
     * Represents the state where the node has not been visited at all.
     * This is the default value for the path state of all nodes.
     */
    public static final int UNTRAVELLED = 0;

    /**
     * Represents the state where the false path of the node has been traversed.
     */
    public static final int FALSE_PATH = 1;

    /**
     * Represents the state where the true path of the node has been traversed.
     */
    public static final int TRUE_PATH = 2;

    /**
     * Represents the state where both the true and false paths of the node have been traversed.
     */
    public static final int BOTH_PATHS = 3;

    /**
     * The current path state of this line of code.
     */
    private int pathTaken;

    /**
     * The statement that this line of code represents.
     */
    private final Statement statement;

    /**
     * The method declaration that this line of code belongs to.
     */
    private final MethodDeclaration methodDeclaration;

    /**
     * The parent conditional statement
     */
    private LineOfCode parent;

    /**
     * The if conditions that are direct descendents of the current statement
     */
    private final List<LineOfCode> children = new ArrayList<>();

    /**
     * A non-null value if this statement represents a JPA query.
     */
    private RepositoryQuery repositoryQuery;

    private boolean result;

    /**
     * Constructs a `LineOfCode` instance for the given statement.
     *
     * @param statement The statement this line of code represents.
     * @throws IllegalStateException if the statement does not belong to a method.
     */
    @SuppressWarnings("unchecked")
    public LineOfCode(Statement statement) {
        this.methodDeclaration = statement.findAncestor(MethodDeclaration.class).orElseThrow();
        this.statement = statement;
    }

    /**
     * Checks if this `LineOfCode` is equal to another object.
     *
     * @param obj The object to compare with.
     * @return `true` if the object is a `LineOfCode` with the same statement, otherwise `false`.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LineOfCode b) {
            return b.statement.equals(statement);
        }
        return false;
    }

    /**
     * Returns the hash code of this `LineOfCode`.
     *
     * @return The hash code of the statement.
     */
    @Override
    public int hashCode() {
        return statement.hashCode();
    }

    /**
     * Gets the current path state of this line of code.
     *
     * @return The path state.
     */
    public int getPathTaken() {
        return pathTaken;
    }

    /**
     * Sets the path state of this line of code and updates the parent if necessary.
     * The state will change only if the current node does not have any child nodes or if all the
     * child nodes are in the both paths visited state.
     *
     * @param pathTaken The new path state.
     */
    public void setPathTaken(int pathTaken) {
        if (children.isEmpty() || children.stream().allMatch(LineOfCode::isFullyTravelled)) {
            this.pathTaken = pathTaken;
            if (parent != null) {
                parent.updatePaths(this);
            }
        }
    }

    /**
     * Update this path because a child node has changed.
     *
     * @param eventSource The child node that triggered the update.
     */
    private void updatePaths(LineOfCode eventSource) {
        if (statement instanceof IfStmt ifStmt) {
            // Check if all children are in BOTH_PATHS state
            boolean allChildrenBothPaths = true;
            for (LineOfCode child : children) {
                if (child.getPathTaken() != BOTH_PATHS) {
                    allChildrenBothPaths = false;
                    break;
                }
            }

            if (allChildrenBothPaths) {
                this.pathTaken = BOTH_PATHS;
                if (parent != null) {
                    parent.updatePaths(this);
                }
                return;
            }

            // Check Then block
            boolean allThenChildrenBothPaths = true;
            for (LineOfCode child : children) {
                if (IfConditionVisitor.isNodeInStatement(ifStmt.getThenStmt(), child.getStatement())) {
                    if (child.getPathTaken() != BOTH_PATHS) {
                        allThenChildrenBothPaths = false;
                        break;
                    }
                }
            }

            if (allThenChildrenBothPaths) {
                this.pathTaken = TRUE_PATH;
                return;
            }

            // Check Else block if present
            if (ifStmt.getElseStmt().isPresent()) {
                boolean allElseChildrenBothPaths = true;
                for (LineOfCode child : children) {
                    if (IfConditionVisitor.isNodeInStatement(ifStmt.getElseStmt().get(), child.getStatement())) {
                        if (child.getPathTaken() != BOTH_PATHS) {
                            allElseChildrenBothPaths = false;
                            break;
                        }
                    }
                }

                if (allElseChildrenBothPaths) {
                    this.pathTaken = FALSE_PATH;
                }
            }
        }
    }

    /**
     * Adds a precondition to this line of code which will determine which path will be taken
     *
     * @param precondition The precondition to add.
     */
    public void addPrecondition(Precondition precondition) {
        preconditions.add(precondition);
    }

    /**
     * Gets the preconditions to be applied before executing this line of code.
     * Applying these preconditions at the start of method execution will result in the conditional
     * statement taking the branch different from the one taken before.
     *
     * @return The list of preconditions.
     */
    public List<Precondition> getPreconditions() {
        return preconditions;
    }

    /**
     * Gets the statement represented by this line of code.
     *
     * @return The statement.
     */
    public Statement getStatement() {
        return statement;
    }

    /**
     * Gets the JPA query associated with this line of code, if any.
     *
     * @return The JPA query, or `null` if none exists.
     */
    public RepositoryQuery getRepositoryQuery() {
        return repositoryQuery;
    }

    /**
     * Sets the JPA query associated with this line of code.
     *
     * @param repositoryQuery The JPA query to set.
     */
    public void setRepositoryQuery(RepositoryQuery repositoryQuery) {
        this.repositoryQuery = repositoryQuery;
    }

    /**
     * Gets the method declaration that this line of code belongs to.
     *
     * @return The method declaration.
     */
    public MethodDeclaration getMethodDeclaration() {
        return methodDeclaration;
    }

    /**
     * Adds a child `LineOfCode` node to this node.
     *
     * @param child The child node to add.
     */
    public void addChild(LineOfCode child) {
        children.add(child);
    }

    /**
     * Sets the parent `LineOfCode` node for this node.
     *
     * @param parent The parent node to set.
     */
    public void setParent(LineOfCode parent) {
        if (parent != null) {
            this.parent = parent;
            parent.addChild(this);
        }
    }

    /**
     * Gets the parent `LineOfCode` node of this node.
     *
     * @return The parent node, or `null` if none exists.
     */
    public LineOfCode getParent() {
        return parent;
    }

    /**
     * Checks if this line of code is in the untraveled state.
     *
     * @return `true` if untraveled, otherwise `false`.
     */
    public boolean isUntravelled() {
        return pathTaken == UNTRAVELLED;
    }

    /**
     * Checks if this line of code is in the false path state.
     *
     * @return `true` if in the false path state, otherwise `false`.
     */
    public boolean isFalsePath() {
        return pathTaken == FALSE_PATH;
    }

    /**
     * Checks if this line of code is in the true path state.
     *
     * @return `true` if in the true path state, otherwise `false`.
     */
    public boolean isTruePath() {
        return pathTaken == TRUE_PATH;
    }

    /**
     * Checks if this line of code is in the both paths state.
     *
     * @return `true` if in the both paths state, otherwise `false`.
     */
    public boolean isFullyTravelled() {
        return pathTaken == BOTH_PATHS;
    }

    /**
     * Returns a string representation of this line of code.
     *
     * @return The string representation of the statement.
     */
    @Override
    public String toString() {
        return statement.toString();
    }

    /**
     * Sets the result of the line of code.
     *
     * @param result The result to set.
     */
    public void setResult(boolean result) {
        this.result = result;
    }
}
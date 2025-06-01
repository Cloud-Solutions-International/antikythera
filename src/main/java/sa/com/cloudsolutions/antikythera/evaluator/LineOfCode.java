package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;


/**
 * Represents a line of code within a method, including its associated conditions and execution paths.
 */
public class LineOfCode {

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
     * The list of preconditions to be applied before executing this line.
     */
    private final LinkedHashSet<Precondition> preconditions = new LinkedHashSet<>();
    /**
     * The statement that this line of code represents.
     */
    private Statement statement;
    /**
     * The method declaration that this line of code belongs to.
     */
    private MethodDeclaration methodDeclaration;
    /**
     * The if conditions that are direct descendents of the current statement
     */
    private final List<LineOfCode> children = new ArrayList<>();
    private Expression binaryExpr;
    /**
     * The current path state of this line of code.
     */
    private int pathTaken;

    /**
     * The parent conditional statement
     */
    private LineOfCode parent;
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
        if (statement instanceof IfStmt ifStmt) {
            this.binaryExpr = ifStmt.getCondition();
        }
    }

    @SuppressWarnings("unchecked")
    public LineOfCode(Expression binaryExpr) {

        binaryExpr.findAncestor(Statement.class).ifPresent(stmt -> {
            statement = stmt;
            this.methodDeclaration = binaryExpr.findAncestor(MethodDeclaration.class).orElseThrow();
        });
        this.binaryExpr = binaryExpr;
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
        return statement.hashCode() + 109 * (methodDeclaration == null ? 11 : methodDeclaration.hashCode());
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
     * child nodes are in the BOTH_PATHS state.
     *
     * @param pathTaken The new path state.
     */
    public void setPathTaken(int pathTaken) {
        if (statement instanceof IfStmt) {
            this.pathTaken = pathTaken;
        } else {
            if ( (this.pathTaken == TRUE_PATH || this.pathTaken == FALSE_PATH)
                    && (pathTaken == TRUE_PATH || pathTaken == FALSE_PATH)) {
                this.pathTaken = BOTH_PATHS;
            }
            else {
                this.pathTaken = pathTaken;
            }
        }
    }


    /**
     * State transition method for the line of code.
     *
     * <p>For if/else statements, it updates the path state based on the current execution path. The
     * possible states are defined in UNTRAVELLED, FALSE_PATH, TRUE_PATH, and BOTH_PATHS.</p>
     *
     * <p>>When both paths in a branching statement has been traversed, we need to check its parent.
     * The current state transition may result in all the branching statements of the parent node
     * to have changed to the BOTH_PATHS state. When that happens the parent's state should change.</p>
     *
     * <p>When the child node is in the else block, then the parent's state needs to show that it is
     * the else path that has been taken. When the child is in the then block the parent needs to
     * show that the then path has been taken.</p>
     */
    public void transition() {
        // Step 1: Update current node's state
        if (isFalsePath()) {
            pathTaken |= TRUE_PATH;
        } else if (isTruePath()) {
            pathTaken |= FALSE_PATH;
        } else if (isUntravelled()) {
            pathTaken = FALSE_PATH;
        } else {
            throw new IllegalStateException("Already completed");
        }

        // Step 2: Update parent state if it exists and is an if statement
        if (parent != null && parent.statement instanceof IfStmt ifStmt) {
            List<LineOfCode> siblingsInBlock = parent.children.stream()
                .filter(child -> isNodeInSameBlock(ifStmt, child.statement, this.statement))
                .toList();

            // Only update parent if there are actually siblings and all are fully traversed
            if (!siblingsInBlock.isEmpty() && siblingsInBlock.stream().allMatch(LineOfCode::isFullyTravelled)) {
                if (isNodeInThenBlock(ifStmt, this.statement)) {
                    parent.pathTaken |= TRUE_PATH;
                } else if (isNodeInElseBlock(ifStmt, this.statement)) {
                    parent.pathTaken |= FALSE_PATH;
                }
            }
        }
    }


    private boolean isNodeInThenBlock(IfStmt ifStmt, Statement node) {
        return ConditionVisitor.isNodeInStatement(node, ifStmt.getThenStmt());
    }

    private boolean isNodeInElseBlock(IfStmt ifStmt, Statement node) {
        return ifStmt.getElseStmt()
                .map(elseStmt -> ConditionVisitor.isNodeInStatement(node, elseStmt))
                .orElse(false);
    }

    private boolean isNodeInSameBlock(IfStmt ifStmt, Statement node1, Statement node2) {
        return (isNodeInThenBlock(ifStmt, node1) && isNodeInThenBlock(ifStmt, node2)) ||
                (isNodeInElseBlock(ifStmt, node1) && isNodeInElseBlock(ifStmt, node2));
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
    public Set<Precondition> getPreconditions() {
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
     * Gets the parent `LineOfCode` node of this node.
     *
     * @return The parent node, or `null` if none exists.
     */
    public LineOfCode getParent() {
        return parent;
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
        return (pathTaken & FALSE_PATH) == FALSE_PATH && (pathTaken & TRUE_PATH) == 0;
    }

    /**
     * Checks if this line of code is in the true path state.
     *
     * @return `true` if in the true path state, otherwise `false`.
     */
    public boolean isTruePath() {
        return (pathTaken & TRUE_PATH) == TRUE_PATH && (pathTaken & FALSE_PATH) == 0;
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


    public boolean getResult() {
        return result;
    }

    public void setResult(boolean b) {
        this.result = b;
    }

    public List<LineOfCode> getChildren() {
        return children;
    }

    public Expression getConditionalExpression() {
        return binaryExpr;
    }
}

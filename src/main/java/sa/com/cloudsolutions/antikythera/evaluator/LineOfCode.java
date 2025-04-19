package sa.com.cloudsolutions.antikythera.evaluator;

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
     * The list of preconditions to be applied before executing this line to make it evaluate to true.
     */
    private final List<Precondition> truePreconditions = new ArrayList<>();

    /**
     * The list of preconditions to be applied before executing this line to make it evaluate to false.
     */
    private final List<Precondition> falsePreconditions = new ArrayList<>();



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
    public LineOfCode(Statement statement) {
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
        return statement.hashCode() + 109;
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
        this.pathTaken = pathTaken;
    }


    /**
     * Adds a precondition to this line of code which will make the condition evaluate to true
     *
     * @param precondition The precondition to add.
     */
    public void addTruePrecondition(Precondition precondition) {
        truePreconditions.add(precondition);
    }

    /**
     * Adds a precondition to this line of code which will make the condition evaluate to false
     *
     * @param precondition The precondition to add.
     */
    public void addFalsePrecondition(Precondition precondition) {
        falsePreconditions.add(precondition);
    }


    /**
     * Gets the preconditions to be applied before executing this line of code to make it evaluate to true.
     *
     * @return The list of true preconditions.
     */
    public List<Precondition> getTruePreconditions() {
        return truePreconditions;
    }

    /**
     * Gets the preconditions to be applied before executing this line of code to make it evaluate to false.
     *
     * @return The list of false preconditions.
     */
    public List<Precondition> getFalsePreconditions() {
        return falsePreconditions;
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
     * Sets the parent `LineOfCode` node for this node.
     *
     * @param parent The parent node to set.
     */
    public void setParent(LineOfCode parent) {
        this.parent = parent;
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
     * Checks if this node is a leaf node.
     *
     * @return `true` since all nodes are leaves in heap implementation.
     */
    public boolean isLeaf() {
        return true;
    }

    public void setResult(boolean b) {
        this.result = b;
    }

    public boolean getResult() {
        return result;
    }
}

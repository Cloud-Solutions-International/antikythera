package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import net.sf.jsqlparser.schema.Table;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a query from a JPARepository
 */
public class RepositoryQuery {

    /**
     * Represents a parameter in the query method.
     *
     * Lets get the normenclature sorted out. Though arguments and parameters are used interchangeably
     * they have a subtle difference.
     *     An argument is the value that is passed to a function when it is called.
     *     A parameter refers to the variable that is used in the function declaration
     */
    public static class QueryMethodParameter {
        /**
         * The name of the argument as defined in the respository function
         */
        Parameter parameter;

        private String placeHolderName;

        /**
         * Typically column names in SQL are camel_cased.
         * Those mappings will be saved here.
         */
        private String columnName;

        /**
         * True if this column was removed from the WHERE clause or GROUP BY.
         */
        boolean removed;

        private List<Integer> placeHolderId;

        /**
         * The position at which this parameter appears in the function parameters list.
         */
        int paramIndex;

        public QueryMethodParameter(Parameter parameter, int index) {
            this.parameter = parameter;
            parameter.getAnnotationByName("Param").ifPresent(a ->
                    setPlaceHolderName(a.asStringLiteralExpr().asString())
            );
            setPlaceHolderId(new ArrayList<>());
            this.paramIndex = index;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        /**
         * The name of the jdbc named parameter.
         * These can typicall be identified in the JPARepository function by the @Param annotation.
         * If they are used along side custom queries, the query will have a place holder that starts
         * with the : character and matches the name of the parameter.
         * example:
         *     select u from User u where u.firstname = :firstname or u.lastname = :lastname
         *     User findByLastnameOrFirstname(@Param("lastname") String lastname,
         *                                  @Param("firstname") String firstname);
         */
        public String getPlaceHolderName() {
            return placeHolderName;
        }

        public void setPlaceHolderName(String placeHolderName) {
            this.placeHolderName = placeHolderName;
        }

        /**
         * Tracks usage of this column with numeric jdbc query parameters
         * Unlike named parameters, numeric parameters can appear in multiple places. THough the same
         * number can be used in two different places it's common to see programmers use two different ids.
         * These numbers will start counting from 1, in keeping with the usual jdbc convention.
         */
        public List<Integer> getPlaceHolderId() {
            return placeHolderId;
        }

        public void setPlaceHolderId(List<Integer> placeHolderId) {
            this.placeHolderId = placeHolderId;
        }

        public Parameter getParameter() {
            return parameter;
        }
    }

    /**
     * Represents a argument in a call to a query method
     */
    public static class QueryMethodArgument {
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

    /**
     * Whether the query is native or not.
     * This is the value of the native flag to the @Query annotation.
     */
    boolean isNative;

    /**
     * The string representation of the query.
     */
    String query;

    /**
     * The result set from the last execution of this query if any
     */
    private ResultSet resultSet;

    /**
     * This is the list of parameters that are defined in the function signature
     */
    private List<QueryMethodParameter> methodParameters;
    /**
     * This is the list of arguments that are passed to the function when being called.
     */
    private List<QueryMethodArgument> methodArguments;

    public RepositoryQuery(String query, boolean isNative) {
        this.isNative = isNative;
        this.query = query;
        methodParameters = new ArrayList<>();
        methodArguments = new ArrayList<>();
    }

    public boolean isNative() {
        return isNative;
    }

    public String getQuery() {
        return query;
    }

    /**
     * Mark that the column was actually not used in the query filters.
     * @param column
     */
    public void remove(String column) {
        for (QueryMethodParameter p : methodParameters) {
            if (p != null && p.columnName != null && p.columnName.equals(column)) {
                p.removed = true;
            }
        }
    }

    public ResultSet getResultSet() {
        return resultSet;
    }

    public void setResultSet(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public List<QueryMethodParameter> getMethodParameters() {
        return methodParameters;
    }
    public List<QueryMethodArgument> getMethodArguments() {
        return methodArguments;
    }
}

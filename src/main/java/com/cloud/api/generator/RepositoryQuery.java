package com.cloud.api.generator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    static class QueryMethodParameter {
        /**
         * The name of the argument as defined in the respository function
         */
        Parameter parameter;
        /**
         * These numbers count from 0 upwards in the sql we will have to add 1
         * because jdbc coutns from 1 onwards
         */
        int placeHolderIndex;
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
        String placeHolderName;

        /**
         * Typically column names in SQL are camel_cased.
         * Those mappings will be saved here.
         */
        String columnName;

        public QueryMethodParameter(Parameter parameter, int index) {
            this.parameter = parameter;
            this.columnName = RepositoryParser.camelToSnake(parameter.getName().toString());
            parameter.getAnnotationByName("@Param").ifPresent(a -> {
                placeHolderName = a.asStringLiteralExpr().asString();
            });
            placeHolderIndex = index;
        }
    }

    /**
     * Represents a argument in a call to a query method
     */
    static class QueryMethodArgument {
        /**
         * The name of the argument as defined in the respository function
         */
        Expression argument;

        int index;

        public QueryMethodArgument(Expression argument, int index) {
            this.argument = argument;
            this.index = index;
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
     * The list of columns that were removed from the query where clause or grouping.
     */
    private List<String> removed;

    /**
     * Keeps track of what columns are being used in where clauses.
     * For between conditions two place holders can be mapped to the same column hence the
     * reason that we have a list of strings.
     */

    private Map<String, List<String>> placeHolders;

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
        placeHolders = new HashMap<>();
    }

    public boolean isNative() {
        return isNative;
    }

    public String getQuery() {
        return query;
    }

    public void setRemoved(List<String> removed) {
        this.removed = removed;
    }

    /**
     * Get a list of filters that were removed from the where clause
     * @return
     */
    public List<String> getRemoved() {
        return removed;
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

    /**
     * Get the place holders for the query
     * @return
     */
    public Map<String, List<String>> getPlaceHolders() {
        return placeHolders;
    }
}

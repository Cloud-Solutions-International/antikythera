package com.cloud.api.generator;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a query from a JPARepository
 */
public class RepositoryQuery {
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

    private Map<String, String> placeHolders;
    /**
     * Represents the mapping of the request or path parameters to the query parameters.
     *
     * The key is the name of the parameter in the query.
     */
    private Map<String, String> parameterMap;

    public RepositoryQuery(String query, boolean isNative) {
        this.isNative = isNative;
        this.query = query;
        parameterMap = new HashMap<>();
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

    public List<String> getRemoved() {
        return removed;
    }


    public ResultSet getResultSet() {
        return resultSet;
    }

    public void setResultSet(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public Map<String, String> getParameterMap() {
        return parameterMap;
    }

    public Map<String, String> getPlaceHolders() {
        return placeHolders;
    }
}

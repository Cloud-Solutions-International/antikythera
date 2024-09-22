package com.cloud.api.generator;

import net.sf.jsqlparser.expression.Expression;

import java.sql.ResultSet;
import java.util.List;

/**
 * Represents a query from a JPARepository
 */
public class RepositoryQuery {
    /**
     * Whether the query is native or not.
     * This is the value of the native flag to the @Query annotation.
     */
    boolean isNative;
    String query;


    /**
     * The result set from the last execution of this query if any
     */
    private ResultSet resultSet;


    /**
     * The list of columns that were removed from the query where clause or grouping.
     */
    private List<String> removed;

    public RepositoryQuery(String query, boolean isNative) {
        this.isNative = isNative;
        this.query = query;
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
}

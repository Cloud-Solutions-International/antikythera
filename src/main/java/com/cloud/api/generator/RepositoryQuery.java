package com.cloud.api.generator;

import net.sf.jsqlparser.expression.Expression;

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
     * The list of columns that were removed from the query where clause or grouping.
     */
    private List<Expression> removed;

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

    public void setRemoved(List<Expression> removed) {
        this.removed = removed;
    }
}

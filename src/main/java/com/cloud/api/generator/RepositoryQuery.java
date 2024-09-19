package com.cloud.api.generator;

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

}

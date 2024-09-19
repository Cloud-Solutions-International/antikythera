package com.cloud.api.generator;

public class RepositoryQuery {
    boolean isNative;
    String query;

    public RepositoryQuery(String query)
    {
        this.query = query;
    }

    public RepositoryQuery(String query, boolean isNative) {
        this.isNative = isNative;
        this.query = query;
    }

    public boolean isNative() {
        return isNative;
    }

    public void setNative(boolean aNative) {
        isNative = aNative;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}

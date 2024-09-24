package com.cloud.api.generator;

import io.restassured.http.Headers;

import java.util.HashMap;
import java.util.Map;

public class ControllerRequest {
    Map<String, String> queryParameters;
    String path;

    public ControllerRequest() {
        this.queryParameters = new HashMap<>();
    }

    public void addQueryParameter(String key, String value) {
        queryParameters.put(key, value);
    }

    public Map<String, String> getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(Map<String, String> queryParameters) {
        this.queryParameters = queryParameters;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}

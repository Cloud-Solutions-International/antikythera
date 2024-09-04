package com.cloud.api.tests;

import com.cloud.api.base.APIBaseClass;
import com.cloud.api.base.BaseTestObject;
import com.cloud.api.constants.StaticHeaders;
import com.cloud.api.exceptions.InvalidJsonStructureException;
import com.cloud.api.exceptions.ResponseTypeConversionException;
import com.cloud.api.rest.APIRequester;
import com.cloud.core.reporting.ExtentLogger;
import io.restassured.RestAssured;
import io.restassured.http.Headers;
import io.restassured.http.Method;
import io.restassured.response.Response;

import java.util.concurrent.TimeUnit;

public class TestHelper extends BaseTestObject  {
    private String baseURI;

    public void serviceSetUp()  {
        baseURI = urlData.get("PharmacyServices.host");
        APIBaseClass.headers = new Headers(
                StaticHeaders.CONTENT_TYPE_JSON,
                StaticHeaders.X_GROUP,
                StaticHeaders.X_HOSPITAL,
                StaticHeaders.X_USER,
                StaticHeaders.X_LOCATION,
                StaticHeaders.ACCEPT_ALL,
                authorizationHeader
        );
    }

    protected Response checkTiming(Response response) {
        APIRequester.authentication = APIRequester.DEFAULT_AUTH;
        ExtentLogger.info("API took " + response.getTimeIn(TimeUnit.MILLISECONDS) + " milliseconds");
        if (checkApiPerformance) {
            softAssert.assertTrue(response.getTimeIn(TimeUnit.MILLISECONDS) <= (long)apiPerformanceSla, "API didn't met the performance SLA of " + apiPerformanceSla + " milliseconds");
        }

        return response;
    }

    protected Response makePost(String body, Headers headers, String relativeUrl)  {
        APIRequester.setBaseURI(baseURI);
        APIRequester.setBasePath(relativeUrl);

        Response response = RestAssured.given().relaxedHTTPSValidation().headers(headers).body(body).when().request(Method.POST);
        APIRequester.resetBasePath();
        APIRequester.resetBaseURI();

        return response;
    }

    protected Response makeGet( Headers headers, String relativeUrl)  {

        APIRequester.setBaseURI(baseURI);
        APIRequester.setBasePath(relativeUrl);

        Response response = RestAssured.given().relaxedHTTPSValidation().headers(headers).when().request(Method.GET);
        APIRequester.resetBasePath();
        APIRequester.resetBaseURI();

        return response;
    }
}
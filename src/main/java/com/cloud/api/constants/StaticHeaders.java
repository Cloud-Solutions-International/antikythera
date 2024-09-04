package com.cloud.api.constants;

import io.restassured.http.Header;

public interface StaticHeaders {

    Header CONTENT_TYPE_JSON = new Header("Content-Type", "application/json");
    Header X_GROUP = new Header("x-group", "58");
    Header X_HOSPITAL = new Header("x-hospital", "59");
    Header X_USER = new Header("x-user", "5550");
    Header ACCEPT_ALL = new Header("Accept", "*/*");
    Header X_LOCATION = new Header("x-location", "31042");
    Header X_PHRLOCATION = new Header("x-location", "23050");
    Header X_PHRUSER = new Header("x-user", "3091");
    Header X_MODULE = new Header("x-module", "phr");

}

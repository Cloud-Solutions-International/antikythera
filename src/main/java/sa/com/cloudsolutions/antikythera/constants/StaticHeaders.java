package sa.com.cloudsolutions.antikythera.constants;

import io.restassured.http.Header;

public interface StaticHeaders {

    Header CONTENT_TYPE_JSON = new Header("Content-Type", "application/json");
    Header X_GROUP = new Header("x-group", "58");
    Header X_HOSPITAL = new Header("x-hospital", "59");
    Header X_USER = new Header("x-user", "5550");
    Header ACCEPT_ALL = new Header("Accept", "*/*");
    Header X_LOCATION = new Header("x-location", "31042");

}

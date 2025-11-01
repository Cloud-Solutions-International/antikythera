package sa.com.cloudsolutions.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.testng.annotations.BeforeClass;
import com.fasterxml.jackson.core.JsonProcessingException;

public class ComplexControllerTest extends TestHelper {

    @BeforeClass()
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    ObjectMapper objectMapper;

    /**
     * Method under test: ComplexController.list()
     * Argument generator : NullArgumentGenerator
     * Author : Antikythera
     */
    @Test()
    @TestCaseType(types = {TestType.BVT, TestType.REGRESSION})
    void listTest_B() throws JsonProcessingException {
        Response response = makeGet(headers, "/entities");
        Assert.assertEquals(500, response.getStatusCode());
    }

    /**
     * Method under test: ComplexController.list()
     * Argument generator : DummyArgumentGenerator
     * Author : Antikythera
     */
    @Test()
    @TestCaseType(types = {TestType.BVT, TestType.REGRESSION})
    void listTest_D() throws JsonProcessingException {
        Response response = makeGet(headers, "/entities");
        Assert.assertEquals(500, response.getStatusCode());
    }

    /**
     * Method under test: ComplexController.list()
     * Argument generator : DatabaseArgumentGenerator
     * Author : Antikythera
     */
    @Test()
    @TestCaseType(types = {TestType.BVT, TestType.REGRESSION})
    void listTest_F() throws JsonProcessingException {
        Response response = makeGet(headers, "/entities");
        Assert.assertEquals(500, response.getStatusCode());
    }
}

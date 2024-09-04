package com.cloud.api.base;

import com.cloud.api.constants.DataSets;
import com.cloud.core.config.enums.ConfigKeys;
import com.cloud.core.testdataprovider.enums.DataProviderType;
import com.cloud.core.testdataprovider.utils.DataProviderUtil;
import com.csi.support.api.dataService.TokenDataProvider;
import io.restassured.http.Header;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.asserts.SoftAssert;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.stream.Collectors;

public abstract class BaseTestObject extends APIBaseTest {

    public static HashMap<String, String> loginData = null;
    public static HashMap<String, String> urlData = null;
    public static HashMap<String, String> phrData = null;
    public static HashMap<String, String> phrHeaders = null;
    public static Header authorizationHeader = null;

    protected static SoftAssert softAssert;

    @BeforeSuite
    public void loadEnvironmentData() throws Exception {
        String path = config.getValue(ConfigKeys.KEY_DATA_FILE_PATH.getKey()) + config.getValue(ConfigKeys.KEY_ENVIRONMENT.getKey()).toLowerCase();
        DataProviderUtil.setDataFile(path, DataProviderType.PROPERTY);
        DataProviderUtil.loadData(EnumSet.allOf(DataSets.class).stream().map(DataSets::name).collect(Collectors.toList()));
        loginData = DataProviderUtil.getDataSet("Data", DataSets.Login.name());
        urlData = DataProviderUtil.getDataSet("Data", DataSets.Url.name());
        phrData = DataProviderUtil.getDataSet("Data", DataSets.PHRData.name());
        phrHeaders = DataProviderUtil.getDataSet("Data", DataSets.phrHeaders.name());

        TokenDataProvider.setTokenDataService(urlData.get("tokenServices.host"));
        authorizationHeader = new Header("authorization", TokenDataProvider.getInstance().tokenGenerate(loginData.get("username"),loginData.get("password")));

    }

    @BeforeMethod
    public void beforeMethod() throws Exception {

        try {
            softAssert = new SoftAssert();

//            String bearerToken = TokenDataProvider.getInstance().tokenGenerate(loginData.get("username"),loginData.get("password"));
//            List<Header> updatedHeaders = new ArrayList<>();
//            headers.asList().forEach(header -> {
//                if (!header.getName().equalsIgnoreCase("authorization"))
//                    updatedHeaders.add(header);
//            });
//            updatedHeaders.add(new Header("authorization", bearerToken));
//            headers = new Headers(updatedHeaders);

        } catch (Exception e) {
            throw new Exception("Failed : beforeMethod()" + e.getLocalizedMessage());
        }
    }

}

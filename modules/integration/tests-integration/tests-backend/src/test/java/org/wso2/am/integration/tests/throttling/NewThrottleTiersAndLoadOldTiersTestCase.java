/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*
*/
package org.wso2.am.integration.tests.throttling;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.annotations.*;
import org.wso2.am.admin.clients.registry.ResourceAdminServiceClient;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.*;
import org.wso2.am.integration.test.utils.clients.APIPublisherRestClient;
import org.wso2.am.integration.test.utils.clients.APIStoreRestClient;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.automation.engine.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.engine.annotations.SetEnvironment;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.test.utils.common.TestConfigurationProvider;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceExceptionException;

import javax.ws.rs.core.Response;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Test case for New Tier groups. After the New Tier groups Old Tier groups will be replace in registory for further
 * Test cases.
 *
 * Note: Always run this Test case first, because most of the test are depend on this
 */
@SetEnvironment(executionEnvironments = {ExecutionEnvironment.STANDALONE})
public class NewThrottleTiersAndLoadOldTiersTestCase extends APIMIntegrationBaseTest {

    private static final Log log = LogFactory.getLog(NewThrottleTiersAndLoadOldTiersTestCase.class);
    private final String API_TAGS = "testTag1, testTag2, testTag3";
    private final String API_DESCRIPTION = "This is test API create by API manager integration test";
    private final String API_VERSION_1_0_0 = "1.0.0";
    private final String TIER_XML_REG_CONFIG_LOCATION = "/_system/governance/apimgt/applicationdata/tiers.xml";
    private final String TIER_XML_REG_CONFIG_APP_LOCATION = "/_system/governance/apimgt/applicationdata/app-tiers.xml";
    private final String TIER_XML_REG_CONFIG_RES_LOCATION = "/_system/governance/apimgt/applicationdata/res-tiers.xml";
    private final String TIER_MANAGE_PAGE_TIER_GOLD = "{ \"value\": \"Gold\", \"text\": \"Gold\" }";
    private final String TIER_MANAGE_PAGE_TIER_SILVER = "<option value=\"Silver\" >Silver</option>";

    private final String API_END_POINT_POSTFIX_URL = "jaxrs_basic/services/customers/customerservice/";
    private final String TIER_MANAGE_PAGE_RESOURCE_TIER_ULTIMATE = "\"value\": \"Ultimate\"";
    private final String TIER_MANAGE_PAGE_RESOURCE_TIER_SILVER = "<option value=\"Silver\" >Silver</option>";
    private final String TIER_MANAGE_PAGE_APPLICATION_TIER_GOLD = "<option value=\"Gold\" >";
    private final String TIER_MANAGE_PAGE_APPLICATION_TIER_LARGE = "<option value=\"Large\" >";

    private final int HTTP_RESPONSE_CODE_OK = Response.Status.OK.getStatusCode();
    private final int HTTP_RESPONSE_CODE_TOO_MANY_REQUESTS = 429;

    private final String NEW_TIER_TEST_API_CONTEXT = "new_Tiers_api";
    private final String New_Tier_TEST_API = "new_Tiers_api_context";

    private final String API_TIER_TEST_API = "test_APITier_api";
    private final String API_TIER_TEST_APP = "test_APITier_app";
    private final String API_TIER_TEST_API_CONTEXT = "test_APITier_api_context";

    private final String APP_TIER_TEST_API = "test_APPTier_api";
    private final String APP_TIER_TEST_API_CONTEXT = "test_APPTier_api_context";
    private final String APP_TIER_TEST_APP = "test_APPTier_app";

    private final String RES_TIER_TEST_API = "test_RESTier_api";
    private final String RES_TIER_TEST_API_CONTEXT = "test_RESTier_api_context";
    private final String RES_TIER_TEST_APP = "test_RESTier_app_name";

    private final String OLD_TIER_TEST_API = "old_Tier_api";
    private final String OLD_TIER_TEST_API_CONTEXT = "old_Tier_api_context";

    private String apiEndPointUrl;
    private String providerName;
    private String newTiersXML;
    private ResourceAdminServiceClient resourceAdminServiceClient;
    private APIPublisherRestClient apiPublisherClientUser;
    private APIStoreRestClient apiStoreClientUser;

    private APIIdentifier APITierTEST_ID;
    private APIIdentifier APPTierTEST_ID;
    private APIIdentifier RESTierTEST_ID;
    private APIIdentifier Before_TierTEST_ID;
    private APIIdentifier After_TierTEST_ID;

    @Factory(dataProvider = "userModeDataProvider")
    public NewThrottleTiersAndLoadOldTiersTestCase(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @DataProvider
    public static Object[][] userModeDataProvider() {
        return new Object[][] { new Object[] { TestUserMode.SUPER_TENANT_ADMIN },
                new Object[] { TestUserMode.TENANT_ADMIN }, };
    }

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        System.out.println(userMode);
        super.init(userMode);
        apiEndPointUrl = getGatewayURLHttp() + API_END_POINT_POSTFIX_URL;
        providerName = user.getUserName();

        String publisherURLHttp = getPublisherURLHttp();
        String storeURLHttp = getStoreURLHttp();
        apiPublisherClientUser = new APIPublisherRestClient(publisherURLHttp);
        apiStoreClientUser = new APIStoreRestClient(storeURLHttp);

        //Login to API Publisher with  admin
        apiPublisherClientUser.login(user.getUserName(), user.getPassword());

        //Login to API Store with  admin
        apiStoreClientUser.login(user.getUserName(), user.getPassword());

        //        apiIdentifier = new APIIdentifier(providerName, API_NAME, API_VERSION_1_0_0);
        String artifactsLocation = TestConfigurationProvider.getResourceLocation() + File.separator + "artifacts" +
                File.separator + "AM" + File.separator + "lifecycletest" + File.separator + "default-tiers.xml";
        resourceAdminServiceClient = new ResourceAdminServiceClient(publisherContext.getContextUrls().getBackEndUrl(),
                createSession(publisherContext));
        newTiersXML = readFile(artifactsLocation);

    }

    @Test(groups = { "wso2.am" }, description = "Test availability of tiers in API Manage Page before change tiers")
    public void testAvailabilityOfTiersInAPIManagePageBeforeChangeTiersXML()
            throws APIManagerIntegrationTestException, RemoteException, ResourceAdminServiceExceptionException,
            MalformedURLException {

        Before_TierTEST_ID = new APIIdentifier(providerName, New_Tier_TEST_API, API_VERSION_1_0_0);
        APICreationRequestBean apiRequestBean = new APICreationRequestBean(New_Tier_TEST_API, NEW_TIER_TEST_API_CONTEXT,
                API_VERSION_1_0_0, providerName, new URL(apiEndPointUrl));
        apiRequestBean.setTags(API_TAGS);
        apiRequestBean.setDescription(API_DESCRIPTION);

        //publish a API
        createAndPublishAPI(Before_TierTEST_ID, apiRequestBean, apiPublisherClientUser, false);

        //test the responses to check new tiers are available
        HttpResponse tierManagePageHttpResponse = apiPublisherClientUser
                .getAPIManagePage(New_Tier_TEST_API, providerName, API_VERSION_1_0_0);
        assertEquals(tierManagePageHttpResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Response code mismatched when invoke to get Tier Permission Page");
        assertTrue(tierManagePageHttpResponse.getData().contains(TIER_MANAGE_PAGE_TIER_SILVER),
                "default tier Silver is not available in Tier Permission page before  add new tear in tiers.xml");
        assertTrue(tierManagePageHttpResponse.getData().contains(TIER_MANAGE_PAGE_RESOURCE_TIER_ULTIMATE),
                "default Resource tier Ultimate is not available in Tier Permission page before add new tear in "
                        + "tiers.xml");

        HttpResponse applicationPageHttpResponse = apiStoreClientUser.getApplicationPage();
        assertEquals(applicationPageHttpResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Response code mismatched when invoke to get Tier Permission Page");
        assertTrue(applicationPageHttpResponse.getData().contains(TIER_MANAGE_PAGE_APPLICATION_TIER_LARGE),
                "default tier Large is not available in Tier Permission page before add new tear in tiers.xml");

    }

    @Test(groups = {
            "wso2.am" }, description = "Test API Tier availability and it's throttle limit",
            dependsOnMethods = "testAvailabilityOfTiersInAPIManagePageBeforeChangeTiersXML")
    public void testAPITiers() throws IOException, APIManagerIntegrationTestException, XPathExpressionException {

        APICreationRequestBean apiRequestBean = new APICreationRequestBean(API_TIER_TEST_API, API_TIER_TEST_API_CONTEXT,
                API_VERSION_1_0_0, providerName, new URL(apiEndPointUrl));
        apiRequestBean.setTags(API_TAGS);
        apiRequestBean.setDescription(API_DESCRIPTION);

        //create and publish a api
        APITierTEST_ID = new APIIdentifier(providerName, API_TIER_TEST_API, API_VERSION_1_0_0);
        createAndPublishAPI(APITierTEST_ID, apiRequestBean, apiPublisherClientUser, false);

        //create an application
        apiStoreClientUser
                .addApplication(API_TIER_TEST_APP, APIMIntegrationConstants.APPLICATION_TIER.UNLIMITED, "", "");
        HttpResponse subscribeResponse = subscribeToAPI(APITierTEST_ID, API_TIER_TEST_APP, apiStoreClientUser);
        assertEquals(subscribeResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Subscribe of API request not successful");

        String accessToken = generateApplicationKeys(apiStoreClientUser, API_TIER_TEST_APP).getAccessToken();
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("accept", "text/xml");
        requestHeaders.put("Authorization", "Bearer " + accessToken);

        //invoke the api and count the success count
        int cnt = 0;
        for (int i = 0; i < APIMIntegrationConstants.API_TIER.GOLD_LIMIT + 10; i++) {
            //Invoke  api
            HttpResponse invokeResponse = HttpRequestUtil
                    .doGet(getAPIInvocationURLHttp(API_TIER_TEST_API_CONTEXT, API_VERSION_1_0_0) + "/" +
                            "customers/123", requestHeaders);
            if (invokeResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK) {
                cnt++;
            } else if (invokeResponse.getResponseCode() == HTTP_RESPONSE_CODE_TOO_MANY_REQUESTS) {
                break;
            }

        }
        assertEquals(cnt, APIMIntegrationConstants.API_TIER.GOLD_LIMIT, "Error number of throttle request count");

    }

    @Test(groups = {
            "wso2.am" }, description = "Test Application Tier availability and it's throttle limit",
            dependsOnMethods = "testAPITiers")
    public void testApplicationTiers()
            throws IOException, APIManagerIntegrationTestException, XPathExpressionException {

        APICreationRequestBean apiRequestBean = new APICreationRequestBean(APP_TIER_TEST_API, APP_TIER_TEST_API_CONTEXT,
                API_VERSION_1_0_0, providerName, new URL(apiEndPointUrl));
        apiRequestBean.setTags(API_TAGS);
        apiRequestBean.setDescription(API_DESCRIPTION);

        //create and publish a api
        APPTierTEST_ID = new APIIdentifier(providerName, APP_TIER_TEST_API, API_VERSION_1_0_0);
        createAndPublishAPI(APPTierTEST_ID, apiRequestBean, apiPublisherClientUser, false);

        //create an application
        apiStoreClientUser.addApplication(APP_TIER_TEST_APP, APIMIntegrationConstants.APPLICATION_TIER.MEDIUM, "", "");
        HttpResponse subscribeResponse = subscribeToAPI(APPTierTEST_ID, APP_TIER_TEST_APP, apiStoreClientUser);
        assertEquals(subscribeResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Subscribe of API request not successful");

        String accessToken = generateApplicationKeys(apiStoreClientUser, APP_TIER_TEST_APP).getAccessToken();
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("accept", "text/xml");
        requestHeaders.put("Authorization", "Bearer " + accessToken);

        //invoke the api and count the success count
        int cnt = 0;
        for (int i = 0; i < APIMIntegrationConstants.APPLICATION_TIER.MEDIUM_LIMIT + 10; i++) {
            //Invoke  api
            HttpResponse invokeResponse = HttpRequestUtil
                    .doGet(getAPIInvocationURLHttp(APP_TIER_TEST_API_CONTEXT, API_VERSION_1_0_0) + "/" +
                            "customers/123", requestHeaders);
            if (invokeResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK) {
                cnt++;
            } else if (invokeResponse.getResponseCode() == HTTP_RESPONSE_CODE_TOO_MANY_REQUESTS) {
                break;
            }

        }
        assertEquals(cnt, APIMIntegrationConstants.APPLICATION_TIER.MEDIUM_LIMIT,
                "Error number of throttle request count");

    }

    @Test(groups = {
            "wso2.am" }, description = "Test Resource Tier availability and it's throttle limit",
            dependsOnMethods = "testApplicationTiers")
    public void testResourceTiers() throws IOException, APIManagerIntegrationTestException, XPathExpressionException {

        APICreationRequestBean apiRequestBean = new APICreationRequestBean(RES_TIER_TEST_API, RES_TIER_TEST_API_CONTEXT,
                API_VERSION_1_0_0, providerName, APIMIntegrationConstants.API_TIER.GOLD,
                APIMIntegrationConstants.RESOURCE_TIER.PLUS, new URL(apiEndPointUrl));
        apiRequestBean.setTags(API_TAGS);
        apiRequestBean.setDescription(API_DESCRIPTION);

        //create and publish a api
        RESTierTEST_ID = new APIIdentifier(providerName, RES_TIER_TEST_API, API_VERSION_1_0_0);
        createAndPublishAPI(RESTierTEST_ID, apiRequestBean, apiPublisherClientUser, false);

        //create an application
        apiStoreClientUser.addApplication(RES_TIER_TEST_APP, APIMIntegrationConstants.RESOURCE_TIER.UNLIMITED, "", "");
        HttpResponse subscribeResponse = subscribeToAPI(RESTierTEST_ID, RES_TIER_TEST_APP, apiStoreClientUser);
        assertEquals(subscribeResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Subscribe of API request not successful");

        String accessToken = generateApplicationKeys(apiStoreClientUser, RES_TIER_TEST_APP).getAccessToken();
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("accept", "text/xml");
        requestHeaders.put("Authorization", "Bearer " + accessToken);

        //invoke the api and count the success count
        int cnt = 0;
        for (int i = 0; i < APIMIntegrationConstants.RESOURCE_TIER.PLUS_LIMIT + 10; i++) {
            //Invoke  api
            HttpResponse invokeResponse = HttpRequestUtil
                    .doGet(getAPIInvocationURLHttp(RES_TIER_TEST_API_CONTEXT, API_VERSION_1_0_0) + "/" +
                            "customers/123", requestHeaders);
            if (invokeResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK) {
                cnt++;
            } else if (invokeResponse.getResponseCode() == HTTP_RESPONSE_CODE_TOO_MANY_REQUESTS) {
                break;
            }

        }
        assertEquals(cnt, APIMIntegrationConstants.RESOURCE_TIER.PLUS_LIMIT, "Error number of throttle request count");

    }

    @Test(groups = {
            "wso2.am" }, description = "Test availability of tiers in API Manage Page after change tiers",
            dependsOnMethods = "testResourceTiers")
    public void testAvailabilityOfTiersInAPIManagePageAfterChangeTiersXML() throws Exception {

        //remove created apis
        clean();

        //add old tiers groups
        resourceAdminServiceClient.updateTextContent(TIER_XML_REG_CONFIG_LOCATION, newTiersXML);
        resourceAdminServiceClient.updateTextContent(TIER_XML_REG_CONFIG_APP_LOCATION, newTiersXML);
        resourceAdminServiceClient.updateTextContent(TIER_XML_REG_CONFIG_RES_LOCATION, newTiersXML);

        APICreationRequestBean apiRequestBean = new APICreationRequestBean(OLD_TIER_TEST_API, OLD_TIER_TEST_API_CONTEXT,
                API_VERSION_1_0_0, providerName, new URL(apiEndPointUrl));
        apiRequestBean.setTags(API_TAGS);
        apiRequestBean.setDescription(API_DESCRIPTION);

        //create and publish a api
        After_TierTEST_ID = new APIIdentifier(providerName, OLD_TIER_TEST_API, API_VERSION_1_0_0);
        createAndPublishAPI(After_TierTEST_ID, apiRequestBean, apiPublisherClientUser, false);

        //check the availability of old throttle tiers
        HttpResponse tierManagePageHttpResponse = apiPublisherClientUser
                .getAPIManagePage(OLD_TIER_TEST_API, providerName, API_VERSION_1_0_0);
        assertEquals(tierManagePageHttpResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Response code mismatched when invoke to get Tier Permission Page");
        assertTrue(tierManagePageHttpResponse.getData().contains(TIER_MANAGE_PAGE_TIER_GOLD),
                "default tier Gold is not available in Tier Permission page before  add new tear in tiers.xml");
        assertTrue(tierManagePageHttpResponse.getData().contains(TIER_MANAGE_PAGE_RESOURCE_TIER_SILVER),
                "default Resource tier Silver is not available in Tier Permission page before  add new tear in "
                        + "tiers.xml");

        HttpResponse applicationPageHttpResponse = apiStoreClientUser.getApplicationPage();
        assertEquals(applicationPageHttpResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Response code mismatched when invoke to get Tier Permission Page");
        assertTrue(applicationPageHttpResponse.getData().contains(TIER_MANAGE_PAGE_APPLICATION_TIER_GOLD),
                "default tier Gold is not available in Tier Permission page before  add new tear in tiers.xml");
    }

    private void clean() throws Exception {
        //remove the Apis
        apiStoreClientUser.removeApplication(API_TIER_TEST_APP);
        apiStoreClientUser.removeApplication(APP_TIER_TEST_APP);
        apiStoreClientUser.removeApplication(RES_TIER_TEST_APP);

        //remove applications
        deleteAPI(APITierTEST_ID, apiPublisherClientUser);
        deleteAPI(APPTierTEST_ID, apiPublisherClientUser);
        deleteAPI(RESTierTEST_ID, apiPublisherClientUser);
        deleteAPI(Before_TierTEST_ID, apiPublisherClientUser);
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {

        deleteAPI(After_TierTEST_ID, apiPublisherClientUser);

        super.cleanUp();
    }

    /**
     * Read the file content and return the content as String.
     *
     * @param fileLocation - Location of the file.
     * @return String - content of the file.
     * @throws APIManagerIntegrationTestException - exception throws when reading the file.
     */
    protected String readFile(String fileLocation) throws APIManagerIntegrationTestException {
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(new File(fileLocation)));
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (IOException ioE) {
            throw new APIManagerIntegrationTestException("IOException when reading the file from:" + fileLocation, ioE);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    log.warn("Error when closing the buffer reade which used to reed the file:" + fileLocation +
                            ". Error:" + e.getMessage());
                }
            }
        }
    }

    protected HttpResponse subscribeToAPI(APIIdentifier apiIdentifier, String applicationName,
            APIStoreRestClient storeRestClient) throws APIManagerIntegrationTestException {
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest(apiIdentifier.getApiName(),
                apiIdentifier.getProviderName());
        subscriptionRequest.setVersion(apiIdentifier.getVersion());
        subscriptionRequest.setApplicationName(applicationName);
        if ((apiIdentifier.getTier() != null) && (!apiIdentifier.getTier().equals(""))) {
            subscriptionRequest.setTier(apiIdentifier.getTier());
        }
        return storeRestClient.subscribeToAPI(subscriptionRequest);
    }

    /**
     * Generate the access token
     *
     * @param storeRestClient - Instance of storeRestClient
     * @param applicationName - Application name
     * @return ApplicationKeyBean - ApplicationKeyBean that contains access token, consumer key and consumer secret
     * @throws APIManagerIntegrationTestException - Exception throws by the  method call of generateApplicationKey()
     *                                            in APIStoreRestClient.java
     */

    protected ApplicationKeyBean generateApplicationKeys(APIStoreRestClient storeRestClient, String applicationName)
            throws APIManagerIntegrationTestException {

        try {
            ApplicationKeyBean applicationKeyBean = new ApplicationKeyBean();
            APPKeyRequestGenerator generateAppKeyRequest = new APPKeyRequestGenerator(applicationName);
            String responseString = storeRestClient.generateApplicationKey(generateAppKeyRequest).getData();
            JSONObject response = new JSONObject(responseString);

            applicationKeyBean.setAccessToken(response.getJSONObject("data").getJSONObject("key").
                    get("accessToken").toString());
            applicationKeyBean.setConsumerKey(response.getJSONObject("data").getJSONObject("key").
                    get("consumerKey").toString());
            applicationKeyBean.setConsumerSecret(response.getJSONObject("data").getJSONObject("key").
                    get("consumerSecret").toString());
            return applicationKeyBean;
        } catch (Exception e) {
            throw new APIManagerIntegrationTestException("Exception when get access token", e);
        }

    }

    /**
     * Delete a API from API Publisher.
     *
     * @param apiIdentifier       - Instance of APIIdentifier object  that include the  API Name, API Version and
     *                            API Provider.
     * @param publisherRestClient - Instance of APIPublisherRestClient.
     * @throws APIManagerIntegrationTestException - Exception throws by the method call of deleteApi() in
     *                                            APIPublisherRestClient.java.
     */
    protected void deleteAPI(APIIdentifier apiIdentifier, APIPublisherRestClient publisherRestClient)
            throws APIManagerIntegrationTestException {

        HttpResponse deleteHTTPResponse = publisherRestClient
                .deleteAPI(apiIdentifier.getApiName(), apiIdentifier.getVersion(), apiIdentifier.getProviderName());
        if (!(deleteHTTPResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK && getValueFromJSON(deleteHTTPResponse,
                "error").equals("false"))) {
            throw new APIManagerIntegrationTestException("Error in API Deletion." +
                    getAPIIdentifierString(apiIdentifier) + " API Context :" + deleteHTTPResponse +
                    "Response Code:" + deleteHTTPResponse.getResponseCode() +
                    " Response Data :" + deleteHTTPResponse.getData());
        }
    }

    /**
     * Retrieve  the value from JSON object bu using the key.
     *
     * @param httpResponse - Response that containing the JSON object in it response data.
     * @param key          - key of the JSON value the need to retrieve.
     * @return String - The value of provided key as a String
     * @throws APIManagerIntegrationTestException - Exception throws when resolving the JSON object in the HTTP response
     */
    protected String getValueFromJSON(HttpResponse httpResponse, String key) throws APIManagerIntegrationTestException {
        try {
            JSONObject jsonObject = new JSONObject(httpResponse.getData());
            return jsonObject.get(key).toString();
        } catch (JSONException e) {
            throw new APIManagerIntegrationTestException(
                    "Exception thrown when resolving the JSON object in the HTTP " + "response ", e);
        }
    }

    /**
     * Create and publish a API.
     *
     * @param apiIdentifier           - Instance of APIIdentifier object  that include the  API Name,
     *                                API Version and API Provider
     * @param apiCreationRequestBean  - Instance of APICreationRequestBean with all needed API information
     * @param publisherRestClient     - Instance of APIPublisherRestClient
     * @param isRequireReSubscription - If publish with re-subscription required option true else false.
     * @throws APIManagerIntegrationTestException - Exception throws by API create and publish activities.
     */
    public void createAndPublishAPI(APIIdentifier apiIdentifier, APICreationRequestBean apiCreationRequestBean,
            APIPublisherRestClient publisherRestClient, boolean isRequireReSubscription)
            throws APIManagerIntegrationTestException {
        //Create the API
        HttpResponse createAPIResponse = publisherRestClient.addAPI(apiCreationRequestBean);
        if (createAPIResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK && getValueFromJSON(createAPIResponse, "error")
                .equals("false")) {
            log.info("API Created :" + getAPIIdentifierString(apiIdentifier));
            //Publish the API
            HttpResponse publishAPIResponse = publishAPI(apiIdentifier, publisherRestClient, isRequireReSubscription);
            if (!(publishAPIResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK && verifyAPIStatusChange(
                    publishAPIResponse, APILifeCycleState.CREATED, APILifeCycleState.PUBLISHED))) {
                throw new APIManagerIntegrationTestException("Error in API Publishing" +
                        getAPIIdentifierString(apiIdentifier) + "Response Code:" + publishAPIResponse.getResponseCode()
                        +
                        " Response Data :" + publishAPIResponse.getData());
            }
            log.info("API Published :" + getAPIIdentifierString(apiIdentifier));
        } else {
            throw new APIManagerIntegrationTestException("Error in API Creation." +
                    getAPIIdentifierString(apiIdentifier) +
                    "Response Code:" + createAPIResponse.getResponseCode() +
                    " Response Data :" + createAPIResponse.getData());
        }
    }

    /**
     * Return a String with combining the value of API Name,API Version and API Provider Name as key:value format
     *
     * @param apiIdentifier - Instance of APIIdentifier object  that include the  API Name,API Version and API Provider
     *                      Name to create the String
     * @return String - with API Name,API Version and API Provider Name as key:value format
     */
    protected String getAPIIdentifierString(APIIdentifier apiIdentifier) {
        return " API Name:" + apiIdentifier.getApiName() + " API Version:" + apiIdentifier.getVersion() +
                " API Provider Name :" + apiIdentifier.getProviderName() + " ";

    }

    /**
     * Publish a API.
     *
     * @param apiIdentifier           - Instance of APIIdentifier object  that include the  API Name,
     *                                API Version and API Provider
     * @param publisherRestClient     - Instance of APIPublisherRestClient
     * @param isRequireReSubscription - If publish with re-subscription required option true else false.
     * @return HttpResponse - Response of the API Publishing activity
     * @throws APIManagerIntegrationTestException -  Exception throws by the method call of
     *                                            changeAPILifeCycleStatusToPublish() in APIPublisherRestClient.java.
     */
    protected HttpResponse publishAPI(APIIdentifier apiIdentifier, APIPublisherRestClient publisherRestClient,
            boolean isRequireReSubscription) throws APIManagerIntegrationTestException {
        APILifeCycleStateRequest publishUpdateRequest = new APILifeCycleStateRequest(apiIdentifier.getApiName(),
                apiIdentifier.getProviderName(), APILifeCycleState.PUBLISHED);
        publishUpdateRequest.setVersion(apiIdentifier.getVersion());
        return publisherRestClient.changeAPILifeCycleStatusToPublish(apiIdentifier, isRequireReSubscription);

    }

    /**
     * verify the API status change. this method will check the latest lifecycle status change
     * is correct according to the given old status and new status.
     *
     * @param httpResponse - Response returned in the the  API lifecycle status change action
     * @param oldStatus    - Status of the API before the change
     * @param newStatus    - Status of the API after the change
     * @return boolean - true if the given status change is correct, if not false
     * @throws APIManagerIntegrationTestException - Exception throws when resolving the JSON object in the HTTP response
     */
    public boolean verifyAPIStatusChange(HttpResponse httpResponse, APILifeCycleState oldStatus,
            APILifeCycleState newStatus) throws APIManagerIntegrationTestException {
        boolean isStatusChangeCorrect = false;
        try {
            JSONObject jsonRootObject = new JSONObject(httpResponse.getData());
            JSONArray jsonArray = (JSONArray) jsonRootObject.get("lcs");
            JSONObject latestChange = (JSONObject) jsonArray.get(0);
            // Retrieve the latest API life cycle status change information if  there are more than one
            // lifecycle status change activities  available in the api
            if (jsonArray.length() > 0) {
                for (int index = 1; index < jsonArray.length(); index++) {
                    if (Long.parseLong(((JSONObject) jsonArray.get(index)).get("date").toString()) > Long
                            .parseLong(latestChange.get("date").toString())) {
                        latestChange = (JSONObject) jsonArray.get(index);
                    }
                }
            }
            // Check the given status change information is correct in latest lifecycle status change action.
            if (latestChange.get("oldStatus").toString().equals(oldStatus.getState()) && latestChange.get("newStatus")
                    .toString().equals(newStatus.getState())) {
                isStatusChangeCorrect = true;
            }
            return isStatusChangeCorrect;
        } catch (JSONException e) {
            throw new APIManagerIntegrationTestException(
                    "Exception thrown when resolving the JSON object in the HTTP response to verify the status change."
                            +
                            " HTTP response data: " + httpResponse.getData() + " HTTP response message: " +
                            httpResponse.getResponseMessage() + " HTTP response code: " + httpResponse
                            .getResponseCode(), e);
        }
    }
}
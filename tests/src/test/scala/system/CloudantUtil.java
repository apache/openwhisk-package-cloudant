/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package system;

import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.google.gson.*;
import io.restassured.response.Response;
import common.TestUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertTrue;

/**
 * Basic tests of the cloudant trigger function.
 */
public class CloudantUtil {
    public static final String USER_PROPERTY = "user";
    public static final String PWD_PROPERTY = "password";
    public static final String DBNAME_PROPERTY = "dbname";
    public static final String DOC_ID = "testId";

    /**
     * The root of the Cloudant installation.
     */
    private static final String CLOUDANT_INSTALL_FILE = "installCatalog.sh";
    private static final String CLOUDANT_HOME = getCloudantHome();
    public static final File ATTACHMENT_FILE_PATH = getFileRelativeToCloudantHome("tests/dat/attach.txt");
    public static final File INDEX_DDOC_PATH = getFileRelativeToCloudantHome("tests/dat/indexdesigndoc.txt");
    public static final File VIEW_AND_SEARCH_DDOC_PATH = getFileRelativeToCloudantHome("tests/dat/searchdesigndoc.txt");
    public static final File FILTER_DDOC_PATH = getFileRelativeToCloudantHome("tests/dat/filterdesigndoc.txt");

    private static Gson gson = new Gson();

    private static class ResponsePair {
        public final Integer fst;
        public final JsonObject snd;

        public ResponsePair(Integer a, JsonObject b) {
            this.fst = a;
            this.snd = b;
        }
    }

    public static class Credential {
        public final String user;
        public final String password;
        public final String dbname;

        public String host() {
            return user + ".cloudant.com";
        }

        public Credential(String user, String password, String dbname) {
            this.user = user;
            this.password = password;
            this.dbname = dbname;
        }

        public Credential(Properties props) {
            this(props.getProperty(USER_PROPERTY), props.getProperty(PWD_PROPERTY), props.getProperty(DBNAME_PROPERTY));
        }

        public static Credential makeFromVCAPFile(String vcapService, String dbNamePrefix) {
            // Create database name using dbNamePrefix and generated uuid
            String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");
            String dbname = dbNamePrefix.toLowerCase() + "-" + uniqueSuffix;

            Map<String,String> credentials = TestUtils.getVCAPcredentials(vcapService);
            String username = credentials.get("username");
            String password = credentials.get("password");
            Properties props = new Properties();
            props.setProperty(USER_PROPERTY, username);
            props.setProperty(PWD_PROPERTY, password);
            props.setProperty(DBNAME_PROPERTY, dbname);
            return new Credential(props);
        }

    }

    public static void setUp(Credential credential) throws Exception {
        deleteTestDatabase(credential);
        for (int i = 0; i < 5; i++) {
            try {
                ResponsePair response = CloudantUtil.createTestDatabase(credential, false);
                if (response.fst == 201)
                    return;
                // respond code is sometimes not 201 but still ok
                // (might be 200 or 202)
                if (response.snd.has("ok")) {
                    if (response.snd.get("ok").getAsBoolean())
                        return;
                }
                if (response.snd.has("reason")) {
                    String reason = response.snd.get("reason").getAsString();
                    if (reason.contains("exists"))
                        return;
                }
            } catch (Throwable t) {
                Thread.sleep(1000);
            }
        }
        assertTrue("failed to create database " + credential.dbname, false);
    }

    public static void unsetUp(Credential credential) throws Exception {
        deleteTestDatabase(credential);
    }

    /**
     * Delete a user-specific Cloudant database.
     *
     * @throws UnsupportedEncodingException
     * @throws InterruptedException
     */
    public static JsonObject deleteTestDatabase(Credential credential) throws UnsupportedEncodingException, InterruptedException {
        return deleteTestDatabase(credential, null);
    }

    public static JsonObject deleteTestDatabase(Credential credential, String dbName) throws UnsupportedEncodingException, InterruptedException {
        // Use DELETE to delete the database.
        // This could fail if the database already exists, but that's ok.
        Response response = null;
        String db = (dbName != null && !dbName.isEmpty()) ? dbName : credential.dbname;
        assertTrue("failed to determine database name", db != null && !db.isEmpty());
        response = given().port(443).baseUri(cloudantAccount(credential.user)).auth().basic(credential.user, credential.password).when().delete("/" + db);
        System.out.format("Response of delete database %s: %s\n", db, response.asString());
        return (JsonObject) new JsonParser().parse(response.asString());
    }

    /**
     * Create a user-specific Cloudant database that will be used for this test.
     *
     * @throws UnsupportedEncodingException
     */
    public static ResponsePair createTestDatabase(Credential credential) throws UnsupportedEncodingException {
        return createTestDatabase(credential, true);
    }

    private static ResponsePair createTestDatabase(Credential credential, boolean failIfCannotCreate) throws UnsupportedEncodingException {
        // Use PUT to create the database.
        // This could fail if the database already exists, but that's ok.
        String dbName = credential.dbname;
        assertTrue("failed to determine database name", dbName != null && !dbName.isEmpty());
        Response response = given().port(443).baseUri(cloudantAccount(credential.user)).auth().basic(credential.user, credential.password).when().put("/" + dbName);
        System.out.format("Response of create database %s: %s\n", dbName, response.asString());
        if (failIfCannotCreate)
            assertTrue("failed to create database " + dbName, response.statusCode() == 201 || response.statusCode() == 202);
        return new ResponsePair(response.statusCode(), (JsonObject) new JsonParser().parse(response.asString()));
    }

    /**
     * read a user-specific Cloudant database to verify database action test
     * cases.
     *
     * @throws UnsupportedEncodingException
     */
    public static JsonObject readTestDatabase(Credential credential) {
        try {
            String db = credential.dbname;
            Response response = given().port(443).baseUri(cloudantAccount(credential.user)).auth().basic(credential.user, credential.password).when().get("/" + db);
            System.out.format("Response of HTTP GET for database %s: %s\n", credential.dbname, response.asString());
            return gson.fromJson(response.asString(), JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Response readTestDatabase(Credential credential, String dbName) {
        try {
            String db = (dbName != null && !dbName.isEmpty()) ? dbName : credential.dbname;
            Response response = given().port(443).baseUri(cloudantAccount(credential.user)).auth().basic(credential.user, credential.password).when().get("/" + db);
            System.out.format("Response of HTTP GET for database %s: %s\n", credential.dbname, response.asString());
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * create a document in the cloudant database
     *
     * @throws UnsupportedEncodingException
     */
    public static JsonObject createDocument(Credential credential, String jsonObj) throws UnsupportedEncodingException {
        JsonObject obj = new JsonParser().parse(jsonObj).getAsJsonObject();

        CloudantClient client = new CloudantClient(credential.user, credential.user, credential.password);
        Database db = client.database(credential.dbname, false);
        com.cloudant.client.api.model.Response res = db.post(obj);
        client.shutdown();

        JsonObject ret = new JsonObject();
        ret.addProperty("ok", true);
        ret.addProperty("id", res.getId());
        ret.addProperty("rev", res.getRev());
        return ret;
    }

    /**
     * get a document from the cloudant database
     *
     * @throws UnsupportedEncodingException
     */
    public static JsonObject getDocument(Credential credential, String docId) throws UnsupportedEncodingException {
        // use GET to get the document
        Response response = given().port(443).baseUri(cloudantAccount(credential.user)).auth().basic(credential.user, credential.password).get("/" + credential.dbname + "/" + docId);
        String responseStr = response.asString();
        if (responseStr.length() > 500)
            responseStr = responseStr.substring(0, 500);
        System.out.format("Response of get document from database %s: %s\n", credential.dbname, responseStr);
        return (JsonObject) new JsonParser().parse(response.asString());
    }

    /**
     * delete a document from the cloudant database
     *
     * @throws UnsupportedEncodingException
     */
    public static JsonObject deleteDocument(Credential credential, JsonObject jsonObj) throws UnsupportedEncodingException {
        CloudantClient client = new CloudantClient(credential.user, credential.user, credential.password);
        Database db = client.database(credential.dbname, false);
        com.cloudant.client.api.model.Response res = null;
        try {
            res = db.remove(jsonObj);
        }
        catch(Exception e) {
            System.out.format("Exception thrown during document delete: %s", e.toString());
        }
        finally {
            client.shutdown();
        }

        JsonObject ret = new JsonObject();
        ret.addProperty("id", res.getId());
        ret.addProperty("rev", res.getRev());
        return ret;
    }

    /**
     * Read bulk documents from the cloudant database
     *
     * @throws UnsupportedEncodingException
     */
    public static JsonArray bulkDocuments(Credential credential, JsonArray bulkDocs) throws UnsupportedEncodingException {
        JsonObject docs = new JsonObject();
        docs.add("docs", bulkDocs);
        // use GET to get the document
        String dbname = credential.dbname;
        Response response = given().port(443).baseUri(cloudantAccount(credential.user)).auth().basic(credential.user, credential.password).contentType("application/json").body(docs.toString()).post("/" + credential.dbname + "/_bulk_docs?include_docs=true");
        String responseStr = response.asString();
        if (responseStr.length() > 500)
            responseStr = responseStr.substring(0, 500);
        System.out.format("Response of get document from database %s: %s\n", dbname, responseStr);
        return (JsonArray) new JsonParser().parse(response.asString());
    }

    public static JsonObject createDocParameterForWhisk() {
        return createDocParameterForWhisk(null);
    }

    public static JsonObject createDocParameterForWhisk(String doc) {
        JsonObject cloudantDoc = new JsonObject();
        String now = new Date().toString();
        cloudantDoc.addProperty("_id", DOC_ID);
        cloudantDoc.addProperty("date", now);
        // Create JSON object that will be passed as an argument to whisk cli
        JsonObject param = new JsonObject();
        if (doc != null && !doc.isEmpty()) {
            param.addProperty("doc", doc);
        } else {
            param.addProperty("doc", cloudantDoc.toString());
        }
        return param;
    }

    public static JsonArray createDocumentArray(int numDocs) {
        // Array of docs for bulk
        JsonArray bulkDocs = new JsonArray();
        for (int i = 1; i <= numDocs; i++) {
            JsonObject cloudantDoc = new JsonObject();
            String now = new Date().toString();
            cloudantDoc.addProperty("_id", CloudantUtil.DOC_ID + i);
            cloudantDoc.addProperty("date", now);
            bulkDocs.add(cloudantDoc);
        }
        return bulkDocs;
    }

    /**
     * Only keep _id and _rev for each document in the JSON array.
     */
    public static JsonArray updateDocsWithOnlyIdAndRev(JsonArray docs) {
        for (int i = 0; i < docs.size(); i++) {
            JsonElement id = docs.get(i).getAsJsonObject().get("id");
            JsonElement rev = docs.get(i).getAsJsonObject().get("rev");
            docs.get(i).getAsJsonObject().add("_id", id);
            docs.get(i).getAsJsonObject().add("_rev", rev);
        }
        return docs;
    }

    public static JsonArray addDeletedPropertyToDocs(JsonArray docs) {
        for (int i = 0; i < docs.size(); i++) {
            JsonElement id = docs.get(i).getAsJsonObject().get("id");
            JsonElement rev = docs.get(i).getAsJsonObject().get("rev");
            docs.get(i).getAsJsonObject().add("_id", id);
            docs.get(i).getAsJsonObject().add("_rev", rev);
            docs.get(i).getAsJsonObject().addProperty("_deleted", true);
        }
        return docs;
    }

    public static JsonObject createDesignFromFile(File jsonFile) throws JsonSyntaxException, IOException {
        return gson.fromJson(readFile(jsonFile), JsonObject.class);
    }

    public static String readFile(File jsonFile) throws IOException {
        return new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Create an index in the cloudant database
     *
     * @throws UnsupportedEncodingException
     */
    public static JsonObject createIndex(Credential credential, String jsonObj) throws UnsupportedEncodingException {
        Response response = given().port(443).baseUri(cloudantAccount(credential.user)).auth().basic(credential.user, credential.password).contentType("application/json").body(jsonObj).when().post("/" + credential.dbname + "/_index");
        System.out.format("Response of create document in database %s: %s\n", credential.dbname, response.asString());
        assertTrue("failed to create index in database " + credential.dbname, response.statusCode() == 200);
        return (JsonObject) new JsonParser().parse(response.asString());
    }

    /**
     * Create a document with attachment in a cloudant database
     *
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     */
    public static com.cloudant.client.api.model.Response createDocumentWithAttachment(Credential credential, File attachmentFilePath) throws UnsupportedEncodingException, FileNotFoundException {
        InputStream attachStream = new FileInputStream(attachmentFilePath);
        String contentType = "text/plain";

        CloudantClient client = new CloudantClient(credential.user, credential.user, credential.password);
        Database db = client.database(credential.dbname, false);
        return db.saveAttachment(attachStream, attachmentFilePath.getName(), contentType);
    }

    private static String cloudantAccount(String user) {
        return "https://" + user + ".cloudant.com";
    }

    public static File getFileRelativeToCloudantHome(String name) {
        return new File(CLOUDANT_HOME, name);
    }

    private static String getCloudantHome() {
        String dir = System.getProperty("user.dir");

        if (dir != null) {
            // Look in the directory tree recursively.
            File propfile = findFileRecursively(dir, CLOUDANT_INSTALL_FILE);
            return propfile != null ? propfile.getParent() : null;
        } else return null;
    }

    private static File findFileRecursively(String dir, String needle) {
        if (dir != null) {
            File base = new File(dir);
            File file = new File(base, needle);
            if (file.exists()) {
                return file;
            } else {
                return findFileRecursively(base.getParent(), needle);
            }
        } else return null;
    }

}

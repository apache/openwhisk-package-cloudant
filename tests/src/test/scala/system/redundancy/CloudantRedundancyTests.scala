/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package system.redundancy

import com.jayway.restassured.RestAssured
import com.jayway.restassured.config.SSLConfig
import common.{WhiskProperties, Wsk, WskProps, WskTestHelpers}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.{pimpAny, _}
import system.CloudantUtil

/**
 * These tests verify that a cloudant redundancy (master/slave) configuration
 * works as expected.  They will only run properly in an environment with two
 * cloudant containers running concurrently and env var HOST_INDEX set to host0 in
 * one container and host1 in the other.  This test also assumes that redis and
 * the active endpoint authorization are configured.  For the auth set the
 * ENDPOINT_AUTH env var in your containers to match the testing.auth property
 * found in your whisk.properties.  To configure redis simply set the REDIS_URL
 * env var in your containers to point to the openwhisk redis container and make
 * sure the container is deployed.  You can run redis.yml to deploy it.
 */
@RunWith(classOf[JUnitRunner])
class CloudantRedundancyTests
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll
    with WskTestHelpers {

    val wskprops = WskProps()
    val wsk = new Wsk
    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)
    var edgeHost = WhiskProperties.getEdgeHost
    val auth = WhiskProperties.getBasicAuth
    val user = auth.fst
    val password = auth.snd

    var endpointPrefix = s"https://$user:$password@$edgeHost/cloudanttrigger/worker0/"

    behavior of "Cloudant redundancy tests"

    it should "fire cloudant trigger before the swap" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                // the package cloudant should be there
                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("fetched package cloudant")
                packageGetResult.stdout should include("ok")

                // create package binding
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
                }

                // create whisk stuff
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson))
                }
                feedCreationResult.stdout should include("ok")

                Thread.sleep(3000)

                // create a test doc in the sample db
                println("create a test doc and wait for trigger")
                CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc1\"}")

                // get activation list of the trigger, expecting exactly 1
                val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = 30).length
                println(s"Found activation size (should be exactly 1): $activations")
                withClue("Change feed trigger count: ") { activations should be(1) }

                // delete the whisk trigger, which must also delete the feed
                wsk.trigger.delete(triggerName)
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should "perform active swap by setting host0 active=false" in {
        val endpointURL = endpointPrefix + "0/active?active=false"
        val expectedResult = "{\"worker\":\"worker0\",\"host\":\"host0\",\"active\":\"swapping\"}".parseJson.asJsObject

        makeGetCallWithExpectedResult(endpointURL, expectedResult)
    }

    it should "verify active swap by checking for host0 active=false" in {
        val endpointURL = endpointPrefix + "0/active"
        val expectedResult = "{\"worker\":\"worker0\",\"host\":\"host0\",\"active\":false}".parseJson.asJsObject

        Thread.sleep(3000)
        makeGetCallWithExpectedResult(endpointURL, expectedResult)
    }

    it should "verify active swap by checking for host1 active=true" in {
        val endpointURL = endpointPrefix + "1/active"
        val expectedResult = "{\"worker\":\"worker0\",\"host\":\"host1\",\"active\":true}".parseJson.asJsObject

        makeGetCallWithExpectedResult(endpointURL, expectedResult)
    }

    it should "fire cloudant trigger again after the swap" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                // the package cloudant should be there
                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("fetched package cloudant")
                packageGetResult.stdout should include("ok")

                // create package binding
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
                }

                // create whisk stuff
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson))
                }
                feedCreationResult.stdout should include("ok")

                Thread.sleep(3000)

                // create a test doc in the sample db
                println("create a test doc and wait for trigger")
                CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc1\"}")

                // get activation list of the trigger, expecting exactly 1
                val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = 30).length
                println(s"Found activation size (should be exactly 1): $activations")
                withClue("Change feed trigger count: ") { activations should be(1) }

                // delete the whisk trigger, which must also delete the feed
                wsk.trigger.delete(triggerName)
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    private def makeGetCallWithExpectedResult(endpointURL: String, expectedResult: JsObject) = {
        val response = RestAssured.
                given().
                config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation())).
                get(endpointURL)
        assert(response.statusCode() == 200)
        var result = response.body.asString.parseJson.asJsObject
        JsObject(result.fields - "hostMachine") shouldBe expectedResult
    }

    override def afterAll() {
        //swap back to original configuration
        RestAssured.
                given().
                config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation())).
                get(endpointPrefix + "0/active?active=true")
    }

}

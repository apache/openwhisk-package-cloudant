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
package system.packages

import io.restassured.RestAssured
import io.restassured.config.SSLConfig
import io.restassured.http.ContentType
import common.TestUtils.FORBIDDEN
import common.{Wsk, WskProps}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import spray.json._

@RunWith(classOf[JUnitRunner])
class CloudantFeedWebTests
    extends FlatSpec
    with BeforeAndAfter
    with Matchers {

    val wskprops = WskProps()

    val webAction = "/whisk.system/cloudantWeb/changesWebAction"
    val webActionURL = s"https://${wskprops.apihost}/api/v1/web$webAction.http"

    val requiredParams = JsObject(
        "triggerName" -> JsString("/invalidNamespace/invalidTrigger"),
        "authKey" -> JsString("DoesNotWork"),
        "dbname" -> JsString("DoesNotExist"),
        "host" -> JsString("bad.hostname"),
        "username" -> JsString("username"),
        "password" -> JsString("password")
    )

    behavior of "Cloudant changes web action"

    it should "not be obtainable using the CLI" in {
        val wsk = new Wsk()
        implicit val wp = wskprops

        wsk.action.get(webAction, FORBIDDEN)
    }

    it should "reject post of a trigger due to missing triggerName argument" in {
        val params = JsObject(requiredParams.fields - "triggerName")

        makePostCallWithExpectedResult(params, JsObject("error" -> JsString("no trigger name parameter was provided")), 400)
    }

    it should "reject post of a trigger due to missing host argument" in {
        val params = JsObject(requiredParams.fields - "host")

        makePostCallWithExpectedResult(params, JsObject("error" -> JsString("cloudant trigger feed: missing host parameter")), 400)
    }

    it should "reject post of a trigger due to missing username argument" in {
        val params = JsObject(requiredParams.fields - "username")

        makePostCallWithExpectedResult(params, JsObject("error" -> JsString("cloudant trigger feed: Must specify parameter/s of iamApiKey or username/password")), 400)
    }

    it should "reject post of a trigger due to missing password argument" in {
        val params = JsObject(requiredParams.fields - "password")

        makePostCallWithExpectedResult(params, JsObject("error" -> JsString("cloudant trigger feed: Must specify parameter/s of iamApiKey or username/password")), 400)
    }

    it should "reject post of a trigger due to missing dbname argument" in {
        val params = JsObject(requiredParams.fields - "dbname")

        makePostCallWithExpectedResult(params, JsObject("error" -> JsString("cloudant trigger feed: missing dbname parameter")), 400)
    }

    it should "reject post of a trigger when authentication fails" in {

        makePostCallWithExpectedResult(requiredParams, JsObject("error" -> JsString("Trigger authentication request failed.")), 401)
    }

    it should "reject delete of a trigger due to missing triggerName argument" in {
        val params = JsObject(requiredParams.fields - "triggerName")

        makeDeleteCallWithExpectedResult(params, JsObject("error" -> JsString("no trigger name parameter was provided")), 400)
    }

    it should "reject delete of a trigger when authentication fails" in {
        makeDeleteCallWithExpectedResult(requiredParams, JsObject("error" -> JsString("Trigger authentication request failed.")), 401)
    }

    def makePostCallWithExpectedResult(params: JsObject, expectedResult: JsObject, expectedCode: Int) = {
        val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .body(params.toString())
                .post(webActionURL)
        assert(response.statusCode() == expectedCode)
        response.body.asString.parseJson.asJsObject shouldBe expectedResult
    }

    def makeDeleteCallWithExpectedResult(params: JsObject, expectedResult: JsObject, expectedCode: Int) = {
        val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .config(RestAssured.config().sslConfig(new SSLConfig().relaxedHTTPSValidation()))
                .body(params.toString())
                .delete(webActionURL)
        assert(response.statusCode() == expectedCode)
        response.body.asString.parseJson.asJsObject shouldBe expectedResult
    }

}

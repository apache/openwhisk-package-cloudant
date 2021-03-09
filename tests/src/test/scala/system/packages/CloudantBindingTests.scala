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

import common.{TestHelpers, Wsk, WskProps, WskTestHelpers}
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import spray.json.DefaultJsonProtocol._
import spray.json._
import system.CloudantUtil

@RunWith(classOf[JUnitRunner])
class CloudantBindingTests extends FlatSpec
    with TestHelpers
    with WskTestHelpers {

    val wskprops = WskProps()
    val wsk = new Wsk

    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    behavior of "Cloudant binding"

    /**
     * Simulate bluemix package binding by supplying a "url" parameter.
     * Additionally, leave out other key parameters (e.g. username, password)
     * to ensure that the action uses the "url" bound parameter.
     */
    it should """Use "url" property if it is available""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val packageName = "cloudantBindingWithURL"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("""Creating cloudant package binding with only a "url" parameter.""")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("url" -> s"https://${myCloudantCreds.user}:${myCloudantCreds.password}@${myCloudantCreds.host}".toJson))
                }

                println("Invoking the document-create action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/create-document",
                    Map(
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "doc" -> JsObject("message" -> "I used the url parameter.".toJson)))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                }
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    /**
     * Simulate a user creating their own binding with "username", "password", and "host".
     * Do not include "url" in the binding to ensure the package uses the other bound properties.
     */
    it should """Use "username", "password", and "host" if "url" is not available""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val packageName = "cloudantBindingWithoutURL"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("""Creating cloudant package binding with "username", "password" and "host".""")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("username" -> myCloudantCreds.user.toJson,
                                "password" -> myCloudantCreds.password.toJson,
                                "host" -> myCloudantCreds.host().toJson))
                }

                println("Invoking the document-create action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/create-document",
                    Map(
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "doc" -> JsObject("message" -> "This time I didn't use the URL param.".toJson)))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                }
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }
}

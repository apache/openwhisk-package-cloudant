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
package system.health

import java.time.{Clock, Instant}

import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Inside}
import org.scalatest.junit.JUnitRunner
import common.TestHelpers
import common.Wsk
import common.WskActorSystem
import common.WskProps
import common.WskTestHelpers
import spray.json.DefaultJsonProtocol.IntJsonFormat
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.DefaultJsonProtocol.BooleanJsonFormat
import spray.json.{JsObject, JsString, pimpAny}
import system.CloudantUtil

/**
 * Tests for Cloudant trigger service
 */
@RunWith(classOf[JUnitRunner])
class CloudantHealthFeedTests
    extends FlatSpec
    with TestHelpers
    with Inside
    with WskTestHelpers
    with WskActorSystem
    with BeforeAndAfterEach {

    val wskprops = WskProps()
    val wsk = new Wsk
    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    behavior of "Cloudant Health trigger service"

    override def beforeEach() = {
        CloudantUtil.setUp(myCloudantCreds)
        CloudantUtil.createDocument(myCloudantCreds, "{\"_id\":\"testid\"}")
    }

    override def afterEach() = {
        // wait 10 seconds to give the cloudant provider a chance to clean
        // up trigger feeds before deleting the database
        Thread.sleep(10 * 1000)
        CloudantUtil.unsetUp(myCloudantCreds)
    }

    it should "fire changes when a document is created" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            // the package cloudant should be there
            val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
            println("fetched package cloudant")
            packageGetResult.stdout should include("ok")

            // create package binding
            assetHelper.withCleaner(wsk.pkg, packageName) {
                (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
            }

            // create whisk stuff
            println(s"Creating trigger: $triggerName")
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson))
            }
            feedCreationResult.stdout should include("ok")

            val activationsBeforeChange = wsk.activation.pollFor(N = 1, Some(triggerName)).length
            activationsBeforeChange should be(0)

            // create a test doc in the sample db
            CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc1\"}")
            val now = Instant.now(Clock.systemUTC())
            println(s"created a test doc at $now")

            // get activation list of the trigger, expecting exactly 1
            val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = 60).length
            val nowPoll = Instant.now(Clock.systemUTC())
            println(s"Found activation size ($nowPoll): $activations")
            withClue("Change feed trigger count: ") { activations should be(1) }

            // delete the whisk trigger, which must also delete the feed
            wsk.trigger.delete(triggerName)

            // recreate the trigger now without the feed
            wsk.trigger.create(triggerName)

            // create a test doc in the sample db, this should not fire the trigger
            println("create another test doc")
            CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc2\"}")

            println("checking for new triggers (no new ones expected)")
            val activationsAfterDelete = wsk.activation.pollFor(N = 2, Some(triggerName)).length
            println(s"Found activation size after delete: $activationsAfterDelete")
            activationsAfterDelete should be(1)
    }

    it should "fire changes since sequence 0 of DB" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
            println("Fetching cloudant package.")
            packageGetResult.stdout should include("ok")

            println("Creating cloudant package binding.")
            assetHelper.withCleaner(wsk.pkg, packageName) {
                (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
            }

            println(s"Creating trigger: $triggerName")
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "maxTriggers" -> 100000000.toJson,
                        "since" -> "0".toJson))
            }
            feedCreationResult.stdout should include("ok")

            val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = 60).length
            activations should be(1)
    }

    it should "fire changes when a document is deleted" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
            println("Fetching cloudant package.")
            packageGetResult.stdout should include("ok")

            println("Creating cloudant package binding.")
            assetHelper.withCleaner(wsk.pkg, packageName) {
                (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
            }

            println(s"Creating trigger: $triggerName")
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "maxTriggers" -> (-1).toJson))
            }
            feedCreationResult.stdout should include("ok")

            val activationsBeforeDelete = wsk.activation.pollFor(N = 1, Some(triggerName)).length
            activationsBeforeDelete should be(0)

            // delete a test doc in the sample db and verify trigger is fired
            println("delete a test doc")
            CloudantUtil.deleteDocument(myCloudantCreds, CloudantUtil.getDocument(myCloudantCreds, "testid"))

            val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = 60).length
            activations should be(1)
    }


    it should "return correct status and configuration" in withAssetCleaner(wskprops) {
        val currentTime = s"${System.currentTimeMillis}"

        (wp, assetHelper) =>
            implicit val wskProps = wp
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                // the package alarms should be there
                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("fetched package cloudant")
                packageGetResult.stdout should include("ok")

                // create package binding
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
                }

                val username = myCloudantCreds.user
                val password = myCloudantCreds.password
                val host = myCloudantCreds.host()
                val dbName = myCloudantCreds.dbname
                val port = 443
                val protocol = "https"
                val since = "now"
                val filter = "test_filter/fruit"
                val queryParams = JsObject("type" -> JsString("tomato"))

                // create whisk stuff
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> username.toJson,
                            "password" -> password.toJson,
                            "host" -> host.toJson,
                            "dbname" -> dbName.toJson,
                            "filter" -> filter.toJson,
                            "query_params" -> queryParams,
                            "protocol" -> protocol.toJson,
                            "port" -> port.toJson,
                            "since" -> since.toJson
                        ))
                }
                feedCreationResult.stdout should include("ok")

                val actionName = s"$packageName/$feed"
                val run = wsk.action.invoke(actionName, parameters = Map(
                    "triggerName" -> triggerName.toJson,
                    "lifecycleEvent" -> "READ".toJson,
                    "authKey" -> wskProps.authKey.toJson
                ))

                withActivation(wsk.activation, run) {
                    activation =>
                        activation.response.success shouldBe true

                        inside(activation.response.result) {
                            case Some(result) =>
                                val config = result.getFields("config").head.asInstanceOf[JsObject].fields
                                val status = result.getFields("status").head.asInstanceOf[JsObject].fields

                                config should contain("name" -> triggerName.toJson)
                                config should contain("username" -> username.toJson)
                                config should contain("password" -> password.toJson)
                                config should contain("dbname" -> dbName.toJson)
                                config should contain("filter" -> filter.toJson)
                                config should contain("query_params" -> queryParams)
                                config should contain("protocol" -> protocol.toJson)
                                config should contain("port" -> port.toJson)
                                config should contain("since" -> since.toJson)
                                config should contain key "namespace"

                                status should contain("active" -> true.toJson)
                                status should contain key "dateChanged"
                                status should contain key "dateChangedISO"
                                status should not(contain key "reason")
                        }
                }
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }
}

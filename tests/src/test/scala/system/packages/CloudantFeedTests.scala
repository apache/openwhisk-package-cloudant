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

import common.TestUtils.ANY_ERROR_EXIT
import common._
import org.junit.runner.RunWith
import org.scalatest.{FlatSpec, Inside}
import org.scalatest.junit.JUnitRunner
import spray.json.DefaultJsonProtocol._
import spray.json._
import system.CloudantUtil

/**
 * Tests for Cloudant trigger service
 */
@RunWith(classOf[JUnitRunner])
class CloudantFeedTests
    extends FlatSpec
    with TestHelpers
    with Inside
    with WskTestHelpers
    with WskActorSystem {

    val wskprops = WskProps()
    val wsk = new Wsk
    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)
    val defaultAction = Some(TestUtils.getTestActionFilename("hello.js"))
    val maxRetries = System.getProperty("max.retries", "30").toInt

    behavior of "Cloudant trigger service"

    it should "return useful error message when changes feed does not include host parameter" in withAssetCleaner(wskprops) {

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
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson),
                        expectedExitCode = 246)
            }
            feedCreationResult.stderr should include("cloudant trigger feed: missing host parameter")

    }

    it should "return useful error message when changes feed does not include dbname parameter" in withAssetCleaner(wskprops) {

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
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson),
                        expectedExitCode = 246)
            }
            feedCreationResult.stderr should include("cloudant trigger feed: missing dbname parameter")

    }

    it should "return useful error message when changes feed does not include password parameter" in withAssetCleaner(wskprops) {

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
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "host" -> myCloudantCreds.host().toJson),
                        expectedExitCode = 246)
            }
            feedCreationResult.stderr should include("cloudant trigger feed: Must specify parameter/s of iamApiKey or username/password")

    }

    it should "return useful error message when changes feed does not include username parameter" in withAssetCleaner(wskprops) {

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
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "password" -> myCloudantCreds.password.toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "host" -> myCloudantCreds.host().toJson),
                        expectedExitCode = 246)
            }
            feedCreationResult.stderr should include("cloudant trigger feed: Must specify parameter/s of iamApiKey or username/password")

    }

    it should "throw error if Cloudant connection is invalid" in withAssetCleaner(wskprops) {

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

            println("Creating cloudant trigger feed with wrong password.")
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> "WRONG_PASSWORD".toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "maxTriggers" -> 1.toJson),
                        expectedExitCode = ANY_ERROR_EXIT)
            }
            println("Creating cloudant trigger should give an error because not confirmed database.")
            feedCreationResult.stderr should include("error")

    }

    it should "disable after reaching max triggers" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val ruleName = s"dummyCloudantRule-${System.currentTimeMillis}"
            val actionName = s"dummyCloudantAction-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
                }

                // create action
                assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
                    action.create(name, defaultAction)
                }

                println("Creating cloudant trigger feed.")
                assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson,
                            "maxTriggers" -> 1.toJson))
                }

                // create rule
                assetHelper.withCleaner(wsk.rule, ruleName) { (rule, name) =>
                    rule.create(name, trigger = triggerName, action = actionName)
                }

                val activationsBeforeCreate = wsk.activation.pollFor(N = 1, Some(triggerName), retries = maxRetries).length
                activationsBeforeCreate should be(0)

                // Create test docs in cloudant and assert that document was inserted successfully
                println("Creating a test doc-1 in the cloudant")
                val response1 = CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc_1\"}")
                response1.get("ok").getAsString should be("true")

                println("Checking for activations")
                val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = maxRetries).length
                println(s"Found activation size (should be exactly 1): $activations")
                activations should be(1)

                println("Creating a test doc-2 in the cloudant")
                val response2 = CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc_2\"}")
                response2.get("ok").getAsString should be("true")

                println("No activations should be created for test_doc_2 since trigger is disabled")
                val newactivations = wsk.activation.pollFor(N = 2, Some(triggerName)).length
                println(s"Activation size should still be one: $newactivations")
                newactivations should be(1)

            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should """filter out triggers that do not meet the filter criteria""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val ruleName = s"dummyCloudantRule-${System.currentTimeMillis}"
            val actionName = s"dummyCloudantAction-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
                }

                // create action
                assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
                    action.create(name, defaultAction)
                }

                //Create filter design doc
                val filterDesignDoc = CloudantUtil.createDesignFromFile(CloudantUtil.FILTER_DDOC_PATH).toString
                val getResponse = CloudantUtil.createDocument(myCloudantCreds, filterDesignDoc)
                getResponse.get("ok").getAsString shouldBe "true"

                println("Creating cloudant trigger feed.")
                assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson,
                            "filter" -> "test_filter/fruit".toJson,
                            "query_params" -> JsObject("type" -> JsString("tomato"))))
                }

                // create rule
                assetHelper.withCleaner(wsk.rule, ruleName) { (rule, name) =>
                    rule.create(name, trigger = triggerName, action = actionName)
                }

                val activationsBeforeCreate = wsk.activation.pollFor(N = 1, Some(triggerName), retries = maxRetries).length
                activationsBeforeCreate should be(0)

                // Create test docs in cloudant and assert that document was inserted successfully
                println("Creating a test doc-1 in the cloudant")
                val response1 = CloudantUtil.createDocument(myCloudantCreds, "{\"kind\":\"fruit\", \"type\":\"apple\"}")
                response1.get("ok").getAsString should be("true")

                println("Checking for activations")
                val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = maxRetries).length
                println(s"Found activation size (should be exactly 1): $activations")
                activations should be(1)

                println("Creating a test doc-2 in the cloudant")
                val response2 = CloudantUtil.createDocument(myCloudantCreds, "{\"kind\":\"dairy\",\"type\":\"butter\"}")
                response2.get("ok").getAsString should be("true")

                println("checking for new activations (not expected since it should be filtered out)")
                val noNewActivations = wsk.activation.pollFor(N = 2, Some(triggerName)).length
                println(s"Found activation size (should still be 1): $noNewActivations")
                noNewActivations should be(1)

                println("Creating a test doc-3 in the cloudant")
                val response3 = CloudantUtil.createDocument(myCloudantCreds, "{\"kind\":\"debatable\", \"type\":\"tomato\"}")
                response3.get("ok").getAsString should be("true")

                println("Checking for new activations (should now have 2)")
                val newActivations = wsk.activation.pollFor(N = 3, Some(triggerName), retries = maxRetries).length
                println(s"Found activation size (should be 2): $newActivations")
                newActivations should be(2)

            }
            finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should "not return fields in configuration that are not passed in during trigger create" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskProps = wp
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

                val username = myCloudantCreds.user
                val password = myCloudantCreds.password
                val host = myCloudantCreds.host()
                val dbName = myCloudantCreds.dbname

                // create whisk stuff
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> username.toJson,
                            "password" -> password.toJson,
                            "host" -> host.toJson,
                            "dbname" -> dbName.toJson
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

                                config should contain key "username"
                                config should contain key "password"
                                config should contain key "host"
                                config should contain key "dbname"

                                config should not {
                                    contain key "query_params"
                                    contain key "filter"
                                    contain key "protocol"
                                    contain key "since"
                                    contain key "port"
                                }
                        }
                }
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should "reject trigger update without passing in any updatable parameters" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskProps = wp
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

                val username = myCloudantCreds.user
                val password = myCloudantCreds.password
                val host = myCloudantCreds.host()
                val dbName = myCloudantCreds.dbname

                // create whisk stuff
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> username.toJson,
                            "password" -> password.toJson,
                            "host" -> host.toJson,
                            "dbname" -> dbName.toJson
                        ))
                }
                feedCreationResult.stdout should include("ok")

                val actionName = s"$packageName/$feed"
                val run = wsk.action.invoke(actionName, parameters = Map(
                    "triggerName" -> triggerName.toJson,
                    "lifecycleEvent" -> "UPDATE".toJson,
                    "authKey" -> wskProps.authKey.toJson
                ))

                withActivation(wsk.activation, run) {
                    activation =>
                        activation.response.success shouldBe false
                }
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should "reject trigger update when query_params is passed in and no filter is defined" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskProps = wp
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

                val username = myCloudantCreds.user
                val password = myCloudantCreds.password
                val host = myCloudantCreds.host()
                val dbName = myCloudantCreds.dbname

                // create whisk stuff
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> username.toJson,
                            "password" -> password.toJson,
                            "host" -> host.toJson,
                            "dbname" -> dbName.toJson
                        ))
                }
                feedCreationResult.stdout should include("ok")

                val actionName = s"$packageName/$feed"
                val run = wsk.action.invoke(actionName, parameters = Map(
                    "triggerName" -> triggerName.toJson,
                    "lifecycleEvent" -> "UPDATE".toJson,
                    "authKey" -> wskProps.authKey.toJson,
                    "query_params" -> JsObject("type" -> JsString("tomato"))
                ))

                withActivation(wsk.activation, run) {
                    activation =>
                        activation.response.success shouldBe false
                }
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should "filter out triggers that do not meet the filter criteria before and after updating query_params" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskProps = wp // shadow global props and make implicit
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val ruleName = s"dummyCloudantRule-${System.currentTimeMillis}"
            val actionName = s"dummyCloudantAction-${System.currentTimeMillis}"
            val packageName = "dummyCloudantPackage"
            val feed = "changes"

            try {
                CloudantUtil.setUp(myCloudantCreds)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) => pkg.bind("/whisk.system/cloudant", name)
                }

                // create action
                assetHelper.withCleaner(wsk.action, actionName) { (action, name) =>
                    action.create(name, defaultAction)
                }

                //Create filter design doc
                val filterDesignDoc = CloudantUtil.createDesignFromFile(CloudantUtil.FILTER_DDOC_PATH).toString
                val getResponse = CloudantUtil.createDocument(myCloudantCreds, filterDesignDoc)
                getResponse.get("ok").getAsString shouldBe "true"

                println("Creating cloudant trigger feed.")
                assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson,
                            "filter" -> "test_filter/fruit".toJson,
                            "query_params" -> JsObject("type" -> JsString("tomato"))))
                }

                // create rule
                assetHelper.withCleaner(wsk.rule, ruleName) { (rule, name) =>
                    rule.create(name, trigger = triggerName, action = actionName)
                }

                val activationsBeforeCreate = wsk.activation.pollFor(N = 1, Some(triggerName), retries = maxRetries).length
                activationsBeforeCreate should be(0)

                // Create test docs in cloudant and assert that document was inserted successfully
                println("Creating a test doc-1 in the cloudant")
                val response1 = CloudantUtil.createDocument(myCloudantCreds, "{\"kind\":\"fruit\", \"type\":\"apple\"}")
                response1.get("ok").getAsString should be("true")

                println("Checking for activations")
                val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = maxRetries).length
                println(s"Found activation size (should be exactly 1): $activations")
                activations should be(1)

                println("Creating a test doc-2 in the cloudant")
                val response2 = CloudantUtil.createDocument(myCloudantCreds, "{\"kind\":\"dairy\",\"type\":\"butter\"}")
                response2.get("ok").getAsString should be("true")

                println("checking for new activations (not expected since it should be filtered out)")
                val noNewActivations = wsk.activation.pollFor(N = 2, Some(triggerName)).length
                println(s"Found activation size (should still be 1): $noNewActivations")
                noNewActivations should be(1)

                println("Updating trigger query_params.")
                val feedUpdateResult = wsk.action.invoke(s"$packageName/$feed", parameters = Map(
                    "triggerName" -> triggerName.toJson,
                    "lifecycleEvent" -> "UPDATE".toJson,
                    "authKey" -> wskProps.authKey.toJson,
                    "query_params" -> JsObject("type" -> JsString("avocado"))
                ))

                withActivation(wsk.activation, feedUpdateResult) {
                    activation =>
                        activation.response.success shouldBe true
                }

                println("Giving the trigger service a moment to process the update")
                Thread.sleep(maxRetries * 1000)

                val runResult = wsk.action.invoke(s"$packageName/$feed", parameters = Map(
                    "triggerName" -> triggerName.toJson,
                    "lifecycleEvent" -> "READ".toJson,
                    "authKey" -> wskProps.authKey.toJson
                ))

                withActivation(wsk.activation, runResult) {
                    activation =>
                        activation.response.success shouldBe true

                        inside(activation.response.result) {
                            case Some(result) =>
                                val config = result.getFields("config").head.asInstanceOf[JsObject].fields

                                config should contain("filter" -> "test_filter/fruit".toJson)
                                config should contain("query_params" -> JsObject("type" -> JsString("avocado")))
                        }
                }

                println("Creating a test doc-3 in the cloudant")
                val response3 = CloudantUtil.createDocument(myCloudantCreds, "{\"kind\":\"berry\", \"type\":\"avocado\"}")
                response3.get("ok").getAsString should be("true")

                println("Checking for new activations (should now have 2)")
                val newActivations = wsk.activation.pollFor(N = 3, Some(triggerName), retries = maxRetries).length
                println(s"Found activation size (should be 2): $newActivations")
                newActivations should be(2)

            }
            finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }
}

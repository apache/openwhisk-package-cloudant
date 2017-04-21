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
package catalog.cloudant

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

import catalog.CloudantUtil
import common.TestHelpers
import common.Wsk
import common.WskProps
import common.WskTestHelpers
import spray.json.DefaultJsonProtocol.IntJsonFormat
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.pimpAny
import common.WskActorSystem
import common.TestUtils.ANY_ERROR_EXIT

/**
 * Tests for Cloudant trigger service
 */
@RunWith(classOf[JUnitRunner])
class CloudantFeedTests
    extends FlatSpec
    with TestHelpers
    with WskTestHelpers
    with WskActorSystem {

    val wskprops = WskProps()
    val wsk = new Wsk
    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    behavior of "Cloudant trigger service"

    it should "fail on create feed when includeDocs is set" in withAssetCleaner(wskprops) {

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

            println("Creating cloudant trigger feed.")
            val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "includeDoc" -> "true".toJson,
                        "maxTriggers" -> 1.toJson),
                        expectedExitCode = 246)
            }
            feedCreationResult.stderr should include("includeDoc parameter is no longer supported")

    }

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
            var feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
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
            var feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
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
            var feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "host" -> myCloudantCreds.host().toJson),
                        expectedExitCode = 246)
            }
            feedCreationResult.stderr should include("cloudant trigger feed: missing password parameter")

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
            var feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "password" -> myCloudantCreds.password.toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "host" -> myCloudantCreds.host().toJson),
                        expectedExitCode = 246)
            }
            feedCreationResult.stderr should include("cloudant trigger feed: missing username parameter")

    }

    it should "delete trigger if its Cloudant connection is not created" in withAssetCleaner(wskprops) {

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

    it should "only invoke as many times as specified" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
        val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
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

                println("Creating cloudant trigger feed.")
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = false) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson,
                            "maxTriggers" -> 1.toJson))
                }
                feedCreationResult.stdout should include("ok")

                // Create 2 test docs in cloudant and assert that document was inserted successfully
                println("Creating a test doc-1 in the cloudant")
                val response1 = CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc_1\"}")
                response1.get("ok").getAsString() should be("true")
                println("Creating a test doc-2 in the cloudant")
                val response2 = CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc_2\"}")
                response2.get("ok").getAsString() should be("true")

                println("Checking for activations")
                val activations = wsk.activation.pollFor(N = 2, Some(triggerName)).length
                println(s"Found activation size (should be exactly 1): $activations")
                activations should be(1)

            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

}

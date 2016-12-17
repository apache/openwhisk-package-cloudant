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
package system.packages

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

/**
 * Tests for Cloudant trigger service
 */
@RunWith(classOf[JUnitRunner])
class CloudantHealthFeedTests
    extends FlatSpec
    with TestHelpers
    with WskTestHelpers
    with WskActorSystem {

    val wskprops = WskProps()
    val wsk = new Wsk
    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    behavior of "Cloudant trigger service"

    it should "bind cloudant package and fire changes trigger using changes feed" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val namespace = wsk.namespace.list().stdout.trim.split("\n").last
            val triggerName = "/" + namespace + s"/dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "/" + namespace + "/dummyCloudantPackage"
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
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson))
                }
                feedCreationResult.stdout should include("ok")

                // Feed is not actually alive yet - see issue #1954
                Thread.sleep(5000)

                // create a test doc in the sample db
                println("create a test doc and wait for trigger")
                CloudantUtil.createDocument(myCloudantCreds, "{\"test\":\"test_doc1\"}")

                // get activation list of the trigger, expecting exactly 1
                val activations = wsk.activation.pollFor(N = 1, Some(triggerName), retries = 30).length
                println(s"Found activation size (should be exactly 1): $activations")
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
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should "should not fail when specifying triggers above 1 Million" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val namespace = wsk.namespace.list().stdout.trim.split("\n").last
            val triggerName = "/" + namespace + s"/dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "/" + namespace + "/dummyCloudantPackage"
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
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName) {
                    (trigger, name) =>
                        trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                            "username" -> myCloudantCreds.user.toJson,
                            "password" -> myCloudantCreds.password.toJson,
                            "host" -> myCloudantCreds.host().toJson,
                            "dbname" -> myCloudantCreds.dbname.toJson,
                            "maxTriggers" -> 100000000.toJson))
                }
                feedCreationResult.stdout should include("ok")

            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }
    
    it should "only invoke as many times as specified" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val namespace = wsk.namespace.list().stdout.trim.split("\n").last
            val triggerName = "/" + namespace + s"/dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "/" + namespace + "/dummyCloudantPackage"
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
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName) {
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
                val activations = wsk.activation.pollFor(N = 4, Some(triggerName)).length
                println(s"Found activation size (should be exactly 1): $activations")
                activations should be(1)
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

    it should "not deny trigger creation when choosing maxTrigger count set to infinity (-1)" in withAssetCleaner(wskprops) {

        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val namespace = wsk.namespace.list().stdout.trim.split("\n").last
            val triggerName = "/" + namespace + s"/dummyCloudantTrigger-${System.currentTimeMillis}"
            val packageName = "/" + namespace + "/dummyCloudantPackage"
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
                val feedCreationResult = assetHelper.withCleaner(wsk.trigger, triggerName, confirmDelete = true) {
                (trigger, name) =>
                    trigger.create(name, feed = Some(s"$packageName/$feed"), parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "maxTriggers" -> -1.toJson),
                        expectedExitCode = 0)
                }
                feedCreationResult.stderr should not include("error")

            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }

    }

}

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

import common._
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import spray.json.DefaultJsonProtocol.{IntJsonFormat, StringJsonFormat}
import spray.json.pimpAny
import system.CloudantUtil

/**
 * Tests for Cloudant trigger service
 */
@RunWith(classOf[JUnitRunner])
class CloudantTriggerPersistencyTest
    extends FlatSpec
    with TestHelpers
    with WskTestHelpers
    with WskActorSystem {

    val dbUsername = WhiskProperties.getProperty("db.username")
    val dbPassword = WhiskProperties.getProperty("db.password")
    val dbPrefix = WhiskProperties.getProperty("db.prefix")

    val wskprops = WskProps()
    val wsk = new Wsk

    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    val cloudantTriggerDBCreds = new CloudantUtil.Credential(dbUsername, dbPassword, dbPrefix + "cloudanttrigger")

    behavior of "Cloudant trigger service"


    ignore should "persist trigger into Cloudant" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp // shadow global props and make implicit
            val namespace = wsk.namespace.list().stdout.trim.split("\n").last
            val triggerName = s"dummyCloudantTrigger-${System.currentTimeMillis}"
            val trigger = "/" + namespace + "/" + triggerName
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
                val feedCreationResult = wsk.trigger.create(trigger,
                    feed = Some(s"$packageName/$feed"),
                    parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "maxTriggers" -> 1.toJson))
                feedCreationResult.stdout should include("ok")

                println("Getting cloudanttrigger doc from the cloudant")
                val docId = s":${namespace}:${triggerName}"
                val persistedResponse = CloudantUtil.getDocument(cloudantTriggerDBCreds, docId)

                println("Deleting cloudant trigger feed.")
                val feedDeletionResult = wsk.trigger.delete(trigger)
                feedDeletionResult.stdout should include("ok")

                println("Getting cloudanttrigger doc from the cloudant")
                val removedResponse = CloudantUtil.getDocument(cloudantTriggerDBCreds, docId)

                persistedResponse.get("id").getAsString() should be(docId)
                removedResponse.get("error").getAsString() should be("not_found")
            } finally {
                CloudantUtil.unsetUp(myCloudantCreds)
            }
    }

}

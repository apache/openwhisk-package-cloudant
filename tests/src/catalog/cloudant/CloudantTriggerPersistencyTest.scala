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
import whisk.core.WhiskConfig
import whisk.core.WhiskConfig.dbPassword
import whisk.core.WhiskConfig.dbPrefix
import whisk.core.WhiskConfig.dbUsername
import common.WskActorSystem

/**
 * Tests for Cloudant trigger service
 */
@RunWith(classOf[JUnitRunner])
class CloudantFeedTests
    extends FlatSpec
    with TestHelpers
    with WskTestHelpers
    with WskActorSystem {

    val config = new WhiskConfig(Map(
        dbUsername -> null,
        dbPassword -> null,
        dbPrefix -> null))

    val wskprops = WskProps()
    val wsk = new Wsk

    val myCloudantCreds = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    val cloudantTriggerDBCreds = new CloudantUtil.Credential(config.dbUsername, config.dbPassword, config.dbPrefix + "cloudanttrigger")

    behavior of "Cloudant trigger service"

    it should "persist trigger into Cloudant" in withAssetCleaner(wskprops) {
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
                val feedCreationResult = wsk.trigger.create(triggerName,
                    feed = Some(s"$packageName/$feed"),
                    parameters = Map(
                        "username" -> myCloudantCreds.user.toJson,
                        "password" -> myCloudantCreds.password.toJson,
                        "host" -> myCloudantCreds.host().toJson,
                        "dbname" -> myCloudantCreds.dbname.toJson,
                        "maxTriggers" -> 1.toJson))
                feedCreationResult.stdout should include("ok")

                println("Getting cloudanttrigger doc from the cloudant")
                val docId = s":${wp.namespace}:${triggerName}";
                val persistedResponse = CloudantUtil.getDocument(cloudantTriggerDBCreds, docId)

                println("Deleting cloudant trigger feed.")
                val feedDeletionResult = wsk.trigger.delete(triggerName)
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

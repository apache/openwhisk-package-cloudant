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

import common._
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import spray.json.DefaultJsonProtocol._
import spray.json._
import system.CloudantUtil
import org.apache.openwhisk.utils.JsHelpers

import scala.collection.mutable.HashSet

@RunWith(classOf[JUnitRunner])
class CloudantAccountActionsTests extends FlatSpec
    with TestHelpers
    with WskTestHelpers {

    val wskprops = WskProps()
    val wsk = new Wsk

    val credential = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    behavior of "Cloudant account actions"


    it should """create cloudant database""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"
            val dbName = credential.dbname.concat("create_db")

            try {
                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("username" -> credential.user.toJson,
                                "password" -> credential.password.toJson,
                                "host" -> credential.host().toJson))
                }

                //create database
                println("Invoking the create-database action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/create-database",
                    Map("dbname" -> dbName.toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                }
                val response = CloudantUtil.readTestDatabase(credential, dbName)
                response.getStatusCode should be (200)
            }
            finally {
                CloudantUtil.deleteTestDatabase(credential, dbName)
            }
    }

    it should """create cloudant database with undefined dbname""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"

            val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
            println("Fetching cloudant package.")
            packageGetResult.stdout should include("ok")

            println("Creating cloudant package binding.")
            assetHelper.withCleaner(wsk.pkg, packageName) {
                (pkg, name) =>
                    pkg.bind("/whisk.system/cloudant", name,
                        Map("username" -> credential.user.toJson,
                            "password" -> credential.password.toJson,
                            "host" -> credential.host().toJson))
            }

            //create database
            println("Invoking the create-database action.")
            withActivation(wsk.activation, wsk.action.invoke(s"$packageName/create-database")) {
                activation =>
                    activation.response.success shouldBe false
                    val result = activation.response.result.get
                    result.fields.get("error") shouldBe Some(JsString("dbname is required."))
            }
    }

    it should """read cloudant database""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"

            try {
                CloudantUtil.setUp(credential)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("username" -> credential.user.toJson,
                                "password" -> credential.password.toJson,
                                "host" -> credential.host().toJson))
                }

                println("Invoking the read-database action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/read-database",
                    Map("dbname" -> credential.dbname.toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("db_name") shouldBe Some(JsString(credential.dbname))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """read cloudant database that does not exist""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"

            val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
            println("Fetching cloudant package.")
            packageGetResult.stdout should include("ok")

            println("Creating cloudant package binding.")
            assetHelper.withCleaner(wsk.pkg, packageName) {
                (pkg, name) =>
                    pkg.bind("/whisk.system/cloudant", name,
                        Map("username" -> credential.user.toJson,
                            "password" -> credential.password.toJson,
                            "host" -> credential.host().toJson))
            }

            println("Invoking the read-database action.")
            withActivation(wsk.activation, wsk.action.invoke(s"$packageName/read-database",
                Map("dbname" -> "doesNotExistDB".toJson))) {
                activation =>
                    activation.response.success shouldBe false
                    val result = activation.response.result.get
                    JsHelpers.getFieldPath(result, "error", "statusCode") shouldBe Some(JsNumber(404))
            }
    }

    it should """delete cloudant database""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"

            try {
                CloudantUtil.setUp(credential)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("username" -> credential.user.toJson,
                                "password" -> credential.password.toJson,
                                "host" -> credential.host().toJson))
                }

                println("Invoking the delete-database action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/delete-database",
                    Map("dbname" -> credential.dbname.toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                }
                val response = CloudantUtil.readTestDatabase(credential)
                response.get("error").getAsString shouldBe "not_found"
                response.get("reason").getAsString shouldBe "Database does not exist."
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """delete cloudant database with incorrect hostname""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"

            try {
                CloudantUtil.setUp(credential)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("username" -> credential.user.toJson,
                                "password" -> credential.password.toJson,
                                "host" -> "invalidHost".toJson))
                }

                println("Invoking the delete-database action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/delete-database",
                    Map("dbname" -> credential.dbname.toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe defined
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list all cloudant databases""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"

            try {
                CloudantUtil.setUp(credential)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("username" -> credential.user.toJson,
                                "password" -> credential.password.toJson,
                                "host" -> credential.host().toJson))
                }

                println("Invoking the list-all-databases action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/list-all-databases")) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        val matchedDBs = new HashSet[JsValue]
                        val databases = result.fields("all_databases").asInstanceOf[JsArray].elements
                        databases map {
                            case x@JsString(credential.dbname) => matchedDBs.add(x)
                            case _ => None
                        }
                        matchedDBs.size should be > 0
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list all cloudant databases with incorrect user""" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            implicit val wskprops = wp
            val packageName = "dummyCloudantPackage"

            try {
                CloudantUtil.setUp(credential)

                val packageGetResult = wsk.pkg.get("/whisk.system/cloudant")
                println("Fetching cloudant package.")
                packageGetResult.stdout should include("ok")

                println("Creating cloudant package binding.")
                assetHelper.withCleaner(wsk.pkg, packageName) {
                    (pkg, name) =>
                        pkg.bind("/whisk.system/cloudant", name,
                            Map("username" -> "invalidUser".toJson,
                                "password" -> credential.password.toJson,
                                "host" -> credential.host().toJson))
                }

                println("Invoking the list-all-databases action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/list-all-databases")) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        JsHelpers.getFieldPath(result, "error", "statusCode") shouldBe Some(JsNumber(401))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

}

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

import java.util.Date

import common._
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import spray.json.DefaultJsonProtocol._
import spray.json._
import system.CloudantUtil
import org.apache.openwhisk.utils.JsHelpers

@RunWith(classOf[JUnitRunner])
class CloudantDatabaseActionsTests extends FlatSpec
    with TestHelpers
    with WskTestHelpers {

    val wskprops = WskProps()
    val wsk = new Wsk

    val credential = CloudantUtil.Credential.makeFromVCAPFile("cloudantNoSQLDB", this.getClass.getSimpleName)

    behavior of "Cloudant database actions"


    it should """create cloudant document""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val doc = CloudantUtil.createDocParameterForWhisk().get("doc").getAsString
                val docJSObj = doc.parseJson.asJsObject

                println("Invoking the create-document action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/create-document",
                    Map("doc" -> docJSObj))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                }
                val getResponse = CloudantUtil.getDocument(credential, "testId")
                Some(JsString(getResponse.get("date").getAsString)) shouldBe docJSObj.fields.get("date")
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """read cloudant document""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                println("Invoking the read-document action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/read-document",
                    Map(
                        "docid" -> response.get("id").getAsString.toJson,
                        "params" -> JsObject("revs_info" -> JsBoolean(true))))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("date") shouldBe defined
                        activation.response.result.get.fields.get("_rev") shouldBe defined
                        activation.response.result.get.fields.get("_revs_info") shouldBe defined
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """read cloudant document with read action""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                println("Invoking the read action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/read",
                    Map(
                        "docid" -> response.get("id").getAsString.toJson,
                        "params" -> JsObject("revs_info" -> JsBoolean(true))))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("date") shouldBe defined
                        activation.response.result.get.fields.get("_rev") shouldBe defined
                        activation.response.result.get.fields.get("_revs_info") shouldBe defined
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """read cloudant document with undefined docid""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                println("Invoking the read-document action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/read-document"))
                    {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("docid is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """write cloudant document""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val doc = CloudantUtil.createDocParameterForWhisk().get("doc").getAsString
                val docJSObj = doc.parseJson.asJsObject

                println("Invoking the write action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/write",
                    Map("doc" -> docJSObj))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                }
                val getResponse = CloudantUtil.getDocument(credential, "testId")
                Some(JsString(getResponse.get("date").getAsString)) shouldBe docJSObj.fields.get("date")
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """write existing cloudant document with overwrite""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                val docJSObj = doc.parseJson.asJsObject

                println("Invoking the write action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/write",
                    Map("doc" -> docJSObj,
                        "overwrite" -> "true".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                }
                val getResponse = CloudantUtil.getDocument(credential, "testId")
                Some(JsString(getResponse.get("date").getAsString)) shouldBe docJSObj.fields.get("date")
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """write new cloudant document with overwrite""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val doc = CloudantUtil.createDocParameterForWhisk().get("doc").getAsString
                val docJSObj = doc.parseJson.asJsObject

                println("Invoking the write action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/write",
                    Map("doc" -> docJSObj,
                        "overwrite" -> "true".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                }
                val getResponse = CloudantUtil.getDocument(credential, "testId")
                Some(JsString(getResponse.get("date").getAsString)) shouldBe docJSObj.fields.get("date")
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """update cloudant document""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                val docJSObj = doc.parseJson.asJsObject
                val updatedDoc = JsObject(docJSObj.fields
                        + ("_rev" -> JsString(response.get("rev").getAsString))
                        + ("updated" -> JsBoolean(true)))

                println("Invoking the update-document action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/update-document",
                    Map("doc" -> updatedDoc))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                        activation.response.result.get.fields.get("rev") shouldBe defined
                }
                val getResponse = CloudantUtil.getDocument(credential, "testId")
                getResponse.get("updated").getAsBoolean shouldBe true
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """update cloudant document with missing revision""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                val docJSObj = doc.parseJson.asJsObject
                val updatedDoc = JsObject(docJSObj.fields + ("updated" -> JsBoolean(true)))

                println("Invoking the update-document action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/update-document",
                    Map("doc" -> updatedDoc))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("doc and doc._rev are required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """delete cloudant document""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                println("Invoking the delete-document action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/delete-document",
                    Map("docid" -> response.get("id").getAsString.toJson,
                        "docrev" -> response.get("rev").getAsString.toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                        activation.response.result.get.fields.get("rev") shouldBe defined
                        activation.response.result.get.fields.get("rev") shouldBe defined
                }
                //Assert that document does not exist
                val getResponse = CloudantUtil.getDocument(credential, response.get("id").getAsString)
                getResponse.get("error").getAsString shouldBe "not_found"
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """delete cloudant document with undefined docid""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                println("Invoking the delete-document action.")
                withActivation(wsk.activation, wsk.action.invoke(s"$packageName/delete-document",
                    Map("docrev" -> response.get("rev").getAsString.toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("docid is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list cloudant documents""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                val docJSObj = doc.parseJson.asJsObject
                println("Invoking the list-documents action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/list-documents",
                    Map("doc" -> docJSObj))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("total_rows") shouldBe Some(JsNumber("1"))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list cloudant documents with undefined host""" in withAssetCleaner(wskprops) {
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
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test doc
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                val docJSObj = doc.parseJson.asJsObject
                println("Invoking the list-documents action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/list-documents",
                    Map("doc" -> docJSObj))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("cloudant account host is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list all cloudant design documents""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create design doc
                val designDoc = CloudantUtil.createDesignFromFile(CloudantUtil.INDEX_DDOC_PATH)
                val response = CloudantUtil.createIndex(credential, designDoc.toString)
                response.get("result").getAsString shouldBe "created"

                println("Invoking the list-design-documents action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/list-design-documents",
                    Map("includedocs" -> "true".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        val rows = result.fields("rows").asInstanceOf[JsArray].elements(0).asJsObject
                        rows.fields.get("doc") shouldBe defined
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list all cloudant design documents with undefined dbname""" in withAssetCleaner(wskprops) {
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

                println("Invoking the list-design-documents action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/list-design-documents",
                    Map("includedocs" -> "true".toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("dbname is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """create cloudant query index""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test design index doc
                val indexDesignDoc = CloudantUtil.createDesignFromFile(CloudantUtil.INDEX_DDOC_PATH).toString
                val indexDocJSObj = indexDesignDoc.parseJson.asJsObject

                println("Invoking the create-query-index action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/create-query-index",
                    Map("index" -> indexDocJSObj))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("result") shouldBe Some(JsString("created"))
                        val docResponse = CloudantUtil.getDocument(credential, "_design/test-query-index")
                        docResponse.get("views").toString should include ("test-query-index")
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """create cloudant query index with undefined index""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                println("Invoking the create-query-index action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/create-query-index")) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("index is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list cloudant query indexes""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test design index doc
                val indexDesignDoc = CloudantUtil.createDesignFromFile(CloudantUtil.INDEX_DDOC_PATH).toString
                val response = CloudantUtil.createDocument(credential, indexDesignDoc)
                response.get("ok").getAsString shouldBe "true"

                println("Invoking the list-query-indexes action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/list-query-indexes")) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        val indexes = result.fields("indexes").asInstanceOf[JsArray].elements(0).asJsObject
                        indexes.fields.get("name") shouldBe Some(JsString("_all_docs"))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """list cloudant query indexes with incorrect dbname""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.substring(1).toJson))
                }

                println("Invoking the list-query-indexes action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/list-query-indexes")) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        JsHelpers.getFieldPath(result, "error", "statusCode") shouldBe Some(JsNumber(404))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant exec query find""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                val docJSObj = doc.parseJson.asJsObject
                println("Invoking the exec-query-find action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/exec-query-find",
                    Map("query" -> JsObject("selector" -> JsObject("_id" -> JsObject("$gt" -> JsNumber(0))))))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        val docs = result.fields("docs").asInstanceOf[JsArray].elements(0).asJsObject
                        docs.fields.get("date") shouldBe docJSObj.fields.get("date")
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant exec query search""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test document for search query
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                //Create test design doc
                val designDoc = CloudantUtil.createDesignFromFile(CloudantUtil.VIEW_AND_SEARCH_DDOC_PATH).toString
                val getResponse = CloudantUtil.createDocument(credential, designDoc)
                getResponse.get("ok").getAsString shouldBe "true"

                //Create search query
                val needle = new Date().toString.substring(0, 2) + '*'
                println("Invoking the exec-query-search action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/exec-query-search",
                    Map("search" -> JsObject("q" -> s"date:$needle".toJson),
                        "docid" -> "test_design".toJson,
                        "indexname" -> "test_search".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("bookmark") shouldBe defined
                        val rows = result.fields("rows").asInstanceOf[JsArray].elements(0).asJsObject
                        rows.fields.get("id") shouldBe Some(JsString("testId"))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant exec query search with undefined search""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test document for search query
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                //Create test design doc
                val designDoc = CloudantUtil.createDesignFromFile(CloudantUtil.VIEW_AND_SEARCH_DDOC_PATH).toString
                val getResponse = CloudantUtil.createDocument(credential, designDoc)
                getResponse.get("ok").getAsString shouldBe "true"

                //Create search query
                println("Invoking the exec-query-search action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/exec-query-search",
                    Map("docid" -> "test_design".toJson,
                        "indexname" -> "test_search".toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("search query is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant exec query view""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test document for search query
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                //Create test design doc
                val designDoc = CloudantUtil.createDesignFromFile(CloudantUtil.VIEW_AND_SEARCH_DDOC_PATH).toString
                val getResponse = CloudantUtil.createDocument(credential, designDoc)
                getResponse.get("ok").getAsString shouldBe "true"

                //Create search query
                println("Invoking the exec-query-view action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/exec-query-view",
                    Map("docid" -> "test_design".toJson,
                        "viewname" -> "test_view".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        val rows = result.fields("rows").asInstanceOf[JsArray].elements(0).asJsObject
                        rows.fields.get("key") shouldBe Some(JsString("testId"))
                        rows.fields.get("value") shouldBe Some(JsNumber(1))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant exec query view with undefined viewname""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test document for search query
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                //Create test design doc
                val designDoc = CloudantUtil.createDesignFromFile(CloudantUtil.VIEW_AND_SEARCH_DDOC_PATH).toString
                val getResponse = CloudantUtil.createDocument(credential, designDoc)
                getResponse.get("ok").getAsString shouldBe "true"

                //Create search query
                println("Invoking the exec-query-view action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/exec-query-view",
                    Map("docid" -> "test_design".toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("viewname is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant delete query index""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test index
                val index = CloudantUtil.createDesignFromFile(CloudantUtil.INDEX_DDOC_PATH)
                val response = CloudantUtil.createIndex(credential, index.toString)
                response.get("result").getAsString shouldBe "created"
                val id = response.get("id").getAsString

                println("Invoking the delete-query-index action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/delete-query-index",
                    Map("docid" -> id.toJson,
                        "indexname" -> index.get("name").getAsString.toJson,
                        "indextype" -> "json".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("ok") shouldBe Some(JsBoolean(true))
                }
                val getResponse = CloudantUtil.getDocument(credential, id)
                getResponse.get("error").getAsString shouldBe "not_found"
                getResponse.get("reason").getAsString shouldBe "deleted"
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant delete query index with undefined indextype""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test index
                val index = CloudantUtil.createDesignFromFile(CloudantUtil.INDEX_DDOC_PATH)
                val response = CloudantUtil.createIndex(credential, index.toString)
                response.get("result").getAsString shouldBe "created"
                val id = response.get("id").getAsString

                println("Invoking the delete-query-index action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/delete-query-index",
                    Map("docid" -> id.toJson,
                        "indexname" -> index.get("name").getAsString.toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("indextype is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant delete view""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                //Create test index
                val view = CloudantUtil.createDesignFromFile(CloudantUtil.VIEW_AND_SEARCH_DDOC_PATH)
                val response = CloudantUtil.createDocument(credential, view.toString)
                response.get("ok").getAsString shouldBe "true"
                val id = response.get("id").getAsString

                println("Invoking the delete-view action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/delete-view",
                    Map("docid" -> id.toJson,
                        "viewname" -> "test_view".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("ok") shouldBe Some(JsBoolean(true))
                }
                //Assert that view is deleted
                val getResponse = CloudantUtil.getDocument(credential, id)
                getResponse.get("views").toString shouldBe "{}"
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant delete view with undefined docic""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                println("Invoking the delete-view action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/delete-view",
                    Map("viewname" -> "test_view".toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("docid is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant create bulk documents""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                val numDocs = 5
                val docsArray = CloudantUtil.createDocumentArray(numDocs).toString
                val docsJsArray = docsArray.parseJson.asInstanceOf[JsArray]

                println("Invoking the manage-bulk-documents.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/manage-bulk-documents",
                    Map("docs" -> JsObject("docs" -> docsJsArray)))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        for (i <- 1 to numDocs) {
                            val response = CloudantUtil.getDocument(credential, s"testId$i")
                            val doc = result.fields("docs").asInstanceOf[JsArray].elements(i-1).asJsObject
                            Some(JsString(response.get("_rev").getAsString)) shouldBe doc.fields.get("rev")
                        }
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant update bulk documents""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                val numDocs = 5
                val bulkDocs = CloudantUtil.createDocumentArray(numDocs)
                //Create test docs for updating
                val responses = CloudantUtil.bulkDocuments(credential, bulkDocs)
                val updateDocsArray = CloudantUtil.updateDocsWithOnlyIdAndRev(responses).toString
                val updatedDocsJsArray = updateDocsArray.parseJson.asInstanceOf[JsArray]

                println("Invoking the manage-bulk-documents.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/manage-bulk-documents",
                    Map("docs" -> JsObject("docs" -> updatedDocsJsArray)))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        for (i <- 1 to numDocs) {
                            val response = CloudantUtil.getDocument(credential, s"testId$i")
                            val doc = result.fields("docs").asInstanceOf[JsArray].elements(i-1).asJsObject
                            Some(JsString(response.get("_rev").getAsString)) shouldBe doc.fields.get("rev")
                        }
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant update bulk documents with malformed JSON params""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                val numDocs = 5
                val bulkDocs = CloudantUtil.createDocumentArray(numDocs)
                //Create test docs for updating
                val responses = CloudantUtil.bulkDocuments(credential, bulkDocs)
                val updateDocsArray = CloudantUtil.updateDocsWithOnlyIdAndRev(responses).toString
                val updatedDocsJsArray = updateDocsArray.parseJson.asInstanceOf[JsArray]

                println("Invoking the manage-bulk-documents.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/manage-bulk-documents",
                    Map("docs" -> updatedDocsJsArray))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        JsHelpers.getFieldPath(result, "error", "statusCode") shouldBe Some(JsNumber(400))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant delete bulk documents""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                val numDocs = 5
                val bulkDocs = CloudantUtil.createDocumentArray(numDocs)
                //Create test docs for updating
                val responses = CloudantUtil.bulkDocuments(credential, bulkDocs)
                //Update document array with deleted field
                val deleteDocsArray = CloudantUtil.addDeletedPropertyToDocs(responses).toString
                val deleteddDocsJsArray = deleteDocsArray.parseJson.asInstanceOf[JsArray]

                println("Invoking the manage-bulk-documents.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/manage-bulk-documents",
                    Map("docs" -> JsObject("docs" -> deleteddDocsJsArray)))) {
                    activation =>
                        activation.response.success shouldBe true
                        for (i <- 1 to numDocs) {
                            val response = CloudantUtil.getDocument(credential, s"testId$i")
                            response.get("error").getAsString shouldBe "not_found"
                            response.get("reason").getAsString shouldBe "deleted"
                        }
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """cloudant delete bulk documents with undefined password""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }
                val numDocs = 5
                val bulkDocs = CloudantUtil.createDocumentArray(numDocs)
                //Create test docs for updating
                val responses = CloudantUtil.bulkDocuments(credential, bulkDocs)
                //Update document array with deleted field
                val deleteDocsArray = CloudantUtil.addDeletedPropertyToDocs(responses).toString
                val deleteddDocsJsArray = deleteDocsArray.parseJson.asInstanceOf[JsArray]

                println("Invoking the manage-bulk-documents.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/manage-bulk-documents",
                    Map("docs" -> JsObject("docs" -> deleteddDocsJsArray)))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("cloudant account password is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """read changes feed""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val doc = CloudantUtil.createDocParameterForWhisk().get("doc").getAsString
                val docJSObj = doc.parseJson.asJsObject

                println("Invoking the read-changes-feed action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/read-changes-feed",
                    Map("doc" -> docJSObj))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("last_seq") shouldBe defined
                        result.fields.get("pending") shouldBe Some(JsNumber("0"))
                        result.fields("results").asInstanceOf[JsArray].elements.size shouldBe 0
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """read changes feed with undefined dbname""" in withAssetCleaner(wskprops) {
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

                val doc = CloudantUtil.createDocParameterForWhisk().get("doc").getAsString
                val docJSObj = doc.parseJson.asJsObject

                println("Invoking the read-changes-feed action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/read-changes-feed",
                    Map("doc" -> docJSObj))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("dbname is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """create attachment""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                //Create test document
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                //Get attachment text file
                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH
                val attachmentData = CloudantUtil.readFile(attachFile).trim()

                println("Invoking the create-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/create-attachment",
                    Map("docid" -> response.get("id").getAsString.toJson,
                        "docrev" -> response.get("rev").getAsString.toJson,
                        "attachment" -> attachmentData.toJson,
                        "attachmentname" -> attachFile.getName.toJson,
                        "contenttype" -> "text/plain".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                        activation.response.result.get.fields.get("rev") shouldBe defined
                }
                val getResponse = CloudantUtil.getDocument(credential, "testId")
                getResponse.get("_attachments").getAsJsonObject().has(attachFile.getName()) shouldBe true
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """create attachment with undefined attachment""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                //Create test document
                val doc = CloudantUtil.createDocParameterForWhisk.get("doc").getAsString
                val response = CloudantUtil.createDocument(credential, doc)
                response.get("ok").getAsString shouldBe "true"

                //Get attachment text file
                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH

                println("Invoking the create-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/create-attachment",
                    Map("docid" -> response.get("id").getAsString.toJson,
                        "docrev" -> response.get("rev").getAsString.toJson,
                        "attachmentname" -> attachFile.getName.toJson,
                        "contenttype" -> "text/plain".toJson))) {
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

    it should """read attachment""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH
                val id = CloudantUtil.createDocumentWithAttachment(credential, attachFile).getId
                val origData = CloudantUtil.readFile(CloudantUtil.ATTACHMENT_FILE_PATH)
                val bytes = Array.fill[Byte](100)(0)

                println("Invoking the read-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/read-attachment",
                    Map("docid" -> id.toJson,
                        "attachmentname" -> attachFile.getName.toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        var i = 0
                        result.fields("data").asInstanceOf[JsArray].elements.foreach({ x =>
                            bytes.update(i, x.convertTo[Byte])
                            i += 1
                        })
                        val byteString = new String(bytes, "utf-8")
                        origData.trim shouldBe byteString.trim
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """read attachment with undefined attachmentname""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH
                val id = CloudantUtil.createDocumentWithAttachment(credential, attachFile).getId

                println("Invoking the read-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/read-attachment",
                    Map("docid" -> id.toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("attachmentname is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """update attachment""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH
                val id = CloudantUtil.createDocumentWithAttachment(credential, attachFile).getId
                val attachmentName = "attach.txt"
                val getResponse = CloudantUtil.getDocument(credential, id)
                val attachment = getResponse.get("_attachments").getAsJsonObject.get(attachmentName).getAsJsonObject

                println("Invoking the update-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/update-attachment",
                    Map("docid" -> id.toJson,
                        "attachmentname" -> attachFile.getName.toJson,
                        "docrev" -> getResponse.get("_rev").getAsString.toJson,
                        "attachment" -> "new_update_string".toJson,
                        "attachmentname" -> attachmentName.toJson,
                        "contenttype" -> "text/plain".toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        val result = activation.response.result.get
                        result.fields.get("id") shouldBe Some(JsString(id))
                }
                val docResponse = CloudantUtil.getDocument(credential, id)
                val updatedAttachment = docResponse.get("_attachments").getAsJsonObject.get(attachmentName).getAsJsonObject
                getResponse.get("_attachments").getAsJsonObject.has(attachmentName) shouldBe true
                attachment.get("revpos") should not be updatedAttachment.get("revpos")
                attachment.get("digest") should not be updatedAttachment.get("digest")
        }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """update attachment with undefined contenttype""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH
                val id = CloudantUtil.createDocumentWithAttachment(credential, attachFile).getId
                val attachmentName = "attach.txt"
                val getResponse = CloudantUtil.getDocument(credential, id)

                println("Invoking the update-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/update-attachment",
                    Map("docid" -> id.toJson,
                        "attachmentname" -> attachFile.getName.toJson,
                        "docrev" -> getResponse.get("_rev").getAsString.toJson,
                        "attachment" -> "new_update_string".toJson,
                        "attachmentname" -> attachmentName.toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        result.fields.get("error") shouldBe Some(JsString("contenttype is required."))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """delete attachment""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                //Create document with attachment
                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH
                val id = CloudantUtil.createDocumentWithAttachment(credential, attachFile).getId
                val getResponse = CloudantUtil.getDocument(credential, id)

                println("Invoking the delete-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/delete-attachment",
                    Map("docid" -> getResponse.get("_id").getAsString.toJson,
                        "docrev" -> getResponse.get("_rev").getAsString.toJson,
                        "attachmentname" -> attachFile.getName.toJson))) {
                    activation =>
                        activation.response.success shouldBe true
                        activation.response.result.get.fields.get("id") shouldBe defined
                        activation.response.result.get.fields.get("rev") shouldBe defined
                }
                //Assert that attachment does not exist in doc
                val response = CloudantUtil.getDocument(credential, id).toString
                val docJSObject = response.parseJson.asJsObject
                docJSObject.fields.get("_attachments") should not be defined
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }

    it should """delete attachment with undefined doc revision""" in withAssetCleaner(wskprops) {
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
                                "host" -> credential.host().toJson,
                                "dbname" -> credential.dbname.toJson))
                }

                //Create document with attachment
                val attachFile = CloudantUtil.ATTACHMENT_FILE_PATH
                val id = CloudantUtil.createDocumentWithAttachment(credential, attachFile).getId
                val getResponse = CloudantUtil.getDocument(credential, id)

                println("Invoking the delete-attachment action.")
                withActivation(wsk.activation, wsk.action.invoke(s"${packageName}/delete-attachment",
                    Map("docid" -> getResponse.get("_id").getAsString.toJson,
                        "attachmentname" -> attachFile.getName.toJson))) {
                    activation =>
                        activation.response.success shouldBe false
                        val result = activation.response.result.get
                        JsHelpers.getFieldPath(result, "error", "statusCode") shouldBe Some(JsNumber(400))
                }
            }
            finally {
                CloudantUtil.unsetUp(credential)
            }
    }
}

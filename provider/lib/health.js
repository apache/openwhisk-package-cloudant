var si = require('systeminformation');
var v8 = require('v8');
var request = require('request');
var _ = require('lodash');
var URL = require('url').URL;
var constants = require('./constants.js');

module.exports = function(logger, utils) {

    // Health Endpoint
    this.endPoint = '/health';

    var triggerName;
    var canaryDocID;
    var monitorStatus;
    var monitorStages = ['triggerStarted', 'triggerFired', 'triggerStopped'];
    var healthMonitor = this;

    // Health Logic
    this.health = function (req, res) {

        var stats = {triggerCount: Object.keys(utils.triggers).length};

        // get all system stats in parallel
        Promise.all([
            si.mem(),
            si.currentLoad(),
            si.fsSize(),
            si.networkStats(),
            si.inetLatency(utils.routerHost)
        ])
        .then(results => {
            stats.triggerMonitor = monitorStatus;
            stats.memory = results[0];
            stats.cpu = _.omit(results[1], 'cpus');
            stats.disk = results[2];
            stats.network = results[3];
            stats.apiHostLatency = results[4];
            stats.heapStatistics = v8.getHeapStatistics();
            res.send(stats);
        })
        .catch(error => {
            stats.error = error;
            res.send(stats);
        });
    };

    this.monitor = function(apikey) {
        var method = 'monitor';

        var auth = apikey.split(':');

        if (triggerName) {
            monitorStatus = Object.assign({}, utils.monitorStatus);
            utils.monitorStatus = {};

            var monitorStatusSize = Object.keys(monitorStatus).length;
            if (monitorStatusSize < 5) {
                //we have a failure in one of the stages
                var stageFailed = monitorStages[monitorStatusSize - 2];
                monitorStatus[stageFailed] = 'failed';
            }
            var existingTriggerID = `:_:${triggerName}`;
            var existingCanaryID = canaryDocID;

            //delete trigger feed from database
            healthMonitor.deleteDocFromDB(existingTriggerID, 0);

            //delete the trigger
            var uri = utils.uriHost + '/api/v1/namespaces/_/triggers/' + triggerName;
            healthMonitor.deleteTrigger(existingTriggerID, uri, auth, 0);

            //delete the canary doc
            healthMonitor.deleteDocFromDB(existingCanaryID, 0);
        }

        //create new cloudant trigger and canary doc
        var docSuffix = utils.worker + utils.host + '_' + Date.now();
        triggerName = 'cloudant_' + docSuffix;
        canaryDocID = 'canary_' + docSuffix;

        //update status monitor object
        utils.monitorStatus.triggerName = triggerName;
        utils.monitorStatus.triggerType = 'changes';

        var triggerURL = utils.uriHost + '/api/v1/namespaces/_/triggers/' + triggerName;
        var triggerID = `:_:${triggerName}`;
        healthMonitor.createTrigger(triggerURL, auth)
        .then(info => {
            logger.info(method, triggerID, info);
            var newTrigger = healthMonitor.createCloudantTrigger(triggerID, apikey);
            healthMonitor.createDocInDB(triggerID, newTrigger);
        })
        .catch(err => {
            logger.error(method, triggerID, err);
        });
    };

    this.createCloudantTrigger = function(triggerID, apikey) {
        var method = 'createCloudantTrigger';

        var dbURL = new URL(utils.db.config.url);
        var dbName = utils.db.config.db;

        var newTrigger = {
            apikey: apikey,
            id: triggerID,
            host: dbURL.hostname,
            port: dbURL.port,
            protocol: dbURL.protocol.replace(':', ''),
            dbname: dbName,
            user: dbURL.username,
            pass: dbURL.password,
            filter: constants.MONITOR_DESIGN_DOC + '/' + constants.DOCS_FOR_MONITOR,
            query_params: {host: utils.host},
            maxTriggers: 1,
            worker: utils.worker,
            monitor: utils.host
        };

        return newTrigger;
    };

    this.createTrigger = function(triggerURL, auth) {
        var method = 'createTrigger';

        return new Promise(function(resolve, reject) {
            request({
                method: 'put',
                uri: triggerURL,
                auth: {
                    user: auth[0],
                    pass: auth[1]
                },
                json: true,
                body: {}
            }, function (error, response) {
                if (error || response.statusCode >= 400) {
                    reject('monitoring trigger create request failed');
                }
                else {
                    resolve('monitoring trigger create request was successful');
                }
            });
        });
    };

    this.createDocInDB = function(docID, doc) {
        var method = 'createDocInDB';

        utils.db.insert(doc, docID, function (err) {
            if (!err) {
                logger.info(method, docID, 'was successfully inserted');
                if (doc.monitor) {
                    setTimeout(function () {
                        var canaryDoc = {
                            isCanaryDoc: true,
                            host: utils.host
                        };
                        healthMonitor.createDocInDB(canaryDocID, canaryDoc);
                    }, 1000 * 60);
                }
            }
            else {
                logger.error(method, docID, err);
            }
        });
    };

    this.deleteTrigger = function(triggerID, uri, auth, retryCount) {
        var method = 'deleteTrigger';

        request({
            method: 'delete',
            uri: uri,
            auth: {
                user: auth[0],
                pass: auth[1]
            },
        }, function (error, response) {
            logger.info(method, triggerID, 'http delete request, STATUS:', response ? response.statusCode : undefined);
            if (error || response.statusCode >= 400) {
                if (!error && response.statusCode === 409 && retryCount < 5) {
                    logger.info(method, 'attempting to delete trigger again', triggerID, 'Retry Count:', (retryCount + 1));
                    setTimeout(function () {
                        healthMonitor.deleteTrigger(triggerID, uri, auth, (retryCount + 1));
                    }, 1000);
                } else {
                    logger.error(method, triggerID, 'trigger delete request failed');
                }
            }
            else {
                logger.info(method, triggerID, 'trigger delete request was successful');
            }
        });
    };

    this.deleteDocFromDB = function(docID, retryCount) {
        var method = 'deleteDocFromDB';

        //delete from database
        utils.db.get(docID, function (err, existing) {
            if (!err) {
                utils.db.destroy(existing._id, existing._rev, function (err) {
                    if (err) {
                        if (err.statusCode === 409 && retryCount < 5) {
                            setTimeout(function () {
                                healthMonitor.deleteDocFromDB(docID, (retryCount + 1));
                            }, 1000);
                        }
                        else {
                            logger.error(method, docID, 'could not be deleted from the database');
                        }
                    }
                    else {
                        logger.info(method, docID, 'was successfully deleted from the database');
                    }
                });
            }
            else {
                logger.error(method, docID, 'could not be found in the database');
            }
        });
    };

};

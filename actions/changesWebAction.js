var request = require('request');
var moment = require('moment');

function main(params) {

    if (!params.authKey) {
        return sendError(400, 'no authKey parameter was provided');
    }
    if (!params.triggerName) {
        return sendError(400, 'no trigger name parameter was provided');
    }

    var triggerParts = parseQName(params.triggerName);
    var triggerID = `:${triggerParts.namespace}:${triggerParts.name}`;

    var triggerURL = `https://${params.apihost}/api/v1/namespaces/${triggerParts.namespace}/triggers/${triggerParts.name}`;

    var nano = require('nano')(params.DB_URL);
    var db = nano.db.use(params.DB_NAME);
    var workers = params.workers instanceof Array ? params.workers : [];

    if (params.__ow_method === "post") {

        // check for parameter errors
        if (!params.dbname) {
            return sendError(400, 'cloudant trigger feed: missing dbname parameter');
        }
        if (!params.host) {
            return sendError(400, 'cloudant trigger feed: missing host parameter');
        }
        if (!params.username) {
            return sendError(400, 'cloudant trigger feed: missing username parameter');
        }
        if (!params.password) {
            return sendError(400, 'cloudant trigger feed: missing password parameter');
        }

        var query_params;

        if (params.filter) {
            query_params = params.query_params;
            if (typeof queryParams === 'string') {
                try {
                    query_params = JSON.parse(params.query_params);
                }
                catch (e) {
                    return sendError(400, 'The query_params parameter cannot be parsed. Ensure it is valid JSON.');
                }
            }
            if (typeof query_params !== 'object') {
                return sendError(400, 'The query_params parameter is not valid JSON');
            }
        }
        else if (params.query_params) {
            return sendError(400, 'The query_params parameter is only allowed if the filter parameter is defined');
        }

        var newTrigger = {
            id: triggerID,
            host: params.host,
            port: params.port,
            protocol: params.protocol || 'https',
            dbname: params.dbname,
            user: params.username,
            pass: params.password,
            apikey: params.authKey,
            since: params.since,
            maxTriggers: params.maxTriggers || -1,
            filter: params.filter,
            query_params: query_params,
            status: {
                'active': true,
                'dateChanged': Date.now()
            }
        };

        return new Promise(function (resolve, reject) {
            verifyTriggerAuth(triggerURL, params.authKey, false)
            .then(() => {
                return verifyUserDB(newTrigger);
            })
            .then(() => {
                return getWorkerID(db, workers);
            })
            .then((worker) => {
                console.log('trigger will be assigned to worker ' + worker);
                newTrigger.worker = worker;
                return createTrigger(db, triggerID, newTrigger);
            })
            .then(() => {
                 resolve({
                     statusCode: 200,
                     headers: {'Content-Type': 'application/json'},
                     body: new Buffer(JSON.stringify({'status': 'success'})).toString('base64')
                 });
            })
            .catch(err => {
                reject(err);
            });
        });

    }
    else if (params.__ow_method === "get") {
        return new Promise(function (resolve, reject) {
            verifyTriggerAuth(triggerURL, params.authKey, false)
            .then(() => {
                return getTrigger(db, triggerID);
            })
            .then(doc => {
                var body = {
                    config: {
                        name: doc.id.split(':')[2],
                        namespace: doc.id.split(':')[1],
                        host: doc.host,
                        port: doc.port,
                        protocol: doc.protocol,
                        dbname: doc.dbname,
                        username: doc.user,
                        password: doc.pass,
                        since: doc.since,
                        filter: doc.filter,
                        query_params: doc.query_params,
                    },
                    status: {
                        active: doc.status.active,
                        dateChanged: moment(doc.status.dateChanged).utc().valueOf(),
                        dateChangedISO: moment(doc.status.dateChanged).utc().format(),
                        reason: doc.status.reason
                    }
                };
                resolve({
                    statusCode: 200,
                    headers: {'Content-Type': 'application/json'},
                    body: new Buffer(JSON.stringify(body)).toString('base64')
                });
            })
            .catch(err => {
                reject(err);
            });
        });
    }
    else if (params.__ow_method === "put") {
        return new Promise(function (resolve, reject) {
            verifyTriggerAuth(triggerURL, params.authKey, true)
            .then(() => {
                return getTrigger(db, triggerID);
            })
            .then(trigger => {
                if (trigger.status && trigger.status.active === false) {
                    reject(sendError(400, `${params.triggerName} cannot be updated because it is disabled`));
                }
                if (params.filter || params.query_params) {
                    var updatedParams = {
                        filter: trigger.filter,
                        query_params: trigger.query_params
                    };

                    if (params.filter) {
                        updatedParams.filter = params.filter;
                    }
                    if (params.query_params) {
                        if (updatedParams.filter) {
                            var query_params = params.query_params;
                            if (typeof query_params === 'string') {
                                try {
                                    query_params = JSON.parse(params.query_params);
                                }
                                catch (e) {
                                    reject(sendError(400, 'The query_params parameter cannot be parsed. Ensure it is valid JSON.'));
                                }
                            }
                            if (typeof query_params !== 'object') {
                                reject(sendError(400, 'The query_params parameter is not valid JSON'));
                            }
                            updatedParams.query_params = query_params;
                        } else {
                            reject(sendError(400, 'The query_params parameter is only allowed if the filter parameter is defined'));
                        }
                    }
                    return updateTrigger(db, triggerID, trigger, updatedParams);
                } else {
                    reject(sendError(400, 'At least one of filter or query_params parameters must be supplied'));
                }
            })
            .then(() => {
                resolve({
                    statusCode: 200,
                    headers: {'Content-Type': 'application/json'},
                    body: new Buffer(JSON.stringify({'status': 'success'})).toString('base64')
                });
           })
           .catch(err => {
               reject(err);
           });
        });
    }
    else if (params.__ow_method === "delete") {

        return new Promise(function (resolve, reject) {
            verifyTriggerAuth(triggerURL, params.authKey, true)
            .then(() => {
                return disableTrigger(db, triggerID, 0);
            })
            .then(() => {
                return deleteTrigger(db, triggerID, 0);
            })
            .then(() => {
                resolve({
                    statusCode: 200,
                    headers: {'Content-Type': 'application/json'},
                    body: new Buffer(JSON.stringify({'status': 'success'})).toString('base64')
                });
            })
            .catch(err => {
                reject(err);
            });
        });
    }
    else {
        return sendError(400, 'unsupported lifecycle event');
    }
}

function getWorkerID(db, availabeWorkers) {

    return new Promise((resolve, reject) => {
        var workerID = availabeWorkers[0] || 'worker0';

        if (availabeWorkers.length > 1) {
            db.view('triggerViews', 'triggers_by_worker', {reduce: true, group: true}, function (err, body) {
                if (!err) {
                    var triggersByWorker = {};

                    availabeWorkers.forEach(worker => {
                        triggersByWorker[worker] = 0;
                    });

                    body.rows.forEach(row => {
                        if (row.key in triggersByWorker) {
                            triggersByWorker[row.key] = row.value;
                        }
                    });

                    // find which worker has the least number of assigned triggers
                    for (var worker in triggersByWorker) {
                        if (triggersByWorker[worker] < triggersByWorker[workerID]) {
                            workerID = worker;
                        }
                    }
                    resolve(workerID);
                } else {
                    reject(err);
                }
            });
        }
        else {
            resolve(workerID);
        }
    });
}

function createTrigger(triggerDB, triggerID, newTrigger) {

    return new Promise(function(resolve, reject) {

        triggerDB.insert(newTrigger, triggerID, function (err) {
            if (!err) {
                resolve();
            }
            else {
                reject(sendError(err.statusCode, 'error creating cloudant trigger.', err.message));
            }
        });
    });
}

function getTrigger(triggerDB, triggerID) {

    return new Promise(function(resolve, reject) {

        triggerDB.get(triggerID, function (err, existing) {
            if (err) {
                reject(err);
            } else {
                resolve(existing);
            }
        });
    });
}

function disableTrigger(triggerDB, triggerID, retryCount) {

    return new Promise(function(resolve, reject) {

        triggerDB.get(triggerID, function (err, existing) {
            if (!err) {
                var updatedTrigger = existing;
                updatedTrigger.status = {'active': false};

                triggerDB.insert(updatedTrigger, triggerID, function (err) {
                    if (err) {
                        if (err.statusCode === 409 && retryCount < 5) {
                            setTimeout(function () {
                                disableTrigger(triggerDB, triggerID, (retryCount + 1))
                                .then(() => {
                                    resolve();
                                })
                                .catch(err => {
                                    reject(err);
                                });
                            }, 1000);
                        }
                        else {
                            reject(sendError(err.statusCode, 'there was an error while marking the trigger for delete in the database.', err.message));
                        }
                    }
                    else {
                        resolve();
                    }
                });
            }
            else {
                reject(sendError(err.statusCode, 'could not find the trigger in the database'));
            }
        });
    });
}

function deleteTrigger(triggerDB, triggerID, retryCount) {

    return new Promise(function(resolve, reject) {

        triggerDB.get(triggerID, function (err, existing) {
            if (!err) {
                triggerDB.destroy(existing._id, existing._rev, function (err) {
                    if (err) {
                        if (err.statusCode === 409 && retryCount < 5) {
                            setTimeout(function () {
                                deleteTrigger(triggerDB, triggerID, (retryCount + 1))
                                .then(resolve)
                                .catch(err => {
                                    reject(err);
                                });
                            }, 1000);
                        }
                        else {
                            reject(sendError(err.statusCode, 'there was an error while deleting the trigger from the database.', err.message));
                        }
                    }
                    else {
                        resolve();
                    }
                });
            }
            else {
                reject(sendError(err.statusCode, 'could not find the trigger in the database'));
            }
        });
    });
}

function updateTrigger(triggerDB, triggerID, existing, params) {

    return new Promise(function(resolve, reject) {
        var message = 'Automatically disabled trigger while updating';
        var status = {
            'active': false,
            'dateChanged': Date.now(),
            'reason': {'kind': 'AUTO', 'statusCode': undefined, 'message': message}
        };
        existing.status = status;
        triggerDB.insert(existing, triggerID, function (err) {
            if (err) {
                reject(sendError(err.statusCode, 'there was an error while disabling the trigger in the database.', err.message));
            }
            else {
                resolve();
            }
        });
    })
    .then(() => {
        return getTrigger(triggerDB, triggerID);
    })
    .then(trigger => {
        for (var key in params) {
            trigger[key] = params[key];
        }
        var status = {
            'active': true,
            'dateChanged': Date.now()
        };
        trigger.status = status;

        return new Promise(function(resolve, reject) {
            triggerDB.insert(trigger, triggerID, function (err) {
                if (err) {
                    reject(sendError(err.statusCode, 'there was an error while updating and re-enabling the trigger in the database.', err.message));
                }
                else {
                    resolve();
                }
            });
        });
    });
}

function verifyTriggerAuth(triggerURL, authKey, isDelete) {
    var auth = authKey.split(':');

    return new Promise(function(resolve, reject) {

        request({
            method: 'get',
            url: triggerURL,
            auth: {
                user: auth[0],
                pass: auth[1]
            },
            rejectUnauthorized: false
        }, function(err, response) {
            if (err) {
                reject(sendError(400, 'Trigger authentication request failed.', err.message));
            }
            else if(response.statusCode >= 400 && !(isDelete && response.statusCode === 404)) {
                reject(sendError(response.statusCode, 'Trigger authentication request failed.'));
            }
            else {
                resolve();
            }
        });
    });
}

function verifyUserDB(triggerObj) {
    var dbURL = `${triggerObj.protocol}://${triggerObj.user}:${triggerObj.pass}@${triggerObj.host}`;

    // add port if specified
    if (triggerObj.port) {
        dbURL += ':' + triggerObj.port;
    }

    return new Promise(function(resolve, reject) {
        try {
            var nanoConnection = require('nano')(dbURL);
            var userDB = nanoConnection.use(triggerObj.dbname);
            userDB.info(function(err, body) {
                if (!err) {
                    resolve();
                }
                else {
                    reject(sendError(err.statusCode, 'error connecting to database ' + triggerObj.dbname, err.message));
                }
            });
        }
        catch(err) {
            reject(sendError(400, 'error connecting to database ' + triggerObj.dbname, err.message));
        }

    });
}

function sendError(statusCode, error, message) {
    var params = {error: error};
    if (message) {
        params.message = message;
    }

    return {
        statusCode: statusCode,
        headers: { 'Content-Type': 'application/json' },
        body: new Buffer(JSON.stringify(params)).toString('base64')
    };
}


function parseQName(qname, separator) {
    var parsed = {};
    var delimiter = separator || ':';
    var defaultNamespace = '_';
    if (qname && qname.charAt(0) === delimiter) {
        var parts = qname.split(delimiter);
        parsed.namespace = parts[1];
        parsed.name = parts.length > 2 ? parts.slice(2).join(delimiter) : '';
    } else {
        parsed.namespace = defaultNamespace;
        parsed.name = qname;
    }
    return parsed;
}


exports.main = main;



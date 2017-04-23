var _ = require('lodash');
var request = require('request');
var constants = require('./constants.js');


module.exports = function(
  logger,
  app,
  triggerDB,
  routerHost
) {

    this.logger = logger;
    this.app = app;
    this.triggerDB = triggerDB;
    this.routerHost = routerHost;

    // this is the default trigger fire limit (in the event that it was not set during trigger creation)
    this.defaultTriggerFireLimit = constants.DEFAULT_MAX_TRIGGERS;
    this.retryDelay = constants.RETRY_DELAY;
    this.retryAttempts = constants.RETRY_ATTEMPTS;

    // Log HTTP Requests
    app.use(function(req, res, next) {
        if (req.url.indexOf('/cloudanttriggers') === 0) {
            logger.info('HttpRequest', req.method, req.url);
        }
        next();
    });

    this.module = 'utils';
    this.triggers = {};

    var that = this;

    // Add a trigger: listen for changes and dispatch.
    this.createTrigger = function(dataTrigger, retryCount) {

        var method = 'createTrigger';

        var sinceToUse = dataTrigger.since ? dataTrigger.since : "now";
        var dbProtocol = dataTrigger.protocol ? dataTrigger.protocol : 'https';

        // unless specified host will default to accounturl without the https:// in front
        var dbHost = dataTrigger.host ? dataTrigger.host : dataTrigger.accounturl.replace('https://','');

        // both couch and cloudant should have their URLs in the username:password@host format
        var dbURL = dbProtocol + '://' + dataTrigger.user + ':' + dataTrigger.pass + '@' + dbHost;

        // add port if specified
        if (dataTrigger.port) {
            dbURL = dbURL + ':' + dataTrigger.port;
        }

        var nanoConnection = require('nano')(dbURL);

        try {
            var triggeredDB = nanoConnection.use(dataTrigger.dbname);

            // Listen for changes on this database.
            var feed = triggeredDB.follow({since: sinceToUse, include_docs: false});

            dataTrigger.feed = feed;
            that.triggers[dataTrigger.id] = dataTrigger;

            feed.on('change', function (change) {
                logger.info(method, 'Trigger', dataTrigger.id, 'got change from', dataTrigger.dbname);

                var triggerHandle = that.triggers[dataTrigger.id];
                if (triggerHandle && (triggerHandle.maxTriggers === -1 || triggerHandle.triggersLeft > 0)) {
                    try {
                        that.fireTrigger(dataTrigger.id, change);
                    } catch (e) {
                        logger.error(method, 'Exception occurred while firing trigger', dataTrigger.id,  e);
                    }
                }
            });

            feed.follow();

            return new Promise(function(resolve, reject) {

            feed.on('error', function (err) {
                logger.error(method,'Error occurred for trigger', dataTrigger.id, '(db ' + dataTrigger.dbname + '):', err);
                // revive the feed if an error occurred for now
                that.deleteTrigger(dataTrigger.id);
                dataTrigger.since = "now";
                if (retryCount > 0) {
                    logger.info(method, 'attempting to recreate trigger', dataTrigger.id, 'Retry Count:', (retryCount - 1));
                    that.createTrigger(dataTrigger, (retryCount - 1))
                    .then(trigger => {
                        resolve(trigger);
                    }).catch(err => {
                        reject(err);
                    });
                } else {
                    logger.error(method, "Trigger's feed produced too many errors. Deleting the trigger", dataTrigger.id, '(db ' + dataTrigger.dbname + ')');
                    reject({
                        error: err,
                        message: "Trigger's feed produced too many errors. Deleting the trigger " + dataTrigger.id
                    });
                }
            });

            feed.on('confirm', function (dbObj) {
                logger.info(method, 'Added cloudant data trigger', dataTrigger.id, 'listening for changes in database', dataTrigger.dbname);
                resolve(dataTrigger);
            });

        });

        } catch (err) {
            logger.info(method, 'caught an exception for trigger', dataTrigger.id, err);
            return Promise.reject(err);
        }

    };

    this.initTrigger = function (obj, id) {

        var method = 'initTrigger';

        // validate parameters here
        logger.info(method, 'create trigger', id, 'with the following args', obj);

        // if the trigger creation request has not set the max trigger fire limit
        // we will set it here (default value can be updated in ./constants.js)
        if (!obj.maxTriggers) {
        	obj.maxTriggers = that.defaultTriggerFireLimit;
        }

        var trigger = {
            id: id,
            accounturl: obj.accounturl,
            dbname: obj.dbname,
            user: obj.user,
            pass: obj.pass,
            host: obj.host,
            port: obj.port,
            protocol: obj.protocol,
            apikey: obj.apikey,
            since: obj.since,
            maxTriggers: obj.maxTriggers,
            triggersLeft: obj.maxTriggers
        };

        return trigger;
    };

    this.initAllTriggers = function () {

        var method = 'initAllTriggers';
        logger.info(method, 'Initializing all cloudant triggers from database.');

        triggerDB.view('filters', 'only_triggers', {include_docs: true}, function(err, body) {
            if (!err) {
                body.rows.forEach(function(trigger) {
                    if (!trigger.doc.status || trigger.doc.status.active === true) {
                        //check if trigger still exists in whisk db
                        var triggerObj = that.parseQName(trigger.doc.id);
                        var host = 'https://' + routerHost + ':' + 443;
                        var triggerURL = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;
                        var auth = trigger.doc.apikey.split(':');

                        logger.info(method, 'Checking if trigger', trigger.doc.id, 'still exists');
                        request({
                            method: 'get',
                            url: triggerURL,
                            auth: {
                                user: auth[0],
                                pass: auth[1]
                            }
                        }, function (error, response) {
                            //disable trigger in database if trigger is dead
                            if (!error && that.shouldDisableTrigger(response.statusCode)) {
                                var message = 'Automatically disabled after receiving a ' + response.statusCode + ' status code when re-creating the trigger';
                                that.disableTriggerInDB(trigger.doc.id, response.statusCode, message);
                                logger.error(method, 'trigger', trigger.doc.id, 'has been disabled due to status code:', response.statusCode);
                            }
                            else {
                                var cloudantTrigger = that.initTrigger(trigger.doc, trigger.doc.id);
                                that.createTrigger(cloudantTrigger, that.retryAttempts)
                                .then(newTrigger => {
                                    logger.info(method, 'Trigger was added.', newTrigger.id);
                                }).catch(err => {
                                    var message = 'Automatically disabled after receiving an exception when re-creating the trigger';
                                    that.disableTriggerInDB(cloudantTrigger.id, undefined, message);
                                    logger.error(method, 'Disabled trigger', cloudantTrigger.id, 'due to exception:', err);
                                });
                            }
                        });
                    }
                    else {
                        logger.info(method, 'ignoring trigger', trigger.doc._id, 'since it is disabled.');
                    }
                });
            } else {
                logger.error(method, 'could not get latest state from database');
            }
        });

    };

    this.addTriggerToDB = function (trigger, res) {

        var method = 'addTriggerToDB';
        triggerDB.insert(_.omit(trigger, 'feed'), trigger.id, function(err) {
            if (!err) {
                logger.info(method, 'trigger', trigger.id, 'was inserted into db.');
                res.status(200).json(_.omit(trigger, 'feed'));
            } else {
                that.deleteTrigger(trigger.id);
                res.status(err.statusCode).json({error: 'Cloudant trigger cannot be created. ' + err});
            }
        });

    };

    this.shouldDisableTrigger = function (statusCode) {
        return ((statusCode >= 400 && statusCode < 500) && [408, 429].indexOf(statusCode) === -1);
    };

    this.disableTriggerInDB = function (id, statusCode, message) {

        var method = 'disableTriggerInDB';

        triggerDB.get(id, function (err, existing) {
            if (!err) {
                if (!existing.status || existing.status.active === true) {
                    var updatedTrigger = existing;
                    var status = {
                        'active': false,
                        'dateChanged': new Date().toISOString(),
                        'reason': {'kind': 'AUTO', 'statusCode': statusCode, 'message': message}
                    };
                    updatedTrigger.status = status;

                    triggerDB.insert(updatedTrigger, id, function (err) {
                        if (err) {
                            logger.error(method, 'there was an error while disabling', id, 'in database. ' + err);
                        }
                        else {
                            that.deleteTrigger(id);
                            logger.info(method, 'trigger', id, 'successfully disabled in database');
                        }
                    });
                }
            }
            else {
                if (err.statusCode === 404) {
                    logger.error(method, 'there was no trigger with id', id, 'in database.', err.error);
                } else {
                    logger.error(method, 'there was an error while getting', id, 'from database', err);
                }
            }
        });
    };

    this.deleteTriggerFromDB = function (id, res) {

        var method = 'deleteTriggerFromDB';

        triggerDB.get(id, function(err, existing) {
            if (!err) {
                triggerDB.destroy(existing._id, existing._rev, function(err) {
                    if (err) {
                        logger.error(method, 'there was an error while deleting', id, 'from database. ' + err);
                        if (res) {
                            res.status(err.statusCode).json({ error: 'Cloudant data trigger ' + id  + 'cannot be deleted. ' + err } );
                        }
                    } else {
                        that.deleteTrigger(id);
                        logger.info(method, 'cloudant trigger', id, ' is successfully deleted');
                        if (res) {
                            res.send('Deleted cloudant trigger ' + id);
                        }
                    }
                });
            } else {
                if (err.statusCode === 404) {
                    logger.info(method, 'there was no trigger with id', id, 'in database.', err.error);
                    if (res) {
                        res.status(404).json({ message: 'there was no trigger with id ' + id + ' in database.' } );
                        res.end();
                    }
                } else {
                    logger.error(method, 'there was an error while getting', id, 'from database', err);
                    if (res) {
                        res.status(err.statusCode).json({ error: 'Cloudant data trigger ' + id  + ' cannot be deleted. ' + err } );
                    }
                }
            }
        });

    };

    // Delete a trigger: stop listening for changes and remove it.
    this.deleteTrigger = function (id) {
        var method = 'deleteTrigger';
        var trigger = that.triggers[id];
        if (trigger) {
            logger.info(method, 'Stopped cloudant trigger', id, 'listening for changes in database', trigger.dbname);
            trigger.feed.stop();
            delete that.triggers[id];
        } else {
            logger.info(method, 'trigger', id, 'could not be found in the trigger list.');
            return false;
        }
    };

    this.fireTrigger = function (id, change) {
        var method = 'fireTrigger';

        var dataTrigger = that.triggers[id];
        var apikey = dataTrigger.apikey;
        var triggerObj = that.parseQName(dataTrigger.id);

        var form = change;
        form.dbname = dataTrigger.dbname;

        logger.info(method, 'firing trigger', dataTrigger.id, 'with db update');

        var host = 'https://' + routerHost + ':' + 443;
        var uri = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;
        var auth = apikey.split(':');

        that.postTrigger(dataTrigger, form, uri, auth, that.retryAttempts)
         .then(triggerId => {
             logger.info(method, 'Trigger', triggerId, 'was successfully fired');
             if (dataTrigger.triggersLeft === 0) {
                 that.disableTriggerInDB(dataTrigger.id, undefined, 'Automatically disabled after reaching max triggers');
                 logger.error(method, 'no more triggers left, disabled', dataTrigger.id);
             }
         }).catch(err => {
             logger.error(method, err);
         });
    };

    this.postTrigger = function (dataTrigger, form, uri, auth, retryCount) {
        var method = 'postTrigger';

        return new Promise(function(resolve, reject) {

            request({
                method: 'post',
                uri: uri,
                auth: {
                    user: auth[0],
                    pass: auth[1]
                },
                json: form
            }, function(error, response) {
                try {
                    logger.info(method, dataTrigger.id, 'http post request, STATUS:', response ? response.statusCode : response);
                    if (error || response.statusCode >= 400) {
                        logger.error(method, 'there was an error invoking', dataTrigger.id, response ? response.statusCode : error);
                        if (!error && that.shouldDisableTrigger(response.statusCode)) {
                            //disable trigger
                            var message = 'Automatically disabled after receiving a ' + response.statusCode + ' status code when firing the trigger';
                            that.disableTriggerInDB(dataTrigger.id, response.statusCode, message);
                            reject('Disabled trigger ' + dataTrigger.id + ' due to status code: ' + response.statusCode);
                        }
                        else {
                            if (retryCount > 0) {
                                logger.info(method, 'attempting to fire trigger again', dataTrigger.id, 'Retry Count:', (retryCount - 1));
                                setTimeout(function () {
                                    that.postTrigger(dataTrigger, form, uri, auth, (retryCount - 1))
                                    .then(triggerId => {
                                        resolve(triggerId);
                                    }).catch(err => {
                                        reject(err);
                                    });
                                }, that.retryDelay);
                            } else {
                                reject('Unable to reach server to fire trigger ' + dataTrigger.id);
                            }
                        }
                    } else {
                        // only manage trigger fires if they are not infinite
                        if (dataTrigger.maxTriggers !== -1) {
                            dataTrigger.triggersLeft--;
                        }
                        logger.info(method, 'fired', dataTrigger.id, dataTrigger.triggersLeft, 'triggers left');
                        resolve(dataTrigger.id);
                    }
                }
                catch(err) {
                    reject('Exception occurred while firing trigger ' + err);
                }
            });
        });
    };

    this.parseQName = function (qname, separator) {
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
    };

    this.authorize = function(req, res, next) {
        next();
    };

};

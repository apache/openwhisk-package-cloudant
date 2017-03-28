var _ = require('lodash');
var request = require('request');
var constants = require('./constants.js');


module.exports = function(
  logger,
  app,
  retriesBeforeDelete,
  triggerDB,
  routerHost
) {

    this.logger = logger;
    this.app = app;
    this.retriesBeforeDelete = retriesBeforeDelete;
    this.triggerDB = triggerDB;
    this.routerHost = routerHost;

    // this is the default trigger fire limit (in the event that is was not set during trigger creation)
    this.defaultTriggerFireLimit = constants.DEFAULT_TRIGGER_COUNT;

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

        // Cleanup connection when trigger is deleted.
        var sinceToUse = dataTrigger.since ? dataTrigger.since : "now";
        var nanoConnection;
        var dbURL;
        var dbProtocol = 'https'; // unless specified protocol will default to https
        if (dataTrigger.protocol) {
            dbProtocol = dataTrigger.protocol;
        }

        // unless specified host will default to accounturl without the https:// in front
        var dbHost;
        if (dataTrigger.host) {
    	    dbHost = dataTrigger.host;
        } else {
    	    dbHost = dataTrigger.accounturl;
    	    dbHost = dbHost.replace('https://','');
        }

        // both couch and cloudant should have their URLs in the username:password@host format
        dbURL = dbProtocol + '://' + dataTrigger.user + ':' + dataTrigger.pass + '@' + dbHost;

        // add port if specified
        if (dataTrigger.port) {
            dbURL = dbURL + ':' + dataTrigger.port;
        }

        logger.info(method, 'found trigger url:', dbURL);
        nanoConnection = require('nano')(dbURL);

        try {
            var triggeredDB = nanoConnection.use(dataTrigger.dbname);

            // Listen for changes on this database.
            var feed = triggeredDB.follow({since: sinceToUse, include_docs: false});

            dataTrigger.feed = feed;
            that.triggers[dataTrigger.id] = dataTrigger;

            feed.on('change', function (change) {
                logger.info(method, 'Trigger', dataTrigger.id, 'got change from', dataTrigger.dbname);

                var triggerHandle = that.triggers[dataTrigger.id];
                if (triggerHandle) {
            	    logger.info(method, triggerHandle.triggersLeft, 'triggers left for', dataTrigger.id);

                    if (triggerHandle.maxTriggers === -1 || triggerHandle.triggersLeft > 0) {
                        try {
                            that.fireTrigger(dataTrigger.id, change);
                        } catch (e) {
                            logger.error(method, 'Exception occurred while firing the trigger', e);
                        }
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
                    logger.info(method, 'attempting to recreate trigger', dataTrigger.id);
                    logger.info(method, 'Retry Count:', (retryCount - 1));
                    that.createTrigger(dataTrigger, (retryCount - 1))
                    .then(function(trigger) {
                        resolve(trigger);
                    }, function(err) {
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
            logger.info(method, 'caught an exception:', err);
            return Promise.reject(err);
        }

    };

    this.initTrigger = function (obj, id) {

        var method = 'initTrigger';

        // validate parameters here
        logger.info(method, 'create has received the following request args', obj);

        // if the trigger creation request has not set the max trigger fire limit
        // we will set it here (default value can be updated in ./constants.js)
        if (!obj.maxTriggers) {
        	logger.info(method, 'maximum trigger fires has not been set by requester.  setting it to the default value of infinity.');
        	logger.info(method, 'setting trigger fire limit', that.defaultTriggerFireLimit);
        	obj.maxTriggers = that.defaultTriggerFireLimit;
        } else {
            logger.info(method, 'maximum trigger fires has been set to:', obj.maxTriggers);
        }

        // if we find that includeDoc is set to true we should warn user here
        // (note: this will only be the set for old feeds.  we no longer allow
        // this to be set for newly created feeds).
        if (obj.includeDoc && (obj.includeDoc === true || obj.includeDoc.toString().trim().toLowerCase() === 'true')) {
            logger.warn(method, 'cloudant trigger feed: includeDoc parameter is no longer supported.');
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
            includeDoc: obj.includeDoc,
            apikey: obj.apikey,
            since: obj.since,
            callback: obj.callback,
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

                    //check if trigger still exists in whisk db
                    var triggerObj = that.parseQName(trigger.doc.id, ':');
                    var host = 'https://' + routerHost +':'+ 443;
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
                    }, function(error, response, body) {
                        //delete from database if trigger no longer exists (404)
                        if (!error && response.statusCode === 404) {
                            logger.info(method, 'Trigger', trigger.doc.id, 'no longer exists');
                            that.deleteTriggerFromDB(trigger.doc.id);
                        }
                        else {
                            var cloudantTrigger = that.initTrigger(trigger.doc, trigger.doc.id);

                            // check here for triggers left if none left end here, and don't create
                            if (cloudantTrigger.maxTriggers === -1 || cloudantTrigger.triggersLeft > 0) {
                                that.createTrigger(cloudantTrigger, retriesBeforeDelete)
                                .then(function (newTrigger) {
                                    logger.info(method, 'Trigger was added.', newTrigger);
                                }, function (err) {
                                    //if feed was not recreated then delete from trigger database
                                    that.deleteTriggerFromDB(cloudantTrigger.id);
                                });
                            } else {
                                logger.info(method, 'found a trigger with no triggers left to fire off.');
                            }
                        }
                    });
                });
            } else {
                logger.error(method, 'could not get latest state from database');
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

    this.addTriggerToDB = function (trigger, res) {

        var method = 'addTriggerToDB';
        triggerDB.insert(_.omit(trigger, 'feed'), trigger.id, function(err, body) {
            if(!err) {
                logger.info(method, 'trigger', trigger.id, 'was inserted into db.');
                res.status(200).json(_.omit(trigger, 'feed'));
            } else {
                that.deleteTrigger(trigger.id);
                res.status(err.statusCode).json({error: 'Cloudant trigger cannot be created.'});
            }
        });

    };

    this.deleteTriggerFromDB = function (id, res) {

        var method = 'deleteTriggerFromDB';

        triggerDB.get(id, function(err, body) {
            if (!err) {
                triggerDB.destroy(body._id, body._rev, function(err, body) {
                    if (err) {
                        logger.error(method, 'there was an error while deleting', id, 'from database');
                        if (res) {
                            res.status(err.statusCode).json({ error: 'Cloudant data trigger ' + id  + 'cannot be deleted.' } );
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
                        res.status(err.statusCode).json({ error: 'Cloudant data trigger ' + id  + ' cannot be deleted.' } );
                    }
                }
            }
        });

    };

    this.fireTrigger = function (id, change) {
        var method = 'fireTrigger';

        var dataTrigger = that.triggers[id];
        var apikey = dataTrigger.apikey;
        var triggerName = dataTrigger.callback.action.name;
        var triggerObj = that.parseQName(triggerName);

        var form = change;
        // pass the fire trigger both the change and an object containing
        // whisk related details
        if (dataTrigger.includeDoc === true || dataTrigger.includeDoc === 'true') {
            var whiskPayloadObject = {
                'error' : {
                    'code' : 1,
                    'message' : 'includeDoc parameter is no longer supported.'
                }
            };
            form.whisk = whiskPayloadObject;
        }
        form.dbname = dataTrigger.dbname;

        logger.info(method, 'firing trigger', triggerName, 'with db update');

        var host = 'https://' + routerHost + ':' + 443;
        var uri = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;
        var auth = apikey.split(':');

        request({
            method: 'post',
            uri: uri,
            auth: {
                user: auth[0],
                pass: auth[1]
            },
            json: form
        }, function(error, response, body) {
            if (dataTrigger) {
                logger.info(method, id, 'http post request, STATUS:', response ? response.statusCode : response);
                if (error || response.statusCode >= 400) {
                    logger.error(method, 'there was an error invoking', id, response ? response.statusCode : response, error, body);
                    if (!error && [408, 429, 500, 503].indexOf(response.statusCode) === -1) {
                        //delete dead triggers
                        that.deleteTriggerFromDB(dataTrigger.id);
                    }
                } else {
                    // only manage trigger fires if they are not infinite
                    if (dataTrigger.maxTriggers !== -1) {
                        dataTrigger.triggersLeft--;
                    }
                    logger.info(method, 'fired', id, 'with body', body, dataTrigger.triggersLeft, 'triggers left');

                    if (dataTrigger.triggersLeft === 0) {
                        logger.info('onTick', 'no more triggers left, deleting');
                        that.deleteTriggerFromDB(dataTrigger.id);
                    }
                }
            } else {
                logger.info(method, 'trigger', id, 'was deleted between invocations');
            }
        });
    };

    this.parseQName = function (qname, separator) {
        var parsed = {};
        var delimiter = separator || '/';
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

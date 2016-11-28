var _ = require('lodash');
var request = require('request');
var constants = require('./constants.js');

module.exports = function(
  tid,
  logger,
  app,
  retriesBeforeDelete,
  triggerDB,
  routerHost
) {

    this.tid = tid;
    this.logger = logger;
    this.app = app;
    this.retriesBeforeDelete = retriesBeforeDelete;
    this.triggerDB = triggerDB;
    this.routerHost = routerHost;

    this.logger.info (tid, 'utils', 'received database to store triggers: ' + triggerDB);

    // this is the default trigger fire limit (in the event that is was not set during trigger creation)
    this.defaultTriggerFireLimit = constants.DEFAULT_TRIGGER_COUNT;

    // maximum number of times to create a trigger
    this.retryCount = constants.RETRIES_BEFORE_DELETE;

    // Log HTTP Requests
    app.use(function(req, res, next) {
        if (req.url.indexOf('/cloudanttriggers') === 0) {
            logger.info(tid, 'HttpRequest', req.method, req.url);
        }
        next();
    });

    this.module = 'utils';
    this.triggers = {};

    // we need a way of know if the triggers should fire without max fire constraint (ie fire infinite times)
    this.unlimitedTriggerFires = false;

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

        logger.info(tid, method,'found trigger url: ', dbURL);
        nanoConnection = require('nano')(dbURL);

        try {
    	  
            var triggeredDB = nanoConnection.use(dataTrigger.dbname);
          
            // Listen for changes on this database.
            // always set the include doc setting to false
            var feed = triggeredDB.follow({since: sinceToUse, include_docs: false});

            dataTrigger.feed = feed;
            that.triggers[dataTrigger.id] = dataTrigger;

            feed.on('change', function (change) {
                var triggerHandle = that.triggers[dataTrigger.id];

                logger.info(tid, method, 'Got change from', dataTrigger.dbname, change, triggerHandle);
                logger.info(tid, method, 'Found triggerHandle', triggerHandle);
        	  
                if (triggerHandle && triggerHandle.retriesLeft > 0) {
            	  
            	    logger.info(tid, method, 'triggers left:', triggerHandle.triggersLeft);
            	    logger.info(tid, method, 'retries left:', triggerHandle.retriesLeft);
            	  
                    if (triggerHandle.triggersLeft === -1) {
                	    logger.info(tid, method, 'found a trigger fire limit set to -1.  setting it to fire infinitely many times');
                        that.unlimitedTriggerFires = true;
                    } else {
                	    that.unlimitedTriggerFires = false;
                    }

                    if (that.unlimitedTriggerFires || triggerHandle.triggersLeft > 0) {
                        try {
                    	    logger.info(tid, method, 'found a valid trigger.  lets fire this trigger', triggerHandle);
                            that.fireTrigger(dataTrigger.id, change);
                        } catch (e) {
                            logger.error(tid, method, 'Exception occurred in callback', e);
                        }
                    }
                }
            });

            feed.follow();

            return new Promise(function(resolve, reject) {

            feed.on('error', function (err) {
                logger.error(tid, method,'Error occurred for trigger', dataTrigger.id, '(db ' + dataTrigger.dbname + '):', err);
                // revive the feed if an error occured for now
                // the user should be in charge of removing the feeds
                logger.info(tid, "attempting to recreate trigger", dataTrigger.id);
                that.deleteTrigger(dataTrigger.id);
                dataTrigger.since = "now";
                if (retryCount > 0) {
                    that.createTrigger(dataTrigger, (retryCount - 1))
                    .then(function(trigger) {
                        logger.error(tid, method, "Retry Count:", (retryCount - 1));
                        resolve(trigger);
                    }, function(err) {
                        reject(err);
                    });
                } else {
                    logger.error(tid, method, "Trigger's feed produced too many errors. Deleting the trigger", dataTrigger.id, '(db ' + dataTrigger.dbname + ')');
                    reject({
                        error: err,
                        message: "Trigger's feed produced too many errors. Deleting the trigger " + dataTrigger.id
                    });
                }
            });

            feed.on('confirm', function (dbObj) {
                logger.info(tid, method, 'Added cloudant data trigger', dataTrigger.id, 'listening for changes in database', dataTrigger.dbname);
                resolve(dataTrigger);
            });

        });
        
        } catch (err) {
            logger.info('caught an exception: ' + err);
            return Promise.reject(err);
        }

    };

    this.initTrigger = function (obj, id) {

        var method = 'initTrigger';

        // validate parameters here
        logger.info(tid, method, 'create has received the following request args', JSON.stringify(obj));

        // if the trigger creation request has not set the max trigger fire limit
        // we will set it here (default value can be updated in ./constants.js)
        if (!obj.maxTriggers) {
        	logger.info(tid, method, 'maximum trigger fires has not been set by requester.  setting it to the default value of infinity.');
        	logger.info(tid, method, 'setting trigger fire limit', that.defaultTriggerFireLimit);
        	obj.maxTriggers = that.defaultTriggerFireLimit;
        } else {
            logger.info(tid, method, 'maximum trigger fires has been set to:', obj.maxTriggers);
        }

        // if we find that includeDoc is set to true we should warn user here
        // (note: this will only be the set for old feeds.  we no longer allow 
        // this to be set for newly created feeds).
        if (obj.includeDoc && (obj.includeDoc === true || obj.includeDoc.toString().trim().toLowerCase() === 'true')) {
            logger.warn(tid, method, 'cloudant trigger feed: includeDoc parameter is no longer supported and will be ignored.');
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
            includeDoc: includeDoc,
            apikey: obj.apikey,
            since: obj.since,
            callback: obj.callback,
            maxTriggers: obj.maxTriggers,
            triggersLeft: obj.maxTriggers,
            retriesLeft: retriesBeforeDelete
        };

        return trigger;

    };

    this.initAllTriggers = function () {

        var method = 'initAllTriggers';
        logger.info(tid, that.module, method, 'Initializing all cloudant triggers from database.');

        triggerDB.list({include_docs: true}, function(err, body) {
            if(!err) {
                body.rows.forEach(function(trigger) {
                    var cloudantTrigger = that.initTrigger(trigger.doc, trigger.doc.id);

                    if (cloudantTrigger.triggersLeft === -1) {
                  	    logger.info(tid, method, 'found a trigger fire limit set to -1.  setting it to fire infinitely many times');
                        that.unlimitedTriggerFires = true;
                    } else {
                        that.unlimitedTriggerFires = false;
                    }

                    // check here for triggers left if none left end here, and don't create
                    if (that.unlimitedTriggerFires || cloudantTrigger.triggersLeft > 0) {
                      that.createTrigger(cloudantTrigger, that.retryCount);
                    } else {
                      logger.info(tid, method, 'found a trigger with no triggers left to fire off.');
                    }
                });
            } else {
                logger.error(tid, method, 'could not get latest state from database');
            }
        });

    };

    // Delete a trigger: stop listening for changes and remove it.
    this.deleteTrigger = function (id) {
        var method = 'deleteTrigger';
        var trigger = that.triggers[id];
        if (trigger) {
            logger.info(tid, method, 'Stopped cloudant trigger',
                id, 'listening for changes in database', trigger.dbname);
            trigger.feed.stop();
            delete that.triggers[id];
        } else {
            logger.info(tid, method, 'trigger', id, 'could not be found in the trigger list.');
            return false;
        }
    };

    this.addTriggerToDB = function (trigger, res) {

        var method = 'addTriggerToDB';
        triggerDB.insert(_.omit(trigger, 'feed'), trigger.id, function(err, body) {
            if(!err) {
                logger.info(tid, method, 'trigger', trigger.id, 'was inserted into db.');
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
            if(!err) {
                triggerDB.destroy(body._id, body._rev, function(err, body) {
                    if(err) {
                        logger.error(tid, method, 'there was an error while deleting', id, 'from database');
                        if (res) {
                            res.status(err.statusCode).json({ error: 'Cloudant data trigger ' + id  + 'cannot be deleted.' } );
                        }
                    } else {
                        that.deleteTrigger(id);
                        logger.info(tid, method, 'cloudant trigger', id, ' is successfully deleted');
                        if (res) {
                            res.send('Deleted cloudant trigger ' + id);
                        }
                    }
                });
            } else {
                if (err.statusCode === 404) {
                    logger.info(tid, method, 'there was no trigger with id', id, 'in database.', err.error);
                    if (res) {
                        res.status(200).json({ message: 'there was no trigger with id ' + id + ' in database.' } );
                        res.end();
                    }
                } else {
                    logger.error(tid, method, 'there was an error while getting', id, 'from database', err);
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
        logger.info(tid, method, 'fireTrigger: change =', change);

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

        logger.info(tid, method, 'fireTrigger: form =', form);
        logger.info(tid, method, 'for trigger', id, 'invoking action', triggerName, 'with db update', JSON.stringify(form));

        var host = 'https://' + routerHost + ':' + 443;
        var uri = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;
        var auth = apikey.split(':');
        logger.info(tid, method, uri, auth, form);

        // only manage trigger fires if they are not infinite
        if (!that.unlimitedTriggerFires) {
      	    logger.info(tid, method, 'found a trigger fire limit set to -1.  setting it to fire infinitely many times');
            dataTrigger.triggersLeft--;
        }

        request({
            method: 'post',
            uri: uri,
            auth: {
                user: auth[0],
                pass: auth[1]
            },
            json: form
        }, function(error, response, body) {
            if(dataTrigger) {
                logger.info(tid, method, 'done http request, STATUS', response ? response.statusCode : response);
                logger.info(tid, method, 'done http request, body', body);
                if(error || response.statusCode >= 400) {
                    dataTrigger.retriesLeft--;

                    // only manage trigger fires if they are not infinite
                    if (!that.unlimitedTriggerFires) {
                    	dataTrigger.triggersLeft++; // setting the counter back to where it used to be
                    }

                    logger.error(tid, method, 'there was an error invoking', id, response ? response.statusCode : response, error, body);
                } else {
                    dataTrigger.retriesLeft = that.retriesBeforeDelete; // reset retry counter
                    logger.info(tid, method, 'fired', id, 'with body', body, dataTrigger.triggersLeft, 'triggers left');
                }

                if(dataTrigger.triggersLeft === 0 || dataTrigger.retriesLeft === 0) {
                    if(dataTrigger.triggersLeft === 0) {
                        logger.info(tid, 'onTick', 'no more triggers left, deleting');
                    }
                    if(dataTrigger.retriesLeft === 0) {
                        logger.info(tid, 'onTick', 'too many retries, deleting');
                    }

                    that.deleteTriggerFromDB(dataTrigger.id);
                }
            } else {
                logger.info(tid, method, 'trigger', id, 'was deleted between invocations');
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

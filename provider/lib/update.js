var request = require('request');

module.exports = function(logger, utils) {

    // Test Endpoint
    this.endPoint = '/cloudanttriggers/:id';

    // Update Logic
    this.update = function (req, res) {

        var method = 'PUT /cloudanttriggers';

        logger.info(method);
        var args = typeof req.body === 'object' ? req.body : JSON.parse(req.body);
        if (args.maxTriggers > utils.triggersLimit) {
            logger.warn(method, 'maxTriggers > ' + utils.triggersLimit + ' is not allowed');
            res.status(400).json({
                error: 'maxTriggers > ' + utils.triggersLimit + ' is not allowed'
            });
            return;
        } else if (!args.callback || !args.callback.action || !args.callback.action.name) {
            logger.warn(method, 'Your callback is unknown for cloudant trigger:', args.callback);
            res.status(400).json({
                error: 'Your callback is unknown for cloudant trigger.'
            });
            return;
        }
        //Check that user has access rights to create a trigger
        var triggerName = args.callback.action.name;
        var triggerObj = utils.parseQName(triggerName);
        var host = 'https://' + utils.routerHost +':'+ 443;
        var triggerURL = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;
        var auth = args.apikey.split(':');

        logger.info(method, 'Checking if user has access rights to create a trigger');
        request({
            method: 'get',
            url: triggerURL,
            auth: {
                user: auth[0],
                pass: auth[1]
            }
        }, function(error, response, body) {
            if (error || response.statusCode >= 400) {
                var errorMsg = 'Trigger authentication request failed.';
                logger.error(method, errorMsg, error);
                if (error) {
                    res.status(400).json({
                        message: errorMsg,
                        error: error.message
                    });
                }
                else {
                    var info = JSON.parse(body);
                    res.status(response.statusCode).json({
                        message: errorMsg,
                        error: info.error
                    });
                }
            }
            else {
                var id = req.params.id;
                var trigger = utils.initTrigger(args, id);
                // number of retries to create a trigger.
                utils.createTrigger(trigger, utils.retriesBeforeDelete)
                .then(function (newTrigger) {
                    logger.info(method, 'Trigger was added and database is confirmed.', newTrigger);
                    utils.addTriggerToDB(newTrigger, res);
                }, function (err) {
                    logger.error(method, 'Trigger could not be created.', err);
                    utils.deleteTrigger(id);
                    res.status(400).json({
                        message: 'Trigger could not be created.',
                        error: err
                    });
                });
            }
        });
    };

};

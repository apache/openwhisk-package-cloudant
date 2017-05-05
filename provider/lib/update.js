var request = require('request');

module.exports = function(logger, utils) {

    // Test Endpoint
    this.endPoint = '/cloudanttriggers/:id';

    // Update Logic
    this.update = function (req, res) {

        var method = 'PUT /cloudanttriggers';
        var args = typeof req.body === 'object' ? req.body : JSON.parse(req.body);

        //Check that user has access rights to create a trigger
        var id = req.params.id;
        var triggerObj = utils.parseQName(id);
        var host = 'https://' + utils.routerHost +':'+ 443;
        var triggerURL = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;
        var auth = args.apikey.split(':');

        logger.info(method, 'Checking if user has access rights to create trigger', id);
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
                    var info;
                    try {
                        info = JSON.parse(body);
                    }
                    catch (e) {
                        info = 'Authentication request failed with status code ' + response.statusCode;
                    }
                    res.status(response.statusCode).json({
                        message: errorMsg,
                        error: typeof info === 'object' ? info.error : info
                    });
                }
            }
            else {
                var id = req.params.id;
                var trigger = utils.initTrigger(args, id);
                // number of retries to create a trigger.
                utils.createTrigger(trigger, utils.retryAttempts)
                .then(newTrigger => {
                    newTrigger.status = {
                        'active': true,
                        'dateChanged': new Date().toISOString(),
                    };
                    utils.addTriggerToDB(newTrigger, res);
                    logger.info(method, 'Trigger was added and database is confirmed.', newTrigger.id);
                }).catch(err => {
                    logger.error(method, 'Trigger', id, 'could not be created.', err);
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

module.exports = function(tid, logger, utils) {

  // Test Endpoint
  this.endPoint = '/cloudanttriggers/:id';

  // Update Logic
  this.update = function (req, res) {

    var method = 'PUT /cloudanttriggers';

    logger.info(tid, method);
    var args = typeof req.body === 'object' ? req.body : JSON.parse(req.body);
    if(args.maxTriggers > utils.triggersLimit) {
        // TODO: update error code to indicate that content provided is not correct
        logger.warn(tid, method, 'maxTriggers > ' + utils.triggersLimit + ' is not allowed');
        res.status(400).json({
            error: 'maxTriggers > ' + utils.triggersLimit + ' is not allowed'
        });
        return;
    } else if (!args.callback || !args.callback.action || !args.callback.action.name) {
        // TODO: update error code to indicate that content provided is not correct
        logger.warn(tid, method, 'Your callback is unknown for cloudant trigger:', args.callback);
        res.status(400).json({
            error: 'You callback is unknown for cloudant trigger.'
        });
        return;
    }
    var id = req.params.id;
    var trigger = utils.initTrigger(args, id);
    // 10 is number of retries to create a trigger.
    var promise = utils.createTrigger(trigger, 10);
    promise.then(function(newTrigger) {
        logger.info(tid, method, "Trigger was added and database is confirmed.", newTrigger);
        utils.addTriggerToDB(newTrigger, res);
    }, function(err) {
        logger.error(tid, method, "Trigger could not be created.", err);
        utils.deleteTrigger(id);
        res.status(400).json({
            message: "Trigger could not be created.",
            error: err
        });
    });

  };

}

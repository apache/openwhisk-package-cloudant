var request = require('request');

module.exports = function(tid, logger, utils) {

    // Test Endpoint
    this.endPoint = '/cloudanttriggers/:id';

    // Delete Logic
    this.delete = function (req, res) {

        var method = 'DELETE /cloudanttriggers';
        logger.info(tid, method);

        var id = req.params.id;
        var args = typeof req.body === 'object' ? req.body : JSON.parse(req.body);

        //Check that user has access rights to delete a trigger
        var triggerObj = utils.parseQName(id, ':');
        var host = 'https://' + utils.routerHost +':'+ 443;
        var triggerURL = host + '/api/v1/namespaces/' + triggerObj.namespace + '/triggers/' + triggerObj.name;
        var auth = args.apikey.split(':');

        request({
            method: 'get',
            url: triggerURL,
            auth: {
                user: auth[0],
                pass: auth[1]
            }
        }, function(error, response, body) {
            //delete from database if user is authenticated (200) or if trigger has already been deleted (404)
            if (!error && (response.statusCode === 200 || response.statusCode === 404)) {
                utils.deleteTriggerFromDB(id, res);
            }
             else {
                var errorMsg = 'Cloudant data trigger ' + id  + ' cannot be deleted.';
                logger.error(tid, method, errorMsg, error);
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
        });
    };

};

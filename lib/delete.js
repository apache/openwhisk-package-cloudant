module.exports = function(tid, logger, utils) {

  // Test Endpoint
  this.endPoint = '/cloudanttriggers/:id';

  // Delete Logic
  this.delete = function (req, res) {

    var method = 'DELETE /cloudanttriggers';
    logger.info(tid, method);
    utils.deleteTriggerFromDB(req.params.id, res);

  };

}

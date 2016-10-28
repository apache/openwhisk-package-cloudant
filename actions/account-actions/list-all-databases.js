/**
 * Get all databases in Cloudant account:
 * https://docs.cloudant.com/database.html#get-databases
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;

  return listAllDatabases(cloudant);
}

function listAllDatabases(cloudant) {
  return new Promise(function(resolve, reject) {
    cloudant.db.list(function(error, response) {
      if (!error) {
        console.log('success', response);
        //Response is an array and only JSON objects can be passed to whisk.done
        var responseObj = {};
        responseObj.all_databases = response;
        resolve(responseObj);
      } else {
        console.log('error', error);
        reject(error);
      }
    });
  });
}

function getCloudantAccount(message) {
  // full cloudant URL - Cloudant NPM package has issues creating valid URLs
  // when the username contains dashes (common in Bluemix scenarios)
  var cloudantUrl;

  if (message.url) {
    // use bluemix binding
    cloudantUrl = message.url;
  } else {
    if (!message.host) {
      whisk.error('cloudant account host is required.');
      return;
    }
    if (!message.username) {
      whisk.error('cloudant account username is required.');
      return;
    }
    if (!message.password) {
      whisk.error('cloudant account password is required.');
      return;
    }

    cloudantUrl = "https://" + message.username + ":" + message.password + "@" + message.host;
  }

  return require('cloudant')({
    url: cloudantUrl
  });
}

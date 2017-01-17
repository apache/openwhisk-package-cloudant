/**
 * Get all databases in Cloudant account:
 * https://docs.cloudant.com/database.html#get-databases
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return Promise.reject(cloudantOrError);
  }
  var cloudant = cloudantOrError;

  return listAllDatabases(cloudant);
}

function listAllDatabases(cloudant) {
  return new Promise(function(resolve, reject) {
    cloudant.db.list(function(error, response) {
      if (!error) {
        console.log('success', response);
        //Response is an array and only JSON objects can be passed
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
      return 'cloudant account host is required.';
    }
    if (!message.username) {
      return 'cloudant account username is required.';
    }
    if (!message.password) {
      return 'cloudant account password is required.';
    }

    cloudantUrl = "https://" + message.username + ":" + message.password + "@" + message.host;
  }

  return require('cloudant')({
    url: cloudantUrl
  });
}

/**
 * Create a document in Cloudant database:
 * https://docs.cloudant.com/document.html#documentCreate
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var doc = message.doc;
  var params = {};

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!doc) {
    return whisk.error('doc is required.');
  }

  if (typeof message.doc === 'object') {
    doc = message.doc;
  } else if (typeof message.doc === 'string') {
    try {
      doc = JSON.parse(message.doc);
    } catch (e) {
      return whisk.error('doc field cannot be parsed. Ensure it is valid JSON.');
    }
  } else {
    return whisk.error('doc field is ' + (typeof doc) + ' and should be an object or a JSON string.');
  }
  var cloudantDb = cloudant.use(dbName);

  if (typeof message.params === 'object') {
    params = message.params;
  } else if (typeof message.params === 'string') {
    try {
      params = JSON.parse(message.params);
    } catch (e) {
      return whisk.error('params field cannot be parsed. Ensure it is valid JSON.');
    }
  }

  return insert(cloudantDb, doc, params);
}

/**
 * Create document in database.
 */
function insert(cloudantDb, doc, params) {
  return new Promise(function(resolve, reject) {
    cloudantDb.insert(doc, params, function(error, response) {
      if (!error) {
        console.log("success", response);
        resolve(response);
      } else {
        console.log("error", error)
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

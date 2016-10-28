/**
 * Create, Update, and Delete documents in bulk:
 * https://docs.cloudant.com/document.html#bulk-operations
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var docs = message.docs;
  var params = {};

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!docs) {
    return whisk.error('docs is required.');
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

  if (typeof message.docs === 'object') {
    docs = message.docs;
  } else if (typeof message.docs === 'string') {
    try {
      docs = JSON.parse(message.docs);
    } catch (e) {
      return whisk.error('docs field cannot be parsed. Ensure it is valid JSON.');
    }
  } else {
    return whisk.error('docs field is ' + (typeof docs) + ' and should be an object or a JSON string.');
  }

  return bulk(cloudantDb, docs, params);
}

function bulk(cloudantDb, docs, params) {
  return new Promise(function(resolve, reject) {
    cloudantDb.bulk(docs, params, function(error, response) {
      if (!error) {
        var responseDocs = {};
        responseDocs.docs = response;
        console.log('success', response);
        resolve(responseDocs);
      } else {
        console.log('Error: ', error);
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

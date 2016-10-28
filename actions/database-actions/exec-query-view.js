/**
 * Execute view against Cloudant database:
 * https://docs.cloudant.com/creating_views.html#using-views
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var docId = message.docid;
  var viewName = message.viewname;
  var params = {};

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!docId) {
    return whisk.error('docid is required.');
  }
  if(!viewName) {
    return whisk.error('viewname is required.');
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

  return queryView(cloudantDb, docId, viewName, params);
}

/**
 * Get view by design doc id and view name.
 */
function queryView(cloudantDb, designDocId, designDocViewName, params) {
  return new Promise(function(resolve, reject) {
    cloudantDb.view(designDocId, designDocViewName, params, function(error, response) {
      if (!error) {
        console.log('success', response);
        resolve(response);
      } else {
        console.error('error', error);
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

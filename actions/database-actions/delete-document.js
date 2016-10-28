/**
 * Delete a document from Cloudant database:
 * https://docs.cloudant.com/document.html#delete
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var docId = message.docid;
  var docRev = message.docrev;

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!docId) {
    return whisk.error('docid is required.');
  }
  if(!docRev) {
    return whisk.error('docrev is required.');
  }
  var cloudantDb = cloudant.use(dbName);

  return destroy(cloudantDb, docId, docRev);

}

/**
 * Delete document by id and rev.
 */
function destroy(cloudantDb, docId, docRev) {
  return new Promise(function(resolve, reject) {
    cloudantDb.destroy(docId, docRev, function(error, response) {
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

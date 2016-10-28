/**
 * Delete a Cloudant index:
 * https://docs.cloudant.com/cloudant_query.html#deleting-an-index
 **/

var DESIGN_PREFIX = '_design/';

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var docId = message.docid;
  var indexName = message.indexname;
  var indexType = message.indextype;

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!docId) {
    return whisk.error('docid is required.');
  }
  if(!indexName) {
    return whisk.error('indexname is required.');
  }
  if(!indexType) {
    return whisk.error('indextype is required.');
  }

  return deleteIndexFromDesignDoc(cloudant, docId, indexName, indexType, dbName);
}

function deleteIndexFromDesignDoc(cloudant, docId, indexName, indexType, dbName) {

  return new Promise(function(resolve, reject) {
    var path = "_index/" + encodeURIComponent(docId)
      + '/' + encodeURIComponent(indexType)
      + '/' + encodeURIComponent(indexName);

    cloudant.request({ db: encodeURIComponent(dbName),
        method : 'delete',
        path : path
      }, function(error, response) {
      if (!error) {
        console.log('success', response);
        resolve(response);
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

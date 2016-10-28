/**
 * Query with Cloudant search:
 * https://docs.cloudant.com/search.html#queries
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var docId = message.docid;
  var indexName = message.indexname;
  var search = message.search;

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!docId) {
    return whisk.error('docid is required.');
  }
  if(!indexName) {
    return whisk.error('indexname is required.');
  }
  if(!search) {
    return whisk.error('search query is required.');
  }
  var cloudantDb = cloudant.use(dbName);

  //Search should be in the form of {"q":"index:my query"}
  if (typeof message.search === 'object') {
    search = message.search;
  } else if (typeof message.search === 'string') {
    try {
      search = JSON.parse(message.search);
    } catch (e) {
      return whisk.error('search field cannot be parsed. Ensure it is valid JSON.');
    }
  } else {
    return whisk.error('search field is ' + (typeof search) + ' and should be an object or a JSON string.');
  }

  return querySearch(cloudantDb, docId, indexName, search);
}

function querySearch(cloudantDb, designDocId, designViewName, search) {
  return new Promise(function(resolve, reject) {
    cloudantDb.search(designDocId, designViewName, search, function(error, response) {
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

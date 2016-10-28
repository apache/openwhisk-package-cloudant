/**
 * Delete a view from design document in Cloudant database:
 * https://docs.cloudant.com/design_documents.html
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
  var viewName = message.viewname;
  var params = {};

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!docId) {
    return whisk.error('docid is required.');
  }
  var cloudantDb = cloudant.use(dbName);

  if(!viewName) {
    return whisk.error('viewname is required.');
  }
  if (typeof message.params === 'object') {
    params = message.params;
  } else if (typeof message.params === 'string') {
    try {
      params = JSON.parse(message.params);
    } catch (e) {
      return whisk.error('params field cannot be parsed. Ensure it is valid JSON.');
    }
  }

  return deleteViewFromDesignDoc(cloudantDb, docId, viewName, params);
}

function deleteViewFromDesignDoc(cloudantDb, docId, viewName, params) {
  //Check that doc id contains _design prefix
  if (docId.indexOf(DESIGN_PREFIX) !== 0) {
    docId = DESIGN_PREFIX + docId;
  }

  return getDocument(cloudantDb, docId)
    .then(function(document) {
        console.log("Got document: " + document);
        delete document.views[viewName];

        //Update the design document after removing the view
        return insert(cloudantDb, document, params);
    });
}

function getDocument(cloudantDb, docId) {
  return new Promise(function(resolve, reject) {
    cloudantDb.get(docId, function(error, response) {
      if (!error) {
        console.log("Got response: " + response);
        resolve(response);
      } else {
        console.log("Got error: " + error);
        reject(error);
      }
    });
  });
}

function insert(cloudantDb, doc, params) {
  return new Promise(function(resolve, reject) {
    cloudantDb.insert(doc, params, function(error, response) {
      if (!error) {
        console.log('success', response);
        resolve(response);
      } else {
        console.log('error', error)
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

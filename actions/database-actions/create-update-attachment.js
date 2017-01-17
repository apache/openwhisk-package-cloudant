/**
 * Create and update attachment for document in Cloudant database:
 * https://docs.cloudant.com/attachments.html#create-/-update
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return Promise.reject(cloudantOrError);
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var docId = message.docid;
  var attName = message.attachmentname;
  var att = message.attachment;
  var contentType = message.contenttype;
  var params = {};

  if(!dbName) {
    return Promise.reject('dbname is required.');
  }
  if(!docId) {
    return Promise.reject('docid is required.');
  }
  if(!attName) {
    return Promise.reject('attachmentname is required.');
  }
  if(!att) {
    return Promise.reject('attachment is required.');
  }
  if(!contentType) {
    return Promise.reject('contenttype is required.');
  }
  //Add document revision to query if it exists
  if(typeof message.docrev !== 'undefined') {
    params.rev = message.docrev;
  }
  var cloudantDb = cloudant.use(dbName);

  if (typeof message.params === 'object') {
    params = message.params;
  } else if (typeof message.params === 'string') {
    try {
      params = JSON.parse(message.params);
    } catch (e) {
      return Promise.reject('params field cannot be parsed. Ensure it is valid JSON.');
    }
  }

  return insert(cloudantDb, docId, attName, att, contentType, params);
}

/**
 * Insert attachment for document in database.
 */
function insert(cloudantDb, docId, attName, att, contentType, params) {
  return new Promise(function(resolve, reject) {
    cloudantDb.attachment.insert(docId, attName, att, contentType, params, function(error, response) {
      if (!error) {
        console.log("success", response);
        resolve(response);
      } else {
        console.log("error", error);
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

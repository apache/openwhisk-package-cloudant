/**
 * Delete attachment for document in Cloudant database:
 * https://docs.cloudant.com/attachments.html#delete
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return whisk.error('getCloudantAccount returned an unexpected object type.');
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;
  var docId = message.docid;
  var attName = message.attachmentname;
  var params = {};

  if(!dbName) {
    return whisk.error('dbname is required.');
  }
  if(!docId) {
    return whisk.error('docid is required.');
  }
  if(!attName) {
    return whisk.error('attachmentname is required.');
  }
  //Add document revision to query if it exists
  if(typeof message.docrev !== 'undefined') {
    params.rev = message.docrev;
  } else {
    return whisk.error('docrev is required.');
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

  return deleteAttachment(cloudantDb, docId, attName, params);
}

/**
 * Delete attachment for document in database.
 */
function deleteAttachment(cloudantDb, docId, attName, params) {
  return new Promise(function(resolve, reject) {
    cloudantDb.attachment.destroy(docId, attName, params, function(error, response) {
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

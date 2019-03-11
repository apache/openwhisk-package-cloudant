// Licensed to the Apache Software Foundation (ASF) under one or more contributor
// license agreements; and to You under the Apache License, Version 2.0.

/**
 * List Cloudant Query indexes in Cloudant database:
 * https://docs.cloudant.com/cloudant_query.html#list-all-cloudant-query-indexes
 **/

function main(message) {
  var cloudantOrError = getCloudantAccount(message);
  if (typeof cloudantOrError !== 'object') {
    return Promise.reject(cloudantOrError);
  }
  var cloudant = cloudantOrError;
  var dbName = message.dbname;

  if(!dbName) {
    return Promise.reject('dbname is required.');
  }
  var cloudantDb = cloudant.use(dbName);

  return index(cloudantDb);
}

function index(cloudantDb) {
  return new Promise(function(resolve, reject) {
    cloudantDb.index(function(error, response) {
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

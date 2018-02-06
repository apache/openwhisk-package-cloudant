function main(message) {

    var cloudantOrError = getCloudantAccount(message);

    if (typeof cloudantOrError !== 'object') {
        return Promise.reject(cloudantOrError);
    }

    var cloudant = cloudantOrError;
    var dbName = message.dbname;
    var doc = message.doc;
    var overwrite;

    if (!dbName) {
        return Promise.reject('dbname is required.');
    }
    if (!doc) {
        return Promise.reject('doc is required.');
    }

    if (typeof message.doc === 'object') {
        doc = message.doc;
    } else if (typeof message.doc === 'string') {
        try {
            doc = JSON.parse(message.doc);
        } catch (e) {
            return Promise.reject('doc field cannot be parsed. Ensure it is valid JSON.');
        }
    } else {
        return Promise.reject('doc field is ' + (typeof doc) + ' and should be an object or a JSON string.');
    }


    if (typeof message.overwrite === 'boolean') {
        overwrite = message.overwrite;
    } else if (typeof message.overwrite === 'string') {
        overwrite = message.overwrite.trim().toLowerCase() === 'true';
    } else {
        overwrite = false;
    }

    var cloudantDb = cloudant.use(dbName);
    return insertOrUpdate(cloudantDb, overwrite, doc);
}

/**
 * If id defined and overwrite is true, checks if doc exists to retrieve version
 * before insert. Else inserts.
 * If
 */
function insertOrUpdate(cloudantDb, overwrite, doc) {
    if (doc._id) {
        if (overwrite) {
            return new Promise(function(resolve, reject) {
                cloudantDb.get(doc._id, function(error, body) {
                    if (!error) {
                        doc._rev = body._rev;
                        insert(cloudantDb, doc)
                            .then(function (response) {
                                resolve(response);
                            })
                            .catch(function (err) {
                               reject(err);
                            });
                    } else {
                        if(error.statusCode === 404) {
                            // If document not found, insert it
                            return insert(cloudantDb, doc);
                        } else {
                            console.error('error', error);
                            reject(error);
                        }
                    }
                });
            });
        } else {
            // May fail due to conflict.
            return insert(cloudantDb, doc);
        }
    } else {
        // There is no option of overwrite because id is not defined.
        return insert(cloudantDb, doc);
    }
}

/**
 * Inserts updated document into database.
 */
function insert(cloudantDb, doc) {
    return new Promise(function(resolve, reject) {
        cloudantDb.insert(doc, function(error, response) {
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

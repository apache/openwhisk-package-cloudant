var request = require('request');

function main(msg) {
    console.log("cloudant trigger feed: ", msg);

    // lifecycleEvent is one of [ create, delete ]
    var lifecycleEvent = (msg.lifecycleEvent || 'CREATE').trim().toUpperCase();

    var namespace = process.env.__OW_NAMESPACE;
    var triggerName = parseQName(msg.triggerName).name;
    var triggerId = ':' + namespace + ':' + triggerName;

    // configuration parameters
    var provider_endpoint = msg.package_endpoint;
    var dbname = msg.dbname;
    var user = msg.username;
    var pass = msg.password;
    var host = msg.host;
    var protocol = msg.protocol || 'https';
    var port = msg.port;
    var maxTriggers = msg.maxTriggers;
    var filter;
    var query_params;

    if (lifecycleEvent === 'CREATE') {

    	// check for parameter errors
        if (msg.includeDoc) {
            return Promise.reject('cloudant trigger feed: includeDoc parameter is no longer supported');
        }
        if (!dbname) {
        	return Promise.reject('cloudant trigger feed: missing dbname parameter - ' + dbname);
        }
        if (!host) {
        	return Promise.reject('cloudant trigger feed: missing host parameter - ' + host);
        }
        if (!user) {
        	return Promise.reject('cloudant trigger feed: missing username parameter - ' + user);
        }
        if (!pass) {
        	return Promise.reject('cloudant trigger feed: missing password parameter - ' + pass);
        }
        if (namespace === "_") {
            return Promise.reject('You must supply a non-default namespace.');
        }

        if (msg.filter) {
            filter = msg.filter;

            if (typeof msg.query_params === 'object') {
                query_params = msg.query_params;
            }
            else if (typeof msg.query_params === 'string') {
                try {
                    query_params = JSON.parse(msg.query_params);
                }
                catch (e) {
                    return Promise.reject('The query_params parameter cannot be parsed. Ensure it is valid JSON.');
                }
            }
        }
        else if (msg.query_params) {
            return Promise.reject('The query_params parameter is only allowed if the filter parameter is defined');
        }

        // auth key for trigger
        var apiKey = msg.authKey;
        var input = {};
        input.accounturl = "https://" + host;
        input.host = host;
        input.port = port;
        input.protocol = protocol;
        input.dbname = dbname;
        input.user = user;
        input.pass = pass;
        input.apikey = apiKey;
        input.maxTriggers = maxTriggers;
        input.filter = filter;
        input.query_params = query_params;

        return cloudantHelper(provider_endpoint, 'put', triggerId, input);
    } else if (lifecycleEvent === 'DELETE') {

        var jsonOptions = {};
        jsonOptions.apikey = msg.authKey;

        return cloudantHelper(provider_endpoint, 'delete', triggerId, jsonOptions);
    } else {
    	return Promise.reject('operation is neither CREATE or DELETE');
    }
}

function cloudantHelper(endpoint, verb, name, input) {
    var url = 'http://' + endpoint + '/cloudanttriggers/' + name;
    var promise = new Promise(function(resolve, reject) {
        request({
            method : verb,
            url : url,
            json: input
        }, function(error, response, body) {
            console.log('cloudant trigger feed: done http request');
            if (!error && response.statusCode === 200) {
                console.log(body);
                resolve();
            }
            else {
                if (response) {
                    console.log('response code:', response.statusCode);
                    reject(body);
                } else {
                    console.log('no response');
                    reject(error);
                }
            }
        });
    });

    return promise;
}

function parseQName(qname) {
    var parsed = {};
    var delimiter = '/';
    var defaultNamespace = '_';
    if (qname && qname.charAt(0) === delimiter) {
        var parts = qname.split(delimiter);
        parsed.namespace = parts[1];
        parsed.name = parts.length > 2 ? parts.slice(2).join(delimiter) : '';
    } else {
        parsed.namespace = defaultNamespace;
        parsed.name = qname;
    }
    return parsed;
}

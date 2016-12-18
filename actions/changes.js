var request = require('request');

function main(msg) {
    console.log("cloudant trigger feed: ", msg);

    // lifecycleEvent is one of [ create, delete ]
    var lifecycleEvent = (msg.lifecycleEvent || 'CREATE').trim().toUpperCase();

    var namespace = process.env.__OW_NAMESPACE;
    var triggerName = parseQName(msg.triggerName).name;

    var trigger = '/' + namespace + '/' + triggerName;
    var replaceNameTrigger = trigger.replace(/\//g, ":");

    // configuration parameters
    var provider_endpoint = msg.package_endpoint;
    var dbname = msg.dbname;
    var user = msg.username;
    var pass = msg.password;
    var host = msg.host;
    var protocol = msg.protocol || 'https';
    var port = msg.port;
    var maxTriggers = msg.maxTriggers;

    if (lifecycleEvent === 'CREATE') {

    	// check for parameter errors
        var paramError;
        if (msg.includeDoc) {
            paramError = 'cloudant trigger feed: includeDoc parameter is no longer supported';
            console.log(paramError, '[error:]', whisk.error(paramError));
            return;
        }
        if (!dbname) {
        	paramError = 'cloudant trigger feed: missing dbname parameter - ' + dbname;
            console.log(paramError, '[error:]', whisk.error(paramError));
            return;
        }
        if (!host) {
        	paramError = 'cloudant trigger feed: missing host parameter - ' + host;
            console.log(paramError, '[error:]', whisk.error(paramError));
            return;
        }
        if (!user) {
        	paramError = 'cloudant trigger feed: missing username parameter - ' + user;
            console.log(paramError, '[error:]', whisk.error(paramError));
            return;
        }
        if (!pass) {
        	paramError = 'cloudant trigger feed: missing password parameter - ' + pass;
            console.log(paramError, '[error:]', whisk.error(paramError));
            return;
        }
        if (namespace === "_") {
            paramError = 'You must supply a non-default namespace.';
            console.log(paramError, '[error:]', whisk.error(paramError));
            return;
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
        input.callback = {};
        input.callback.action = {};
        input.callback.action.name = trigger;

        return cloudantHelper(provider_endpoint, 'put', replaceNameTrigger, input);
    } else if (lifecycleEvent === 'DELETE') {

        var jsonOptions = {};
        jsonOptions.apikey = msg.authKey;

        return cloudantHelper(provider_endpoint, 'delete', replaceNameTrigger, jsonOptions);
    } else {
    	return whisk.error('operation is neither CREATE or DELETE');
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

var request = require('request');

function main(msg) {
    console.log("cloudant trigger feed: ", msg);

    // lifecycleEvent is one of [ create, delete ]
    var lifecycleEvent = (msg.lifecycleEvent || 'CREATE').trim().toUpperCase();

    // whisk trigger to fire
    var trigger = msg.triggerName;
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

    var validProperties = {
        authKey: "",
        bluemixServiceName: "",
        dbname: "",
        host: "",
        lifecycleEvent: "",
        maxTriggers: "",
        package_endpoint: "",
        password: "",
        triggerName: "",
        username: ""
    };

    if (lifecycleEvent === 'CREATE') {

        // handle any invalid parameters here
    	for (var prop in msg) {
    	    if (!(prop in validProperties)) {
    	    	var eMsg = 'cloudant trigger feed: invalid property not supported: ' + prop;
    	    	console.log(eMsg,'[error:]', whisk.error(eMsg));
                return;
    	    }
    	}

        // if the max triggers has not been set we will set it to infinity here
        if (!maxTriggers) {
            maxTriggers = '-1';
        }

    	// check for missing mandatory parameters
        var paramError;
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

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
    var includeDoc = msg.includeDoc || false;
    var host = msg.host;
    var maxTriggers = msg.maxTriggers || 1000;

    if (lifecycleEvent == 'CREATE') {
        // auth key for trigger
        var apiKey = msg.authKey;
        var auth = apiKey.split(':');
        var input = {};
        input["accounturl"] = "https://" + host;
        input["dbname"] = dbname;
        input["user"] = user;
        input["pass"] = pass;
        input["includeDoc"] = includeDoc;
        input["apikey"] = apiKey;
        input["maxTriggers"] = maxTriggers;
        input["callback"] = {};
        input["callback"]["action"] = {};
        input["callback"]["action"]["name"] = trigger;

        return cloudantHelper(provider_endpoint, 'put', replaceNameTrigger, auth, input);
    } else if (lifecycleEvent == 'DELETE') {
        return cloudantHelper(provider_endpoint, 'delete', replaceNameTrigger);
    } else {
        return whisk.error('operation is neither CREATE or DELETE');
    }
}

function cloudantHelper(endpoint, verb, name, auth, input) {
    var uri = 'http://' + endpoint + '/cloudanttriggers/' + name;
    var options = {
        method : verb,
        uri : uri
    };

    if(auth){
        options.auth = {
            user : auth[0],
            pass : auth[1]
        }
    }

    if (verb === 'put') {
        options.json = input;
    }

    var promise = new Promise(function(resolve, reject) {
      request(options, function(error, response, body) {
          console.log('cloudant trigger feed: done http request', '[error:]', error);
          if (!error && response.statusCode == 200) {
              console.log(body);
              resolve();
          } else {
              if (response) {
                  console.log('response code:', response.statusCode);
              } else {
                  console.log('no response');
              }
              reject(error);
          }
      });
    });

    return promise;
}

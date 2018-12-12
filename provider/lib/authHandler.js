function handleAuth(triggerData, options) {

    var auth = triggerData.apikey.split(':');
    options.auth = {
        user: auth[0],
        pass: auth[1]
    };
    return Promise.resolve(options);
}

module.exports = {
    'handleAuth': handleAuth
};

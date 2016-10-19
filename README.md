RESTful service to listen for changes to a Cloudant database.

Changes to a Cloudant database can trigger the invocation of an action, passing the action the body of the updated document.

See the scripts directory for examples on how to register a new Cloudant trigger.

## Testing the Cloudant trigger service

The following commands will test the Cloudant trigger service:

# Build 
cd <bluewhisk_home>
gradle distDocker

# Deploy
Follow the instructions in [ansible/README.md][../../../ansible/README.md]

# Register an action and listen for changes to a test Cloudant database.
./bin/wsk create HELLOCLOUDANT ./actions/hellocloudant.js
./catalog/providers/cloudantTrigger/scripts/addTestTriggers.sh

# Insert a new document to the test Cloudant database.
./catalog/providers/cloudantTrigger/scripts/addNewDocument.sh

You should see the output from invoking the HELLOCLOUDANT action in the ELK logs.


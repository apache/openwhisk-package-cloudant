#!/bin/bash

# Register test cloudant triggers.

# The service location (can be specified as command line parameter.)
SERVICEURL="http://${CLOUDANTTRIGGER_HOST:-"localhost"}:4001" # default
[ $# -eq 1 ] && SERVICEURL="$1" # override in command line arg

# Delete all registry entries.
#curl -X DELETE "$SERVICEURL/cloudanttriggers"; echo

curl -X PUT -H 'Content-Type: application/json' \
        -d '{
          "accounturl": "https://cusinatest.cloudant.com",
          "dbname": "foo",
          "user": "cusinatest",
          "pass": "hal4you!",
	      "callback": { "action": {"name":"HELLOCLOUDANT"} }
        }' \
        "$SERVICEURL/cloudanttriggers/foo_db"; echo

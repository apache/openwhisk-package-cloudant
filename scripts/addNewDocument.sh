#!/bin/bash

# Add a new document to the test Cloudant database.

DBUSER="cusinatest"
DBPASS="hal4you!"
DBNAME="foo"

curl -v -X POST -H 'Content-Type: application/json' \
	-u "$DBUSER:$DBPASS" \
        -d "{
	  \"message\": \"This is a test document.\",
	  \"date\": \"$(date)\"
        }" \
        "https://$DBUSER.cloudant.com/$DBNAME/"



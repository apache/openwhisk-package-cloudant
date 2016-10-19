FROM ubuntu:14.04

ENV DEBIAN_FRONTEND noninteractive

# Initial update and some basics.
# This odd double update seems necessary to get curl to download without 404 errors.
RUN apt-get update --fix-missing && \
  apt-get install -y wget && \
  apt-get update && \
  apt-get install -y curl && \
  apt-get update && \
  apt-get remove -y nodejs && \
  curl -sL https://deb.nodesource.com/setup_0.12 | bash - && \
  apt-get install -y nodejs

# only package.json
ADD package.json /cloudantTrigger/
RUN cd /cloudantTrigger; npm install

# App
ADD . /cloudantTrigger

EXPOSE 8080

# Run the app
CMD ["/bin/bash", "-c", "node /cloudantTrigger/app.js >> /logs/cloudantTrigger_logs.log 2>&1"]

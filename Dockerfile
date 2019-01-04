FROM node:10.15.0

# only package.json
ADD package.json /
RUN cd / && npm install --production

# App
ADD provider/. /cloudantTrigger/

EXPOSE 8080

# Run the app
CMD ["/bin/bash", "-c", "node /cloudantTrigger/app.js"]

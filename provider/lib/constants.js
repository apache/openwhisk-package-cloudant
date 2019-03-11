// Licensed to the Apache Software Foundation (ASF) under one or more contributor
// license agreements; and to You under the Apache License, Version 2.0.

const TRIGGER_DB_SUFFIX = 'cloudanttrigger';
const DEFAULT_MAX_TRIGGERS = -1;
const RETRY_ATTEMPTS = 12;
const RETRY_DELAY = 1000; //in milliseconds
const REDIS_FIELD = 'active';
const FILTERS_DESIGN_DOC = 'triggerFilters';
const VIEWS_DESIGN_DOC = 'triggerViews';
const MONITOR_DESIGN_DOC = 'monitorFilters';
const TRIGGERS_BY_WORKER = 'triggers_by_worker';
const DOCS_FOR_MONITOR = 'canary_docs';
const MONITOR_INTERVAL = 5 * 1000 * 60; //in milliseconds


module.exports = {
    TRIGGER_DB_SUFFIX: TRIGGER_DB_SUFFIX,
    DEFAULT_MAX_TRIGGERS: DEFAULT_MAX_TRIGGERS,
    RETRY_ATTEMPTS: RETRY_ATTEMPTS,
    RETRY_DELAY: RETRY_DELAY,
    REDIS_FIELD: REDIS_FIELD,
    FILTERS_DESIGN_DOC: FILTERS_DESIGN_DOC,
    VIEWS_DESIGN_DOC: VIEWS_DESIGN_DOC,
    TRIGGERS_BY_WORKER: TRIGGERS_BY_WORKER,
    MONITOR_INTERVAL: MONITOR_INTERVAL,
    MONITOR_DESIGN_DOC: MONITOR_DESIGN_DOC,
    DOCS_FOR_MONITOR: DOCS_FOR_MONITOR
};

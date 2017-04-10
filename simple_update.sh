#!/bin/bash
git pull && mvn clean compile package
mvn exec:java 2> nxt_runtime_log.txt

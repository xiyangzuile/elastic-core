#!/bin/bash
rm -rf elastic_test_db/ && git pull && mvn clean compile package && mvn exec:java

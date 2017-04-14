#!/bin/bash
git pull && mvn clean compile package
mvn exec:java

#!/bin/bash

ORANGE='\033[0;33m'
LBLUE='\033[1;34m'
NC='\033[0m'
MINUTES_TO_CLEAR='5'

git pull && mvn clean compile package

if [ -t 0 ]; then stty -echo -icanon -icrnl time 0 min 0; fi

echo -e "${LBLUE}If you are sure that your node wasn't running for last $MINUTES_TO_CLEAR minutes press any key to run node immediately. Other way it is recommended to wait.${NC}"

secs=$((MINUTES_TO_CLEAR * 60))
while [ $secs -gt 0 ] && [ "x$keypress" = "x" ]; do
   echo -ne "${ORANGE}$secs\033[0K\r${NC}"
   keypress="`cat -v`"
   sleep 1
   : $((secs--))
done

if [ -t 0 ]; then stty sane; fi

mvn exec:java

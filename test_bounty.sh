#!/bin/bash
ID=`curl -s 'http://127.0.0.1:6876/nxt?requestType=getMineableWork&n=1' 2>/dev/null | python2 -c 'import json,sys;obj=json.load(sys.stdin);print obj["work_packages"][0]["work_id"]'`
echo "Trying $ID"
curl --data "work_id=$ID&inputs=0,0,0,0,0,0,0,0&is_pow=false&deadline=1440&feeNQT=0&n=1&secretPhrase=NoDaddyNotToday" http://127.0.0.1:6876/nxt?requestType=createPoX 


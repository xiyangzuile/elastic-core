#!/bin/sh
java -cp classes nxt.tools.ManifestGenerator
/bin/rm -f elastic-core.jar
jar cfm elastic-core.jar resource/nxt.manifest.mf -C classes . || exit 1
/bin/rm -f elastic-core-service.jar
jar cfm elastic-core-service.jar resource/nxtservice.manifest.mf -C classes . || exit 1

echo "jar files generated successfully"

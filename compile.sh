#!/bin/sh
CP="lib/*:classes"
SP=src/java/

/bin/rm -f elastic-core.jar
/bin/rm -f elastic-core-service.jar
/bin/rm -rf classes
/bin/mkdir -p classes/
/bin/rm -rf addons/classes
/bin/mkdir -p addons/classes/

echo "compiling nxt core..."
find src/java/nxt/ -name "*.java" > sources.tmp
javac -encoding utf8 -sourcepath "${SP}:src/anc/" -classpath "${CP}" -d classes/ @sources.tmp || exit 1
echo "nxt core class files compiled successfully"

rm -f sources.tmp

find addons/src/ -name "*.java" > addons.tmp
if [ -s addons.tmp ]; then
    echo "compiling add-ons..."
    javac -encoding utf8 -sourcepath "${SP}:addons/src:src/evil/" -classpath "${CP}:addons/classes:addons/lib/*" -d 
addons/classes 
@addons.tmp || exit 1
    echo "add-ons compiled successfully"
    rm -f addons.tmp
else
    echo "no add-ons to compile"
    rm -f addons.tmp
fi

echo "compilation done"

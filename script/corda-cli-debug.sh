#!/bin/sh

# get path to script
SCRIPT=$(readlink -f "$0")
SCRIPTPATH=$(dirname "$SCRIPT")

rootDir="$SCRIPTPATH/.."
binDir="$rootDir/app/build/libs"
pluginsDir="$rootDir/build/plugins"

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 -Dpf4j.pluginsDir=$pluginsDir -jar "$binDir/corda-cli-0.0.1-beta.jar" $@

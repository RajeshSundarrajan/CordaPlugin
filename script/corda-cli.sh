#!/usr/bin/env bash

cd ../build/plugins
pluginDir=$(pwd)
cd ../../script

java -Dpf4j.pluginsDir=$pluginDir -jar ..\app\build\libs\corda-cli.jar $@
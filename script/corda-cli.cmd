@echo off
cd ..\
SET rootDir=%cd%
SET pluginsDir="%rootDir%\build\plugins"
cd script

java -Dpf4j.pluginsDir=%pluginsDir% -XX:+FlightRecorder -XX:StartFlightRecording=duration=200s,filename=flight.jfr -jar ..\app\build\libs\corda-cli.jar %*
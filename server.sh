#!/bin/sh

JAVA=java
$JAVA -cp ${name.final}.jar -Dctbrec.config=server.json ctbrec.recorder.server.HttpServer

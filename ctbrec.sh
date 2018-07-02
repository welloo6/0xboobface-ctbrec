#!/bin/sh

#JAVA=/opt/jdk-10.0.1/bin/java
JAVA=java

$JAVA -version
$JAVA -Djdk.gtk.version=3 -cp ${name.final}.jar ctbrec.ui.Launcher

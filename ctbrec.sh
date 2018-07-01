#!/bin/sh

#JAVA=/opt/jdk-10.0.1/bin/java
JAVA=java

$JAVA -version
$JAVA -Djdk.gtk.version=3 -cp ctbrec-1.0.0-final.jar ctbrec.ui.Launcher

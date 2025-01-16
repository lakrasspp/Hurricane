#!/bin/bash
#for MacOS - "/usr/libexec/java_home -V"  and "export JAVA_HOME=`/usr/libexec/java_home -v 15.0`"
java --add-exports=java.desktop/sun.awt=ALL-UNNAMED -Dsun.java2d.uiScale.enabled=false -Dsun.java2d.win.uiScaleX=1.0 -Dsun.java2d.win.uiScaleY=1.0 -Xss8m -Xms1024m -Xmx4096m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath="heapdumps" -jar hafen.jar

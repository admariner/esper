#!/bin/sh

## run via '. ./setenv.sh'
##
##

if [ -z "${JAVA_HOME}" ]
then
  echo "JAVA_HOME not set"
  exit 0
fi

if [ ! -x "${JAVA_HOME}/bin/java" ]
then
  echo Cannot find java executable, check JAVA_HOME
  exit 0
fi

LIB=../../../lib
LIB_COMPILER=../../../dependencies/compiler

CLASSPATH=.
CLASSPATH=$CLASSPATH:../terminalsvc-sender/target/example-terminalsvc-sender-1.0.jar
CLASSPATH=$CLASSPATH:../terminalsvc-common/target/example-terminalsvc-common-1.0.jar
CLASSPATH=$CLASSPATH:../terminalsvc-receiver/target/example-terminalsvc-receiver-1.0.jar
CLASSPATH=$CLASSPATH:../lib/jboss-jms-api_1.1_spec-1.0.0.Final.jar
CLASSPATH=$CLASSPATH:../lib/jboss-client.jar
CLASSPATH=$CLASSPATH:../../../esper-common-9.0.0.jar
CLASSPATH=$CLASSPATH:../../../esper-compiler-9.0.0.jar
CLASSPATH=$CLASSPATH:../../../esper-runtime-9.0.0.jar
CLASSPATH=$CLASSPATH:$LIB_COMPILER/slf4j-api-1.7.36.jar
CLASSPATH=$CLASSPATH:$LIB_COMPILER/slf4j-reload4j-1.7.36.jar
CLASSPATH=$CLASSPATH:$LIB_COMPILER/reload4j-1.2.25.jar
CLASSPATH=$CLASSPATH:$LIB_COMPILER/antlr4-runtime-4.13.1.jar
CLASSPATH=$CLASSPATH:$LIB_COMPILER/commons-compiler-3.1.12.jar
CLASSPATH=$CLASSPATH:$LIB_COMPILER/janino-3.1.12.jar

export CLASSPATH="$CLASSPATH"

#!/bin/sh

# A note to cygwin users: please replace "-cp ${CLASSPATH}" with "-cp `cygpath -wp $CLASSPATH`"
#

. ./setenv.sh

if [ ! -d "../target" ]
then
    mkdir ../target
fi
if [ ! -d "../target/classes" ]
then
    mkdir ../target/classes
fi

SOURCEPATH=../src/main/java
EXAMPLESOURCEPATH=$SOURCEPATH/com/espertech/esper/example/cycledetect

${JAVA_HOME}/bin/javac -cp ${CLASSPATH} -d ../target/classes -source 17 -sourcepath ${SOURCEPATH} ${EXAMPLESOURCEPATH}/CycleDetectMain.java ${EXAMPLESOURCEPATH}/CycleDetectorAggregationStateFactory.java ${EXAMPLESOURCEPATH}/CycleDetectorAggregationAccessorOutputFactory.java ${EXAMPLESOURCEPATH}/CycleDetectorAggregationAccessorDetectFactory.java

#!/bin/sh
dir=`dirname $0`
cd $dir

if [ -z $minHeap ]; then minHeap=64; fi
if [ -z $maxHeap ]; then maxHeap=512; fi

java -classpath './lib/*.jar' -Xms${minHeap}M -Xmx${maxHeap}M -jar umongo.jar


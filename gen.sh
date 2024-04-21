#!/bin/bash
pwd=`pwd`"/"
filenames=($(grep -r "^service " app/*/*.thrift  | awk -F ":" '{print $1}'))

for filename in "${filenames[@]}"; do
  dir=`echo $filename | awk -F "/" '{for(i=1;i<NF;i++) str=str$i"/"}END{print str}'`
  project=($(echo "$dir" | awk -F "/" '{print $2}'))
  package=`grep 'namespace java' "$filename" | awk '{print $NF}'`
  class_name=`grep '^service' "$filename" | awk -F '({| )+' '{print $(NF-1)}'`

  echo "detect project: "$project
  cd $pwd && mkdir -p _mvnprojects/$project/src/main/thrift && rm -rf _mvnprojects/$project/src/main/thrift/* && \
    mkdir -p _mvnprojects/$project/src/main/resources/META-INF/services && cp -rf $dir* _mvnprojects/$project/src/main/thrift
  echo $package.$class_name\$Client\$Factory > _mvnprojects/$project/src/main/resources/META-INF/services/org.apache.thrift.TServiceClientFactory
  cat template.xml | awk -v project="$project" '{gsub(/\$\{ARTIFACTID\}/, project); print}' > _mvnprojects/$project/pom.xml
  cd _mvnprojects/$project && mvn package  && mv -f target/$project-0.0.1-SNAPSHOT.jar $pwd"services"
done
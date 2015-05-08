#!/bin/sh

curl -O http://update.imagej.net/bootstrap.js

test -f db.xml.gz ||
jrunscript bootstrap.js add-update-site Micro-Manager-dev http://sites.imagej.net/Micro-Manager-dev

now=$(date +%s)
jrunscript bootstrap.js update plugins/Micro-Manager/MMCoreJ.jar plugins/Micro-Manager/MMJ_.jar

for artifactId in MMCoreJ MMJ_
do
	file="$(pwd)/plugins/Micro-Manager/${artifactId}.jar"
	if test $(stat -c %Y "$file") -ge $now
	then
		# unfortunately, this version is not recorded on the update site
		version=1.4.22-SNAPSHOT
		mvn -f "$(dirname "$0")/../pom.xml" -Pdeploy-to-imagej deploy:deploy-file \
			-Durl=dav:http://maven.imagej.net/content/repositories/snapshots \
			-DrepositoryId=imagej.snapshots \
			-DgroupId=org.micromanager \
			-Dversion=$version \
			-Dpackaging=jar \
			-DartifactId=${artifactId} \
			-Dfile="$file"
	fi
done

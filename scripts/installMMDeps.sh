#!/usr/bin/env bash

cd ..

mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/MMCoreJ.jar -DgroupId=org.micromanager \
                         -DartifactId=MMCoreJ -Dversion=2.0.0 \
                         -Dpackaging=pom -DlocalRepositoryPath=local-repo/

mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/MMJ_.jar -DgroupId=org.micromanager \
                         -DartifactId=MMJ_ -Dversion=2.0.0 \
                         -Dpackaging=pom -DlocalRepositoryPath=local-repo/


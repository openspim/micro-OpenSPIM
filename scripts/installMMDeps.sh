#!/usr/bin/env bash

cd ..

mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/MMCoreJ.jar -DgroupId=org.micromanager \
                         -DartifactId=MMCoreJ -Dversion=2.0.0 \
                         -Dpackaging=jar -DlocalRepositoryPath=local-repo/

mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/MMJ_.jar -DgroupId=org.micromanager \
                         -DartifactId=MMJ_ -Dversion=2.0.0 \
                         -Dpackaging=jar -DlocalRepositoryPath=local-repo/

mvn org.apache.maven.plugins:maven-install-plugin:2.3.1:install-file \
                         -Dfile=lib/MMAcqEngine.jar -DgroupId=org.micromanager \
                         -DartifactId=MMAcqEngine -Dversion=2.0.0 \
                         -Dpackaging=jar -DlocalRepositoryPath=local-repo/

mvn install:install-file -DgroupId=jdk.tools -DartifactId=jdk.tools -Dpackaging=jar -Dversion=1.8 -Dfile=lib/tools.jar -DgeneratePom=true

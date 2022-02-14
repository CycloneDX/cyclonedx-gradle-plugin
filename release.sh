#!/usr/bin/env bash
export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
export PATH=JAVA_HOME/bin:$PATH

read -p "Really deploy to Gradle plugin repository and Maven Central (Y/N)? "
if ( [ "$REPLY" == "Y" ] ) then

  ./gradlew clean build cyclonedx
  ./gradlew publishToMavenLocal
  ./gradlew publishPlugins

else
  echo -e "Exit without deploy"
fi

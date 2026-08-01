#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper script
# ----------------------------------------------------------------------------

if [ -z "$JAVA_HOME" ]; then
  javaExecutable="java"
else
  javaExecutable="$JAVA_HOME/bin/java"
fi

wrapperJarPath=".mvn/wrapper/maven-wrapper.jar"
if [ ! -e "$wrapperJarPath" ]; then
  wrapperUrl="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  echo "Downloading Maven Wrapper jar from $wrapperUrl"
  mkdir -p .mvn/wrapper
  if command -v curl >/dev/null 2>&1; then
    curl -f -L -s -o "$wrapperJarPath" "$wrapperUrl"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$wrapperJarPath" "$wrapperUrl"
  else
    echo "ERROR: Neither curl nor wget found. Please download $wrapperUrl to $wrapperJarPath manually." >&2
    exit 1
  fi
fi

exec "$javaExecutable" \
  -Dmaven.multiModuleProjectDirectory="$PWD" \
  -classpath "$wrapperJarPath" \
  org.apache.maven.wrapper.MavenWrapperMain \
  "$@"

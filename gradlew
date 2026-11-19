#!/bin/sh
# Gradle wrapper script (standard)
APP_BASE_NAME=`basename "$0"`
APP_HOME=`cd "${APP_BASE_NAME%/*:-./}" > /dev/null && pwd -P || pwd`
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD=maximum
warn () { echo "$*" >&2; }
die () { echo; echo "$*" >&2; echo "This failure was caused by something that has gone wrong." >&2; echo "Please run with --stacktrace option to get the stack trace." >&2; exit 1; }
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
CLASSPATH="gradle/wrapper/gradle-wrapper.jar"
if [ ! -r "$CLASSPATH" ]; then die "Missing gradle-wrapper.jar"; fi
exec java ${DEFAULT_JVM_OPTS} -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

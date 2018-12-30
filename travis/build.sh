#!/usr/bin/env bash

echo "Travis Container environment variables:"
env | sort

echo

if [[ "${TRAVIS_BRANCH}" == "dev" ]]; then
  echo "Start compiling and assembling apk..."
  ./gradlew clean testDebug lintDebug assembleDebug --stacktrace
else
  echo "Start compiling WITHOUT assembling apk..."
  ./gradlew clean testDebug lintDebug --stacktrace
fi


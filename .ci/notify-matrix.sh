#!/usr/bin/env bash

MESSAGE=$1
APK_FILE=$2

BASE_URL="https://${MATRIX_HOMESERVER}"

if [[ -z "${APK_FILE}" ]]; then
  echo "Sending apk file..."

  # TODO: send to matrix chat
fi
  
echo "Sending info message..."

# TODO: send to matrix chat
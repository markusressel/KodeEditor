#!/usr/bin/env bash

MESSAGE=$1
APK_FILE=$2

BASE_URL="https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}"

if [[ -z "${APK_FILE}" ]]; then
  echo "Sending apk file..."
  MESSAGE_ID=$(curl \
    --silent \
    --form chat_id="${TELEGRAM_CHAT_ID}" \
    --form document=@"${APK_FILE}" \
    "${BASE_URL}/sendDocument" \
    | jq 'if (.ok == true) then .result.message_id else empty end')
fi

echo "Sending info message..."
curl \
  --silent \
  -X POST \
  "${BASE_URL}/sendMessage" \
  -d "chat_id=${TELEGRAM_CHAT_ID}" \
  -d "text=${MESSAGE}" \
  -d "reply_to_message_id=${MESSAGE_ID}" \
  -d "parse_mode=markdown" \
  -d "disable_web_page_preview=true" \
  >/dev/null

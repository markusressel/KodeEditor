#!/usr/bin/env bash

MESSAGE=$1

BASE_URL="https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}"

echo "Sending info message..."
curl \
  --silent \
  -X POST \
  "${BASE_URL}/sendMessage" \
  -d "chat_id=${TELEGRAM_CHAT_ID}" \
  -d "text=${MESSAGE}" \
  -d "parse_mode=markdown" \
  -d "disable_web_page_preview=true" \
  >/dev/null

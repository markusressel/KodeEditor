#!/usr/bin/env bash
if [[ "${TRAVIS_BRANCH}" == "dev" ]]; then
  echo "Generating Telegram Messages..."
  
  BASE_URL="https://api.telegram.org/${TELEGRAM_TOKEN}"
  
  # prepare telegram message to send as reply to the apk file
  if [[ "${TRAVIS_PULL_REQUEST}" != "false" ]]; then
    PR_LINK_TEXT=$(cat <<EOF
[Pull Request](https://github.com/freitagsrunde/k4ever-android/pull/${TRAVIS_PULL_REQUEST})
EOF
)
  fi
  
  if [[ "${TRAVIS_TEST_RESULT}" == "0" ]]; then
    RESULT_EMOJI="✅"
  else
    RESULT_EMOJI="🔥"
  fi 
  
  COMMITS_INVOLVED=$(git log --oneline ${TRAVIS_COMMIT_RANGE})
  
  MESSAGE=$(cat <<EOF
${RESULT_EMOJI} *Travis Build* [#${TRAVIS_BUILD_NUMBER}](https://travis-ci.org/freitagsrunde/k4ever-android/builds/${TRAVIS_BUILD_ID}) *(${TRAVIS_EVENT_TYPE})*

Commits:
\`${COMMITS_INVOLVED}\`

${PR_LINK_TEXT}
EOF
)
 
  if [[ "${TRAVIS_TEST_RESULT}" == "0" ]]; then
    # find compiled .apk file
    APK_FILE=$(find ./app/build/outputs/apk/debug -type f -name "k4ever*.apk")
    
    echo "Sending apk file..."
    
    # send apk file via telegram bot
    MESSAGE_ID=$(curl \
      --silent \
      --form chat_id="${CHAT_ID}" \
      --form document=@"${APK_FILE}" \
      "${BASE_URL}/sendDocument" \
      | jq 'if (.ok == true) then .result.message_id else empty end')
  fi
  
  echo "Sending info message..."
  # send telegram chat message
  curl \
    --silent \
    -X POST \
    "${BASE_URL}/sendMessage" \
    -d "chat_id=${CHAT_ID}" \
    -d "text=${MESSAGE}" \
    -d "reply_to_message_id=${MESSAGE_ID}" \
    -d "parse_mode=markdown" \
    -d "disable_web_page_preview=true" \
    >/dev/null
 
fi


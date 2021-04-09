#!/usr/bin/env bash
echo "Generating Messages..."

RUN_URL="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
# TODO: how to get PR_NUMBER ?
#       https://github.com/kceb/pull-request-url-action/blob/master/index.js
PR_URL="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/pull/${PR_NUMBER}"

MESSAGE=

# TODO: how to find out if the build was successful?
#       the integration into the github action is also important for this to work
if [[ "${RUN_RESULT}" == "success" ]]; then
  RESULT_EMOJI="✅"
else
  RESULT_EMOJI="🔥"
fi

if [[ -n "${GITHUB_HEAD_REF}" ]]; then
  # this is a PR

  COMMITS_INVOLVED=$(git log --oneline ^"${GITHUB_BASE_REF}" HEAD)
  PR_LINK_TEXT=$(cat <<EOF
[Pull Request](${GITHUB_URL}/pull/${TRAVIS_PULL_REQUEST})
EOF
)

  MESSAGE=$(cat <<EOF
${RESULT_EMOJI} *Build* [#${GITHUB_RUN_NUMBER}](${RUN_URL}) *(${RUN_RESULT})*

Commits:
\`${COMMITS_INVOLVED}\`

${PR_LINK_TEXT}
EOF
)

else
  # this is NOT a PR

  CURRENT_COMMIT_MSG=$(git log --oneline)
  MESSAGE=$(cat <<EOF
${RESULT_EMOJI} *Build* [#${GITHUB_RUN_NUMBER}](${RUN_URL}) *(${RUN_RESULT})*

Commit:
\`${CURRENT_COMMIT_MSG}\`
EOF
)

fi

# find compiled .apk file
APK_FILE=$(find "./app/build/outputs/apk/debug" -type f -name "*.apk")

if [[ -n "${TELEGRAM_CHAT_ID}" ]]; then
  ./.ci/notify-telegram.sh "${MESSAGE}" "${APK_FILE}"
fi

if [[ -n "${MATRIX_HOMESERVER}" ]]; then
  ./.ci/notify-matrix.sh "${MESSAGE}" "${APK_FILE}"
fi



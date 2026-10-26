#!/usr/bin/env bash
# Prints "VERSION_NAME=…" and "RELEASE_TAG=…" lines for $GITHUB_ENV.
#
#   release-version.sh <tag-prefix>      e.g. android-v, ios-v
#
# A pushed <prefix>X.Y.Z tag releases X.Y.Z. A push to main releases the latest
# <prefix> tag with the patch number bumped (0.1.0 when there is none yet). Anything
# else (pull requests) is a build only, with no tag.
set -euo pipefail
prefix="$1"

if [[ "$GITHUB_REF" == refs/tags/"$prefix"* ]]; then
  version="${GITHUB_REF#refs/tags/"$prefix"}"
  echo "VERSION_NAME=$version"
  echo "RELEASE_TAG=$prefix$version"
elif [[ "$GITHUB_EVENT_NAME" == push && "$GITHUB_REF" == refs/heads/main ]]; then
  git fetch --quiet --tags --force origin
  last=$(git tag -l "$prefix[0-9]*" --sort=-v:refname | head -1)
  if [[ -z "$last" ]]; then
    version=0.1.0
  else
    IFS=. read -r major minor patch <<< "${last#"$prefix"}"
    version="$major.$minor.$((${patch%%[!0-9]*} + 1))"
  fi
  echo "VERSION_NAME=$version"
  echo "RELEASE_TAG=$prefix$version"
else
  echo "VERSION_NAME=0.0.0-${GITHUB_SHA::7}"
  echo "RELEASE_TAG="
fi

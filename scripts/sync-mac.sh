#!/bin/bash
# Sync working tree to the Mac build host, excluding heavy/generated dirs.
rsync -az --delete \
  --exclude '.git/' --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' \
  --exclude 'node_modules/' --exclude '*.apk' --exclude '*.ipa' \
  --exclude 'ipa-out/' --exclude 'iosApp/iosApp.xcodeproj/' \
  --exclude 'local.properties' \
  /home/omar/personal-agent/ mac:build/personal-agent/

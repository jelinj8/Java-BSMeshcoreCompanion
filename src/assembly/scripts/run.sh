#!/usr/bin/env bash
# Linux and macOS. Runs from the app directory: config/, data/ and log/ are resolved against it.
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR" || exit 1

exec java \
  --module-path "$DIR/lib" \
  --add-modules javafx.controls \
  -Djava.library.path="$DIR" \
  -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager \
  -cp "$DIR/config:$DIR/app.jar:$DIR/lib/*" \
  cz.bliksoft.meshcorecompanion.AppLauncher "$@"

#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"

java \
  -XstartOnFirstThread \
  --module-path "$DIR/lib" \
  --add-modules javafx.controls \
  -Djava.library.path="$DIR" \
  -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager \
  -cp "$DIR/config:$DIR/app.jar:$DIR/lib/*" \
  cz.bliksoft.meshcorecompanion.AppLauncher "$@"

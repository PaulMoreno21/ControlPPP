#!/bin/sh
set -eu
backend_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
jar_path=${1:-"$backend_dir/build/libs/practicas-0.0.1-SNAPSHOT.jar"}
case "$jar_path" in
  /*) ;;
  *) jar_path="$PWD/$jar_path" ;;
esac
cd "$backend_dir"
exec java -jar "$jar_path" --crear-admin-general

#!/bin/sh
set -eu

destination=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
source_dir=${1:-"$HOME/Library/Application Support/PrismLauncher/instances/Auram/minecraft/mods"}

while IFS=: read -r artifact version; do
    cp -v "$source_dir/$artifact-$version.jar" "$destination/"
done < "$destination/auram.txt"

#!/usr/bin/env bash
# Package shared model data and the runnable JAR, without experiments or environments.
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
output="${1:-$repo/cluster-inputs-and-jar-v1.9.tar.gz}"
if [[ "$output" != /* ]]; then
  output="$PWD/$output"
fi
files=(
  matsim-gunma-1.x-SNAPSHOT.jar
  input/v1.9/gunma-v1.9-config.xml
  input/v1.8/gunma-v1.8-network.xml.gz
  input/v1.8/gunma-v1.8-1pct-plans.xml.gz
  input/v1.8/gunma-v1.8-1pct-facilities.xml.gz
  input/v1.8/gunma-v1.8-vehicleTypes.xml
  resources/mode_share_per_group_dist_ref.csv
)
for file in "${files[@]}"; do
  if [[ ! -f "$repo/$file" ]]; then
    echo "Missing required file: $repo/$file" >&2
    exit 1
  fi
done
mkdir -p "$(dirname -- "$output")"
COPYFILE_DISABLE=1 tar -czf "$output" -C "$repo" "${files[@]}"
echo "Created $output (inputs and JAR only). Extract into the cluster repository root."

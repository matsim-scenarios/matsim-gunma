#!/usr/bin/env bash
#SBATCH --time=08:00:00
#SBATCH --partition=smp
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=4
#SBATCH --mem=16G
#SBATCH --job-name=gunma-dashboards
#SBATCH --output=%x-%j.log
# Run from the experiment directory that contains the JAR, input/, resources/, and results/.
set -euo pipefail
if [[ "${1:-}" == --help ]]; then
  echo "Usage: bash scripts/postprocess-comparison.sh [comparison-directory] [counts-file] [runner-options...]"
  echo "Cluster: sbatch scripts/postprocess-comparison.sh"
  echo "Uses GunmaSimwrapperRunner for baseline then policy, without rerunning MATSim."
  echo "Set GUNMA_REPO, GUNMA_JAR, JAVA_BIN, JAVAC_BIN, or GUNMA_ANALYSIS_MEMORY if needed."
  exit 0
fi
if [[ -n "${SLURM_JOB_ID:-}" ]]; then
  repo="${GUNMA_REPO:-${SLURM_SUBMIT_DIR:?Submit from the experiment directory}}"
else
  repo="${GUNMA_REPO:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
fi
cd "$repo"
if [[ -n "${SLURM_JOB_ID:-}" ]] && type module >/dev/null 2>&1; then
  module load java/25
fi
java_bin="${JAVA_BIN:-java}"
javac_bin="${JAVAC_BIN:-$(dirname -- "$(command -v "$java_bin")")/javac}"
jar="${GUNMA_JAR:-$repo/matsim-gunma-1.x-SNAPSHOT.jar}"
comparison="${1:-$repo/results/comparison}"
counts="${2:-$repo/input/v1.8/gunma-v1.8-counts-jartic.xml.gz}"
if [[ $# -gt 0 ]]; then shift; fi
if [[ $# -gt 0 ]]; then shift; fi
source="$repo/src/main/java/org/matsim/dashboard/GunmaSimwrapperRunner.java"
for file in "$jar" "$source" "$counts"; do
  [[ -f "$file" ]] || { echo "Missing required file: $file" >&2; exit 1; }
done
[[ -d "$comparison/baseline" && -d "$comparison/policy" ]] || {
  echo "Expected baseline/ and policy/ under $comparison" >&2; exit 1;
}
[[ -d "$repo/resources" ]] || { echo "Missing dashboard reference resources: $repo/resources" >&2; exit 1; }
"$java_bin" -version
# Compile only the updated runner against the existing fat JAR: no Maven build or new JAR needed.
classes=$(mktemp -d "${TMPDIR:-/tmp}/gunma-dashboards.XXXXXX")
trap 'rm -rf -- "$classes"' EXIT
"$javac_bin" -sourcepath "" -cp "$jar" -d "$classes" "$source"
"$java_bin" -Djava.awt.headless=true "-Xmx${GUNMA_ANALYSIS_MEMORY:-12G}" \
  -cp "$classes:$jar:$repo" org.matsim.dashboard.GunmaSimwrapperRunner \
  --counts "$counts" "$comparison/baseline" "$comparison/policy" "$@"
echo "Dashboard post-processing finished for baseline and policy."
echo "Upload each complete case directory, including dashboard-*.yaml, simwrapper-config.yaml, and analysis/."

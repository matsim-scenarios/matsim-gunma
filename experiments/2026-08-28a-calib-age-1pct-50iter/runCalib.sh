#!/bin/bash --login
#SBATCH --time=200:00:00
#SBATCH --partition=smp
#SBATCH --output=./logfiles/logfile_%x-%j.log
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=12
#SBATCH --mem=64G
#SBATCH --job-name=gunma-calib-age-1pct-50iter
#SBATCH --mail-type=END,FAIL
#SBATCH --mail-user=rehmann@vsp.tu-berlin.de

# Experiment settings. These can also be overridden when submitting the job:
# RUN_SAMPLE=25pct RUN_MATSIM_ITERATIONS=5 RUN_CALIBRATION_RUNS=20 sbatch runCalib.sh
sample="${RUN_SAMPLE:-1pct}"
matsim_iterations="${RUN_MATSIM_ITERATIONS:-50}"
calibration_runs="${RUN_CALIBRATION_RUNS:-10}"

date
hostname

if [ ! -d "logfiles" ]; then
        mkdir logfiles
fi

command="python -u calibrate_age_asc.py"

echo ""
echo "command is $command --${sample} --iterations ${matsim_iterations}"
echo "calibration runs: ${calibration_runs}"
echo ""

source env/bin/activate

module add java/25
java -version

GUNMA_CALIB_ARGS="--${sample} --iterations ${matsim_iterations}" \
GUNMA_CALIB_TRIALS="${calibration_runs}" \
$command

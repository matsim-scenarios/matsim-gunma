# Post-process an existing baseline/policy comparison

Run this on the cluster, where the complete simulation outputs still exist. It uses
`GunmaSimwrapperRunner` for the full existing Gunma dashboard set (overview,
population, activities, OD, work OD, trips, and traffic counts), baseline first and
policy second. It does not rerun MATSim or change its scoring/configuration files.

From the experiment directory containing the existing fat JAR and `results/`:

```bash
tar -xzf gunma-dashboard-postprocessing.tar.gz
sbatch scripts/postprocess-comparison.sh
```

The batch script loads Java 25 and compiles only the updated runner into a temporary
directory against the existing JAR. No Maven build, new JAR, or Python environment
is required. The archive supplies the runner, launcher, reference resources, and
the v1.8 observed counts that v1.9 reuses.

The defaults are `results/comparison/{baseline,policy}` and
`input/v1.8/gunma-v1.8-counts-jartic.xml.gz`. To override them:

```bash
sbatch scripts/postprocess-comparison.sh /path/to/results/comparison /path/to/counts.xml.gz
```

Keep the original output events, network, plans, activities, persons, trips, logs,
and iteration statistics in each case directory. The small QA download is not
sufficient for this post-processing. Missing main inputs are checked for both cases
before either dashboard set is written. The sample scaling is read from each
case's effective output config (0.01 for these runs); do not expand the CSVs again.

Outputs are created inside each original case directory:

- `dashboard-*.yaml` and `simwrapper-config.yaml`
- `analysis/`, including copied reference/shape files and generated dashboard data
- `analysis/traffic-car/count_comparison_by_hour.csv` for the paper

Existing dashboard definitions are regenerated; SimWrapper handles existing analysis
outputs according to its normal caching. Original simulation outputs are retained.
Upload the complete baseline/policy case directories for SimWrapper, not just the
YAML files. The work-OD dashboard retains the existing runner's prepared OD layers;
these are not newly inferred mode-specific flows from the policy simulation.

After completion, rerun the download block in `src/main/R/paper_ewgt.qmd` to retrieve
the traffic-count CSV, then render the paper figures.

For a definition-only check (does not run the analyses):

```bash
bash scripts/postprocess-comparison.sh results/comparison input/v1.8/gunma-v1.8-counts-jartic.xml.gz --generate-only
```

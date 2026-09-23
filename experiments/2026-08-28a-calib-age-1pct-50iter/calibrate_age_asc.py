#!/usr/bin/env python3
"""Calibrate age-group-specific mode constants with advanced scoring.

This is intentionally separate from calibrate.py.  It calibrates only grouped
alternative-specific constants; it does not calibrate distance utilities.
"""

import os
import sys
from typing import Sequence

import numpy as np
import pandas as pd

from matsim.calibration import create_calibration, utils
from matsim.calibration.analysis import read_trips_and_persons
from matsim.calibration.base import CalibratorBase


MODES = ["walk", "car", "ride", "bike"]
FIXED_MODE = "walk"
AGE_GROUP = "age_group"
AGE_GROUPS = ["0-17", "18-29", "30-44", "45-64", "65-74", "75-84", "85+"]


def age_group(age):
    """Return the calibration group for an age value."""
    if pd.isna(age):
        return pd.NA
    if age < 18:
        return "0-17"
    if age < 30:
        return "18-29"
    if age < 45:
        return "30-44"
    if age < 65:
        return "45-64"
    if age < 75:
        return "65-74"
    if age < 85:
        return "75-84"
    return "85+"


def read_age_target(path="resources/mode_share_per_group_dist_ref.csv"):
    """Aggregate the existing detailed age reference into conditional shares."""
    ref = pd.read_csv(path)
    ref["age"] = ref["age"].astype("string").str.strip()
    ref = ref[ref["age"].notna() & ref["age"].ne("")].copy()

    age_mapping = {
        "0 - 17": "0-17",
        "18 - 29": "18-29",
        "30 - 44": "30-44",
        "45 - 64": "45-64",
        "65 - 74": "65-74",
        "75 - 84": "75-84",
        "85+": "85+",
    }
    ref[AGE_GROUP] = ref["age"].map(age_mapping)
    if ref[AGE_GROUP].isna().any():
        unknown = sorted(ref.loc[ref[AGE_GROUP].isna(), "age"].unique())
        raise ValueError(f"Unknown reference age groups: {unknown}")

    target = (ref[ref.main_mode.isin(MODES)]
              .groupby([AGE_GROUP, "main_mode"], as_index=False)["share"].sum())
    target["share"] = target["share"] / target.groupby(AGE_GROUP)["share"].transform("sum")
    target = target.rename(columns={"main_mode": "mode"})
    target["age_order"] = target[AGE_GROUP].map({v: i for i, v in enumerate(AGE_GROUPS)})
    return target.sort_values(["age_order", "mode"]).drop(columns="age_order")


def add_age_group(persons):
    """Add the categorical attribute used by advanced scoring."""
    persons = persons.copy()
    persons[AGE_GROUP] = persons["age"].map(age_group)
    if persons[AGE_GROUP].isna().any():
        raise ValueError("Some calibration persons have no usable age")
    return persons


def filter_persons(persons):
    persons = persons[persons.person.str.startswith("gunma")].copy()
    persons = add_age_group(persons)
    print(f"Filtered {len(persons)} persons")
    return persons


def filter_modes(trips):
    return trips[trips.main_mode.isin(MODES)]


def get_advanced_params(config, group, value, mode):
    """Return/create advanced-scoring mode parameters for one person group."""
    advanced = config.setdefault("advancedScoring", {})
    parameters = advanced.setdefault("scoringParameters", [])

    for parameter_set in parameters:
        if parameter_set.get(group) != value:
            continue
        mode_params = parameter_set.setdefault("modeParams", [])
        for mode_param in mode_params:
            if mode_param.get("mode") == mode:
                return mode_param
        mode_param = {"mode": mode}
        mode_params.append(mode_param)
        return mode_param

    mode_param = {"mode": mode}
    parameters.append({group: value, "modeParams": [mode_param]})
    return mode_param


class AdvancedASCGroupCalibrator(CalibratorBase):
    """Calibrate grouped ASCs written as advancedScoring deltaConstants."""

    def __init__(self, modes: Sequence[str], initial, target, fixed_mode=FIXED_MODE, lr=None):
        super().__init__(modes, initial, target, lr, None)
        self.fixed_mode = fixed_mode
        self.target = target.rename(columns={"main_mode": "mode"}, errors="ignore")
        self.groups = AGE_GROUPS

        if set(self.target[AGE_GROUP]) != set(self.groups):
            raise ValueError(f"Age target must contain exactly: {', '.join(self.groups)}")

    @property
    def name(self):
        return "age_asc"

    @property
    def num_targets(self):
        return len(self.groups) * len(self.modes)

    def init_study(self, study):
        study.set_user_attr("modes", list(self.modes))
        study.set_user_attr("group", AGE_GROUP)
        study.set_user_attr("groups", self.groups)
        study.set_user_attr("fixed_mode", self.fixed_mode)
        print("Calibrating advanced-scoring age groups:", self.groups)
        print(self.target.to_string(index=False))

    def update_config(self, study, trial, prefix, config):
        for group in self.groups:
            for mode in self.modes:
                parameter = f"[{AGE_GROUP}={group}]-{mode}"
                value = 0.0 if mode == self.fixed_mode else trial.suggest_float(
                    prefix + parameter, -sys.float_info.max, sys.float_info.max
                )
                get_advanced_params(config, AGE_GROUP, group, mode)["deltaConstant"] = value

    def sample_initial(self, param):
        return 0.0

    def update_step(self, param, last_trial, completed):
        group_param, _, mode = param.rpartition("-")
        if mode == self.fixed_mode:
            return 0.0
        group = group_param.removeprefix(f"[{AGE_GROUP}=").removesuffix("]")
        target = self.target[(self.target[AGE_GROUP] == group) & (self.target["mode"] == mode)]["share"].iloc[0]
        fixed_target = self.target[(self.target[AGE_GROUP] == group) & (self.target["mode"] == self.fixed_mode)]["share"].iloc[0]
        return self.calc_asc_update(
            target,
            last_trial.user_attrs[f"{param}_share"],
            fixed_target,
            last_trial.user_attrs[f"{group_param}-{self.fixed_mode}_share"],
        )

    def calc_stats(self, trial, run_dir, transform_persons=None, transform_trips=None):
        trips, persons = read_trips_and_persons(
            run_dir, transform_persons=transform_persons, transform_trips=transform_trips
        )

        if AGE_GROUP not in trips:
            if "age" not in trips:
                trips = trips.merge(persons[["person", "age"]], on="person", how="left")
            trips[AGE_GROUP] = trips.age.map(age_group)

        errors = []
        for group in self.groups:
            subgroup = trips[trips[AGE_GROUP] == group]
            shares = subgroup.groupby("main_mode").size() / len(subgroup)
            target = self.target[self.target[AGE_GROUP] == group].set_index("mode").share
            obtained = shares.reindex(target.index, fill_value=0.0)
            error = (obtained - target).abs()

            for mode in self.modes:
                parameter = f"[{AGE_GROUP}={group}]-{mode}"
                trial.set_user_attr(f"{parameter}_share", float(obtained[mode]))
                trial.set_user_attr(f"{parameter}_mae", float(error[mode]))
                errors.append(float(error[mode]))

            print(f"{group}:" + "".join(f" {mode}={obtained[mode]:.3f}" for mode in self.modes))

        return np.asarray(errors)


target = read_age_target()
study, obj = create_calibration(
    "calib-age-asc-7groups",
    AdvancedASCGroupCalibrator(
        MODES,
        {"walk": 0.0, "car": -1.127816, "ride": -1.393684, "bike": -1.726253},
        target,
        lr=utils.linear_scheduler(start=0.3, interval=30),
    ),
    os.environ.get("GUNMA_CALIB_JAR", "matsim-gunma-1.x-SNAPSHOT.jar"),
    "input/v1.8/gunma-v1.8-config.xml",
    args=os.environ.get("GUNMA_CALIB_ARGS", "--1pct"),
    jvm_args=os.environ.get("GUNMA_CALIB_JVM_ARGS", "-Xmx55G -Xms55G -XX:+AlwaysPreTouch -XX:+UseParallelGC"),
    transform_persons=filter_persons,
    transform_trips=filter_modes,
    chain_runs=utils.default_chain_scheduler,
    debug=True,
)

study.optimize(obj, int(os.environ.get("GUNMA_CALIB_TRIALS", "10")))

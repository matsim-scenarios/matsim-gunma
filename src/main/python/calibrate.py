#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import pandas as pd
import geopandas as gpd

from matsim.calibration import create_calibration, ASCCalibrator, utils, analysis

#%%

# if os.path.exists("mid.csv"):
#     srv = pd.read_csv("mid.csv")
#     sim = pd.read_csv("sim.csv")
#
#     _, adj = analysis.calc_adjusted_mode_share(sim, srv)
#
#     print(srv.groupby("mode").sum())
#
#     print("Adjusted")
#     print(adj.groupby("mode").sum())
#
#     adj.to_csv("mid_adj.csv", index=False)

#%%

# modes = ["walk", "car", "ride", "pt", "bike"]
# INITIAL ASCs
modes = ["walk", "car", "ride", "bike"]
fixed_mode = "walk"
initial = {
    "bike": -1.264026088416823,
    "car": -1.0023286735499686,
    "ride": -0.6512361412363425,
}

# Target (Trip-Based?) Mode Share
target = {
    "walk": 0.117,
    "bike": 0.086,
    "car": 0.682,
    "ride": 0.115
}

# region = gpd.read_file("input/shp/gunma_2450.shp").set_crs("EPSG:2450")


def filter_persons(persons):
    df = persons[persons.person.str.startswith("gunma")]
    print("Filtered %s persons" % len(df))
    return df

def filter_modes(df):
    return df[df.main_mode.isin(modes)]


# FIXME: Adjust paths and config

study, obj = create_calibration(
    "calib",
    ASCCalibrator(modes, initial, target, lr=utils.linear_scheduler(start=0.3, interval=15)),
    "matsim-gunma-1.x-SNAPSHOT.jar",
    "input/v1.5/gunma-v1.5-config.xml",
    args="--1pct",
    jvm_args="-Xmx55G -Xms55G -XX:+AlwaysPreTouch -XX:+UseParallelGC",
    transform_persons=filter_persons,
    transform_trips=filter_modes,
    chain_runs=utils.default_chain_scheduler, debug=True
)

#%%

# how many times the optimization occurs.
study.optimize(obj, 10)

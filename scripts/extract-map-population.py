#!/usr/bin/env python3
"""Stream MATSim home coordinates to a compact CSV without loading all plans into memory."""
import argparse
import csv
import gzip
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--input", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
args.output.parent.mkdir(parents=True, exist_ok=True)
temporary = args.output.with_name(args.output.name + ".tmp")
count = 0
try:
    with gzip.open(args.input, "rb") as source, gzip.open(temporary, "wt", encoding="utf-8", newline="") as output:
        writer = csv.writer(output)
        writer.writerow(("person", "home_x", "home_y"))
        context = ET.iterparse(source, events=("start", "end"))
        _, root = next(context)
        for event, element in context:
            if event != "end" or element.tag != "person":
                continue
            attrs = {a.get("name"): a.text for a in element.findall("attributes/attribute")}
            if not element.get("id") or not attrs.get("home_x") or not attrs.get("home_y"):
                raise ValueError("Missing person ID or home coordinates: " + str(element.get("id")))
            writer.writerow((element.get("id"), float(attrs["home_x"]), float(attrs["home_y"])))
            count += 1
            root.clear()
    if not count:
        raise ValueError("No persons found in " + str(args.input))
    temporary.replace(args.output)
except BaseException:
    temporary.unlink(missing_ok=True)
    raise
print("Extracted", count, "persons from", args.input, "to", args.output)

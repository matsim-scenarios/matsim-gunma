JAR := matsim-gunma-*.jar
V := v1.5

p := input/$V
gunma := ../shared-svn/projects/matsim-gunma/data

MEMORY ?= 30G

CRS := EPSG:2450

# in my case, osmosis can be called directly. - jr
osmosis := osmosis

SUMO_HOME := /Users/jakob/sumo

# Scenario creation tool
sc := java -Xms$(MEMORY) -Xmx$(MEMORY) -XX:+UseParallelGC -cp $(JAR) org.matsim.prepare.RunOpenGunmaCalibration

$(JAR):
	mvn package


S := 1
X := 0.01


NETWORK_FINAL := $(p)/gunma-$V-network.xml.gz
PLANS_FINAL := $(p)/gunma-$V-$Spct-plans.xml.gz
FACILITIES_FINAL := $(p)/gunma-$V-$Spct-facilities.xml.gz
CONFIG_FINAL := $(p)/gunma-$V-config.xml
VEHICLES_FINAL := $(p)/gunma-$V-vehicleTypes.xml


network: $(NETWORK_FINAL)
vehicles : $(VEHICLES_FINAL)
facilities: $(FACILITIES_FINAL)
plans: $(PLANS_FINAL)


###############################################################
### A) CONFIG
###############################################################

# 1) Change VERSION in OpenGunmaScenario
# 2) make empty directory in input matching VERSION
# 3) Run PrepareConfig

#$(p)/gunma-$V-config.xml: $(gunma)/raw/matsim_inputs_lichen_luo/config_simulation.xml
#	$(sc) prepare prepare-config\
#    	 --input $<\
#    	 --output $@

###############################################################
### B) NETWORK
###############################################################


#### B1) Create OSM Network
# A finegrained network is created within bounding box around Gunma. This is combined with a coarser network for the
# surrounding area (75km around Gunma), to allow for trips that start or end outside of the study area. The two networks
# are merged together
# Railways are removed; I'm not sure why these would be here in the first place
# Inputs:
# - japan-260210.osm.pbf is downloaded from geofabrik.
# - The .poly files required for osmosis are created in the R script (process_census.R). They can also be made in QGIS.
# Note: --wx creates a .osm output while --wb creates an .osm.pbf file
NETWORK_OSM := $(p)/b1_network.osm
$(NETWORK_OSM): $(gunma)/raw/01_shapefiles/osm_pbf_geofabrik/japan-260210.osm.pbf

	# Detailed network includes bikes as well
	$(osmosis) --rb file=$<\
	 --tf accept-ways bicycle=designated highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,living_street,unclassified,cycleway\
	 --bounding-polygon file=$(gunma)/processed/01_shapefiles/study_area_boundary/gunma_00km_envelope.poly \
	 --used-node --wb input/network-detailed.osm.pbf

	$(osmosis) --rb file=$<\
	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction\
	 --bounding-polygon file=$(gunma)/processed/01_shapefiles/study_area_boundary/gunma_75km_envelope.poly \
	 --used-node --wb input/network-coarse.osm.pbf

	$(osmosis) --rb file=input/network-coarse.osm.pbf --rb file=input/network-detailed.osm.pbf\
  	 --merge\
  	 --tag-transform file=input/remove-railway.xml\
  	 --wx $@

	rm input/network-detailed.osm.pbf
	rm input/network-coarse.osm.pbf


### B2) Create SUMO Network
# Uses netconvert to create a SUMO network from the OSM file. We use the same parameters as in the Berlin scenario
# We also reproject the network to a local coordinate system (EPSG:2450),
# We also keep all attributes from OSM, which we will use later for filtering and for the location choice model.
NETWORK_SUMO := $(p)/b2_sumo.net.xml
$(NETWORK_SUMO): $(NETWORK_OSM)
	$(SUMO_HOME)/bin/netconvert --geometry.remove --ramps.guess --ramps.no-split\
	 --type-files $(SUMO_HOME)/data/typemap/osmNetconvert.typ.xml\
	 --tls.guess-signals true --tls.discard-simple --tls.join --tls.default-type actuated\
	 --junctions.join --junctions.corner-detail 5\
	 --roundabouts.guess --remove-edges.isolated\
	 --no-internal-links --keep-edges.by-vclass passenger,truck,bicycle\
	 --remove-edges.by-vclass hov,tram,rail,rail_urban,rail_fast,pedestrian\
	 --output.original-names --output.street-names\
	 --osm.lane-access false --osm.bike-access false\
	 --osm.all-attributes\
	 --osm.extra-attributes way:id\
	 --proj "+proj=tmerc +lat_0=36 +lon_0=138.5 +k=1 +x_0=0 +y_0=0 +ellps=GRS80 +units=m +no_defs"\
	 --osm-files $< -o=$@



### B3) Create MATSim Network
$(NETWORK_FINAL): $(NETWORK_SUMO)
	$(sc) prepare network-from-sumo $< --target-crs EPSG:2450 --lane-restrictions REDUCE_CAR_LANES --output $@

	$(sc) prepare clean-network $@  --output $@ --modes car,bike,ride,truck --remove-turn-restrictions

	mv $(p)/gunma-$V-network-ft.csv.gz $(p)/b3_network-ft.csv.gz
	mv $(p)/gunma-$V-network-linkGeometries.csv $(p)/b3_network-linkGeometries.csv


###############################################################
### C) VEHICLE TYPES
###############################################################

$(VEHICLES_FINAL):
	$(sc) prepare prepare-vehicle-types --output $@

###############################################################
### D) FACILITIES
###############################################################
# Inputs:
# - jis_zones_75km_envelope.shp : is created by process_shape_files.Rmd (all JIS zones within the 75km envelope surrounding Gunma)
# - facility_locations_yellowpages.csv : is created by process_facilities.Rmd. This contains all the "work", "education", and "other"
# 	facility types within 75km of Gunma Prefecture.
#	 --shp $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp \

FACILITIES_FULL := $(p)/d1_facilities-all.xml.gz
$(FACILITIES_FULL): $(NETWORK_FINAL)
	$(sc) prepare facilities-gunma --network $< \
	 --telfacs $(gunma)/processed/facility_locations_yellowpages/facility_locations_yellowpages.csv \
	 --output $@

###############################################################
### E) POPULATION
###############################################################

# 1) Merge Shapefiles for census data

$(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m-2450.shp: $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5338/MESH05338.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5438/MESH05438.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5439/MESH05439.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5538/MESH05538.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5539/MESH05539.shp
	ogr2ogr $@ $(word 1,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 2,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 3,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 4,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 5,$^) -nln mesh250m
	ogr2ogr -t_srs EPSG:2450  $@ $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp -nln mesh250m



# 2) Create Male Population of Gunma
# We split by gender because different columns in census table are used for men and women.
PLANS_MEN := $(p)/e02_plans_men-100pct.xml.gz
$(PLANS_MEN): $(gunma)/raw/microcensus/tblT001102Q10.txt $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m-2450.shp
	$(sc) prepare gunma-population\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:4612\
		--sex MALE\
		--output $@

# 3) Create Female Population of Gunma
PLANS_WOMEN := $(p)/e03_plans_women-100pct.xml.gz
$(PLANS_WOMEN): $(gunma)/raw/microcensus/tblT001102Q10.txt $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m-2450.shp
	$(sc) prepare gunma-population\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:4612\
		--sex FEMALE\
		--output $@

# 4) Generate commuter population: people who live outside of Gunma prefecture but work in Gunma. We don't include age or gender for these agents.
PLANS_COMMUTERS := $(p)/e04_plans_commuters-100pct.xml.gz
$(PLANS_COMMUTERS): $(gunma)/processed/commuters_od_matrix/work_od_matrix_scaled.csv $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp
	$(sc) prepare gunma-commuter\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:2450\
		--output $@

# 5) Merge populations, and add JIS codes for home locations.
PLANS_STATIC_100 := $(p)/e05_plans_static-100pct.xml.gz
$(PLANS_STATIC_100): $(PLANS_MEN)  $(PLANS_WOMEN) $(PLANS_COMMUTERS)
	$(sc) prepare merge-populations $^\
		--output $@

	$(sc) prepare lookup-jis-code --input $@ --output $@ --shp $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp

# 6) Downsample (e.g. to 25% or 1%)
# downsample-population keeps the filename. So the second step changes filename of e06 to remain consistent
PLANS_STATIC = $(p)/e06_plans_static-$Spct.xml.gz
$(PLANS_STATIC): $(PLANS_STATIC_100)
	$(sc) prepare downsample-population $< \
		--sample-size 1.0 \
		--samples $X

	mv $(subst e06,e05,$(PLANS_STATIC)) $(PLANS_STATIC)



# 7) Assign daily plan to each MATSim Agent
# Here we match MATSim agents with travel survey respondents, based on their age and gender. The daily plan
# from the travel survey respondent is copied to the MATSim agent, but the locations are deleted.
# TODO: I don't understand why assign-reference-population is necessary - it seems to be doing the same thing as activity-sampling. It even calls upon that class... I'm gonna skip it for now until I understand better
# NOTE: We we hardcoded in the City/District Divide; this only works for Gunma.
# NOTE: We ignore employment for now, because we haven't included it into our static population as of yet.
PLANS_ACTS := $(p)/e07_plans_activities-$Spct.xml.gz
$(PLANS_ACTS): $(PLANS_STATIC) $(gunma)/processed/travel_survey/person_attributes.csv $(gunma)/processed/travel_survey/activities.csv
	$(sc) prepare activity-sampling \
		--seed 1 \
 		--input $< \
 		--output $@ \
 		--persons $(word 2,$^)\
 		--activities $(word 3,$^)


# 8) Location Choice
PLANS_LOCS := $(p)/e08_plans_locations-$Spct.xml.gz
$(PLANS_LOCS): $(PLANS_ACTS) $(FACILITIES_FULL) $(p)/gunma-$V-network.xml.gz $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp $(gunma)/processed/commuters_od_matrix/work_od_matrix_scaled.csv
	$(sc) prepare init-location-choice \
	 --input $< \
	 --output $@ \
	 --facilities $(word 2,$^) \
	 --network $(word 3,$^) \
	 --shp $(word 4,$^) \
	 --commuter $(word 5,$^) \
	 --sample $X \
	 --k 5

# 9) Eval Run (O iterations) because we need experienced plans for the next step.
# Note: I am not actually using the inputs because the path needs to be slightly different :/
PLANS_EXP := $(p)/e09_plans_experienced-$Spct.xml.gz
$(PLANS_EXP): $(p)/gunma-$V-config.xml $(PLANS_LOCS) $(FACILITIES_FULL) $(VEHICLES_FINAL)
	$(sc) run \
	--$Spct \
	--config $< \
	--population $(word 2,$^) \
	--facilities $(word 3,$^) \
	--mode eval

	cp output/eval.output_experienced_plans.xml.gz $@

# 10) Create the counts file required for location choice
$(p)/gunma-$V-counts-mlit.xml.gz: $(gunma)/processed/roadcounts/matsim_linkId_to_roadcounts.csv
	$(sc) prepare counts-from-mlit --input $< --output $@


# 11) create $(p)/gunma-$V-$Spct.plans_selection_$(ERROR_METRIC).csv, which specifies for each agent, which is the best plan

ERROR_METRIC ?= log_error
PLANS_SELECTION_CSV := $(p)/e11_plans_selection_$(ERROR_METRIC)-$Spct.csv
$(PLANS_SELECTION_CSV): $(PLANS_EXP) $(p)/gunma-$V-network.xml.gz $(p)/gunma-$V-counts-mlit.xml.gz
	$(sc) prepare run-count-opt\
	 --input $<\
	 --network $(word 2,$^)\
     --counts $(word 3,$^)\
	 --output $@ \
	 --sample-size $X \
	 --metric $(ERROR_METRIC)


# 12) filter the plans to only include the best plan
PLANS_SELECTION_XML := $(p)/e12_plans_selection_$(ERROR_METRIC)-$Spct.xml.gz
$(PLANS_SELECTION_XML) : $(PLANS_LOCS) $(PLANS_SELECTION_CSV)
	$(sc) prepare select-plans-idx\
 	 --input $< \
 	 --csv $(word 2,$^)\
 	 --output $@


# 13) run 20 iterations with route choice.
output/eval-$(ERROR_METRIC) : $(p)/gunma-$V-config.xml $(PLANS_SELECTION_XML) $(FACILITIES_FULL)
	$(sc) run \
	 --mode "routeChoice" \
	 --iterations 20 \
	 --output $@ \
	 --$Spct \
	 --population $(word 2,$^) \
	 --facilities $(word 3,$^) \
	 --config $<


# 14) Prepare Initial plans
# A) When I created the commuters's daily plans, I didn't add any start_time to work or home, because it depends on how long
# they have to travel. Now that some iterations have run through, I've added start times based on the trip to that activity.
# B) Splits activity types into type_DURATION, i've turned off merging overnight activities. TODO: check w/ KN or MK
$(PLANS_FINAL): output/eval-$(ERROR_METRIC)
	$(sc) prepare amend-start-time-commuters \
	--input $</routeChoice.output_selected_plans.xml.gz --output $@

	$(sc) prepare split-activity-types-duration \
	 --input $@ --output $@

###############################################################
### F) FACILITIES - FILTERING
###############################################################

$(FACILITIES_FINAL): $(FACILITIES_FULL) $(PLANS_FINAL)
	$(sc) prepare facilities-filter --input $< \
	 --plans $(word 2,$^) \
	 --output $@


###############################################################
### G) CALIBRATION
###############################################################
# Upload to folder in cluster
# - input/$V
# - JAR
# - calibrate.py

# Copy following from other calibration run (e.g. Berlin v7.0-1pct)
# - env
# - run

# Calibration script runs RunOpenGunmaScenario! (Not RunOpenGunmaCalibration 😵‍💫)
# So make sure you make all neccessary changes there. e.g turn SimWrapper off and
# increase iterations to 500.

#### DASHBOARD
output/dashboard-1.yaml:
	$(sc) prepare gunma-dashboard output/




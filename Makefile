JAR := matsim-gunma-*.jar
V := v1.4

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
config: $p/gunma-$V-config.xml
network: $p/gunma-$V-network.xml
facilities: $p/gunma-$V-facilities.xml
plans: $p/gunma-locations-$V-$Spct.plans.xml.gz
plans_exp: $p/gunma-experienced-$V-$Spct.plans.xml.gz


###############################################################
### A) CONFIG
###############################################################

$p/gunma-$V-config.xml: $(gunma)/raw/matsim_inputs_lichen_luo/config_simulation.xml
	$(sc) prepare prepare-config\
    	 --input $<\
    	 --output $@

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
$p/network.osm: $(gunma)/raw/01_shapefiles/osm_pbf_geofabrik/japan-260210.osm.pbf

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
$p/sumo.net.xml: $p/network.osm
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



### B2) Create MATSim Network
$p/gunma-$V-network.xml: $p/sumo.net.xml
	$(sc) prepare network-from-sumo $< --target-crs EPSG:2450 --lane-restrictions REDUCE_CAR_LANES --output $@

	$(sc) prepare clean-network $@  --output $@ --modes car,bike,ride,truck --remove-turn-restrictions


#	$(sc) prepare reproject-network\
#	 --input $@	--output $@\
#	 --input-crs $(CRS) --target-crs $(CRS)\
	 --mode truck=freight
#
#	$(sc) prepare apply-network-params freespeed capacity\
# 	  --network $@ --output $@\
#	  --input-features $p/berlin-$V-network-ft.csv.gz\
#	  --model org.matsim.prepare.network.BerlinNetworkParams
#
#	$(sc) prepare apply-network-params capacity\
# 	  --network $@ --output $@\
#	  --input-features $p/berlin-$V-network-ft.csv.gz\
#	  --road-types residential,living_street\
#	  --capacity-bounds 0.3\
#	  --model org.matsim.application.prepare.network.params.hbs.HBSNetworkParams\
#	  --decrease-only


###############################################################
### C) VEHICLE TYPES
###############################################################

xxx : $p/gunma-$V-vehicleTypes.xml
$p/gunma-$V-vehicleTypes.xml:
	$(sc) prepare prepare-vehicle-types --output $@

###############################################################
### C) FACILITIES
###############################################################
# Inputs:
# - jis_zones_75km_envelope.shp : is created by process_shape_files.Rmd (all JIS zones within the 75km envelope surrounding Gunma)
# - facility_locations_yellowpages.csv : is created by process_facilities.Rmd. This contains all the "work", "education", and "other"
# 	facility types within 75km of Gunma Prefecture.
#	 --shp $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp \

$p/gunma-$V-facilities.xml: $p/gunma-$V-network.xml
	$(sc) prepare facilities-gunma --network $< \
	 --telfacs $(gunma)/processed/facility_locations_yellowpages/facility_locations_yellowpages.csv \
	 --output $@

###############################################################
### D) POPULATION
###############################################################

# 1) Merge Shapefiles for census data

$(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m-2450.shp: $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5338/MESH05338.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5438/MESH05438.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5439/MESH05439.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5538/MESH05538.shp $(gunma)/raw/01_shapefiles/mesh250m_census/QDDSWQ5539/MESH05539.shp
	ogr2ogr $@ $(word 1,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 2,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 3,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 4,$^) -nln mesh250m
	ogr2ogr -update -append $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp $(word 5,$^) -nln mesh250m
	ogr2ogr -t_srs EPSG:2450  $@ $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m.shp -nln mesh250m



ddd: $p/gunma-static-$V-100pct.plans.xml.gz
# 2) Generate static population plans
#		--facilities $(word 3,$^) --facilities-attr resident\
# input/facilities.gpkg
$p/gunma-static-men-$V-100pct.plans.xml.gz: $(gunma)/raw/microcensus/tblT001102Q10.txt $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m-2450.shp
	$(sc) prepare gunma-population\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:4612\
		--sex MALE\
		--output $@

$p/gunma-static-women-$V-100pct.plans.xml.gz: $(gunma)/raw/microcensus/tblT001102Q10.txt $(gunma)/processed/01_shapefiles/mesh250m_census/mesh250m-2450.shp
	$(sc) prepare gunma-population\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:4612\
		--sex FEMALE\
		--output $@

$p/gunma-static-commuters-$V-100pct.plans.xml.gz: $(gunma)/processed/commuters_od_matrix/work_od_matrix_scaled.csv $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp
	$(sc) prepare gunma-commuter\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:2450\
		--output $@


$p/gunma-static-$V-100pct.plans.xml.gz:	$p/gunma-static-men-$V-100pct.plans.xml.gz  $p/gunma-static-women-$V-100pct.plans.xml.gz $p/gunma-static-commuters-$V-100pct.plans.xml.gz
	$(sc) prepare merge-populations $^\
		--output $@

	#rm $p/gunma-static-women-$V-100pct.plans.xml.gz
	#rm $p/gunma-static-men-$V-100pct.plans.xml.gz

	$(sc) prepare lookup-jis-code --input $@ --output $@ --shp $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp


# 3) Downsample to 25% and 1%
$p/gunma-static-$V-$Spct.plans.xml.gz: $p/gunma-static-$V-100pct.plans.xml.gz
	$(sc) prepare downsample-population $< \
		--sample-size 1.0 \
		--samples $X

# 3) Assign daily plan to each MATSim Agent
# Here we match MATSim agents with travel survey respondents, based on their age and gender. The daily plan
# from the travel survey respondent is copied to the MATSim agent, but the locations are deleted.
# TODO: I don't understand why assign-reference-population is necessary - it seems to be doing the same thing as activity-sampling. It even calls upon that class... I'm gonna skip it for now until I understand better
# NOTE: We we hardcoded in the City/District Divide; this only works for Gunma.
# NOTE: We ignore employment for now, because we haven't included it into our static population as of yet.
$p/gunma-activities-$V-$Spct.plans.xml.gz: $p/gunma-static-$V-$Spct.plans.xml.gz $(gunma)/processed/travel_survey/person_attributes.csv $(gunma)/processed/travel_survey/activities.csv
	$(sc) prepare activity-sampling \
		--seed 1 \
 		--input $< \
 		--output $@ \
 		--persons $(word 2,$^)\
 		--activities $(word 3,$^)

	#$(sc) prepare assign-reference-population --population $@ --output $@\
#	 --persons src/main/python/table-persons.csv\
#  	 --activities src/main/python/table-activities.csv\
#  	 --shp $(germany)/../matsim-berlin/data/SrV/zones/zones.shp\
#  	 --shp-crs $(CRS)\
#	 --facilities $(word 2,$^)\
#	 --network $(word 3,$^)\



# 4) Location Choice
$p/gunma-locations-$V-$Spct.plans.xml.gz: $p/gunma-activities-$V-$Spct.plans.xml.gz $p/gunma-$V-facilities.xml $p/gunma-$V-network.xml $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_75km_envelope.shp $(gunma)/processed/commuters_od_matrix/work_od_matrix_scaled.csv
	$(sc) prepare init-location-choice \
	 --input $< \
	 --output $@ \
	 --facilities $(word 2,$^) \
	 --network $(word 3,$^) \
	 --shp $(word 4,$^) \
	 --commuter $(word 5,$^) \
	 --sample $X \
	 --k 5

# 5) Eval Run
$p/gunma-experienced-$V-$Spct.plans.xml.gz: $p/gunma-$V-config.xml $p/gunma-locations-$V-$Spct.plans.xml.gz
	$(sc) run --$Spct --config $< --population $(word 2,$^) --mode eval

	cp output/eval.output_experienced_plans.xml.gz $@


###############################################################
### E) C A D Y T S
###############################################################

ERROR_METRIC ?= log_error

# 1) Create the counts file required for cadyts
xxx: $p/gunma-$V-counts-mlit.xml.gz

$p/gunma-$V-counts-mlit.xml.gz: $(gunma)/processed/roadcounts/matsim_linkId_to_roadcounts.csv
	$(sc) prepare counts-from-mlit --input $< --output $@


# 2) create $p/gunma-$V-$Spct.plans_selection_$(ERROR_METRIC).csv, which specifies for each agent, which is the best plan
$p/gunma-$V-$Spct.plans_selection_$(ERROR_METRIC).csv: $p/gunma-experienced-$V-$Spct.plans.xml.gz $p/gunma-$V-network.xml $p/gunma-$V-counts-mlit.xml.gz
	$(sc) prepare run-count-opt\
	 --input $<\
	 --network $(word 2,$^)\
     --counts $(word 3,$^)\
	 --output $@ \
	 --sample-size $X \
	 --metric $(ERROR_METRIC)


# 3) filter the plans to only include the best plan
$p/gunma-$V-$Spct.plans_$(ERROR_METRIC).xml.gz : $p/gunma-locations-$V-$Spct.plans.xml.gz $p/gunma-$V-$Spct.plans_selection_$(ERROR_METRIC).csv
	$(sc) prepare select-plans-idx\
 	 --input $< \
 	 --csv $(word 2,$^)\
 	 --output $@


# 4) run 20 iterations with route choice.
output/eval-$(ERROR_METRIC) : $p/gunma-$V-config.xml $p/gunma-$V-$Spct.plans_$(ERROR_METRIC).xml.gz
	$(sc) run \
	 --mode "routeChoice" \
	 --iterations 20 \
	 --output $@ \
	 --$Spct \
	 --population $(word 2,$^) \
	 --config $<




# I'm not sure if the following code is an alternative to the above code, or if it complements it.
#$p/gunma-$V-$Spct.plans_cadyts.xml.gz:
#	$(sc) prepare extract-plans-idx\
#	 --input output/cadyts/cadyts.output_plans.xml.gz\
#	 --output $p/gunma-$V-$Spct.plans_selection_cadyts.csv
#
#	$(sc) prepare select-plans-idx\
#	 --input $p/gunma-cadyts-input-$V-$Spct.plans.xml.gz\
#	 --csv $p/gunma-$V-$Spct.plans_selection_cadyts.csv\
#	 --output $@


### Prepare Initial plans


# A) When I created the commuters's daily plans, I didn't add any start_time to work or home, because it depends on how long
# they have to travel. Now that some iterations have run through, I've added start times based on the trip to that activity.
# B) Splits activity types into type_DURATION
qqq: $p/gunma-$V-$Spct.plans-initial.xml.gz
$p/gunma-$V-$Spct.plans-initial.xml.gz: output/eval-$(ERROR_METRIC)
	$(sc) prepare amend-start-time-commuters \
	--input $</routeChoice.output_selected_plans.xml.gz --output $@

	$(sc) prepare split-activity-types-duration \
	 --input $@ --output $@

#### DASHBOARD
output/dashboard-1.yaml:
	$(sc) prepare gunma-dashboard output/


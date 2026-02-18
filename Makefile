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


S := 25
X := 0.25
config: $p/gunma-$V-config.xml
network: $p/gunma-$V-network.xml
facilities: $p/gunma-$V-facilities.xml
plans: $p/gunma-locations-$V-$Spct.plans.xml.gz
plans_exp: $p/gunma-experienced-$V-$Spct.plans.xml.gz


### X) Prepare Zonal Shape file
# add quotes around the id...
dhjsld : $(gunma)/processed/01_shapefiles/jis_zones/jis_zones_50km_clip.shp
$(gunma)/processed/01_shapefiles/jis_zones/jis_zones.shp: $(gunma)/raw/01_shapefiles/jis_zones/N03-20_200101.shp
	ogr2ogr \
	  -t_srs EPSG:2450 \
	  -dialect SQLITE \
	  -sql "SELECT N03_007 AS id, ST_Union(geometry) AS geometry \
	        FROM \"N03-20_200101\" \
	        WHERE N03_007 IS NOT NULL AND N03_007 != '' \
	        GROUP BY N03_007" \
	  $@ \
	  $<

#$(gunma)/processed/01_shapefiles/jis_zones/jis_zones_quoted_ids.shp: $(gunma)/processed/jis_zones/jis_zones.shp
#	ogr2ogr \
#	  -dialect SQLITE \
#	  -sql "SELECT '\"' || id || '\"' AS id, geometry FROM jis_zones" \
#	  $@ \
#	  $<

$(gunma)/processed/01_shapefiles/jis_zones/jis_zones_50km_clip.shp: $(gunma)/processed/01_shapefiles/jis_zones/jis_zones.shp
	ogr2ogr \
	$@ \
	$< \
	-clipsrc $(gunma)/processed/01_shapefiles/study_area_boundary/gunma_50km_envelope.shp


### X2 Prep OSM Data
# 2) Filter for only gunma (in EPSG:4326)
# result looks good in QGIS

xxx23 : $(gunma)/processed/gunma.osm.pbf


### A) CONFIG
$p/gunma-$V-config.xml: $(gunma)/raw/matsim_inputs_lichen_luo/config_simulation.xml
	$(sc) prepare prepare-config\
    	 --input $<\
    	 --output $@


### B) NETWORK
# We reuse the network from Luo (20XX)
# TODO: network currently doesn't have road types retained. Consider redoing network generation, so we can make sure no activities put by highways
#$p/gunma-$V-network.xml: $(gunma)/raw/matsim_inputs_lichen_luo/NetworkRed191211fromJOSM_noHighway_cle_speed0.66Adjusted.xml
#	cp $< $@
pp: $(gunma)/processed/gunma.osm.pbf
$(gunma)/processed/gunma.osm.pbf: $(gunma)/raw/kanto-260119.osm.pbf
	$(osmosis) --rb file=$< \
 	 --bounding-polygon file=$(gunma)/raw/shp/gunma_4326_poly/gunma_4326.poly completeWays=yes \
	 --used-node \
	 --write-pbf $@


# wx outputs to .osm
# wb outputs to .osm.pbf
$p/network.osm: $(gunma)/processed/gunma.osm.pbf

	# Detailed network includes bikes as well
	$(osmosis) --rb file=$<\
	 --tf accept-ways bicycle=designated highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,living_street,unclassified,cycleway\
	 --used-node --wx $@
#	 --used-node --wb input/network-detailed.osm.pbf
#	 --bounding-polygon file="$p/area/area.poly"\

#	$(osmosis) --rb file=$<\
#	 --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction\
#	 --used-node --wb input/network-coarse.osm.pbf
#
#	$(osmosis) --rb file=input/network-coarse.osm.pbf --rb file=input/network-detailed.osm.pbf\
#  	 --merge\
#  	 --tag-transform file=input/remove-railway.xml\
#  	 --wx $@

#	rm input/network-detailed.osm.pbf
#	rm input/network-coarse.osm.pbf

# - changed zone to 54 because gunma is in zone UTM zone 54 (removed +ellps=GRS80
#	 --proj "+proj=utm +zone=54 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
#	 --proj "+proj=utm +zone=54 +datum=WGS84 +units=m +no_defs"\
#	 --proj "+proj=utm +zone=54 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs"\
#		 --net-offset auto \


ddddd : $p/gunma-$V-network.xml.gz


eeeee: $p/sumo.net.xml
		 --#proj "+proj=utm +zone=54 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs" \

#	 --osm.extra-attributes smoothness,surface,crossing,tunnel,traffic_sign,bus:lanes,bus:lanes:forward,bus:lanes:backward,cycleway,cycleway:right,cycleway:left,bicycle\

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

#
#	$(SUMO_HOME)/bin/netconvert \
#		 --osm-files $< \
#		 --output-file $@ \
#		 --proj "+proj=tmerc +lat_0=36 +lon_0=138.5 +k=1 +x_0=0 +y_0=0 +ellps=GRS80 +units=m +no_defs"\
#		 --junctions.join --no-internal-links
# --input-crs EPSG:32654



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


### C) FACILITIES
$p/gunma-$V-facilities.xml: $p/gunma-$V-network.xml
	$(sc) prepare facilitiesGunma --network $< \
	 --telfacs $(gunma)/processed/facility_locations_yellowpages.csv \
	 --shp $(gunma)/processed/jis_zones/jis_zones.shp \
	 --output $@

### D) POPULATION
# 1) Merge Shapefiles for census data
$(gunma)/raw/shp/mesh250m/mesh250m.shp: $(gunma)/raw/shp/mesh250m/QDDSWQ5338/MESH05338.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5438/MESH05438.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5439/MESH05439.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5538/MESH05538.shp $(gunma)/raw/shp/mesh250m/QDDSWQ5539/MESH05539.shp
	ogr2ogr $@ $(word 1,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 2,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 3,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 4,$^) -nln mesh250m
	ogr2ogr -update -append $@ $(word 5,$^) -nln mesh250m
	ogr2ogr -t_srs EPSG:2450 $(gunma)/raw/shp/mesh250m_census/mesh250m-2450.shp $@ -nln mesh250m



ddd: $p/gunma-static-$V-100pct.plans.xml.gz
# 2) Generate static population plans
#		--facilities $(word 3,$^) --facilities-attr resident\
# input/facilities.gpkg
$p/gunma-static-men-$V-100pct.plans.xml.gz: $(gunma)/raw/microcensus/tblT001102Q10.txt $(gunma)/raw/shp/mesh250m_census/mesh250m-2450.shp
	$(sc) prepare gunma-population\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:4612\
		--sex MALE\
		--output $@

$p/gunma-static-women-$V-100pct.plans.xml.gz: $(gunma)/raw/microcensus/tblT001102Q10.txt $(gunma)/raw/shp/mesh250m_census/mesh250m-2450.shp
	$(sc) prepare gunma-population\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:4612\
		--sex FEMALE\
		--output $@

$p/gunma-static-commuters-$V-100pct.plans.xml.gz: $(gunma)/processed/work_od_matrix.csv $(gunma)/processed/1_shapefiles/jis_zones/jis_zones.shp
	$(sc) prepare gunma-commuters\
		--input $<\
		--sample 1.0\
		--shp $(word 2,$^) --shp-crs EPSG:2450\
		--output $@§


$p/gunma-static-$V-100pct.plans.xml.gz:	$p/gunma-static-men-$V-100pct.plans.xml.gz  $p/gunma-static-women-$V-100pct.plans.xml.gz $p/gunma-static-commuters-$V-100pct.plans.xml.gz
	$(sc) prepare merge-populations $^\
		--output $@

	#rm $p/gunma-static-women-$V-100pct.plans.xml.gz
	#rm $p/gunma-static-men-$V-100pct.plans.xml.gz

	$(sc) prepare lookup-jis-code --input $@ --output $@ --shp $(gunma)/processed/1_shapefiles/jis_zones/jis_zones.shp




xxx : $p/gunma-static-$V-$Spct.plans.xml.gz
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
# NOTE: I've updated the range to 15 years on either side for people above 65, which is hella wide, but necessary until we get a larger travel survey population
# TODO: filter reference population less aggressively, so we have a larger pool (especially for old people) to choose from.
# todo: include pt population in generation. Just filter out this population or teleport them before
giig: $p/gunma-activities-$V-1pct.plans.xml.gz

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
$p/gunma-locations-$V-$Spct.plans.xml.gz: $p/gunma-activities-$V-$Spct.plans.xml.gz $p/gunma-$V-facilities.xml $p/gunma-$V-network.xml $(gunma)/processed/jis_zones/jis_zones.shp $(gunma)/processed/work_od_matrix.csv
	$(sc) prepare init-location-choice \
	 --input $< \
	 --output $@ \
	 --facilities $(word 2,$^) \
	 --network $(word 3,$^) \
	 --shp $(word 4,$^) \
	 --commuter $(word 5,$^) \
	 --sample $X \
	 --k 1

# 5) Eval Run
$p/gunma-experienced-$V-$Spct.plans.xml.gz: $p/gunma-$V-config.xml $p/gunma-locations-$V-$Spct.plans.xml.gz
	$(sc) run --$Spct --config $< --population $(word 2,$^) --mode eval

	cp output/eval.output_experienced_plans.xml.gz $@


output/dashboard-1.yaml:
	$(sc) prepare gunma-dashboard output/
#





# SKIPPED STEPS - these are steps done in the Berlin Scenario that we do not do here:
# Commercial Traffic
# prepare merge-plans -->  "This file requires eval runs" ??? What does that mean?

# Chose Plan to match the traffic counts...

#text defalt encoding:UTF-8
library(data.table)
library(stringr)
library(plyr)
library(dplyr)
library(rgdal)
library(sp)
library(sf)
library(stringi)
library(readr)

# author: LUO Lichen
# data processing for 2015 Gunma Person Trip Survey Data
# it is an updated and combined version of original PTdataProcessing.R and PTdataFurtherProcessing.R
# aiming at filtering and aggregating Gunma PT data with the purpose to especially fiting LUO`s thesis.`
# the results are expected to be load into DAS_Tour&TripForming.R
# version of 20210501

Sys.setlocale(category = "LC_ALL", locale = "Japanese_Japan.932")

PTGunma<-fread("../shared-svn/projects/matsim-gunma/data/raw/travel_survey/pt.csv")
setnames(PTGunma, c("レコード区分",	"票区分",	"回収分類",	"バッチ番号",	"整理番号小ゾーン",	"整理番号世帯SEQ",	"世帯人数",	"調査年次",	
                    "現住所", "現住所「番」「番地」",	"現住所「号」",	"所有車両軽乗用車",	"所有車両乗用車",	"所有車両軽貨物車",	"所有車両貨物自動車",
                    "所有車両自転車",	"所有車両原動機付自転車",	"所有車両自動二輪車",	"個人番号",	"世帯票性別",	"世帯票年齢",	"職業",	"就業形態",
                    "勤務先・通学先・通園先の所在地",	"勤務先・通学先・通園先の所在地「番」「番地」",	"勤務先・通学先・通園先の所在地「号」",
                    "外出の際の付き添い",	"保有運転免許①",	"保有運転免許②", "自由に使える自動車", "調査日月", "調査日日","トリップ有無",
                    "トリップ数","トリップ番号",	"出発地区分",	"出発地ゾーン",	"出発地「番」「番地」",	"出発地「号」",	"出発地施設の種類",
                    "出発時刻午前・午後・翌午前",	"出発時刻時",	"出発時刻分",	"到着地区分",	"到着地ゾーン",	"到着地「番」「番地」", 
                    "到着地「号」",	"到着地施設の種類",	"到着時刻午前・午後・翌午前・翌午後",	"到着時刻時",	"到着時刻分",	"着目的",
                    "交通手段①手段",	"交通手段①所要時間","交通手段①乗換地点","交通手段②手段","交通手段②所要時間","交通手段②乗換地点	",
                    "交通手段③手段",	"交通手段③所要時間","交通手段③乗換地点",	"交通手段④手段","交通手段④所要時間"	,"交通手段④乗換地点",
                    "交通手段⑤手段",	"交通手段⑤所要時間",	"駐輪場所",	"自動車利用運転有無",	"自動車利用自動車の所有者",	"自動車利用乗車人員",
                    "自動車利用駐車場所",	"自動車利用有料道路利用有無",	"自動車利用乗車ICコード",	"自動車利用降車ICコード",	"拡大係数個人",
                    "拡大係数トリップ",	"所有車両自動車台数",	"所有車両二輪車台数",	"所有車両自動車所有の有無",	"所有車両二輪車所有の有無",
                    "トリップ所要時間着時刻－発時刻",	"トリップ所要時間アンリンクト積み上げ",	"発目的",	"目的種類分類0",	"目的種類分類Ⅰ",
                    "目的種類分類Ⅱ",	"目的種類分類Ⅲ","代表交通手段分類0","代表交通手段分類Ⅰ", "代表交通手段分類Ⅱ",	"代表交通手段分類Ⅲ",
                    "マストラ乗車代表交通手段",	"マストラ乗車駅バス停コード",	"マストラ乗車直接端末手段（分類０）",	"マストラ乗車代表端末手段",
                    "マストラ乗車直接端末所要時間",	"マストラ乗車代表端末所要時間",	"マストラ乗車端末全体所要時間",	"マストラ降車代表交通手段",
                    "マストラ降車駅バス停コード",	"マストラ降車直接端末手段",	"マストラ降車代表端末手段",	"マストラ降車直接端末所要時間",
                    "マストラ降車代表端末所要時間",	"マストラ降車端末全体所要時間",	"現住所マッチングレベル",	"現住所緯度",	"現住所経度",
                    "通勤・通学先マッチングレベル",	"通勤・通学先緯度",	"通勤・通学先経度",	"出発地マッチングレベル",	"出発地緯度",
                    "出発地経度", "到着地マッチングレベル",	"到着地緯度",	"到着地経度"))

# mesh_kokuseiDT <- fread("../shared-svn/projects/matsim-gunma/data/raw/travel_survey/mesh_kokusei.csv", encoding = "UTF-8")
# mesh_katsudoDT <- fread("../shared-svn/projects/matsim-gunma/data/raw/travel_survey/mesh_katsudo.csv", encoding = "UTF-8")
# mesh_combineDT <- fread("../shared-svn/projects/matsim-gunma/data/raw/travel_survey/mesh_combine.csv", encoding = "UTF-8")

#EPSG:2450の範囲に一旦設定
#network bounding box (WGS84) (order: left right top bottom)
#bbox<-c(137.4700,139.9300,38.6600,34.0900)#epsg2450の範囲
#bbox<-c(139.013443,139.1227913,36.4292936,36.3465174)#前橋
#bbox<-c(138.6914063,139.3011475,36.7168707,36.2929908)#0112_gunma
# bbox<-c(138.3728027,139.7021484,37.0727105,35.9735406)#0115_kanto
#bbox <- c(138.1947754,139.9294922,37.2937784,35.23000)#merged
#bbox <- c(130,145,40,30)

#Transforming coordinates to EPSG (as required by MATSim)
#sinput="+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs" #epgs coordinates used by google maps  (DO NOT CHANGE)
#soutput="+proj=tmerc +lat_0=36 +lon_0=138.5 +k=0.9999 +x_0=0 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs" #epgs coordinates used by MATSIm for Japan
#check http://spatialreference.org/ to find the appropriate spatial reference
#END OF GENERAL SETTINGS


  #id_person
    PTforMatsim <- data.table(matrix(nrow = nrow(PTGunma), ncol = 0))    
    PTforMatsim[,":="(id_person = PTGunma$"整理番号小ゾーン"*100000000000 + PTGunma$"整理番号世帯SEQ"*1000000 + 
                        PTGunma$"個人番号"*10000 + PTGunma$"世帯票年齢"*100 + PTGunma$"職業",
                      id_person2 = PTGunma$"整理番号小ゾーン", 
                      id_person3 = PTGunma$"整理番号世帯SEQ", 
                      id_person4 = PTGunma$"個人番号",
                      id_household = PTGunma$"整理番号小ゾーン"*1000000 + PTGunma$"整理番号世帯SEQ"*100 +
                        PTGunma$"世帯人数"*10 + PTGunma$"調査年次")]
 
  #tripnum
    PTforMatsim[,"tripnum":=as.numeric(PTGunma$"トリップ番号")]
  
  #starttime
    setnames(PTGunma,"出発時刻午前・午後・翌午前","arriveAMPM")
    setnames(PTGunma,"出発時刻時","arriveHour")
    setnames(PTGunma,"出発時刻分","arriveMin")
    timeDT<-PTGunma[,.SD,.SDcols=c("arriveHour","arriveMin","arriveAMPM")]
    timeDT[arriveHour==99|arriveMin==99|arriveAMPM==9,":="(arriveHour = 9999, arriveMin = 9999)]
    timeDT[arriveAMPM==1,arriveHour:=arriveHour] #converting to military time
    timeDT[arriveAMPM==2,arriveHour:=arriveHour+12L] #converting to military time
    timeDT[arriveAMPM==3,arriveHour:=arriveHour+24L] 
    timeDT[arriveAMPM==4,arriveHour:=arriveHour+36L] 
    
    timeDT[,"starttime" := arriveHour * 60 + arriveMin]
    timeDT[starttime>9999,"starttime":=-9999]
    PTforMatsim[,"starttime":= as.numeric(timeDT$starttime)][is.na(starttime),"starttime":=-9999]
  
  #Coordinate
    PTforMatsim[,"h_x":=as.numeric(PTGunma$"現住所経度")][is.na(h_x),"h_x":=-9999]
    PTforMatsim[,"h_y":=as.numeric(PTGunma$"現住所緯度")][is.na(h_y),"h_y":=-9999]
    PTforMatsim[,"s_x":=as.numeric(PTGunma$"出発地経度")][is.na(s_x),"s_x":=-9999]
    PTforMatsim[,"s_y":=as.numeric(PTGunma$"出発地緯度")][is.na(s_y),"s_x":=-9999]
    PTforMatsim[,"d_x":=as.numeric(PTGunma$"到着地経度")][is.na(d_x),"d_x":=-9999]
    PTforMatsim[,"d_y":=as.numeric(PTGunma$"到着地緯度")][is.na(d_y),"d_y":=-9999]
    PTforMatsim[,"ws_x":=as.numeric(PTGunma$"通勤・通学先経度")]
    PTforMatsim[,"ws_y":=as.numeric(PTGunma$"通勤・通学先緯度")]
  
  #bike
    PTforMatsim[,"bike_hhOwnership":=PTGunma$"所有車両自転車"]
  
  #age
    PTforMatsim[,"age":=as.numeric(PTGunma$"世帯票年齢")]
  
  # attributes about household info
    PTforMatsim[,"numMemInHH":= as.numeric(PTGunma$"世帯人数")]
    PTforMatsim[,":="("hasMemAgeLess20" = 0, "hasMemAgeLess6" = 0, "hasMemAgeOver65" =0, "hasMemAgeOver75" = 0)]
    PTforMatsim[id_household %in% PTforMatsim[age<20,id_household], "hasMemAgeLess20":=1]
    PTforMatsim[id_household %in% PTforMatsim[age<6,id_household], "hasMemAgeLess6":=1] # NOT CONSISTENT WITH THE FOLLOWING DataUnderSchoolAge FILTERING 
    PTforMatsim[id_household %in% PTforMatsim[age>65,id_household], "hasMemAgeOver65":=1]
    PTforMatsim[id_household %in% PTforMatsim[age>75,id_household], "hasMemAgeOver75":=1]
  
  #gender
    PTforMatsim[,"male":=0L]
    PTforMatsim[PTGunma$"世帯票性別"==1,"male":=1]
  
  #license
    setnames(PTGunma,"保有運転免許①","license")
    PTforMatsim[,"license":=0L]
    PTforMatsim[PTGunma$license==2,"license":=1] #license = 2 means 免許あり in the questionnaire
    PTforMatsim[PTGunma$license==9,"license":=9]
  
  #caravailability
    PTforMatsim[,"car_availability":=0L]
    PTforMatsim[PTGunma$"自由に使える自動車"==1,"car_availability":=1]
    PTforMatsim[PTGunma$"自由に使える自動車"==2,"car_availability":=2]
    PTforMatsim[PTGunma$"自由に使える自動車"==9,"car_availability":=9]
  
  #car household ownership
    PTforMatsim[,"car_hhOwnership":=PTGunma$"所有車両自動車台数"]
  
  #job
    PTforMatsim[,"jobType":=PTGunma$"職業"]
    PTforMatsim[,"employType":=PTGunma$"就業形態"]
  
  #accompanyNecessity
    PTforMatsim[,"accompanyNecessity":=PTGunma$"外出の際の付き添い"]
  
  #trip mode
    PTforMatsim[,"tripmode":=as.numeric(PTGunma$"代表交通手段分類Ⅰ")][is.na(tripmode),"tripmode":=99] #tripmode
    PTforMatsim[,"drivingOrNot":=as.numeric(PTGunma$"自動車利用運転有無")][is.na(drivingOrNot),"drivingOrNot":=99]  #drivingOrNot
    PTforMatsim[,"whoOwnsTheCar":=as.numeric(PTGunma$"自動車利用自動車の所有者")]   #who Owns The Car
    PTforMatsim[,"carOccupancy":=as.numeric(PTGunma$"自動車利用乗車人員")]   #car Occupancy
    PTforMatsim[,"usingTollOrNot":=as.numeric(PTGunma$"自動車利用有料道路利用有無")]   #using Toll Or Not
    PTforMatsim[,"carParkingType":=as.numeric(PTGunma$"自動車利用駐車場所")]   # car parking type
    PTforMatsim[,"bicParkingType":=as.numeric(PTGunma$"駐輪場所")]   # bicycle parking type
  
  
  #trippurpose: purpose at destination
    PTforMatsim[,"tripurpose":=as.numeric(PTGunma$"着目的")][is.na(tripurpose),"tripurpose":=99]
    PTforMatsim[,"depFacilityType" := PTGunma$"出発地施設の種類"]
    PTforMatsim[,"arrFacilityType" := PTGunma$"到着地施設の種類"]
  
  #tripduration
    setnames(PTGunma,"トリップ所要時間着時刻－発時刻","tripduration")
    PTforMatsim[,"tripduration":=as.numeric(PTGunma$tripduration)][is.na(tripduration),"tripduration":=9999]
    PTforMatsim[,"arrivetime":=as.numeric(starttime)+as.numeric(tripduration)]
  
  #job aggregate
    PTforMatsim[jobType == 1, jobTypeAggr := "prim sector"]
    PTforMatsim[jobType %in% c(2,3,4), jobTypeAggr := "seco sector"]
    PTforMatsim[jobType %in% c(5,6,7,8,9,10,11,12,13,14,15,16,17,18), jobTypeAggr := "teri sector"]
    PTforMatsim[jobType == 19, jobTypeAggr := "other"]
    PTforMatsim[jobType %in% c(20,21,22), jobTypeAggr := "student"]
    PTforMatsim[jobType %in% c(23,24,25), jobTypeAggr := "unemployed"]
  
  # if you are the 世帯主, then we assume that 自由に使える自動車= 1
    PTforMatsim[id_person4 == 1 & car_availability==9 & car_hhOwnership > 0 & car_hhOwnership != 999, car_availability:=1]
  
  # aggregate tripurposes 
    PTforMatsim[(tripurpose==1|tripurpose>9) & tripurpose!=99, tripurpose:=1] # including作業as work
    PTforMatsim[tripurpose==1&arrivetime<=420, tripurpose:=15]
    PTforMatsim[tripurpose==1&arrivetime<=480, tripurpose:=16]
    PTforMatsim[tripurpose==1&arrivetime<=540, tripurpose:=17]
    PTforMatsim[tripurpose==1&arrivetime<=600, tripurpose:=18]
    PTforMatsim[tripurpose==1, tripurpose:=19]
    
    #PTforMatsim[tripurpose==8, tripurpose:=2]
    PTforMatsim[tripurpose==2&arrivetime<=420, tripurpose:=20]
    PTforMatsim[tripurpose==2&arrivetime<=480, tripurpose:=21]
    PTforMatsim[tripurpose==2&arrivetime<=540, tripurpose:=22]
    PTforMatsim[tripurpose==2&arrivetime<=600, tripurpose:=23]
    PTforMatsim[tripurpose==2, tripurpose:=24]
    
    PTforMatsim[tripurpose %in% c(15,16,17,18,19), "tripurposeAggr":="work"]
    PTforMatsim[tripurpose %in% c(20,21,22,23,24), "tripurposeAggr":="education"]
    PTforMatsim[tripurpose==3, "tripurposeAggr":="tohome"]
    PTforMatsim[tripurpose==4, "tripurposeAggr":="shopping"]
    PTforMatsim[tripurpose %in% c(5,6), "tripurposeAggr":="leisure"]
    PTforMatsim[tripurpose==7, "tripurposeAggr":="medical"]
    PTforMatsim[tripurpose==8, "tripurposeAggr":="other"]
    PTforMatsim[tripurpose==9, "tripurposeAggr":="pickup"]
  
  # aggregate tripmodes
    PTforMatsim[tripmode %in% c(1,2,3,4), "CHOICE_mode":="pt"]
    PTforMatsim[tripmode %in% c(14,15), "CHOICE_mode":="walk"]
    PTforMatsim[tripmode==13, "CHOICE_mode":="bicycle"]
    PTforMatsim[tripmode %in% c(6,8,9,10), "CHOICE_mode":="car"]
  # if you are not using car, then these attributes have no meaning
    PTforMatsim[CHOICE_mode != "car",":="(drivingOrNot = 99, whoOwnsTheCar = 99, carOccupancy = 99, usingTollOrNot = 99)]
  # if you are using car while has no license, then you must be a passenger
    PTforMatsim[CHOICE_mode == "car" & drivingOrNot==9 & license==0, drivingOrNot := 2]
    PTforMatsim[CHOICE_mode == "car" & drivingOrNot==1, "CHOICE_mode":="car_driver"]
    PTforMatsim[CHOICE_mode == "car" & drivingOrNot==2, "CHOICE_mode":="car_passen"]
 
  # number of trips, by person
    PTforMatsim[,numOfTrips := max(tripnum), by=id_person]
  
#fwrite(PTforMatsim, "PTforMatsim_test.csv")
#PTforMatsim <- fread("PTforMatsim_test.csv", encoding = "UTF-8")
 
# --------------------FILTERING-------------
# remove those too young to decide DAS by his or herself
#### CANCELED - JR
  #   DataUnderSchoolAge <- PTforMatsim[age <= 6]
  #   PTforMatsim <- PTforMatsim[age > 6]
  #   #PTforMatsim_original <- PTforMatsim 
  #   #PTforMatsim <- PTforMatsim_original
  # #remove missing values of user specific variables (for any missing value delete all observations of that person)
  # PTforMatsim[,uniqueN(id_person)] #134664
  # PTforMatsim[,uniqueN(id_household)] #62389
  # 
####
  
    
    ### JR we don't need to remove the entire household if there is one bad apple. changed id_household -> id_person
  # Missing Values
    # remove those attributes are not complete
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[license==9 | car_availability==9, id_household]] #household: 62389->60564 #### CANCELED - JR
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[car_hhOwnership==999, id_household]] #51397 #### CANCELED - JR
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[h_x == -9999 | h_y == -9999, id_person]] #51394
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[jobType == 99, id_household]] #49221 #### CANCELED - JR
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[employType == 9, id_household]] #47904 #### CANCELED - JR
      
    # for has trip only  
      #remove missing values for trip specific variables (only with those had trips)
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & starttime == -9999, id_person]] #household: 35037 
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & tripmode == 99, id_person]] # 34019
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & (tripduration==99 | tripurpose==99) , id_person]] # 32989
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & (s_x == -9999 | s_y == -9999 | d_x == -9999 | d_y == -9999), id_person]] # 23430
      
      # remove person with trip that driving state is not clear
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[tripnum != 0 & drivingOrNot==9, id_household]] # 23118
      
  # Inexplicable    
      
      # O=D
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & s_x == d_x & s_y == d_y, id_person]] # 22834
      
      #remove start or destination not in mesh data (without size measure)
      # PTforMatsim[tripnum != 0, sMeshCode := coordiToMeshCode(s_x, s_y)] # start mesh code
      # PTforMatsim[tripnum != 0, dMeshCode := coordiToMeshCode(d_x, d_y)] # destination mesh code
      # PTforMatsim[tripnum != 0, s_x2450 := CRStransformer(PTforMatsim[tripnum != 0], CRS("+init=epsg:4326"), CRS("+init=epsg:2450"), "s_x", "s_y")$s_x]
      # PTforMatsim[tripnum != 0, s_y2450 := CRStransformer(PTforMatsim[tripnum != 0], CRS("+init=epsg:4326"), CRS("+init=epsg:2450"), "s_x", "s_y")$s_y]
      # PTforMatsim[tripnum != 0, d_x2450 := CRStransformer(PTforMatsim[tripnum != 0], CRS("+init=epsg:4326"), CRS("+init=epsg:2450"), "d_x", "d_y")$d_x]
      # PTforMatsim[tripnum != 0, d_y2450 := CRStransformer(PTforMatsim[tripnum != 0], CRS("+init=epsg:4326"), CRS("+init=epsg:2450"), "d_x", "d_y")$d_y]
      #PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[tripnum != 0 & !sMeshCode %in% meshLanduseDT$KEY_CODE, id_household]]   
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[tripnum != 0 & tripurposeAggr !="tohome" & !dMeshCode %in% mesh_katsudoDT$KEY_CODE, id_household]]  #22732 
      
    # remove those home mesh is not in meshdata
      # PTforMatsim[,hMeshCode := coordiToMeshCode(h_x, h_y)] # home mesh code
      # PTforMatsim[, h_x2450 := CRStransformer(PTforMatsim, CRS("+init=epsg:4326"), CRS("+init=epsg:2450"), "h_x", "h_y")$h_x] #define coordinates in epsg2450
      # PTforMatsim[, h_y2450 := CRStransformer(PTforMatsim, CRS("+init=epsg:4326"), CRS("+init=epsg:2450"), "h_x", "h_y")$h_y]
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[!hMeshCode %in% mesh_kokuseiDT$KEY_CODE, id_household]]  #22697

    # those education trip went to a mesh without education employee or office #APPLIED for 210501 dataset but in DataComb&GroupSepa.R, should been done here
      # PTforMatsim<-PTforMatsim[!id_household %in% 
      #                            PTforMatsim[tripurposeAggr == "education" &
      #                                          !dMeshCode %in% mesh_katsudoDT[Office_edu != 0,KEY_CODE], unique(id_household)]]  #22029
      
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[tripurposeAggr %in% c("other", "shopping", "leisure", "medical", "pickup") &
      #                                                           !dMeshCode %in% mesh_katsudoDT[Office_teri != 0,KEY_CODE], unique(id_household)]] #22020
      
      
    # remove trips/persons that have only home activities all day
      # PTforMatsim[,actiChain := paste(substr(tripurposeAggr, start = 1, stop = 1), collapse = ''), by = id_person]
      # PTforMatsim <- PTforMatsim[!id_household %in% PTforMatsim[!is.na(stri_extract_first_regex(PTforMatsim$actiChain, '^t+$')) , id_household]] #21856
      
    # remove persons who has "tohome" trips but are not heading home
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & (d_x != h_x | d_y != h_y) & tripurposeAggr == "tohome",
                                                           id_person]]  #21828
  
    # remove first or the last trips that not start/end at home
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum == 1 & (s_x != h_x | s_y != h_y), id_person]] #21382
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & tripnum == numOfTrips & (d_x != h_x | d_y != h_y), id_person]]  #21148
    
   
  #fwrite(PTforMatsim, "PTforMatsim_test2.csv") # data of 210429(?)
  #PTforMatsimtest2 <- fread("PTforMatsim_test2.csv", encoding = "UTF-8") 
    
 

  # assumption  
    # remove those did work activity while being unemployed and those did study while not being student
      # PTforMatsim<-PTforMatsim[!id_household %in% PTforMatsim[is.na(employType) & tripurposeAggr == "work" |
      #                                                           jobTypeAggr != "student" & tripurposeAggr == "education",
      #                                                         id_household]] # 20708
    # not applicable modes
      PTforMatsim<-PTforMatsim[!id_person %in% PTforMatsim[tripnum != 0 & tripmode %in% c(5,7,11,12,16), id_person]] # 19512
    # remove those accompanyNecessity is 1(need accompany when make trips)
      # PTforMatsim <- PTforMatsim[!id_household %in% PTforMatsim[accompanyNecessity == 1 , id_household]] # 18217
    
    
    
    
  # attribute: mode availability
    # if the traveler has ever perform a car_driver trip, then his or her availability must be 1
      # PTforMatsim[id_person %in% PTforMatsim[CHOICE_mode=="car_driver", id_person], car_availability := 1]
      # PTforMatsim[id_household %in% PTforMatsim[CHOICE_mode=="bicycle", id_household], bike_hhOwnership := 1]
  
    # PTforMatsim[bike_hhOwnership==99, bike_hhOwnership:=0]
    # PTforMatsim[,BicycleAvail:=as.numeric(bike_hhOwnership>0)]
    
    # PTforMatsim[,CarAvail:=0L]
    # PTforMatsim[car_availability %in% c(1,2), CarAvail:=1L]
    
    # PTforMatsim[,PassenAvail := 1]
    # PTforMatsim[,WalkAvail := 1]
      
      
      
      ####
      
      PTforMatsim %>% view()
      
      library(tidyverse)
    
      # 429 people 95+ 
      # before there were much less.
      PTforMatsim  %>% group_by(id_person) %>% dplyr::summarise(age = first(age)) %>% filter(age >= 95)
      
      ### 
      
      
      fwrite(PTforMatsim,"../shared-svn/projects/matsim-gunma/data/processed/travel_survey/travel-survey-2026-02-10.csv")
      
      
      ###

#   
# 
#   # re-bind with Data Under School Age (because we dont need the trip data for them so we dont clean them)
#     PTforMatsim_withKid <- rbind(PTforMatsim, DataUnderSchoolAge[id_household %in% PTforMatsim[,unique(id_household)]], fill = TRUE)
#     PTforMatsim_withKid <- PTforMatsim_withKid[order(id_household, id_person4)]
#   
#   # re-assign id_person & id_household
#     PTforMatsim_withKid[,id_person:=rleid(id_person)]
#     PTforMatsim_withKid[,id_household:=rleid(id_household)]
#     
#   # re-assign id_person 
#     #PTforMatsim[,id_person:=rleid(id_person)]
#   
# setcolorder(PTforMatsim_withKid, c("id_person",	"id_person2",	"id_person3",	"id_person4",	"id_household",	"numMemInHH", "hasMemAgeLess6", "hasMemAgeLess20",
#                            "hasMemAgeOver65","hasMemAgeOver75", "age",	"male", "jobType","employType",
#                            "accompanyNecessity", "bike_hhOwnership", "car_hhOwnership", "license", "car_availability",
#                            "h_x",	"h_y",	"s_x",	"s_y",	"d_x",	"d_y", "ws_x", "ws_y", "h_x2450",	"h_y2450",	"s_x2450",	"s_y2450",	"d_x2450",	"d_y2450",
#                            "tripnum",	"starttime", "arrivetime",	"tripduration","tripurpose", "tripmode",	
#                            "drivingOrNot", "usingTollOrNot", "whoOwnsTheCar", "carOccupancy", "carParkingType", "bicParkingType"))
# 
# # separate person with trips and no trip  
#   DataUnderSchoolAge <- PTforMatsim_withKid[age <= 6]
#   PTforMatsim <- PTforMatsim_withKid[age > 6]
#   PTforMatsimNoTrip <- PTforMatsim[tripnum==0]
#   PTforMatsimHasTrip <- PTforMatsim[tripnum!=0]
#   PTforMatsimHasTrip[,uniqueN(id_person)]
#   PTforMatsimHasTrip[,uniqueN(id_household)]
#   PTforMatsimNoTrip[,uniqueN(id_person)]
#   PTforMatsimNoTrip[,uniqueN(id_household)]
#   
# fwrite(PTforMatsimNoTrip, "DAS_data/ptData210501NoTrip.csv", quote=F, col.names=T, append=F, row.names=F, sep=",")   # notice that for the 210501 dataset, nrow or #id_household etc is not what you get if you run the codes above, 
# fwrite(PTforMatsimHasTrip, "DAS_data/ptData210501HasTrip.csv", quote=F, col.names=T, append=F, row.names=F, sep=",") # because at least one filter was not applied for 210501, but is the same as the filters are applied later
# fwrite(DataUnderSchoolAge, "DAS_data/ptDataUnderSchoolAge210501.csv", quote=F, col.names=T, append=F, row.names=F, sep=",")
# 
# 
# notrip <- fread("DAS_data/ptData210501NoTrip.csv")
# trip <- fread("DAS_data/ptData210501HasTrip.csv")
# underage <- fread("DAS_data/ptDataUnderSchoolAge210501.csv")
# combined <- rbind(trip, notrip)
# trip[,uniqueN(id_person)]
# trip[,uniqueN(id_household)]
# notrip[,uniqueN(id_person)]
# notrip[,uniqueN(id_household)]
# combined[,uniqueN(id_person)]
# combined[,uniqueN(id_household)] #18482


# Spine Metrics Plugin 

## Description
A plugin for [Fiji](https://github.com/fiji/fiji) to calcute dendritic spine metrics with semi-automatic algorithm. 

1. Determines spine type (based on the threshold value of neck differential
2. Calculates the following metrics of a spine:
  * Neck width (doesn't apply for stubbed spine)
  * Neck length (doesn't apply for stubbed spine)
  * spine head width
  * spine length
  * perimetr
  * area

Results are represented in a ResultTable which can be saved and converted into different format.

## Requirements:
* Single binary image [Example](https://github.com/VanyaKrylov/Spine_Metrics-Plugin/blob/master/pic.tif)

Types of spines supported:
* Normal spines
* Floating (detached) spines 

To increase accuracy and perfomance for spines of small size use Fiji upscale function

## Usage
1. Choose spine type
2(Normal spine). Choose base points in any order for Normal spines 
2(Floating spine). Choose attach point on dendrit AND THEN point on the detached spine

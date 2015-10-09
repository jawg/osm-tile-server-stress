## OpenStreetMap Tile-Server Stress

This gatling script allows you to qualify and stress an **OpenStreetMap TileServer**.

The Testing scenario is the following:
 * Each user will randomly select a region in the regions.csv file (round-robin strategy)
 * In this region, the user will randomly choose a position (lat/lng)
 * Then, the user will successively zoom in (level by level) to this position and request 8x6 surrounding tiles.

### Requirements
 * SBT : http://www.scala-sbt.org/download.html
 * Scala : http://www.scala-lang.org/download/

Scala plugin for IntelliJ platform also helps.

### How to use

 * Clone the project  
 ```git clone https://github.com/mapsquare/openstreetmap-tile-server-stress.git```sh  
 * Set your environment properties in {projectRoot}/src/test/resources  
 Properties are server.url, simulation.usercount...  
 * Browse the project root and execute the following command  
 ```sbt "testOnly io.mapsquare.OsmSimulation"```sh


## License

Copyright 2015 eBusiness Information

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

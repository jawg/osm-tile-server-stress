/*
 Copyright 2015 eBusiness Information
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package io.mapsquare

import java.util.Random

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.core.structure.{ChainBuilder, PopulatedScenarioBuilder}
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.math._

object Parameters {

  val propertiesFromSystem = ConfigFactory.systemProperties()
  val propertiesFromFile = ConfigFactory.load("parameters.properties")
  val properties = propertiesFromSystem.withFallback(propertiesFromFile)

  // Url of the server to stress test
  val SERVER_URLS = properties.getString("server.urls").trim().split(",").toList

  // File to load containing the region rectangles where users will choose their initial latitudes and longitudes.
  // sample.csv contains an example of the format used.
  val CSV_FILE = properties.getString("simulation.regions")

  // Amount of users. Users will be dispatched as equally as possible across regions.
  val USERS = properties.getString("simulation.users.count").toInt

  // Users zoom will start between these values
  val MIN_START_ZOOM = properties.getString("simulation.map.zoom.start.min").toInt
  val MAX_START_ZOOM = properties.getString("simulation.map.zoom.start.max").toInt

  // Zoom will end between these values
  val MIN_END_ZOOM = properties.getString("simulation.map.zoom.end.min").toInt
  val MAX_END_ZOOM = properties.getString("simulation.map.zoom.end.max").toInt

  // Size of the fetched tile matrix, should match the amount of tiles loaded at once on a browser screen.
  val WIDTH = properties.getString("simulation.map.width").toInt
  val HEIGHT = properties.getString("simulation.map.height").toInt

  // Users amount can be ramped up over this duration in seconds
  val RAMP_TIME = properties.getString("simulation.users.ramp.time").toInt.seconds

  // Amount of time a user will wait between zooms will be between these values.
  val MIN_THINK_TIME = properties.getString("simulation.users.idle.time.min").toInt.seconds
  val MAX_THINK_TIME = properties.getString("simulation.users.idle.time.max").toInt.seconds

  // Note :
  // The time units can be specified, for instance 1.minute, 1000.millis, etc
}

object Transformations {

  import Parameters._

  implicit class PositiveMod(a: Int) {

    // a mod b
    def %%(b: Int) = (a % b + b) % b
  }

  private def lat2y(lat: Double, z: Int): Int =
    ((1 - log(tan(toRadians(lat)) + 1 / cos(toRadians(lat))) / Pi) / 2.0 * (1 << z)).toInt


  private def lng2x(lng: Double, z: Int): Int =
    ((lng + 180.0) / 360.0 * (1 << z)).toInt

  private def setOfImagesByCoords(coords: Seq[Coord]): Seq[Seq[String]] =
    coords.map { coord =>
      val z = coord.z
      val xs = coord.x - ((WIDTH / 2) - 1 + (WIDTH % 2)) to coord.x + (WIDTH / 2)
      val ys = coord.y - ((HEIGHT / 2) - 1 + (HEIGHT % 2)) to coord.y + (HEIGHT / 2)

      val xsMod = for (x <- xs) yield x %% (1 << z)
      val ysMod = for (y <- ys) yield y %% (1 << z)

      for {
        x <- xsMod
        y <- ysMod
      } yield s"/$z/$x/$y.png"
    }

  def randomLatitudes: Expression[Session] = { session =>
    val seed = session("seed").as[String].toLong
    val rand = new Random(seed)

    val latMin = session("LatMin").as[String].toDouble
    val latMax = session("LatMax").as[String].toDouble
    val lngMin = session("LngMin").as[String].toDouble
    val lngMax = session("LngMax").as[String].toDouble

    val lng = rand.nextDouble() * (lngMax - lngMin) + lngMin
    val lat = rand.nextDouble() * (latMax - latMin) + latMin

    val randStartZoom =
      if ((MAX_START_ZOOM - MIN_START_ZOOM) > 0)
        rand.nextInt(MAX_START_ZOOM - MIN_START_ZOOM) + MIN_START_ZOOM
      else MIN_START_ZOOM

    val trueMinEndZoom = math.max(MIN_END_ZOOM,MAX_START_ZOOM)
    val randEndZoom =
      if ((MAX_END_ZOOM - trueMinEndZoom) > 0)
        rand.nextInt(MAX_END_ZOOM - trueMinEndZoom) + trueMinEndZoom
      else trueMinEndZoom

    val coords = for {
      z <- randStartZoom until randEndZoom
    } yield Coord(lng2x(lng, z), lat2y(lat, z), z)

    val maxThinkTime = MAX_THINK_TIME.toSeconds.toInt
    val minThinkTime = MIN_THINK_TIME.toSeconds.toInt

    val thinkTime =
      if ((maxThinkTime - minThinkTime) > 0)
        rand.nextInt(maxThinkTime - minThinkTime) + minThinkTime
      else minThinkTime

    session.set("thinkTime", thinkTime).set("imagesByCoords", setOfImagesByCoords(coords))
  }
}

case class Coord(x: Int, y: Int, z: Int)

object OsmRequestBuilder {

  import Parameters._

  val TilesToFetch = WIDTH * HEIGHT

  val queries = {
    val rawQueries = for {
      index <- 0 until TilesToFetch
    } yield {
        val queryName = s"$${Region}"
        val queryString = s"$${imagesByCoords(currentCoord)($index)}"
        http(queryName).get(queryString)
      }

    rawQueries.head.resources(rawQueries.tail: _*)
  }

  def applyPaths: ChainBuilder =
    repeat("${imagesByCoords.size()}", "currentCoord") {
      exec {
        queries.check(
          status.is(200),
          header("Content-Type").is("image/png"))
      }.pause("${thinkTime}")
    }
}

class OsmSimulation extends Simulation {

  import Parameters._

  val httpProtocol = http
    .shareConnections
  val random = new util.Random

  def scenarios(urls: List[String]): List[PopulatedScenarioBuilder] =
    urls.map {url =>
      scenario("OsmSimulation " + url + " " + random.nextInt.toString)
        .feed(csv(CSV_FILE).circular)
        .feed(csv("seeds.csv").circular)
        .exec(Transformations.randomLatitudes)
        .exec(OsmRequestBuilder.applyPaths)
        .inject(rampUsers(math.ceil(USERS.toDouble / urls.size).toInt) over RAMP_TIME)
        .protocols(httpProtocol.baseURL(url))
    }

  setUp(scenarios(SERVER_URLS))
}

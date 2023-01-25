package avni

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class avniSyncDetailsMultiUserTest extends Simulation {

  val httpsProtocol = http
    .baseUrl("http://localhost:8021")
    .acceptHeader("application/json")
    .acceptLanguageHeader("en;q=1.0,de-AT;q=0.9")
    .acceptEncodingHeader("gzip;q=1.0,compress;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36")

  val arr = Array("taqim@lahi", "taqi@goonj", "taqi@guat", "AchalaB@rwbnitiuat", "shivangi@guat", "gajbeer@guat",
    "praveen@guat", "anshuraj@guat", "arjun@guat", "anjana@guat")
  val rand = new scala.util.Random
  val scn = scenario("Post sync details Request")
    .feed(Iterator.continually(
      Map("randomUserName" -> arr(rand.nextInt(arr.length)))
      ))
      .exec(http("Post HTTP")
        .post("/syncDetails")
        .header("accept","application/json")
        .header("USER-NAME", "#{randomUserName}")
        .header("Content-Type", "application/json").body(StringBody("[]")))

      // setUp(scn.inject(atOnceUsers(10)).protocols(httpsProtocol)) //Ten requests at once
      setUp(scn.inject(rampUsers(100).during(30)).protocols(httpsProtocol))

}

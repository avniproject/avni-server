package avni

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class avniSyncTelemetryLoadTest extends Simulation {
 
  val httpsProtocol = http
  // .baseUrl("https://staging.avniproject.org") //Use relevant env URL
  .baseUrl("http://localhost:8021") 
  .acceptHeader("application/json")
  .acceptLanguageHeader("en;q=1.0,de-AT;q=0.9")
  .acceptEncodingHeader("gzip;q=1.0,compress;q=0.5")
.userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36") 


val scn = scenario("Post sync telemetry Request")
  .exec(http("Post HTTP")
  .post("/syncTelemetry")
  .header("accept","application/json")
//    Include relevant env auth-token
  .header("auth-token", "<AUTH-TOKEN>")
  .header("Content-Type", "application/json").body(RawFileBody("syncTelemetryRequest.json")).asJson)

    setUp(scn.inject(rampUsers(60).during(60)).protocols(httpsProtocol)) //60 requests ramped up over 60 seconds. Do not exceed more than 120 requests per minute

    //    setUp(scn.inject(atOnceUsers(1)).protocols(httpsProtocol)) //Only one request, keep one or the other setup commented.

}

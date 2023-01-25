package avni

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class avniGetCognitoLoadTest extends Simulation {
 
  val httpProtocol = http
  .baseUrl("https://staging.avniproject.org") 
  .acceptHeader("application/json")
  .acceptLanguageHeader("en;q=1.0,de-AT;q=0.9")
  .acceptEncodingHeader("gzip;q=1.0,compress;q=0.5")
.userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36") 


val scn = scenario("Simple Get cognito-details Request")
  .exec(http("Get HTTP")
  .get("/cognito-details"))
  setUp(scn.inject(rampUsers(50).during(30)).protocols(httpProtocol))

}

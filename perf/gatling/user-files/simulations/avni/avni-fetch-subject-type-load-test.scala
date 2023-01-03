package avni

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class avniFetchSubjectTypeLoadTest extends Simulation {
 
  val httpsProtocol = http
  .baseUrl("http://localhost:8021") 
  .acceptHeader("application/json")
  .acceptLanguageHeader("en;q=1.0,de-AT;q=0.9")
  .acceptEncodingHeader("gzip;q=1.0,compress;q=0.5")
.userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36") 


val scn = scenario("Get subjectTypeUuid Request")
  .exec(http("Get HTTP")
  .get("/individual?subjectTypeUuid=5a7f1ace-6edb-4625-b259-93c2b6ab4275&lastModifiedDateTime=1900-01-01T00:00:00.000Z&now=2023-01-03T05:43:51.546Z&size=100&page=0")
  .header("accept","application/json")
  .header("USER-NAME", "taqim@lahi"))

setUp(scn.inject(atOnceUsers(1)).protocols(httpsProtocol)) //Only one request, keep one or the other setup commented.

}

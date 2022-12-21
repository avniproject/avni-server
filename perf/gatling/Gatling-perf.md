# Refer https://medium.com/@markostapfner/load-testing-using-gatling-io-3-0-for-beginners-75a9b3f93f62 for information on initial setup of Gatling.



# Excerpts from the Article for backup reference are as follows:

The first step in load-testing is to download Gatling 3.0 from the official Gatling Website: https://gatling.io/download/ . This tutorial is created using version 3.0.1.1.

Now extract the .zip archive in the desired folder.


Gatling.IO extracted
Inside the /bin folder, there are executables for Linux, macOS and Windows. To run gatling on macOS, switch to the folder just type into the terminal: ./gatling.sh Then you have to wait some seconds, while gatling is searching for simulations in the user-files directory.

Now let’s create our first user simulation: medium-test.scala

Inside the gatling folder, we switch in the folder: cd /user-files/simulations/computerdatabase In this folder, there are currently 5 test simulations shipped by default. We create a new file called medium-test.scala using a normal text editor or just using vim or nano .

We paste the following code in this file:

package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class medium-test extends Simulation {

}
In the first line, we specify the package of this simulation. Next, we import the dependencies to use Scala. Then, we create a class (this is also the name of our simulation) that extends the Simulation class from Gatling.

Inside the class, we specify the Protocol that we want to test:

val httpProtocol = http
.baseUrl("http://medium.com")
.acceptHeader("application/json")
.acceptLanguageHeader("en;q=1.0,de-AT;q=0.9")
.acceptEncodingHeader("gzip;q=1.0,compress;q=0.5")
.userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36")
The most important concept in gatling is that we write scenarios and they get executed by a specified amount of independent user.

Next, we have to create such a scenario for our users.

val scn = scenario("Simple Get Request")
.exec(http("Get HTTP")
.get("/"))
Let’s have a deeper look:

scenario("Simple Get Request")
This creates a new Scenario named “Simple Get Request”.

.exec(http("Get HTTP")
Exec lets the users execute a request, in this case, it is an HTTP request. In the end, the .get("/")) indicates that we access the index file in the given baseUrl path.

Now the important part:

setUp(scn.inject(atOnceUsers(100)).protocols(httpProtocol))
Here, we set up users for our scenarios. We inject a special specified amount of users, here at once 100 concurrent users. After that, we link our created protocol to the users.

So here again the complete code snippet:

package computerdatabase

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random

class mediumtest extends Simulation {
val httpProtocol = http
.baseUrl("http://medium.com")
.acceptHeader("application/json")
.acceptLanguageHeader("en;q=1.0,de-AT;q=0.9")
.acceptEncodingHeader("gzip;q=1.0,compress;q=0.5")
.userAgentHeader("Safari")

val scn = scenario("Simple Get Request")
.exec(http("Get HTTP")
.get("/"))

setUp(scn.inject(atOnceUsers(100)).protocols(httpProtocol))
}
Let’s step into the terminal and execute ./gatling.sh inside the bin folder.


Here we can see our simulations, in my case, this is the first one [0]. Just type the number, hit enter and give it an optional description. Then, the simulation will be running.

After that, you will receive an overview of the simulation inside directly inside your terminal:


Of course, you will also receive a well-formatted HTML-Page with the results and statistics of the simulation in the generated report. Just click on the index.html file in the given report-folder in the terminal. You will see this:


Gatling.IO Simulation Results
So now, you successfully started a first simulation of users. Gatling provides many more ways to run simulations and to simulate typical users.
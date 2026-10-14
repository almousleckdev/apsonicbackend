package apsonic;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class ApsonicSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:3000")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    ScenarioBuilder publicScn = scenario("Public Traffic")
        .exec(http("Get Home Route").get("/"))
        .pause(1)
        .exec(http("Get Cms Config").get("/api/v1/public/cms/homepage"))
        .pause(1)
        .exec(http("Get Products").get("/api/v1/public/products"))
        .pause(1)
        .exec(http("Get News").get("/api/v1/public/news"));

    ScenarioBuilder adminScn = scenario("Admin Traffic")
        .exec(http("Login")
            .post("/api/v1/auth/login")
            .body(StringBody("{\"username\":\"admin\",\"password\":\"password\"}")) 
            .check(status().is(200))
            .check(jsonPath("$.data.accessToken").saveAs("token"))
        )
        .pause(1)
        .exec(http("Admin Get Products")
            .get("/api/v1/admin/products")
            .header("Authorization", "Bearer #{token}")
        )
        .pause(1)
        .exec(http("Admin Get Users")
            .get("/api/v1/admin/users")
            .header("Authorization", "Bearer #{token}")
        );

    {
        setUp(
            publicScn.injectOpen(rampUsersPerSec(5).to(50).during(15), constantUsersPerSec(50).during(30)),
            adminScn.injectOpen(rampUsersPerSec(2).to(10).during(15), constantUsersPerSec(10).during(30))
        ).protocols(httpProtocol);
    }
}

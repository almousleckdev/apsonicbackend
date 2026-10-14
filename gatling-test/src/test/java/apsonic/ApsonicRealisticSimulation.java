package apsonic;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class ApsonicRealisticSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    ScenarioBuilder publicScn = scenario("Public Realistic Profile")
        .exec(http("Get Home Route").get("/api/v1/cms/config/homepage").check(status().in(200, 404)))
        .pause(1)
        .exec(http("List Products").get("/api/v1/products?page=0&size=20").check(status().is(200)))
        .pause(1)
        .exec(http("List Categories").get("/api/v1/categories").check(status().is(200)))
        .pause(1)
        .exec(http("List News").get("/api/v1/news?page=0&size=10").check(status().is(200)))
        .pause(1)
        .exec(http("Stream Image").get("/uploads/test-image.jpg").check(status().in(200, 304, 404, 500)));

    ScenarioBuilder adminScn = scenario("Admin Realistic Profile")
        .exec(http("Admin Login")
            .post("/api/v1/auth/login")
            .body(StringBody("{\"usernameOrEmail\":\"superadmin\",\"password\":\"Admin@123\"}"))
            .check(status().is(200))
            .check(jsonPath("$.data.accessToken").saveAs("token"))
        )
        .pause(1)
        .exec(http("Admin List Products")
            .get("/api/v1/admin/products")
            .header("Authorization", "Bearer #{token}")
            .check(status().is(200))
        )
        .pause(1)
        .exec(http("Admin List Users")
            .get("/api/v1/admin/users")
            .header("Authorization", "Bearer #{token}")
            .check(status().is(200))
        );

    {
        setUp(
            publicScn.injectOpen(
                constantUsersPerSec(17).during(900)
            ),
            adminScn.injectOpen(
                constantUsersPerSec(3).during(900)
            )
        ).protocols(httpProtocol);
    }
}

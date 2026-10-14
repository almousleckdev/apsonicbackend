package apsonic;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class ApsonicStressSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    ScenarioBuilder publicScn = scenario("Public Traffic Profile")
        .exec(http("Get Home Route").get("/api/v1/cms/config/homepage").check(status().in(200, 404)))
        .pause(1)
        .exec(http("List Products").get("/api/v1/products?page=0&size=20").check(status().is(200)))
        .pause(1)
        .exec(http("List Categories").get("/api/v1/categories").check(status().is(200)))
        .pause(1)
        .exec(http("List News").get("/api/v1/news?page=0&size=10").check(status().is(200)))
        .pause(1)
        .exec(http("Stream Image").get("/uploads/test-image.jpg").check(status().in(200, 304, 404, 500)));

    ScenarioBuilder adminScn = scenario("Admin CRUD Profile")
        .exec(http("Admin Login")
            .post("/api/v1/auth/login")
            .body(StringBody("{\"usernameOrEmail\":\"superadmin\",\"password\":\"Admin@123\"}"))
            .check(status().in(200, 401)) // Allow 401 because DB is empty
            .check(jsonPath("$.data.accessToken").optional().saveAs("token"))
        )
        .pause(1)
        .doIf(session -> session.contains("token")).then(
            exec(http("Admin List Products")
                .get("/api/v1/admin/products")
                .header("Authorization", "Bearer #{token}")
            )
            .pause(1)
            .exec(http("Admin List Users")
                .get("/api/v1/admin/users")
                .header("Authorization", "Bearer #{token}")
            )
        );

    {
        setUp(
            publicScn.injectOpen(
                rampUsersPerSec(10).to(50).during(20),
                rampUsersPerSec(50).to(100).during(20),
                rampUsersPerSec(100).to(200).during(20)
            ),
            adminScn.injectOpen(
                rampUsersPerSec(2).to(10).during(60)
            )
        ).protocols(httpProtocol);
    }
}

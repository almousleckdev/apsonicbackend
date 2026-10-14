# Apsonic Backend

Production-ready Spring Boot enterprise backend foundation designed as a **Modular Monolith** for managing products, categories, pages, blogs, media assets, partners, teams, and core website content.

---

## Architecture Overview

This project implements a **Modular Monolith Architecture** following modern Spring Boot best practices.

### Key Architectural Highlights
- **High Cohesion & Low Coupling**: Domain logic is strictly separated into independent feature modules (`modules/auth`, `modules/product`, `modules/category`, etc.).
- **Stateless Authentication**: Security foundation powered by **Spring Security** with **JWT (JSON Web Tokens)** and **RBAC (Role-Based Access Control)**.
- **Auditing & Traceability**: Automated JPA auditing (`created_at`, `updated_at`, `created_by`, `updated_by`, `deleted`, `version`) and request-scoped MDC tracing (`X-Trace-Id`).
- **Database Migrations**: Version-controlled schema migrations using **Flyway** with automatic initial schema and seed data setup.
- **Standardized API Contract**: Envelope response models (`ApiResponse<T>`, `PageResponse<T>`, `ErrorResponse`) ensuring consistency across all endpoints.
- **OpenAPI 3.0 Documentation**: Interactive Swagger documentation integrated with Bearer JWT security scheme.

---

## Package & Directory Structure

```
backend/
├── docker-compose.yml              # Containerized PostgreSQL, Redis, pgAdmin
├── .env.example                    # Environment variable template
├── .env                            # Local environment configuration
├── pom.xml                         # Maven dependencies & compiler configurations
├── README.md                       # Comprehensive architecture & developer guide
└── src/
    ├── main/
    │   ├── java/com/apsonic/backend/
    │   │   ├── BackendApplication.java
    │   │   ├── config/             # Spring configuration beans
    │   │   │   ├── JpaConfig.java
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── CorsConfig.java
    │   │   │   ├── WebMvcConfig.java
    │   │   │   ├── JacksonConfig.java
    │   │   │   ├── CacheConfig.java
    │   │   │   └── properties/
    │   │   │       ├── JwtProperties.java
    │   │   │       └── CorsProperties.java
    │   │   ├── security/           # Authentication & security infrastructure
    │   │   │   ├── jwt/
    │   │   │   │   ├── JwtTokenProvider.java
    │   │   │   │   └── JwtAuthenticationFilter.java
    │   │   │   ├── service/
    │   │   │   │   └── CustomUserDetailsService.java
    │   │   │   ├── UserPrincipal.java
    │   │   │   ├── JwtAuthenticationEntryPoint.java
    │   │   │   └── CustomAccessDeniedHandler.java
    │   │   ├── shared/             # Common utilities, contracts, & audit models
    │   │   │   ├── api/
    │   │   │   │   ├── ApiResponse.java
    │   │   │   │   ├── PageResponse.java
    │   │   │   │   └── ErrorResponse.java
    │   │   │   ├── audit/
    │   │   │   │   ├── BaseEntity.java
    │   │   │   │   ├── BaseAuditableEntity.java
    │   │   │   │   └── SecurityAuditorAware.java
    │   │   │   ├── exception/
    │   │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   │   ├── BaseException.java
    │   │   │   │   ├── ResourceNotFoundException.java
    │   │   │   │   ├── BadRequestException.java
    │   │   │   │   ├── UnauthorizedException.java
    │   │   │   │   ├── ForbiddenException.java
    │   │   │   │   └── DuplicateResourceException.java
    │   │   │   ├── filter/
    │   │   │   │   └── TraceIdFilter.java
    │   │   │   ├── validation/
    │   │   │   │   ├── NullOrNotBlank.java
    │   │   │   │   └── ValidPassword.java
    │   │   │   └── util/
    │   │   │       └── SecurityUtils.java
    │   │   └── modules/            # Domain Feature Modules
    │   │       ├── auth/           # Core Authentication & User Management
    │   │       │   ├── controller/
    │   │       │   ├── dto/
    │   │       │   ├── entity/
    │   │       │   ├── init/
    │   │       │   ├── mapper/
    │   │       │   ├── repository/
    │   │       │   └── service/
    │   │       ├── product/        # Product catalog module (CMS)
    │   │       ├── category/       # Category hierarchy module (CMS)
    │   │       ├── content/        # Pages & Blog posts module (CMS)
    │   │       ├── media/          # Assets & Uploads module (CMS)
    │   │       ├── partner/        # Partner logos & links module (CMS)
    │   │       └── team/           # Team profiles & bios module (CMS)
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-prod.yml
    │       ├── logback-spring.xml
    │       └── db/migration/
    │           └── V1__init_security_and_auth.sql
    └── test/
        └── java/com/apsonic/backend/
            ├── BaseUnitTest.java
            ├── BaseIntegrationTest.java
            └── modules/auth/
                ├── AuthServiceTest.java
                ├── controller/AuthControllerTest.java
                └── AuthIntegrationTest.java
```

---

## Requirements & Tech Stack

| Component | Technology / Library | Version |
| :--- | :--- | :--- |
| **JDK** | OpenJDK | Java 21 |
| **Framework** | Spring Boot | 3.4.2 |
| **Security** | Spring Security + JJWT | 0.12.6 |
| **Database** | PostgreSQL | 16-alpine |
| **Cache** | Redis | 7-alpine |
| **Migration** | Flyway | Integrated |
| **Mapping** | MapStruct + Lombok | 1.6.3 |
| **Documentation**| Springdoc OpenAPI / Swagger UI | 2.8.5 |
| **Testing** | JUnit 5, Mockito, Testcontainers | 1.20.4 |

---

## Getting Started

### 1. Prerequisites
- **Java 21 JDK** installed (`java -version`)
- **Docker** and **Docker Compose** installed
- Maven 3.9+ (or use included `./mvnw`)

### 2. Environment Setup
Copy the template environment file:
```bash
cp .env.example .env
```

### 3. Spin Up Infrastructure
Start PostgreSQL, Redis, and pgAdmin containers:
```bash
docker-compose up -d
```
Check status:
```bash
docker-compose ps
```

* **PostgreSQL**: `localhost:5432` (`apsonic_cms` / `apsonic_user`)
* **pgAdmin 4**: `http://localhost:5050` (`admin@apsonic.com` / `admin_secret_2026`)
* **Redis**: `localhost:6379`

### 4. Build & Run Application
Run using the Maven wrapper:
```bash
./mvnw spring-boot:run
```

---

## Security & Initial User Credentials

Flyway migration `V1__init_security_and_auth.sql` automatically populates default roles and permissions, as well as an initial Super Admin account:

### Pre-defined System Roles:
1. `ROLE_SUPER_ADMIN`: Full system administrative permissions.
2. `ROLE_ADMIN`: Full CMS content management permissions.
3. `ROLE_EDITOR`: Content edit & publish permissions.
4. `ROLE_USER`: Standard site member.

---

## Key API Endpoints

### Authentication (`/api/v1/auth`)

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Authenticate user & receive JWT access/refresh tokens | Public |
| `POST` | `/api/v1/auth/refresh` | Obtain new access token via refresh token | Public |
| `GET` | `/api/v1/auth/me` | Fetch profile details of current user | Bearer JWT |
| `POST` | `/api/v1/auth/change-password` | Update account password | Bearer JWT |
| `POST` | `/api/v1/auth/logout` | Revoke refresh token | Bearer JWT |

---

## API Documentation & Actuator

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Actuator Health Endpoint**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## Testing Guidelines

Execute unit and controller tests:
```bash
./mvnw test -Dtest=AuthServiceTest,AuthControllerTest
```

Execute integration tests (requires Docker for Testcontainers):
```bash
./mvnw test -Dtest=AuthIntegrationTest
```

Run full test suite:
```bash
./mvnw test
```

---

## Guidelines for Feature Development (CMS Modules)

When implementing new domain features (e.g. `product`, `category`, `content`, `media`, `partner`, `team`):

1. **Entities**: Extend `BaseEntity` or `BaseAuditableEntity` in `com.apsonic.backend.shared.audit`.
2. **Flyway Migrations**: Create new migration files under `src/main/resources/db/migration/` using standard naming (`V2__create_products_table.sql`, etc.).
3. **Data Access**: Create Spring Data JPA repositories in `modules/{feature}/repository/`.
4. **DTOs & Validation**: Place request/response DTOs in `modules/{feature}/dto/` and use annotations like `@NotBlank`, `@Size`, or custom validators in `shared/validation`.
5. **Services**: Interface in `modules/{feature}/service/` and implementation in `modules/{feature}/service/impl/`.
6. **Controllers**: REST controllers in `modules/{feature}/controller/` returning `ApiResponse<T>` or `PageResponse<T>`. Apply `@PreAuthorize("hasAuthority('READ_PRODUCTS')")` for RBAC enforcement.

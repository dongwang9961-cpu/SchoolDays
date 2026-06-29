# SchoolDays Backend

Spring Boot 4 backend for the multi-tenant school management system.

## Data Access

- Flyway manages PostgreSQL schema migrations.
- jOOQ is the database query layer.
- JPA/Hibernate is not used.

## Lightweight jOOQ Repositories

The project includes a small Spring Data-style proxy for simple jOOQ repositories:

```java
@JooqRepositoryBean(table = "tenants")
public interface TenantRepository extends JooqRepository<Record, UUID> {
    Optional<Record> findByName(String name);
    List<Record> findByStatus(String status);
    boolean existsByName(String name);
}
```

Supported derived methods are intentionally narrow:

- `save(record)`: primary-key insert/upsert for a single jOOQ record
- `findBy<Field>`
- `findBy<Field>And<Field>`
- `existsBy<Field>`
- base methods from `JooqRepository`: `findById`, `findAll`, `existsById`, `deleteById`

Use hand-written jOOQ queries for joins, reporting, dynamic filters, tenant-aware authorization checks, and other complex workflows.

Services should own business workflows and transactions. DAO classes should own persistence details. Use the simple `@JooqRepositoryBean` repository layer for single-table lookups such as `findByEmail`, `findByName`, and `findById`; keep explicit jOOQ inside DAOs for joins, `on conflict`, expiration checks, tenant-sensitive filters, and other queries that need precise SQL.

## Authentication API

Authentication uses Spring Security with stateless signed JWS/JWT bearer tokens. Passwords are stored with BCrypt, and registration is completed through a short-lived email token stored as a SHA-256 hash in `user_registration_links.token_hash`.

Create a parent registration link for a tenant:

```http
POST /api/auth/request-parent-registration-link
Content-Type: application/json

{
  "tenantId": "tenant-uuid",
  "email": "parent@example.com"
}
```

Create a self-service registration link. Public self-service is intentionally limited to parent accounts; teachers and school administrators must use invitation acceptance endpoints.

```http
POST /api/auth/request-self-service-registration-link
Content-Type: application/json

{
  "tenantId": "tenant-uuid",
  "email": "parent@example.com",
  "intendedRole": "PARENT"
}
```

Complete an invited/self-assisted registration:

```http
POST /api/auth/complete-registration
Content-Type: application/json

{
  "token": "raw-email-token",
  "email": "parent@example.com",
  "password": "change-me-securely",
  "firstName": "Pat",
  "lastName": "Parent",
  "phone": "555-0100"
}
```

The legacy path `POST /api/auth/register/complete` is also available for now.

Accept a new-school tenant invitation:

```http
POST /api/auth/accept-tenant-invitation
Content-Type: application/json

{
  "token": "raw-tenant-invitation-token",
  "email": "admin@example.com",
  "password": "change-me-securely",
  "firstName": "Ada",
  "lastName": "Admin"
}
```

Accept a teacher invitation:

```http
POST /api/auth/accept-teacher-invitation
Content-Type: application/json

{
  "token": "raw-teacher-invitation-token",
  "email": "teacher@example.com",
  "password": "change-me-securely",
  "firstName": "Terry",
  "lastName": "Teacher"
}
```

Start Google login:

```http
GET /api/auth/google/start?tenantId=tenant-uuid
```

The callback endpoint is `GET /api/auth/google/callback?code=...&state=...`. The callback exchanges the Google authorization code, verifies the Google email, creates or links a parent user for the tenant carried in the signed state, and returns the same bearer token shape as password login. Configure `SCHOOLDAYS_GOOGLE_CLIENT_ID`, `SCHOOLDAYS_GOOGLE_CLIENT_SECRET`, and `SCHOOLDAYS_GOOGLE_REDIRECT_URI` before enabling Google login in a deployed environment.

Login:

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "parent@example.com",
  "password": "change-me-securely"
}
```

Current user:

```http
GET /api/auth/me
Authorization: Bearer <access-token>
```

Logout:

```http
POST /api/auth/logout
```

Logout is client-side for stateless JWT auth: discard the access token. Configure the signing secret with `SCHOOLDAYS_JWT_SECRET` in every deployed node.

Tokens are signed JWS/JWT bearer tokens using `HS256`. Each token includes a `kid` header. The API signs new tokens with `schooldays.security.jwt.current-key-id` and verifies incoming tokens with any configured key in `schooldays.security.jwt.keys`.

The API will not start unless the current key id exists in the configured key ring, and every secret must be at least 32 characters.

## Generate jOOQ Classes

The test source set includes a callable generator that starts embedded PostgreSQL, applies Flyway migrations, and generates jOOQ classes from the migrated schema:

```bash
mvn test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.schooldays.codegen.GenerateJooqClasses
```

Optional arguments:

```bash
mvn test-compile exec:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.schooldays.codegen.GenerateJooqClasses \
  -Dexec.args="src/main/java com.schooldays.jooq.generated"
```

## Requirements

- Java 17+
- Maven 3.9+
- PostgreSQL, configured through environment variables

## Database Configuration

Do not commit real database credentials. Use environment variables:

```bash
export SPRING_DATASOURCE_URL='postgresql-url-here'
export SPRING_DATASOURCE_USERNAME='database-user'
export SPRING_DATASOURCE_PASSWORD='database-password'
export SCHOOLDAYS_JWT_CURRENT_KEY_ID='primary'
export SCHOOLDAYS_JWT_SECRET='replace-with-at-least-32-random-characters'
```

For Neon, Spring's JDBC driver expects a URL like:

```bash
export SPRING_DATASOURCE_URL='jdbc:postgresql://your-neon-host/neondb?sslmode=require'
export SPRING_DATASOURCE_USERNAME='neondb_owner'
export SPRING_DATASOURCE_PASSWORD='your-password'
```

The `postgresql://user:password@host/database?...` format from Neon should be converted to the JDBC format above for this Spring Boot app.

## Test Data

The application property defaults test data to disabled:

```properties
schooldays.seed-test-data.enabled=false
```

For Neon/local manual testing, `run-neon.sh` enables seed data by default:

```bash
./run-neon.sh
```

Skip seed data with:

```bash
./run-neon.sh --no-seed-test-data
```

If you run Spring Boot directly, enable seed data with:

```bash
SCHOOLDAYS_SEED_TEST_DATA=true mvn spring-boot:run
```

The startup seeder is idempotent and lives in:

```text
src/main/java/com/schooldays/config/TestDataSeedConfig.java
```

Seeded records:

```text
School:
  Name: Longlong Art Studio
  Slug: longlong-art-studio
  Local frontend URL: http://localhost:5173/school/longlong-art-studio

Platform admin:
  Email: admin@schooldays.test
  Password: SchoolDays123!
  Role: PLATFORM_ADMIN

School admin:
  Email: school.admin@longlong-art-studio.test
  Password: SchoolDays123!
  Role: SCHOOL_ADMIN for Longlong Art Studio
```

Leave `SCHOOLDAYS_SEED_TEST_DATA` unset or set it to `false` to avoid creating these records.

## JWT Key Rotation

For simple deployments, `SCHOOLDAYS_JWT_SECRET` supplies the `primary` key:

```properties
schooldays.security.jwt.current-key-id=primary
schooldays.security.jwt.keys.primary=${SCHOOLDAYS_JWT_SECRET}
```

For rotation, configure multiple keys and move `current-key-id` to the new key:

```properties
schooldays.security.jwt.current-key-id=2026-07
schooldays.security.jwt.keys.2026-07=new-secret-at-least-32-characters
schooldays.security.jwt.keys.2026-06=old-secret-at-least-32-characters
```

New tokens are signed with `2026-07`. Tokens signed with `2026-06` continue to verify until you remove the old key. Keep old keys for at least the access-token TTL plus a small clock-skew buffer.

## Run

```bash
mvn spring-boot:run
```

## Verify

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/health/database
```

Flyway runs database migrations automatically on startup.

The initial schema is defined here:

```text
src/main/resources/db/migration/V1__initial_schema.sql
```

When the application starts with a valid PostgreSQL datasource, Flyway will create the schema history table and run this migration automatically.

## Downgrade Scripts

Manual downgrade scripts live here:

```text
src/main/resources/db/downgrade
```

Flyway Community does not execute undo migrations automatically. Treat these as manual rollback scripts unless you later use Flyway Teams' undo support.

Run downgrades in reverse version order:

```text
U3__seed_test_data.sql
U2__add_tenant_slug.sql
U1__initial_schema.sql
```

Example with `psql`:

```bash
psql "$SPRING_DATASOURCE_URL" -f src/main/resources/db/downgrade/U3__seed_test_data.sql
```

`U3` removes only seeded rows marked with `metadata.seed = true`. `U2` drops the tenant slug column. `U1` is destructive and drops all application tables.

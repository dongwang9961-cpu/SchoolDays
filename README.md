# SchoolDays

Monorepo for the SchoolDays multi-tenant school management system.

## Layout

```text
backend/   Spring Boot 4 API, Flyway migrations, jOOQ data access
frontend/  Vite vanilla JavaScript frontend
docs/      Product and database design documents
```

## Backend

```bash
cd backend
mvn test
mvn spring-boot:run
```

Backend details are in [backend/README.md](backend/README.md).

### Test Data

The Neon helper script imports test seed data by default for local/manual testing:

```bash
cd backend
./run-neon.sh
```

Skip seed data with `./run-neon.sh --no-seed-test-data`.

Seed accounts use password `SchoolDays123!`:

```text
admin@schooldays.test        PLATFORM_ADMIN
school.admin@longlong-art-studio.test        SCHOOL_ADMIN for Longlong Art Studio
```

The seeded school is `Longlong Art Studio` with slug `longlong-art-studio`, available in the frontend at `http://localhost:5173/school/longlong-art-studio`.

### Gmail Notifications

To let school admins or teachers send email through their own Gmail account, configure the backend with:

```text
SCHOOLDAYS_GMAIL_CLIENT_ID
SCHOOLDAYS_GMAIL_CLIENT_SECRET
SCHOOLDAYS_GMAIL_REDIRECT_URI=https://api.schooldays.cc/api/oauth/google/gmail/callback
```

For local testing, use `http://localhost:8080/api/oauth/google/gmail/callback` as the redirect URI and add it to the Google OAuth client.

### System Registration Email

Parent registration links are sent by the system, not by an admin Gmail account. Use one verified platform sender domain so new schools do not require a new email-domain setup.

```text
noreply@schooldays.cc
```

The email display name is still school-specific. For Longlong Art Studio, families will see a sender like:

```text
Longlong Art Studio <noreply@schooldays.cc>
```

Local development logs the email by default and still returns the registration link in the API response. For production with Resend, verify `schooldays.cc` with the email provider, then configure:

```text
SCHOOLDAYS_PUBLIC_BASE_URL=https://www.schooldays.cc
SCHOOLDAYS_SYSTEM_EMAIL_PROVIDER=resend
SCHOOLDAYS_SYSTEM_EMAIL_API_KEY=...
SCHOOLDAYS_SYSTEM_EMAIL_FROM_EMAIL=noreply@schooldays.cc
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api` plus `/actuator` requests to the backend at `http://localhost:8080`.

Frontend details are in [frontend/README.md](frontend/README.md).

## Docs

The current design document lives at [docs/school-management-system-design.md](docs/school-management-system-design.md).

# School Management System Design

## Overview

This document describes a multi-tenant school management system where each school operates as an isolated tenant. The system supports school administration, parent registration, child profiles, class enrollment for specific dates, teacher invitations, and attendance check-in by both parents and teachers.

## Goals

- Support multiple schools in one platform with tenant-level data isolation.
- Allow parents to register, add children, enroll children in classes, and check children in.
- Allow schools to manage sites, programs, classes, schedules, and teacher assignments.
- Allow teachers to self-register through invitations, view assigned classes, and manage attendance.
- Provide a clean foundation for future billing, reporting, notifications, and permissions.

## Primary Roles

### Platform Admin

- Creates and manages school tenants.
- Configures global settings.
- Supports platform-level operations such as billing, tenant status, and security policies.

### School Admin

- Manages one school tenant.
- Creates sites, programs, and classes.
- Defines class schedules.
- Invites teachers.
- Views enrollment and attendance records.

### Parent

- Registers an account.
- Adds one or more children.
- Registers children for classes on particular dates.
- Checks children in for classes.
- Reviews enrollment and attendance history.

### Teacher

- Accepts an invitation or self-registers when invited.
- Views assigned classes.
- Checks students in.
- Reviews class attendance.

## Tenant Structure

```text
School / Tenant
  └── Sites
        └── Programs
              └── Classes
                    ├── Teachers
                    ├── Class Schedule
                    ├── Enrollments
                    └── Attendance
```

Example:

```text
Bright Kids Academy
  ├── Downtown Site
  │     ├── Summer Camp Program
  │     │     ├── Art Class
  │     │     └── Music Class
  │     └── After School Program
  │           └── Math Support
  └── North Site
        └── Weekend Program
              └── Robotics Class
```

## Parent Workflow

1. Parent opens a tenant-specific website, tenant-specific registration link, or public class link.
2. System resolves the tenant from the domain, subdomain, slug, or share token. In local development, this can be simulated with a URL such as `http://localhost:5173/school/longlong-art-studio`.
3. Parent chooses an authentication method.
   - Email link registration.
   - Google login, if enabled for the tenant.
4. For email link registration, system sends a registration link to the parent email address.
5. For Google login, system verifies the Google account email and does not require a separate invitation link.
6. System validates the tenant, verified email, intended role, and entry context.
7. Parent completes or confirms account setup for that tenant.
8. Parent logs into the tenant's parent portal.
9. Parent adds one or more child profiles within that tenant.
10. If the parent entered from a public class link, the system automatically displays the target site, program, and class.
11. If the parent entered from the tenant homepage, the parent browses available public classes across that tenant's sites and programs.
12. Parent chooses available class dates for the selected class.
13. System validates that:
   - The selected dates match the class weekday schedule.
   - The class belongs to the resolved tenant.
   - Parent registration is still open for the class.
   - The class has available capacity.
   - The child is not already registered for the same class and date.
14. If the class is free, system creates an enrollment and related enrollment dates.
15. If the class requires payment, system calculates tuition, snack fees, and other configured fee items.
16. Parent completes payment if required.
17. System creates or activates the enrollment after payment succeeds, or leaves it pending if admin review is required.
18. On class day, parent checks in the child.
19. System creates or updates the attendance record.

## Payment Workflow

Some classes are free and some classes require payment. Paid classes can include one or more fee components. Some fee components are required, such as tuition. Others are optional add-ons, such as snack or lunch.

Recommended behavior:

1. School admin configures pricing for a class.
2. Pricing can include multiple fee items.
   - Tuition fee, usually required
   - Snack fee, optional
   - Lunch fee, optional
   - Material fee, optional
   - Registration fee, optional
3. Parent selects class dates or enrollment period.
4. Parent selects optional add-ons if available.
5. System calculates the required amount and optional add-on amount.
6. System shows a payment summary before enrollment is finalized.
7. Parent pays through Stripe Checkout, or school admin records an offline payment.
8. Online payment redirects the parent to Stripe Checkout and uses a Stripe webhook to confirm success.
9. Offline payment can be entered by school admin for face-to-face payment, bill, cash, or check.
10. System records the payment transaction.
11. If all required fees are paid or manually recorded as received, the enrollment can become active or move to approval review.
12. If optional fees are not paid, the enrollment can still become active, but the child does not receive those optional perks.
13. If required payment fails, is not received, or is abandoned, the enrollment remains pending or is not created, depending on tenant policy.
14. School admin can review payments, refunds, offline payment records, optional add-ons, and unpaid enrollments.

Free class behavior:

- No payment checkout is required.
- Enrollment can proceed directly after validation.
- Payment records are not required for zero-cost classes unless the school wants an audit record.

Paid class behavior:

- Enrollment should not become active until required payment succeeds, unless an admin override is used.
- Optional fees should not block enrollment.
- If an optional fee is not paid, the child should not receive the related perk, such as snack or lunch.
- Admin override and admin-entered offline payments should be audited.
- Payment amount should be snapshotted at checkout so later price changes do not rewrite historical transactions.

Optional add-on behavior:

- Optional add-ons can include snack, lunch, supplies, or other tenant-defined perks.
- Parents can choose optional add-ons during enrollment.
- Optional add-ons should be tracked separately from required fees.
- Staff should be able to see which enrolled children paid for each optional perk.
- Optional perks can be date-based if needed, such as lunch only on selected class dates.

Offline payment behavior:

- School admin can record payment received outside the online payment platform.
- Supported offline methods can include cash, check, bill, invoice, Zelle, wire transfer, bank transfer, or other tenant-defined methods.
- Admin should enter amount, payment method, received date, payer, and optional reference number.
- For check payments, the system can track check number and whether the check has cleared.
- Offline payment records should identify the admin who entered the payment.
- Offline payment records should be editable only through audited adjustments, not silent overwrites.

Receipt upload behavior:

- Parent can choose an offline method such as Zelle or wire transfer.
- Parent uploads a receipt or proof of payment.
- System stores the receipt file reference and creates a pending payment review record.
- Enrollment remains `pending_payment_review` or `pending_admin_approval`.
- School admin reviews the receipt.
- If approved, system records the payment as received and activates enrollment or moves it to the next approval step.
- If rejected, enrollment remains pending or is cancelled according to tenant policy.
- Admin approval or rejection should be audited.

Stripe online payment behavior:

- Stripe is the default online payment provider.
- Use Stripe Checkout for hosted online payment.
- Use Stripe webhooks as the source of truth for successful payment, failed payment, expired checkout, and refunds.
- Store Stripe Checkout Session ID and PaymentIntent ID in `PaymentTransaction`.
- Do not store raw card data in this system.
- Offline admin payments remain separate from Stripe payments.

## Parent Class Browsing Workflow

Parents can browse the tenant website to find available classes before registering or enrolling a child.

Recommended behavior:

1. Parent opens the tenant website.
2. System resolves the tenant from the domain, subdomain, or tenant slug.
3. System shows only active public sites for that tenant.
4. Parent can filter or browse by:
   - Site
   - Program
   - Class category
   - Age range
   - Weekday
   - Date range
   - Availability
5. Parent opens a class detail page.
6. System shows the class site, program, schedule, date range, available dates, and availability.
7. Parent starts registration or login from that class page.
8. System preserves the selected class context through email-link registration and login.

This browsing experience is still tenant-scoped. A parent browsing Bright Kids Academy should only see Bright Kids Academy sites, programs, and classes.

## Self-Service Registration Workflow

Some invitations or registrations can be self-service. Instead of an administrator manually creating every invitation, a user can start registration from the tenant website.

Recommended behavior:

1. User opens the tenant website or public class link.
2. User chooses email-link registration or Google login.
3. System resolves the tenant from the website or link.
4. System determines the intended role from the entry point.
   - Parent registration page creates a `PARENT` registration link.
   - Parent Google login can create or link a `PARENT` account without a separate invitation link.
   - Teacher self-service page can create a `TEACHER` registration request if the tenant allows it.
   - Tenant onboarding page can create a `SCHOOL_ADMIN` tenant request if platform self-service onboarding is enabled.
5. For email-link registration, system creates a `UserRegistrationLink`.
6. For Google login, system verifies the Google identity and email.
7. System validates the tenant, email, intended role, and entry context.
8. User completes account setup if needed.
9. System assigns the allowed role or creates a pending approval record if approval is required.

Self-service does not remove security checks. It only means the user can initiate the registration process themselves.

## Tenant Invitation Workflow

1. Platform admin enters the school name and primary administrator email.
2. System creates a pending tenant invitation.
3. System sends an invitation and registration link to the school administrator email.
4. Invited administrator opens the invitation link.
5. Administrator completes registration.
6. System creates the new tenant, or activates a tenant placeholder created during invitation.
7. System creates or links the administrator user account.
8. System assigns the user the `SCHOOL_ADMIN` role for the new tenant.
9. System marks the tenant invitation as accepted.
10. School administrator can log in and configure sites, programs, classes, and teacher invitations.

## School Admin Workflow

1. School admin logs into the school admin portal.
2. School admin creates one or more sites.
3. School admin creates programs under each site.
4. School admin creates classes under each program.
5. School admin defines class schedules using weekdays and times.
6. School admin assigns one or more teachers to each class.
7. School admin invites teachers by email when needed.
8. School admin monitors enrollment and attendance.

## Teacher Invitation Workflow

1. Teacher onboarding starts from either an admin invitation or a tenant-enabled self-service teacher request.
2. For admin invitation, school admin enters the teacher email address.
3. For self-service request, teacher enters their own email address from the tenant teacher registration page.
4. System checks whether a user already exists with that email.
5. If the user exists:
   - System adds the teacher role for the tenant if needed.
   - System creates the class assignment.
6. If the user does not exist:
   - System creates a pending teacher invitation.
   - System sends a registration link to the teacher email.
7. Teacher opens the invitation link.
8. System validates the link token, tenant, intended role, and expiration.
9. Teacher completes self-registration.
10. If approval is required, school admin approves the teacher before access is granted.
11. System links the new user to the tenant and class assignment.
12. Teacher can log in and view assigned classes.

## Teacher Attendance Workflow

1. Teacher logs into the teacher portal.
2. Teacher views assigned classes.
3. Teacher selects a class and date.
4. Teacher views registered children for that date.
5. Teacher checks in students.
6. Teacher reviews the attendance summary.

Attendance summary examples:

- Registered
- Checked in
- Absent
- Late, if supported in a future version

## Notification Workflow

Teachers and school administrators can send email notifications to parents or students through Gmail, tenant-configured SMTP, or a platform default provider.

Recommended behavior:

1. School admin configures an email provider for the tenant.
2. The provider can be Gmail OAuth, custom SMTP, or platform default email.
3. Teacher or admin prepares the email outside the core school management data model.
4. The email can come from pure text, an imported `.eml` file, an uploaded image, or an external email composition flow.
5. Teacher or admin selects the target audience.
6. Teacher or admin can add explicit CC email addresses.
7. System validates that the sender is allowed to message the selected students.
8. Background job sends the email through the configured provider.
9. System saves the raw email body as a blob if retention is required.
10. System creates an `EmailNotificationHistory` record with sender, audience, CC list, BCC count, source type, body blob reference, status, and provider reference.

Teacher examples:

- Send a class reminder to all parents of students enrolled in Beginner Robotics.
- Send a note to one student's parent about missing materials.
- Send a schedule-change message to students enrolled on a specific date.

School admin examples:

- Send a site-wide closure notice.
- Send a program-wide announcement.
- Send a class cancellation notice.
- Send onboarding information to parents.

Permission rules:

- A teacher can only message parents or students connected to classes assigned to that teacher.
- A teacher can only message students enrolled in the selected class or class date.
- A school admin can message parents or students within the same tenant.
- A user cannot send notifications across tenant boundaries.
- The frontend may allow selecting children, classes, programs, or sites, but the backend must re-check access before sending.

Delivery rules:

- Email should be sent to the parent or guardian contact, not directly to a child, unless the school explicitly supports student email addresses.
- Selected student recipients should be added as BCC recipients so families do not see other student or parent email addresses.
- The safest implementation is to send one email job per student or family recipient, with that recipient in BCC and the configured sender in the `To` field.
- Explicit CC email addresses can receive visible copies, such as school administrators or program coordinators.
- CC addresses must be entered intentionally and should not be auto-populated from the student list.
- Imported `.eml` files should be parsed or handed to the email provider safely before sending.
- The system should not manage email subject/body/content as first-class business data.
- Email body content can be stored as a blob in object storage for history or compliance.
- Blob storage should preserve the original email content format when possible.
- Failed send summaries should be visible to school admins.
- Sensitive email provider credentials must be encrypted or stored in a secrets manager.
- Notification history should avoid storing sensitive student information in the email body because the system does not need to manage message content.

## Business Rules

- Every tenant-owned record must include `tenant_id`.
- Tenant data must be isolated in all queries and authorization checks.
- Each tenant behaves like an independent website.
- Parent registration must be scoped to the tenant resolved from the current website, subdomain, tenant slug, or invite/share link.
- Parents cannot choose a school during registration.
- Email is the login identity for parents, teachers, and administrators.
- Users must verify email ownership before completing registration.
- Email verification can happen through a system-sent email link or through Google login.
- Parent Google login does not require a separate invitation link when self-service parent registration is allowed for the tenant.
- Google login must still resolve to the current tenant and intended role.
- Google login should not bypass teacher, admin, tenant invitation, or approval requirements unless explicitly allowed.
- Google login should request only basic identity permissions needed to get the verified email and profile.
- Gmail send permission should be requested separately only when a user connects Gmail as an email notification provider.
- The system should not request broad Gmail mailbox access just for login.
- A registration link can be created by an administrator invitation or a self-service request.
- Registration links must be single-use, time-limited, and stored as token hashes.
- A registration link must bind the email, tenant, and intended role.
- Users should not be allowed to change the email address while completing registration from a link.
- Self-service registration must still be tenant-scoped and role-scoped.
- Self-service teacher registration should require tenant configuration and may require admin approval before the teacher can access classes.
- Self-service tenant onboarding should be optional and may require platform approval before the tenant becomes active.
- Public class links must resolve to exactly one tenant.
- Public class links should resolve to exactly one class and automatically display that class, including its site and program context.
- When a parent registers from a public class link, the target class context should be preserved through email-link registration, Google login, and normal login.
- Tenant websites can expose a public class catalog showing available classes across that tenant's sites and programs.
- Public class catalog pages must only show classes from the resolved tenant.
- Parent portals should only show sites, programs, classes, children, enrollments, and attendance for the current tenant.
- A child belongs to a parent and a tenant.
- A school can have one or more sites, subject to the tenant's configured site limit.
- A site can have multiple programs.
- A program can have multiple classes.
- A class can have one or more teachers.
- A teacher can teach multiple classes.
- A class can have one or more weekday schedules.
- A class can define `registration_opens_at` and `registration_closes_at`.
- A class can be free or paid.
- Paid classes can include multiple fee components, such as tuition, snack cost, materials, or registration fees.
- Fee components can be required or optional.
- Required unpaid fees can block enrollment activation.
- Optional unpaid fees should not block enrollment activation.
- Optional fee payment controls perk eligibility, such as whether the child receives snack or lunch.
- Fee amounts should be stored as integer minor currency units, such as cents.
- Payment totals should be snapshotted when the parent checks out.
- Required payment must succeed before enrollment becomes active, unless an authorized admin override is used.
- Admin payment overrides, discounts, refunds, and manual adjustments should be audited.
- School admins can manually record offline payments such as cash, check, bill, invoice, or other face-to-face payments.
- Offline payment records must include who recorded the payment and when it was recorded.
- Check payments may remain pending until marked cleared by an admin.
- Parents can upload proof of payment for Zelle, wire transfer, or bank transfer.
- Uploaded proof of payment does not activate enrollment until school admin approves it.
- Payment receipt approval and rejection must be audited.
- Parent enrollment should only be allowed while registration is open.
- Parent enrollment should be blocked after `registration_closes_at`.
- School admins can enroll a child after `registration_closes_at` only if admin override is allowed and the action is audited.
- Teachers should not bypass registration cutoff unless explicitly granted that permission by the tenant.
- A child can only enroll in valid dates for the class schedule.
- A child should not be enrolled twice in the same class for the same date.
- Attendance should be unique per child, class, and class date.
- Non-important or school-specific fields should be stored in `metadata` JSONB columns.
- A JSONB field should be promoted to a normal column when it becomes important for filtering, sorting, joining, validation, uniqueness, or reporting.
- JSONB should not be used for core relationships such as tenant, site, program, class, user, child, enrollment, teacher assignment, or attendance links.
- Tenant invitations are platform-level records. They may reference a `tenant_id` after a tenant placeholder is created, but they are not scoped inside an existing school tenant.
- Accepting a tenant invitation should create or activate exactly one tenant and assign the accepting user as a `SCHOOL_ADMIN`.
- Notification provider configuration belongs to a tenant.
- Teachers can only send notifications to students or parents for their assigned classes.
- School admins can send notifications within their own tenant only.
- Notification delivery should be auditable by sender, audience context, provider, BCC recipient count, status, and provider reference.
- Student and parent notification recipients should not be visible to each other. Use private BCC delivery or individual email jobs.
- Notification CC recipients are visible and must be explicitly selected or entered.
- Imported `.eml` notification files and uploaded image content do not need structured database records unless retention is required.

Recommended attendance uniqueness rule:

```text
unique(child_id, class_id, class_date)
```

Recommended enrollment date uniqueness rule:

```text
unique(enrollment_id, class_date)
```

## Suggested Modules

- Authentication and authorization
- Tenant management
- Tenant invitation and onboarding management
- School site management
- Program management
- Class management
- Teacher invitation management
- Parent and child profile management
- Enrollment management
- Payment management
- Attendance management
- Notification management
- Reporting

## Example API Endpoints

All backend API endpoints use the `/api` prefix. Frontend routes, such as public class pages or invitation landing pages, do not need this prefix.

The frontend is split into separate HTML entry points:

```text
/                  -> root platform page, served by index.html
/school/{slug}     -> school website page, served by school.html
```

The root page is for platform users and school administrator invitation acceptance. The school page is tenant-scoped and is used for parent registration, parent registration completion, teacher invitation acceptance, and later public class browsing.

In local development, `/school/longlong-art-studio` simulates the future `longlong-art-studio.schooldays.example.com` subdomain. The frontend resolves the school by calling `GET /api/public/schools/longlong-art-studio`, then uses the returned tenant id for parent registration and Google login.

Domain endpoints whose service logic is not complete should still be exposed by the backend and return `501 Not Implemented` with a stable JSON body. This allows the frontend and future integrations to rely on the route contract while each module is implemented.

### Authentication

```http
POST /api/auth/request-parent-registration-link
POST /api/auth/request-self-service-registration-link
POST /api/auth/complete-registration
GET /api/auth/google/start
GET /api/auth/google/callback
POST /api/auth/login
POST /api/auth/logout
POST /api/auth/accept-tenant-invitation
POST /api/auth/accept-teacher-invitation
```

### Public School Lookup

```http
GET /api/public/schools/:slug
```

### Platform Tenant Invitations

```http
POST /api/platform/tenant-invitations
GET /api/platform/tenant-invitations
POST /api/tenant-invitations/:token/accept
```

### Tenant and School Setup

```http
GET /api/tenants/:tenantId/sites
POST /api/tenants/:tenantId/sites
PATCH /api/tenants/:tenantId/sites/:siteId

POST /api/tenants/:tenantId/programs
PATCH /api/tenants/:tenantId/programs/:programId

POST /api/tenants/:tenantId/classes
PATCH /api/tenants/:tenantId/classes/:classId
POST /api/tenants/:tenantId/classes/:classId/schedules
```

`GET /api/tenants/:tenantId/sites` returns both the site list and the current tenant site quota:

```json
{
  "sites": [],
  "quota": {
    "unlimitedSites": false,
    "maxSites": 5,
    "currentSiteCount": 0,
    "remainingSites": 5
  }
}
```

`POST /api/tenants/:tenantId/sites` must reject creation when the tenant has reached its site limit.

### Teacher Management

```http
POST /api/tenants/:tenantId/classes/:classId/teachers/invite
GET /api/tenants/:tenantId/teacher-invitations
POST /api/tenants/:tenantId/classes/:classId/teachers
DELETE /api/tenants/:tenantId/classes/:classId/teachers/:teacherUserId
```

### Parent and Child Management

```http
GET /api/parents/me/children
POST /api/parents/me/children
PATCH /api/parents/me/children/:childId
```

### Enrollment

```http
GET /api/parents/me/enrollments
POST /api/enrollments
GET /api/classes/:classId/available-dates
```

### Payments

```http
GET /api/classes/:classId/pricing
POST /api/enrollments/:enrollmentId/stripe-checkout-sessions
POST /api/webhooks/stripe
POST /api/enrollments/:enrollmentId/offline-payments
POST /api/enrollments/:enrollmentId/payment-receipts
POST /api/tenants/:tenantId/payment-receipts/:receiptId/approve
POST /api/tenants/:tenantId/payment-receipts/:receiptId/reject
GET /api/parents/me/payments
GET /api/tenants/:tenantId/payments
POST /api/tenants/:tenantId/payments/:paymentId/refund
```

### Attendance

```http
POST /api/attendance/check-in
GET /api/classes/:classId/attendance?date=2026-07-01
GET /api/parents/me/children/:childId/attendance
```

### Notifications

```http
GET /api/tenants/:tenantId/notification-providers
POST /api/tenants/:tenantId/notification-providers
PATCH /api/tenants/:tenantId/notification-providers/:providerId

POST /api/tenants/:tenantId/notifications
GET /api/tenants/:tenantId/notification-history
```

## Recommended Architecture

```text
Frontend
  ├── Parent portal
  ├── Teacher portal
  └── School admin portal

Backend API
  ├── Authentication
  ├── Tenant-aware authorization
  ├── Enrollment validation
  ├── Payment checkout and payment history
  ├── Attendance check-in
  └── Reporting

Database
  └── PostgreSQL

Background Jobs
  ├── Teacher invitation emails
  ├── Enrollment confirmations
  ├── Payment confirmations
  └── Attendance reminders

Payments
  ├── Stripe Checkout
  ├── Stripe webhooks
  └── Offline admin-entered payments

Notifications
  ├── Email
  ├── Gmail integration
  ├── Tenant SMTP settings
  └── SMS, optional
```

## Database Recommendation

Use PostgreSQL because the system has strong relational requirements:

- Schools have sites.
- Sites have programs.
- Programs have classes.
- Classes have schedules and teachers.
- Parents have children.
- Children have enrollments.
- Enrollments create attendance records.
- Paid enrollments create payment transactions.

PostgreSQL also supports strong constraints, indexing, transactional integrity, reporting queries, and future row-level security if needed.

Use PostgreSQL `jsonb` columns for flexible metadata. This keeps the first version simpler while still allowing schools to store custom or optional information without schema changes.

Suggested convention:

```text
metadata jsonb not null default '{}'
```

If a value in `metadata` becomes commonly queried, add either a dedicated column or a targeted JSONB index.

## Multi-Tenant Strategy

For the first version, use a shared database and shared schema with `tenant_id` on tenant-owned tables.

Each tenant should behave like an independent website. Parents should enter through a tenant-specific domain, subdomain, slug, invitation link, or public class link. The system resolves the tenant before registration and automatically scopes the parent account, children, enrollments, attendance, and notifications to that tenant.

Example tenant entry patterns:

```text
https://brightkids.example.com
https://app.example.com/bright-kids
https://app.example.com/t/bright-kids/classes/beginner-robotics
http://localhost:5173/school/bright-kids
```

Production should use school-specific subdomains when available. During development, `/school/{slug}` is the canonical simulation of that subdomain.

Parent-facing pages should not include a school selector. If a parent needs access to more than one tenant, they should enter each tenant through that tenant's own website or invitation link.

Benefits:

- Easier to build and maintain.
- Lower operational cost.
- Simple reporting across schools.
- Good fit for a SaaS-style platform.

For larger enterprise tenants, the platform can later support dedicated databases or stronger isolation boundaries.

## Case Study: End-to-End Flow

This case study shows how the system works from the first school invitation through parent registration, child enrollment, check-in, and attendance review.

### Scenario

Bright Kids Academy wants to use the platform for its after-school programs. A platform admin invites the school owner, Jane Smith, to register the school. Jane will create a site, set up a program and class, invite a teacher, publish the class link, and allow parents to register their children.

### Step 1: Platform Admin Sends Tenant Invitation

The platform admin creates a tenant invitation:

```text
School name: Bright Kids Academy
Admin email: jane@brightkids.example
Invitation status: pending
```

System behavior:

1. Creates a `TenantInvitation` record.
2. Stores optional onboarding details in `metadata`.
3. Generates a secure invitation token.
4. Sends Jane an invitation email.

Example invitation email:

```text
Subject: You are invited to set up Bright Kids Academy

Hello Jane,

You have been invited to create your school account for Bright Kids Academy.
Use the link below to complete registration and start setting up your school.

Accept invitation: https://app.example.com/tenant-invitations/accept?token=...
```

### Step 2: School Admin Accepts Invitation and Registers

Jane opens the invitation link and completes registration.

She enters:

```text
First name: Jane
Last name: Smith
Email: jane@brightkids.example
Password: ********
School timezone: America/Detroit
```

System behavior:

1. Validates the invitation token.
2. Confirms the token was sent to `jane@brightkids.example`.
3. Prevents Jane from changing the verified email during account setup.
4. Creates or activates the `Tenant` record for Bright Kids Academy.
5. Creates Jane's `User` record if one does not already exist.
6. Assigns Jane the `SCHOOL_ADMIN` role for the new tenant.
7. Marks the `TenantInvitation` as accepted.
8. Redirects Jane to the school admin dashboard.

Result:

```text
Tenant: Bright Kids Academy
Admin user: Jane Smith
Role: SCHOOL_ADMIN
```

### Step 3: School Admin Creates a Site

Jane creates the first site:

```text
Site name: Downtown Campus
Timezone: America/Detroit
Address: 123 Main St, Ann Arbor, MI 48104
```

System behavior:

1. Creates a `SchoolSite` record.
2. Stores searchable fields such as `tenant_id`, `name`, `timezone`, and `status` as regular columns.
3. Stores optional address and contact details in `metadata`.

Example site record:

```json
{
  "id": "site_001",
  "tenant_id": "tenant_bright_kids",
  "name": "Downtown Campus",
  "timezone": "America/Detroit",
  "status": "active",
  "metadata": {
    "address": {
      "line1": "123 Main St",
      "city": "Ann Arbor",
      "state": "MI",
      "postalCode": "48104"
    }
  }
}
```

### Step 4: School Admin Creates a Program

Jane creates a program under the Downtown Campus site:

```text
Program name: After-School Enrichment
Description: Weekly enrichment classes for elementary students.
```

System behavior:

1. Creates a `Program` record linked to the site.
2. Stores `tenant_id`, `site_id`, `name`, and `status` as regular columns.
3. Stores the description and optional age range in `metadata`.

### Step 5: School Admin Sets Up a New Class

Jane creates a class under the program:

```text
Class name: Beginner Robotics
Capacity: 12 students
Start date: 2026-09-08
End date: 2026-12-18
Registration opens: 2026-08-01 09:00
Registration closes: 2026-09-05 23:59
Schedule: Tuesdays and Thursdays, 3:30 PM - 5:00 PM
Payment: required
Required tuition fee: $160.00
Optional snack fee: $20.00
Optional lunch fee: $45.00
```

System behavior:

1. Creates a `Class` record.
2. Creates two `ClassSchedule` records:
   - Tuesday, 3:30 PM - 5:00 PM
   - Thursday, 3:30 PM - 5:00 PM
3. Creates class pricing with required tuition and optional snack/lunch fee items.
4. Validates future enrollment dates against these weekday schedule records.

Example class structure:

```text
Bright Kids Academy
  └── Downtown Campus
        └── After-School Enrichment
              └── Beginner Robotics
                    ├── Tuesday, 3:30 PM - 5:00 PM
                    └── Thursday, 3:30 PM - 5:00 PM
```

### Step 6: School Admin Invites a Teacher

Jane invites a teacher named Mark:

```text
Teacher email: mark.teacher@example.com
Assigned class: Beginner Robotics
```

System behavior:

1. Checks whether a user already exists for Mark's email.
2. If Mark does not exist, creates a `TeacherInvitation`.
3. Sends Mark an invitation and registration link.
4. Mark clicks the email link before completing registration.
5. System prevents Mark from changing the verified email during account setup.
6. After Mark registers, assigns him the `TEACHER` role for Bright Kids Academy.
7. Creates a `TeacherAssignment` linking Mark to Beginner Robotics.

Result:

```text
Teacher: Mark Teacher
Class: Beginner Robotics
Role: TEACHER
Tenant: Bright Kids Academy
```

### Step 7: School Admin Publishes the Class Link

Jane publishes a public registration link for the class:

```text
https://brightkids.example.com/classes/beginner-robotics
```

Public page behavior:

1. Shows school name, site, program, class name, schedule, date range, and available dates.
2. Automatically treats Beginner Robotics as the target class for registration.
3. Allows new parents to register for Bright Kids Academy or existing Bright Kids Academy parents to log in.
4. After login or registration, returns the parent to the same target class.
5. Does not expose private tenant data, internal IDs, teacher-only views, or attendance records.

Recommended public link rules:

- The link must resolve to Bright Kids Academy before registration starts.
- The link should resolve to exactly one target class, including its site and program context.
- The target class should be displayed automatically; parents should not need to manually select site, program, or class again.
- Use a public or share token instead of exposing raw database IDs.
- Only show active classes that are configured as public.
- Validate class capacity and available dates server-side.
- Require parent registration before completing child enrollment.
- Do not show a school selector on the parent registration page.

Parents can also browse the Bright Kids Academy website before opening a specific class:

```text
https://brightkids.example.com/classes
```

Browsing behavior:

1. Shows available classes across Bright Kids Academy sites.
2. Allows filtering by site, program, weekday, date range, age range, and availability.
3. Opens a class detail page when the parent chooses a class.
4. Preserves the chosen class through registration and login.

### Step 8: Parent Registers from the Public Link

A parent, Alex Johnson, opens the Bright Kids Academy public class link and starts registration for that tenant.

Alex chooses Google login:

```text
Provider: Google
Verified email: alex.parent@example.com
```

Because Google verifies Alex's email, the system does not need to send a separate invitation or registration link for this parent self-service flow.

Alternative email-link path:

```text
Email: alex.parent@example.com
System sends: Complete your Bright Kids Academy registration
```

Alex completes or confirms account setup:

```text
Parent name: Alex Johnson
Email: alex.parent@example.com
Phone: 555-0123
Password: ********
```

System behavior:

1. Resolves the tenant as Bright Kids Academy from the domain.
2. Resolves the public link target as Downtown Campus, After-School Enrichment, Beginner Robotics.
3. Verifies Alex's email from Google, or validates the email-link token if Alex chose email registration.
4. Stores the target class context with the registration flow.
5. Creates Alex's `UserIdentity` record for Google if needed.
6. Creates Alex's `User` record or links to an existing login identity.
7. Prevents Alex from changing the verified email during account setup.
8. Assigns Alex the `PARENT` role for Bright Kids Academy.
9. Redirects Alex back to the Beginner Robotics registration flow with the class already selected.

### Step 9: Parent Adds a Child

Alex adds a child profile:

```text
Child name: Emma Johnson
Date of birth: 2018-04-12
Medical note: Peanut allergy
Emergency contact: Taylor Johnson, 555-0144
```

System behavior:

1. Creates a `Child` record linked to Alex and Bright Kids Academy.
2. Stores important searchable fields as columns.
3. Stores medical notes and emergency contact details in `metadata`.

### Step 10: Parent Registers the Child for Class Dates

Alex selects Beginner Robotics and chooses dates:

```text
Selected dates:
- 2026-09-08
- 2026-09-10
- 2026-09-15
- 2026-09-17
```

System behavior:

1. Confirms each selected date is between the class start and end dates.
2. Confirms each selected date falls on Tuesday or Thursday.
3. Confirms parent registration is still open.
4. Confirms class capacity is available for each date.
5. Confirms Emma is not already enrolled for the same class and date.
6. Calculates required tuition.
7. Allows Alex to choose optional snack or lunch.
8. Shows Alex the payment summary.
9. Redirects Alex to Stripe Checkout.
10. Receives payment confirmation from the Stripe webhook.
11. Creates a `PaymentTransaction` record after payment succeeds.
12. Creates an `Enrollment` record after required payment succeeds.
13. Creates optional perk records only for paid optional add-ons.
14. Creates related `EnrollmentDate` records.

Result:

```text
Parent: Alex Johnson
Child: Emma Johnson
Class: Beginner Robotics
Dates: 4 selected dates
Payment status: paid
Required payment total: $160.00
Optional perks selected: snack
Optional payment total: $20.00
Enrollment status: active
```

Alternative offline payment path:

```text
Payment method: Check
Check number: 1042
Received by: Jane Smith
Payment total: $160.00 required tuition
Optional perks: not paid
```

In this case, Jane records the payment from the school admin portal. The system creates a manual `PaymentTransaction` record and activates the enrollment if tenant policy allows check payments to count as received. If the tenant requires check clearing first, the enrollment can remain `pending_payment` until Jane marks the check as cleared.

Alternative parent-uploaded receipt path:

```text
Payment method: Zelle
Receipt uploaded by: Alex Johnson
Receipt file: zelle-confirmation.png
Payment total: $160.00 required tuition
Enrollment status: pending_payment_review
```

In this case, Alex pays outside the system through Zelle or wire transfer, then uploads a receipt. The system stores the receipt and keeps the enrollment pending. Jane reviews the receipt from the admin portal. If Jane approves it, the system creates or updates the `PaymentTransaction` record and activates the enrollment. If Jane rejects it, the enrollment remains pending or is cancelled according to tenant policy.

### Step 11: Parent Checks In the Child

On September 8, 2026, Alex arrives with Emma and checks her in from the parent portal.

System behavior:

1. Confirms Alex has the `PARENT` role for Bright Kids Academy.
2. Confirms Emma belongs to Alex for this tenant.
3. Confirms Emma is enrolled in Beginner Robotics on September 8, 2026.
4. Creates or updates an `Attendance` record.

Example attendance result:

```text
Child: Emma Johnson
Class: Beginner Robotics
Date: 2026-09-08
Status: checked_in
Checked in by: Alex Johnson
Checked in by role: PARENT
Checked in at: 2026-09-08 15:22
```

### Step 12: Teacher Checks In Students

If a parent does not check in a child, Mark can check in students from the teacher portal.

Teacher view:

```text
Class: Beginner Robotics
Date: 2026-09-08

Registered students:
- Emma Johnson: already checked in by parent
- Noah Lee: not checked in
- Mia Chen: not checked in
```

Mark checks in Noah and Mia.

System behavior:

1. Confirms Mark has the `TEACHER` role for Bright Kids Academy.
2. Confirms Mark is assigned to Beginner Robotics.
3. Confirms each child is enrolled for the selected class date.
4. Creates or updates attendance records.

### Step 13: Teacher Reviews Attendance Periodically

Mark reviews attendance at the end of each class day or weekly.

Daily attendance view:

```text
Class: Beginner Robotics
Date: 2026-09-08

Registered: 12
Checked in: 10
Absent: 2
```

Weekly attendance view:

```text
Class: Beginner Robotics
Week: 2026-09-08 to 2026-09-12

Student          Tue 09/08    Thu 09/10
Emma Johnson     Present      Present
Noah Lee         Present      Absent
Mia Chen         Present      Present
```

System behavior:

1. Reads enrollments for the selected class and date range.
2. Reads attendance records for the same class and date range.
3. Calculates present and absent states.
4. Allows filtering by class, date, week, program, or site.
5. Allows school admins to view broader reports across teachers and programs.

### Step 14: School Admin Monitors Operations

Jane uses the admin dashboard to monitor:

- Active classes.
- Public registration links.
- Enrollment counts.
- Payment status and unpaid enrollments.
- Teacher assignments.
- Daily attendance.
- Missing check-ins.
- Capacity by class date.

### Step 15: Teacher or Admin Sends Notifications

Jane configures email delivery for Bright Kids Academy:

```text
Provider type: SMTP
From name: Bright Kids Academy
From email: notifications@brightkids.example
SMTP host: smtp.example.com
SMTP port: 587
Security: STARTTLS
```

Alternatively, Jane can connect a Gmail account through OAuth:

```text
Provider type: Gmail
Connected account: jane@brightkids.example
```

Mark sends a reminder to parents of students enrolled in Beginner Robotics:

```text
Audience: Beginner Robotics parents
Source: external email or imported file
Subject snapshot: Reminder: Robotics class tomorrow
CC: jane@brightkids.example
BCC mode: one private BCC delivery per student/family recipient
```

Mark could use a prepared email without the system managing the email body:

```text
Import file: robotics-reminder.eml
Subject snapshot: Reminder: Robotics class tomorrow
Content storage: outside the school management data model
```

Or he could send an image-only announcement:

```text
Source: uploaded image handled by email sending flow
Subject snapshot: Robotics winter showcase
```

System behavior:

1. Confirms Mark is assigned to Beginner Robotics.
2. Finds active enrollments for the selected class or selected class date.
3. Resolves parent email addresses for the enrolled children.
4. Sends email using the tenant's configured Gmail or SMTP provider.
5. Sends student or family recipients as private BCC recipients so recipients cannot see each other's addresses.
6. Saves the raw email body as a blob for history.
7. Stores a lightweight `EmailNotificationHistory` record.
8. Records sender, audience, CC list, BCC recipient count, subject snapshot if available, body blob reference, provider reference, and send status.

Jane can also send a program-wide announcement:

```text
Audience: After-School Enrichment program
Subject snapshot: Winter schedule update
```

Because Jane is a school admin, she can message broader audiences within Bright Kids Academy, but not outside that tenant.

This gives the school a complete loop:

```text
Tenant invitation
  -> School admin registration
  -> Site setup
  -> Program setup
  -> Class setup
  -> Teacher invitation
  -> Public class link
  -> Parent registration
  -> Child enrollment
  -> Payment, if required
  -> Parent or teacher check-in
  -> Attendance reporting
  -> Teacher or admin notifications
```

## MVP Scope

The first version should include:

1. Parent registration and login, including optional Google login.
2. Tenant invitation and school administrator self-registration.
3. School admin login.
4. Teacher invitation and self-registration.
5. Site, program, and class creation.
6. Class weekday schedule management.
7. Child profile management.
8. Child enrollment for selected class dates.
9. Free and paid class enrollment with required tuition and optional snack/lunch perk support.
10. Parent check-in.
11. Teacher check-in.
12. Attendance report by class and date.
13. Email notifications through Gmail or tenant-configured SMTP.

## Future Enhancements

- Invoices, discounts, coupons, and advanced billing.
- Waitlists.
- Capacity rules by date.
- Sibling discounts.
- Parent notifications.
- Gmail or SMTP email provider configuration.
- Teacher notes.
- Late check-in tracking.
- Check-out workflow.
- Emergency contacts.
- Waivers and documents.
- Class cancellation and makeup dates.
- Reporting dashboards.
- Mobile app support.
- QR code check-in.
- Role-based permission customization.

## Technical Appendix: Table Definitions

The schema should keep important operational fields as regular columns and move secondary, rarely queried, or tenant-specific details into JSONB columns.

Keep these as first-class columns:

- Primary keys and foreign keys.
- Tenant identifiers.
- User login fields.
- Names used for search and display.
- Status fields.
- Dates and times used for scheduling, enrollment, and attendance.
- Capacity and other values needed for validation.
- Fields used in uniqueness rules, joins, filtering, and reporting.

Use JSONB for non-essential details:

- Optional profile fields.
- Notes and comments.
- UI or tenant preferences.
- Extended contact details.
- Address details that do not need separate searching at first.
- Medical, pickup, waiver, or emergency-contact details.
- Audit context that is useful but not core to constraints.
- Future custom fields per school.

### Tenant

Represents a school or organization using the platform.

- `id`
- `name`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `metadata` values:

```json
{
  "settings": {
    "defaultTimezone": "America/Detroit",
    "allowParentCheckIn": true,
    "allowTeacherCheckIn": true,
    "max_sites": 5
  },
  "branding": {
    "logoUrl": "https://example.com/logo.png",
    "primaryColor": "#2457C5"
  }
}
```

Tenant site limits are plan/settings data and can live in `metadata.settings.max_sites`:

- `1` means the school can create one site.
- `5` means the school can create up to five sites.
- `"unlimited"` means VIP schools can create as many sites as needed.

If the setting is missing or invalid, the backend should default to `1` site.

### TenantInvitation

Tracks invitations for new schools or organizations to join the platform.

- `id`
- `school_name`
- `admin_email`
- `invited_by_user_id`
- `tenant_id`
- `status`
- `token_hash`
- `expires_at`
- `accepted_at`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `metadata` values:

```json
{
  "contact": {
    "adminFirstName": "Jane",
    "adminLastName": "Smith",
    "phone": "555-0188"
  },
  "onboarding": {
    "source": "sales",
    "notes": "Interested in after-school program management"
  }
}
```

### User

Represents a person who can log into the platform. Email is the user's login identity. A user may have different roles across different tenants, but they must verify ownership of the email address through a system-sent link or a trusted identity provider such as Google before registration is completed.

- `id`
- `email`
- `phone`
- `password_hash`
- `first_name`
- `last_name`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Recommended constraints:

```text
unique(lower(email))
```

Email should be normalized before storage and lookup.

Example `metadata` values:

```json
{
  "profile": {
    "preferredName": "Don",
    "avatarUrl": null
  },
  "notificationPreferences": {
    "email": true,
    "sms": false
  }
}
```

### UserIdentity

Links a user to an external or internal authentication method.

- `id`
- `user_id`
- `provider`
- `provider_subject`
- `email`
- `email_verified`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `provider` values:

- `EMAIL_LINK`
- `GOOGLE`

Notes:

- `provider_subject` stores the stable subject identifier from the provider.
- For Google login, use Google's verified email to create or link the user.
- Google login should use basic OpenID Connect identity scopes, such as `openid`, `email`, and `profile`.
- Google login can replace the email invitation link for parent self-service registration.
- Google login should not automatically grant teacher or admin access unless that role flow explicitly allows it.
- Google login should not request Gmail send/read permissions.

### UserRegistrationLink

Tracks system-sent email links that allow a parent, teacher, or administrator to complete registration.

- `id`
- `tenant_id`
- `email`
- `intended_role`
- `invitation_type`
- `related_invitation_id`
- `token_hash`
- `status`
- `expires_at`
- `used_at`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `intended_role` values:

- `PARENT`
- `TEACHER`
- `SCHOOL_ADMIN`

Example `invitation_type` values:

- `PARENT_REGISTRATION`
- `SELF_SERVICE_REGISTRATION`
- `TEACHER_INVITATION`
- `TENANT_INVITATION`
- `PASSWORDLESS_LOGIN`

Notes:

- The raw token should only appear in the email link.
- Store only a hash of the token in the database.
- A registration link should be single-use and expire after a configured period.
- The link should resolve to exactly one tenant and one intended role.
- The link can be generated by an administrator invitation or by a self-service registration request.

### Role

Defines a permission group.

- `id`
- `name`

Example role names:

- `PLATFORM_ADMIN`
- `SCHOOL_ADMIN`
- `TEACHER`
- `PARENT`

### UserRole

Associates users with roles, optionally scoped to a tenant.

- `id`
- `user_id`
- `role_id`
- `tenant_id`
- `metadata` JSONB

### SchoolSite

Represents a physical or operational location for a school.

- `id`
- `tenant_id`
- `name`
- `timezone`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `metadata` values:

```json
{
  "address": {
    "line1": "123 Main St",
    "line2": "Suite 200",
    "city": "Ann Arbor",
    "state": "MI",
    "postalCode": "48104"
  },
  "contact": {
    "phone": "555-0100",
    "email": "site@example.com"
  }
}
```

### Program

Represents a program offered at a site, such as after-school care, summer camp, weekend classes, or enrichment.

- `id`
- `tenant_id`
- `site_id`
- `name`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `metadata` values:

```json
{
  "description": "After-school enrichment classes for elementary students.",
  "ageRange": {
    "min": 6,
    "max": 12
  }
}
```

### Class

Represents a specific class under a program.

- `id`
- `tenant_id`
- `program_id`
- `name`
- `capacity`
- `start_date`
- `end_date`
- `registration_opens_at`
- `registration_closes_at`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `metadata` values:

```json
{
  "description": "Beginner robotics class using visual programming and small group projects.",
  "requirements": ["Laptop optional", "No prior experience required"],
  "room": "Lab 2",
  "registration": {
    "allowAdminOverrideAfterClose": true
  }
}
```

### ClassPricing

Defines whether a class is free or requires payment.

- `id`
- `tenant_id`
- `class_id`
- `pricing_type`
- `currency`
- `total_amount`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `pricing_type` values:

- `FREE`
- `PAID`

Notes:

- Store `total_amount` in minor currency units, such as cents.
- For a free class, `pricing_type` should be `FREE` and `total_amount` should be `0`.
- For a paid class, `total_amount` can be calculated from related fee items.

### ClassFeeItem

Defines individual fee components for a paid class.

- `id`
- `tenant_id`
- `class_pricing_id`
- `fee_type`
- `name`
- `amount`
- `required`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `fee_type` values:

- `TUITION`
- `SNACK`
- `MATERIAL`
- `REGISTRATION`
- `LUNCH`
- `OTHER`

Example:

```text
Required tuition fee: $160.00
Optional snack fee: $20.00
Optional lunch fee: $45.00
```

Notes:

- `required = true` means the fee must be paid before enrollment becomes active.
- `required = false` means the fee is an optional add-on.
- Optional add-ons can grant perks such as snack, lunch, supplies, or extended care.

### ClassSchedule

Defines the weekdays and times when a class occurs.

- `id`
- `class_id`
- `weekday`
- `start_time`
- `end_time`
- `metadata` JSONB

Example:

```text
Class: Robotics Basics
Schedule:
- Monday, 3:00 PM - 5:00 PM
- Wednesday, 3:00 PM - 5:00 PM
```

### TeacherAssignment

Assigns one or more teachers to a class.

- `id`
- `class_id`
- `teacher_user_id`
- `assigned_by_user_id`
- `metadata` JSONB
- `created_at`

### Child

Represents a child profile managed by a parent.

- `id`
- `tenant_id`
- `parent_user_id`
- `first_name`
- `last_name`
- `date_of_birth`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `metadata` values:

```json
{
  "medical": {
    "notes": "Peanut allergy",
    "allergies": ["peanuts"]
  },
  "emergencyContacts": [
    {
      "name": "Jane Doe",
      "phone": "555-0130",
      "relationship": "Aunt"
    }
  ],
  "authorizedPickup": [
    {
      "name": "John Doe",
      "relationship": "Grandparent"
    }
  ]
}
```

### Enrollment

Represents a child being registered for a class.

- `id`
- `tenant_id`
- `child_id`
- `class_id`
- `enrollment_status`
- `created_by_user_id`
- `metadata` JSONB
- `created_at`
- `updated_at`

### EnrollmentDate

Represents the specific class dates selected for an enrollment.

- `id`
- `enrollment_id`
- `class_date`
- `status`
- `metadata` JSONB

### EnrollmentPerk

Tracks optional add-ons or perks purchased for an enrollment.

- `id`
- `tenant_id`
- `enrollment_id`
- `class_fee_item_id`
- `perk_type`
- `perk_status`
- `starts_on`
- `ends_on`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `perk_type` values:

- `SNACK`
- `LUNCH`
- `SUPPLIES`
- `EXTENDED_CARE`
- `OTHER`

Example `perk_status` values:

- `active`
- `cancelled`
- `refunded`
- `expired`

Notes:

- A child can be actively enrolled even if they have no optional perks.
- Staff can use this table to know which children should receive snack, lunch, or other add-ons.
- If optional perks are date-specific, store selected dates in `metadata` or add a separate `EnrollmentPerkDate` table later.

### PaymentTransaction

Records payment attempts and successful payments for paid enrollments.

- `id`
- `tenant_id`
- `enrollment_id`
- `payer_user_id`
- `provider`
- `provider_payment_id`
- `payment_source`
- `payment_method`
- `amount`
- `currency`
- `payment_status`
- `paid_at`
- `recorded_by_user_id`
- `recorded_at`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `payment_status` values:

- `pending`
- `succeeded`
- `failed`
- `refunded`
- `partially_refunded`
- `cleared`

Example `metadata` values:

```json
{
  "feeBreakdown": [
    {
      "type": "TUITION",
      "amount": 16000,
      "required": true
    },
    {
      "type": "SNACK",
      "amount": 2000,
      "required": false,
      "perk": "SNACK"
    }
  ],
  "adminOverride": false,
  "offlinePayment": {
    "referenceNumber": "1042",
    "receivedBy": "Jane Smith",
    "checkCleared": false
  }
}
```

Notes:

- Store `amount` in minor currency units, such as cents.
- Snapshot fee breakdown at payment time.
- Do not store raw credit card data in this system.
- Stripe payment IDs can be stored, but Stripe secret keys and webhook signing secrets should be stored securely.
- For online payments, `payment_source` can be `ONLINE` and `provider` should be `STRIPE`.
- Store Stripe Checkout Session ID and Stripe PaymentIntent ID in `provider_payment_id` or `metadata`.
- For offline payments, `payment_source` can be `OFFLINE` and `payment_method` can be `CASH`, `CHECK`, `BILL`, `INVOICE`, `ZELLE`, `WIRE`, `BANK_TRANSFER`, or `OTHER`.
- For admin-entered offline payments, `recorded_by_user_id` should identify the admin who entered the payment.

### PaymentReceipt

Stores parent-uploaded proof of payment for offline methods such as Zelle, wire transfer, or bank transfer.

- `id`
- `tenant_id`
- `enrollment_id`
- `uploaded_by_user_id`
- `payment_transaction_id`
- `payment_method`
- `amount`
- `currency`
- `receipt_file_url`
- `receipt_status`
- `reviewed_by_user_id`
- `reviewed_at`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `payment_method` values:

- `ZELLE`
- `WIRE`
- `BANK_TRANSFER`
- `OTHER`

Example `receipt_status` values:

- `pending_review`
- `approved`
- `rejected`

Notes:

- Uploading a receipt does not automatically activate enrollment.
- Admin approval is required before the payment counts as received.
- When approved, the system can create or update the related `PaymentTransaction`.
- Receipt files should be stored in object storage, not directly in the database.
- Rejection should include a reason in `metadata`.

### Attendance

Represents a child check-in record for a class on a specific date.

- `id`
- `tenant_id`
- `child_id`
- `class_id`
- `class_date`
- `checked_in_at`
- `checked_in_by_user_id`
- `checked_in_by_role`
- `status`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `metadata` values:

```json
{
  "source": "parent_portal",
  "note": "Arrived 5 minutes late"
}
```

### TeacherInvitation

Tracks invitations sent to teachers.

- `id`
- `tenant_id`
- `email`
- `invited_by_user_id`
- `teacher_user_id`
- `status`
- `token_hash`
- `expires_at`
- `accepted_at`
- `metadata` JSONB
- `created_at`
- `updated_at`

### NotificationProvider

Stores the email delivery configuration for a tenant. A school can use Gmail integration or its own SMTP settings.

- `id`
- `tenant_id`
- `provider_type`
- `status`
- `from_email`
- `from_name`
- `created_by_user_id`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `provider_type` values:

- `GMAIL`
- `SMTP`
- `PLATFORM_DEFAULT`

Example `metadata` values:

```json
{
  "gmail": {
    "accountEmail": "admin@brightkids.example",
    "oauthConnected": true,
    "permission": "send_email"
  },
  "smtp": {
    "host": "smtp.example.com",
    "port": 587,
    "username": "notifications@brightkids.example",
    "security": "STARTTLS"
  }
}
```

Sensitive provider values, such as SMTP passwords or Gmail refresh tokens, should be encrypted or stored in a secrets manager. They should not be stored as plain JSONB values.

Gmail integration notes:

- Connecting Gmail for notifications is separate from Google login.
- Gmail notification integration should request permission to send email from the connected account.
- Avoid requesting Gmail read or mailbox-wide permissions unless a future feature truly requires it.
- The connected Gmail account can belong to an authorized school admin or configured school sender account.

### EmailNotificationHistory

Records the history of email notifications sent by teachers or administrators. The system does not need to manage or edit email content. It only keeps enough information for audit, troubleshooting, and reporting.

- `id`
- `tenant_id`
- `sender_user_id`
- `provider_id`
- `audience_type`
- `source_type`
- `cc_emails` JSONB
- `bcc_recipient_count`
- `subject_snapshot`
- `body_blob_url`
- `body_blob_mime_type`
- `external_reference`
- `status`
- `sent_at`
- `metadata` JSONB
- `created_at`
- `updated_at`

Example `audience_type` values:

- `CHILD`
- `CLASS`
- `PROGRAM`
- `SITE`
- `CUSTOM`

Example `source_type` values:

- `COMPOSED`
- `IMPORTED_EML`
- `IMAGE_UPLOAD`

Example `metadata` values:

```json
{
  "classId": "class_beginner_robotics",
  "childIds": ["child_emma_johnson"],
  "channel": "email",
  "bccMode": "per_student",
  "originalFileName": "robotics-reminder.eml",
  "bodyStorage": "object_storage",
  "deliverySummary": {
    "sent": 12,
    "failed": 0
  }
}
```

Notes:

- `subject_snapshot` is optional and only records the subject used at send time if available.
- `body_blob_url` points to the stored raw email body or imported email content.
- `body_blob_mime_type` records whether the blob is text, HTML, `.eml`, image content, or another supported email format.
- `external_reference` can store a Gmail message ID, SMTP provider message ID, storage reference, or imported file reference.
- The email body can be saved as a blob for history or compliance, but it should not be managed as structured business data.
- The system should not query or depend on email body content for core workflows.

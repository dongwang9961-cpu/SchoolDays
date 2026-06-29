-- Manual downgrade for V1__initial_schema.sql.
-- Destructive: drops all SchoolDays application tables.

drop table if exists email_notification_history cascade;
drop table if exists notification_providers cascade;
drop table if exists teacher_invitations cascade;
drop table if exists attendance cascade;
drop table if exists payment_receipts cascade;
drop table if exists payment_transactions cascade;
drop table if exists enrollment_perks cascade;
drop table if exists enrollment_dates cascade;
drop table if exists enrollments cascade;
drop table if exists children cascade;
drop table if exists teacher_assignments cascade;
drop table if exists class_schedules cascade;
drop table if exists class_fee_items cascade;
drop table if exists class_pricing cascade;
drop table if exists classes cascade;
drop table if exists programs cascade;
drop table if exists school_sites cascade;
drop table if exists user_roles cascade;
drop table if exists roles cascade;
drop table if exists user_registration_links cascade;
drop table if exists user_identities cascade;
drop table if exists tenant_invitations cascade;
drop table if exists users cascade;
drop table if exists tenants cascade;

drop extension if exists pgcrypto;

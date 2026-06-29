-- Manual downgrade for V2__add_tenant_slug.sql.
-- This removes tenant public slugs.

drop index if exists uq_tenants_slug;

alter table tenants
    drop column if exists slug;

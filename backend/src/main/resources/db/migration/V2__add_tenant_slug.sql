alter table tenants
    add column if not exists slug varchar(80);

create unique index if not exists uq_tenants_slug
    on tenants(lower(slug))
    where slug is not null;

create table external_students (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    external_id varchar(255) not null,
    student_name varchar(255) not null,
    gender varchar(50),
    birth_date date,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_external_students_tenant_external_id unique (tenant_id, external_id)
);

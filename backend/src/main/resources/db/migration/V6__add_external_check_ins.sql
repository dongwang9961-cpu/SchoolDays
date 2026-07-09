create table external_check_ins (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    external_student_id varchar(255) not null,
    class_id uuid not null references classes(id),
    check_date date not null,
    check_in_time timestamptz not null default now(),
    checked_in_by_user_id uuid references users(id),
    checked_in_by_role varchar(50),
    status varchar(50) not null default 'checked_in',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_external_check_ins_tenant_student_class_date unique (tenant_id, external_student_id, class_id, check_date)
);

create index idx_external_check_ins_tenant_class_date on external_check_ins (tenant_id, class_id, check_date desc);

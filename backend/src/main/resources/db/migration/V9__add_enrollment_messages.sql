create table messages (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    enrollment_id uuid references enrollments(id) on delete cascade,
    sender_user_id uuid not null references users(id),
    recipient_user_id uuid not null references users(id),
    message_type varchar(50) not null,
    body text not null,
    read_at timestamptz,
    metadata jsonb not null default '{}',
    child_id uuid references children(id),
    class_id uuid references classes(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_messages_body_length check (char_length(btrim(body)) between 1 and 2000),
    constraint chk_messages_enrollment_context check (message_type <> 'enrollment_rejected' or enrollment_id is not null)
);

create index idx_messages_tenant_enrollment
    on messages (tenant_id, enrollment_id, created_at desc);

create index idx_messages_tenant_recipient
    on messages (tenant_id, recipient_user_id, created_at desc);

create index idx_messages_tenant_child
    on messages (tenant_id, child_id, created_at desc);

create table user_history (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    original_user_id uuid not null,
    deleted_from_tenant_id uuid,
    deleted_by_user_id uuid,
    email varchar(320) not null,
    phone varchar(50) not null,
    password_hash varchar(255),
    first_name varchar(100),
    last_name varchar(100),
    status varchar(50) not null default 'deleted',
    metadata jsonb not null default '{}',
    deleted_reason varchar(255),
    deleted_at timestamptz not null default now(),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_user_history_original_user on user_history(original_user_id);
create index idx_user_history_email on user_history(email);
create index idx_user_history_deleted_at on user_history(deleted_at);

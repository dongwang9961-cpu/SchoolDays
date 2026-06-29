create extension if not exists pgcrypto;

create table tenants (
    id uuid primary key default gen_random_uuid(),
    name varchar(255) not null,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table tenant_invitations (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    school_name varchar(255) not null,
    admin_email varchar(320) not null,
    invited_by_user_id uuid,
    tenant_id uuid references tenants(id),
    status varchar(50) not null default 'pending',
    token_hash varchar(255) not null,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table users (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    email varchar(320) not null,
    phone varchar(50) not null,
    password_hash varchar(255),
    first_name varchar(100),
    last_name varchar(100),
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_users_email unique (email)
);

alter table tenant_invitations
    add constraint fk_tenant_invitations_invited_by
        foreign key (invited_by_user_id) references users(id);

create table user_identities (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    user_id uuid not null references users(id),
    provider varchar(50) not null,
    provider_subject varchar(255) not null,
    email varchar(320) not null,
    email_verified boolean not null default false,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_user_identities_provider_subject unique (provider, provider_subject)
);

create table user_registration_links (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid references tenants(id),
    email varchar(320) not null,
    intended_role varchar(50) not null,
    invitation_type varchar(50) not null,
    related_invitation_id uuid,
    token_hash varchar(255) not null,
    status varchar(50) not null default 'pending',
    expires_at timestamptz not null,
    used_at timestamptz,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table roles (
    id uuid primary key default gen_random_uuid(),
    name varchar(80) not null,
    constraint uq_roles_name unique (name)
);

insert into roles (name)
values ('PLATFORM_ADMIN'), ('SCHOOL_ADMIN'), ('TEACHER'), ('PARENT')
on conflict do nothing;

create table user_roles (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    user_id uuid not null references users(id),
    role_id uuid not null references roles(id),
    tenant_id uuid references tenants(id),
    metadata jsonb not null default '{}',
    constraint uq_user_roles_scope unique (user_id, role_id, tenant_id)
);

create table school_sites (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    name varchar(255) not null,
    timezone varchar(100) not null,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table programs (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    site_id uuid not null references school_sites(id),
    name varchar(255) not null,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table classes (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    program_id uuid not null references programs(id),
    name varchar(255) not null,
    capacity integer,
    start_date date not null,
    end_date date not null,
    registration_opens_at timestamptz,
    registration_closes_at timestamptz,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table class_pricing (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    class_id uuid not null references classes(id),
    pricing_type varchar(50) not null,
    currency char(3) not null default 'USD',
    total_amount integer not null default 0,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_class_pricing_class unique (class_id)
);

create table class_fee_items (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenants(id),
    class_pricing_id uuid not null references class_pricing(id),
    fee_type varchar(50) not null,
    name varchar(255) not null,
    amount integer not null,
    required boolean not null default true,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table class_schedules (
    id uuid primary key default gen_random_uuid(),
    class_id uuid not null references classes(id),
    weekday smallint not null,
    start_time time not null,
    end_time time not null,
    metadata jsonb not null default '{}',
    constraint ck_class_schedules_weekday check (weekday between 1 and 7)
);

create table teacher_assignments (
    id uuid primary key default gen_random_uuid(),
    class_id uuid not null references classes(id),
    teacher_user_id uuid not null references users(id),
    assigned_by_user_id uuid references users(id),
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    constraint uq_teacher_assignments unique (class_id, teacher_user_id)
);

create table children (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    parent_user_id uuid not null references users(id),
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    date_of_birth date,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table enrollments (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    child_id uuid not null references children(id),
    class_id uuid not null references classes(id),
    enrollment_status varchar(50) not null default 'pending',
    created_by_user_id uuid references users(id),
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table enrollment_dates (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    enrollment_id uuid not null references enrollments(id),
    class_date date not null,
    status varchar(50) not null default 'active',
    metadata jsonb not null default '{}',
    constraint uq_enrollment_dates unique (enrollment_id, class_date)
);

create table enrollment_perks (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    enrollment_id uuid not null references enrollments(id),
    class_fee_item_id uuid not null references class_fee_items(id),
    perk_type varchar(50) not null,
    perk_status varchar(50) not null default 'active',
    starts_on date,
    ends_on date,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table payment_transactions (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    enrollment_id uuid references enrollments(id),
    payer_user_id uuid references users(id),
    provider varchar(50) not null,
    provider_payment_id varchar(255),
    payment_source varchar(50) not null,
    payment_method varchar(50),
    amount integer not null,
    currency char(3) not null default 'USD',
    payment_status varchar(50) not null default 'pending',
    paid_at timestamptz,
    recorded_by_user_id uuid references users(id),
    recorded_at timestamptz,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table payment_receipts (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    enrollment_id uuid references enrollments(id),
    uploaded_by_user_id uuid not null references users(id),
    payment_transaction_id uuid references payment_transactions(id),
    payment_method varchar(50) not null,
    amount integer not null,
    currency char(3) not null default 'USD',
    receipt_file_url text not null,
    receipt_status varchar(50) not null default 'pending_review',
    reviewed_by_user_id uuid references users(id),
    reviewed_at timestamptz,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table attendance (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    child_id uuid not null references children(id),
    class_id uuid not null references classes(id),
    class_date date not null,
    checked_in_at timestamptz,
    checked_in_by_user_id uuid references users(id),
    checked_in_by_role varchar(50),
    status varchar(50) not null default 'checked_in',
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_attendance_child_class_date unique (child_id, class_id, class_date)
);

create table teacher_invitations (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    email varchar(320) not null,
    invited_by_user_id uuid references users(id),
    teacher_user_id uuid references users(id),
    status varchar(50) not null default 'pending',
    token_hash varchar(255) not null,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table notification_providers (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    provider_type varchar(50) not null,
    status varchar(50) not null default 'active',
    from_email varchar(320),
    from_name varchar(255),
    created_by_user_id uuid references users(id),
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table email_notification_history (
    id uuid primary key default gen_random_uuid(),
    seq_id bigserial not null unique,
    tenant_id uuid not null references tenants(id),
    sender_user_id uuid references users(id),
    provider_id uuid references notification_providers(id),
    audience_type varchar(50) not null,
    source_type varchar(50) not null,
    cc_emails jsonb not null default '[]',
    bcc_recipient_count integer not null default 0,
    subject_snapshot varchar(500),
    body_blob_url text,
    body_blob_mime_type varchar(255),
    external_reference varchar(255),
    status varchar(50) not null default 'pending',
    sent_at timestamptz,
    metadata jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_user_roles_user on user_roles(user_id);
create index idx_user_roles_tenant_seq on user_roles(tenant_id, seq_id);
create index idx_school_sites_tenant on school_sites(tenant_id);
create index idx_school_sites_tenant_seq on school_sites(tenant_id, seq_id);
create index idx_programs_tenant_site on programs(tenant_id, site_id);
create index idx_programs_tenant_seq on programs(tenant_id, seq_id);
create index idx_classes_tenant_program on classes(tenant_id, program_id);
create index idx_classes_tenant_seq on classes(tenant_id, seq_id);
create index idx_children_tenant_parent on children(tenant_id, parent_user_id);
create index idx_children_tenant_seq on children(tenant_id, seq_id);
create index idx_enrollments_tenant_child on enrollments(tenant_id, child_id);
create index idx_enrollments_tenant_seq on enrollments(tenant_id, seq_id);
create index idx_enrollment_dates_class_date on enrollment_dates(class_date);
create index idx_enrollment_dates_seq on enrollment_dates(seq_id);
create index idx_enrollment_perks_tenant_seq on enrollment_perks(tenant_id, seq_id);
create index idx_payment_transactions_tenant on payment_transactions(tenant_id);
create index idx_payment_transactions_tenant_seq on payment_transactions(tenant_id, seq_id);
create index idx_payment_receipts_tenant_seq on payment_receipts(tenant_id, seq_id);
create index idx_attendance_tenant_class_date on attendance(tenant_id, class_id, class_date);
create index idx_attendance_tenant_seq on attendance(tenant_id, seq_id);
create index idx_tenant_invitations_seq on tenant_invitations(seq_id);
create index idx_users_seq on users(seq_id);
create index idx_user_identities_seq on user_identities(seq_id);
create index idx_user_registration_links_tenant_seq on user_registration_links(tenant_id, seq_id);
create index idx_teacher_invitations_tenant_seq on teacher_invitations(tenant_id, seq_id);
create index idx_notification_providers_tenant_seq on notification_providers(tenant_id, seq_id);
create index idx_email_notification_history_tenant_seq on email_notification_history(tenant_id, seq_id);

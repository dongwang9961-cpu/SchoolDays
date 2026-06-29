package com.schooldays.config;

import org.jooq.DSLContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestDataSeedConfig {

    @Bean
    @ConditionalOnProperty(prefix = "schooldays.seed-test-data", name = "enabled", havingValue = "true")
    ApplicationRunner seedTestData(DSLContext dsl) {
        return new TestDataSeeder(dsl);
    }

    private static class TestDataSeeder implements ApplicationRunner {

        private final DSLContext dsl;

        private TestDataSeeder(DSLContext dsl) {
            this.dsl = dsl;
        }

        @Override
        public void run(ApplicationArguments args) {
            dsl.execute("""
                    do $$
                    begin
                        if exists (
                            select 1
                            from tenants
                            where lower(slug) in ('abc', 'longlong-art-studio')
                               or id = '11111111-1111-1111-1111-111111111111'::uuid
                        ) then
                            update tenants
                            set name = 'Longlong Art Studio',
                                status = 'active',
                                slug = 'longlong-art-studio',
                                metadata = metadata || jsonb_build_object(
                                    'seed', true,
                                    'website_path', '/school/longlong-art-studio',
                                    'settings', coalesce(metadata->'settings', '{}'::jsonb) || jsonb_build_object('max_sites', 5)
                                ),
                                updated_at = now()
                            where lower(slug) in ('abc', 'longlong-art-studio')
                               or id = '11111111-1111-1111-1111-111111111111'::uuid;
                        else
                            insert into tenants (id, name, status, slug, metadata)
                            values (
                                '11111111-1111-1111-1111-111111111111',
                                'Longlong Art Studio',
                                'active',
                                'longlong-art-studio',
                                jsonb_build_object(
                                    'seed', true,
                                    'website_path', '/school/longlong-art-studio',
                                    'settings', jsonb_build_object('max_sites', 5)
                                )
                            );
                        end if;

                        insert into users (id, email, phone, password_hash, first_name, last_name, status, metadata)
                        values (
                            '22222222-2222-2222-2222-222222222222',
                            'admin@schooldays.test',
                            '555-0001',
                            crypt('SchoolDays123!', gen_salt('bf', 10)),
                            'Platform',
                            'Admin',
                            'active',
                            jsonb_build_object('seed', true)
                        )
                        on conflict (email) do update
                        set password_hash = excluded.password_hash,
                            phone = excluded.phone,
                            first_name = excluded.first_name,
                            last_name = excluded.last_name,
                            status = excluded.status,
                            metadata = users.metadata || excluded.metadata,
                            updated_at = now();

                        update users
                        set email = 'school.admin@longlong-art-studio.test',
                            phone = '555-0002',
                            first_name = 'Longlong',
                            last_name = 'Admin',
                            status = 'active',
                            metadata = metadata || jsonb_build_object('seed', true),
                            updated_at = now()
                        where id = '33333333-3333-3333-3333-333333333333'::uuid
                           or email in ('school.admin@abc.test', 'school.admin@longlong-art-studio.test');

                        insert into users (id, email, phone, password_hash, first_name, last_name, status, metadata)
                        values (
                            '33333333-3333-3333-3333-333333333333',
                            'school.admin@longlong-art-studio.test',
                            '555-0002',
                            crypt('SchoolDays123!', gen_salt('bf', 10)),
                            'Longlong',
                            'Admin',
                            'active',
                            jsonb_build_object('seed', true)
                        )
                        on conflict (email) do update
                        set password_hash = excluded.password_hash,
                            phone = excluded.phone,
                            first_name = excluded.first_name,
                            last_name = excluded.last_name,
                            status = excluded.status,
                            metadata = users.metadata || excluded.metadata,
                            updated_at = now();

                        insert into user_roles (user_id, role_id, tenant_id, metadata)
                        select users.id, roles.id, null, jsonb_build_object('seed', true)
                        from users
                        join roles on roles.name = 'PLATFORM_ADMIN'
                        where users.email = 'admin@schooldays.test'
                          and not exists (
                              select 1
                              from user_roles existing
                              where existing.user_id = users.id
                                and existing.role_id = roles.id
                                and existing.tenant_id is null
                          );

                        insert into user_roles (user_id, role_id, tenant_id, metadata)
                        select users.id, roles.id, tenants.id, jsonb_build_object('seed', true)
                        from users
                        join roles on roles.name = 'SCHOOL_ADMIN'
                        join tenants on lower(tenants.slug) = 'longlong-art-studio'
                        where users.email = 'school.admin@longlong-art-studio.test'
                        on conflict (user_id, role_id, tenant_id) do update
                        set metadata = user_roles.metadata || excluded.metadata;
                    end $$;
                    """);
        }
    }
}

-- Manual downgrade for V3__seed_test_data.sql and the optional startup seeder.
-- Removes only rows explicitly marked with metadata.seed = true.

delete from user_roles
where metadata ->> 'seed' = 'true'
  and (
      user_id in (
          select id
          from users
          where email in (
              'admin@schooldays.test',
              'school.admin@longlong-art-studio.test',
              'school.admin@abc.test'
          )
      )
      or tenant_id in (
          select id
          from tenants
          where lower(slug) in ('longlong-art-studio', 'abc')
             or id = '11111111-1111-1111-1111-111111111111'::uuid
      )
  );

delete from users
where metadata ->> 'seed' = 'true'
  and (
      email in (
          'admin@schooldays.test',
          'school.admin@longlong-art-studio.test',
          'school.admin@abc.test'
      )
      or id in (
          '22222222-2222-2222-2222-222222222222'::uuid,
          '33333333-3333-3333-3333-333333333333'::uuid
      )
  );

delete from tenants
where metadata ->> 'seed' = 'true'
  and (
      lower(slug) in ('longlong-art-studio', 'abc')
      or id = '11111111-1111-1111-1111-111111111111'::uuid
  );

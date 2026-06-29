update users
set phone = '555-0000',
    updated_at = now()
where phone is null
   or btrim(phone) = '';

alter table users
    alter column phone set not null;

-- drop table if exists account_active_periods;
-- drop table if exists account_active_periods_temp;
-- drop table if exists transaction_items;
-- drop table if exists timestamps;
-- drop table if exists transactions;
-- drop table if exists draft_accounts;
-- drop table if exists checking_accounts;
-- drop table if exists charge_accounts;
-- drop table if exists real_accounts;
-- drop table if exists category_accounts;
-- drop table if exists accounts;
-- drop table if exists staged_accounts;
-- drop table if exists staged_draft_accounts;
-- drop table if exists staged_real_accounts;
-- drop table if exists staged_category_accounts;
-- drop table if exists access_details;
-- drop table if exists budget_access;
-- drop type if exists coarse_access;
-- drop type if exists fine_access;
-- drop table if exists budgets;
-- drop table if exists users;

create table if not exists users
(
    id    uuid         not null primary key,
--     password  ...        null,
    login varchar(110) not null unique
);

create table if not exists budgets
(
    -- there will be an account, a, with this a.budget_id=id, a.type='category', and a.name=general_account_name
    id                   uuid        not null primary key,
    general_account_name varchar(50) not null default 'General'
);

create table if not exists budget_access
(
    id              uuid         not null primary key,
    user_id         uuid         not null references users (id),
    budget_id       uuid         not null references budgets (id),
    budget_name     varchar(110) not null,
    time_zone       varchar(110) not null,
    analytics_start timestamp    not null default now(),
    -- if null, check fine_access
    coarse_access   varchar,
    unique (user_id, budget_id),
    unique (user_id, budget_name)
);

create index if not exists lookup_budget_access_by_user
    on budget_access (user_id);

create table if not exists accounts
(
    id                   uuid           not null unique,
    name                 varchar(50)    not null,
    type                 varchar(20)    not null,
    description          varchar(110)   not null default '',
    balance              numeric(30, 2) not null default 0.0,
    companion_account_id uuid           null references accounts (id),
    budget_id            uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, type, budget_id),
    unique (companion_account_id, budget_id)
);

create index if not exists accounts_by_type
    on accounts (budget_id, type);

create table if not exists account_active_periods
(
    id             uuid      not null unique,
    start_date_utc timestamp not null default '0001-01-01T00:00:00Z',
    end_date_utc   timestamp not null default '9999-12-31T23:59:59.999Z',
    account_id     uuid      not null references accounts (id),
    budget_id      uuid      not null references budgets (id),
    unique (start_date_utc, account_id, budget_id)
);

create index if not exists lookup_account_active_periods_by_account_id
    on account_active_periods (account_id, budget_id);

create table if not exists transactions
(
    id                        uuid         not null unique,
    description               varchar(110) not null default '',
    timestamp_utc             timestamp    not null default now(),
    -- 'expense', 'transfer', 'allowance', 'income', 'clearing', 'initial', 'reimburse',
    -- 'clearing' transaction transfer from real to charge or draft accounts
    type                      varchar(20)  not null,
    -- the transaction that clears this transaction
    cleared_by_transaction_id uuid         null,
    budget_id                 uuid         not null references budgets (id),
    primary key (id, budget_id)
);

create index if not exists lookup_transaction_by_date
    on transactions (timestamp_utc desc, budget_id);

create index if not exists lookup_transaction_by_type_and_date
    on transactions (timestamp_utc desc, type, budget_id);

create table if not exists transaction_items
(
    id             uuid           not null unique,
    transaction_id uuid           not null references transactions (id),
    description    varchar(110)   null,
    amount         numeric(30, 2) not null,
    account_id     uuid           not null references accounts (id),
    -- 'none' 'outstanding' 'cleared' 'clearing'
    -- 'outstanding' and 'cleared' are expenditures on category accounts
    -- 'clearing' transfer from real to charge or draft accounts
    draft_status   varchar        not null default 'none',
    budget_id      uuid           not null references budgets (id)
);

create index if not exists lookup_account_transaction_items_by_account
    on transaction_items (account_id, budget_id);

insert into users (id, login)
VALUES ('d9073256-0c5e-472e-bf3b-41ed1c1e5c35', 'fake@fake.com');

-- create budget
insert into budgets (id, general_account_name)
values ('ccac6f53-04f3-4da5-a2ea-de39c6843e47', 'General');

insert into budget_access (id, user_id, budget_id, budget_name, time_zone, coarse_access)
VALUES ('a11a93df-6110-430e-ba76-08c1da364530', 'd9073256-0c5e-472e-bf3b-41ed1c1e5c35',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47', 'scratch budget', 'America/Chicago', 'admin');

-- select *
-- from users u
--          join budget_access ba on u.id = ba.user_id
-- where 1 = 1;

-- create accounts
insert into accounts (id, name, type, budget_id)
VALUES ('1d5221d6-acb3-4377-8fa1-bc3289fa75ca', 'General', 'category', 'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into account_active_periods (id, account_id, budget_id)
values ('f9b5abad-7eba-4dbc-8d46-7339fbb51a0b', '1d5221d6-acb3-4377-8fa1-bc3289fa75ca',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into accounts (id, name, type, budget_id)
VALUES ('bea7965c-6f6a-4c80-ad3f-b9c1367664c0', 'Food', 'category', 'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into account_active_periods (id, account_id, budget_id)
values ('d3d04d1f-a6cf-43b9-bf9e-eea0c5068a90', 'bea7965c-6f6a-4c80-ad3f-b9c1367664c0',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into accounts (id, name, type, budget_id)
VALUES ('0aa01c8e-9944-45de-866b-388261010045', 'Necessities', 'category', 'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into account_active_periods (id, account_id, budget_id)
values ('a2ef365b-d27b-4521-ad77-1eaf1d54c2f1', '0aa01c8e-9944-45de-866b-388261010045',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into accounts (id, name, type, budget_id)
VALUES ('76ea4bb9-ad3e-4aff-832a-3d3b907713ba', 'Wallet', 'real', 'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into account_active_periods (id, account_id, budget_id)
values ('ad697e12-9e4f-44d3-b02e-673e488c9234', '76ea4bb9-ad3e-4aff-832a-3d3b907713ba',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into accounts (id, name, type, budget_id)
VALUES ('5bf25e8c-275d-40a8-a43f-0967697cf87c', 'Checking', 'real', 'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
insert into account_active_periods (id, account_id, budget_id)
values ('fa72ccf5-077e-474c-a93e-113aa88ba1fe', '5bf25e8c-275d-40a8-a43f-0967697cf87c',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');
-- insert into draft_accounts (id, name, description, balance, real_account_id, budget_id)
-- VALUES ('58d6e527-2a20-49c2-991b-70bb0aa6b935', 'Checking Drafts', 'Checking Drafts', 0.0,
--         '5bf25e8c-275d-40a8-a43f-0967697cf87c',
--         'ccac6f53-04f3-4da5-a2ea-de39c6843e47');

-- insert into account_active_periods (charge_account_id, draft_account_id, real_account_id, category_account_id,
--                                     budget_id)
-- values (null, null, null, '1d5221d6-acb3-4377-8fa1-bc3289fa75ca', 'ccac6f53-04f3-4da5-a2ea-de39c6843e47');

select t.*,
       i.amount      as item_amount,
       i.description as item_description,
       i.account_id
from transactions t
         join transaction_items i
              on i.transaction_id = t.id
                  and i.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
         join accounts acc
              on i.account_id = acc.id
                  and acc.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
                  and acc.type = 'category'
where t.id in (select id
               from transactions
--                where cleared_by_transaction_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
--                  and budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
);

select *
from accounts acc
         join account_active_periods aap
              on acc.id = aap.account_id
                  and acc.budget_id = aap.budget_id
where acc.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
  and acc.type = 'category';

select *
from accounts acc
         join account_active_periods aap
              on acc.id = aap.account_id
                  and acc.budget_id = aap.budget_id
where acc.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
  and acc.type = 'category'
  and now() > aap.start_date_utc
  and now() < aap.end_date_utc;

update account_active_periods
set end_date_utc = now()
where account_id = '0aa01c8e-9944-45de-866b-388261010045'
  and budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47';

update account_active_periods
set end_date_utc = now()
-- from -- account_active_periods aap,
--      category_accounts acc
where budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
  and account_id not in
      (select id
       from accounts
       where budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
         and type = 'category'
         and (id = '1d5221d6-acb3-4377-8fa1-bc3289fa75ca'
           or id = 'bea7965c-6f6a-4c80-ad3f-b9c1367664c0'))
  and exists
    (select 1
     from accounts acc
     --               join account_active_periods aap
--                    on acc.id = aap.category_account_id
--                        and acc.budget_id = aap.budget_id
     where acc.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
       and acc.type = 'category'
       and acc.id = account_id
       and acc.budget_id = budget_id
       and now() > start_date_utc
       and now() < end_date_utc)
;

insert into account_active_periods (id, start_date_utc, account_id, budget_id)
values ('94c7bee3-80d6-4c8f-ba55-28b8f98dac7a', now(), '0aa01c8e-9944-45de-866b-388261010045',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');

-- income 100 to wallet
insert into transactions (id, description, timestamp_utc, budget_id)
VALUES ('773e5d24-5af1-4719-aa15-34fc1288100f', 'income', '2024-07-21T12:03:34Z',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');

-- income 1000 to checking

-- allocation
insert into transactions (id, description, timestamp_utc, budget_id)
VALUES ('7db502f8-d633-4dd4-a890-f8c1257ede79', 'allocate', '2024-07-21T12:04:34Z',
        'ccac6f53-04f3-4da5-a2ea-de39c6843e47');

select *
from transaction_items as item
         join transactions as trans on item.transaction_id = trans.id;

-- invariants:
--   for each transaction:
--      sum of category and draft amounts == sum of real amounts

select *
from transaction_items;
select *
from transactions
where id = 'd94137d7-d771-4d66-95cb-fb21ba90c964';

select t.id            as transaction_id,
       t.description   as transaction_description,
       t.timestamp_utc as transaction_timestamp,
       i.amount,
       t.type,
       i.description,
       i.account_id,
       i.draft_status
from transactions t
         join transaction_items i
              on i.transaction_id = t.id
                  and t.budget_id = i.budget_id
-- where t.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
--   and i.account_id = '1d5221d6-acb3-4377-8fa1-bc3289fa75ca'
order by t.timestamp_utc desc, t.id
limit 30
;

select -- t.id            as transaction_id,
       t.description   as transaction_description,
       t.timestamp_utc as transaction_timestamp,
       i.amount,
       t.type,
       i.description,
       i.draft_status,
       a.name,
       a.type          as account_type
--        i.id
from transactions t
         join transaction_items i
              on i.transaction_id = t.id
                  and t.budget_id = i.budget_id
         join accounts a
              on a.id = i.account_id
where a.type = 'real'
  and a.name = 'Hongyi''s Wallet'
--     and i.account_id = '1d5221d6-acb3-4377-8fa1-bc3289fa75ca'
order by t.timestamp_utc desc, t.id
-- limit 30
;

select t.description   as transaction_description,
       t.timestamp_utc as transaction_timestamp,
       i.amount,
--     t.type,
       i.description,
--        i.account_id,
       i.draft_status,
       a.name,
       i.id
from transaction_items i
         join transactions t
              on i.transaction_id = t.id
                  and i.budget_id = t.budget_id
         join accounts a
              on a.id = i.account_id
where t.type = 'income'
  and i.amount > 0
  and a.type = 'real'
--   and t.timestamp_utc >= ?
--   ${if (options.endDateLimited) "and t.timestamp_utc < ?" else ""}
--   and ti.budget_id = ?
order by t.timestamp_utc asc
;

select t.*,
       i.amount      as item_amount,
       i.description as item_description,
       i.category_account_id,
       i.draft_account_id,
       i.real_account_id,
       i.charge_account_id,
       i.draft_status
from transactions t
         join transaction_items i on i.transaction_id = t.id
where t.id in (select id
               from transactions
                        join transaction_items ti
                             on transactions.id = ti.transaction_id
                                 and ti.budget_id = '89bc165a-ee70-43a4-b637-2774bcfc3ea4'
                                 and transactions.budget_id = '89bc165a-ee70-43a4-b637-2774bcfc3ea4'
               where ti.category_account_id = '1d5221d6-acb3-4377-8fa1-bc3289fa75ca'
               order by transactions.timestamp_utc desc
               limit 30)
;

select t.*,
       i.amount      as item_amount,
       i.description as item_description,
       i.category_account_id
from transactions t
         join transaction_items i on i.transaction_id = t.id
where t.id in (select id
               from transactions
               where cleared_by_transaction_id = 'd94137d7-d771-4d66-95cb-fb21ba90c964')
  and i.category_account_id is not null
;

-- view transaction by id
select t.*,
       i.amount      as item_amount,
       i.description as item_description,
       i.category_account_id,
       i.draft_account_id,
       i.real_account_id,
       i.charge_account_id,
       i.draft_status
from transactions t
         join transaction_items i on i.transaction_id = t.id
where t.id = 'd94137d7-d771-4d66-95cb-fb21ba90c964'
;

select id
from transactions
where cleared_by_transaction_id = 'd94137d7-d771-4d66-95cb-fb21ba90c964';

select *
from draft_accounts
where id = '4771e455-02b5-4b1e-98c4-16819d2ab3ad';

select tr.id as transaction_id, tr.timestamp_utc
from transactions tr
         join transaction_items ti
              on tr.id = ti.transaction_id
                  and ti.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
                  and tr.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
where ti.category_account_id = ('1d5221d6-acb3-4377-8fa1-bc3289fa75ca'::uuid)
order by tr.timestamp_utc desc
limit ('30'::int4) offset ('0'::int4);

select t.timestamp_utc as timestamp_utc,
       t.description   as description,
--        ba.budget_name  as budget_name,
--        u.login         as login,
       i.amount        as item_amount,
       i.description   as item_description,
       i.category_account_id,
--        i.draft_account_id,
       i.charge_account_id,
       i.real_account_id
from transactions t
         join transaction_items i on i.transaction_id = t.id
--          join budgets b on t.budget_id = b.id
--          join budget_access ba on b.id = ba.budget_id
--          join users u on ba.user_id = u.id
where t.budget_id = 'ccac6f53-04f3-4da5-a2ea-de39c6843e47'
  and t.id in (select tr.id
               from transactions tr
                        join transaction_items ti
                             on tr.id = ti.transaction_id
               where ti.category_account_id = ('1d5221d6-acb3-4377-8fa1-bc3289fa75ca'::uuid)
               order by tr.timestamp_utc desc
               limit ('30'::int4) offset ('0'::int4))
;

select ti.id,
       ti.description,
       ti.amount,
       ti.draft_status,
       ca.name,
       t.description as transaction_descirption,
       t.timestamp_utc
from transaction_items ti
         join charge_accounts ca on ti.charge_account_id = ca.id and ti.budget_id = ca.budget_id
         join transactions t on t.id = ti.transaction_id and t.budget_id = ti.budget_id
where ti.budget_id = '91eca65d-7c6d-46dd-b3a2-1eb992b4bf83'
  and charge_account_id is not null;

update transaction_items
set draft_status = 'outstanding'
where draft_status = 'none';

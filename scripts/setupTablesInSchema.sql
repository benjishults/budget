create table if not exists users
(
    id    uuid         not null primary key,
--     password  ...        null,
    login varchar(110) not null unique
);

create table if not exists budgets
(
    id                 uuid not null primary key,
    general_account_id uuid not null unique
);

create table if not exists budget_access
(
    id            uuid         not null primary key,
    user_id       uuid         not null references users (id),
    budget_id     uuid         not null references budgets (id),
    budget_name   varchar(110) not null,
    time_zone     varchar(110) not null,
    -- if null, check fine_access
    coarse_access varchar,
    unique (user_id, budget_id)
);

create index if not exists lookup_budget_access_by_user
    on budget_access (user_id);

create table if not exists access_details
(
    budget_access_id uuid    not null references budget_access (id),
    fine_access      varchar not null
);

create index if not exists lookup_access_details_by_budget_access
    on access_details (budget_access_id);

create table if not exists category_accounts
(
    id          uuid           not null unique,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_id   uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, budget_id)
);

create table if not exists real_accounts
(
    id          uuid           not null unique,
    name        varchar(50)    not null,
    description varchar(110)   not null default '',
    balance     numeric(30, 2) not null default 0.0,
    budget_id   uuid           not null references budgets (id),
    primary key (id, budget_id),
    unique (name, budget_id)
);

create table if not exists transactions
(
    id                        uuid         not null unique,
    description               varchar(110) not null default '',
    timestamp_utc             timestamp    not null default now(),
    -- the transaction that clears this transaction
    cleared_by_transaction_id uuid         null,
    budget_id                 uuid         not null references budgets (id),
    primary key (id, budget_id)
);

create index if not exists lookup_transaction_by_date
    on transactions
        (timestamp_utc desc,
         budget_id);

create table if not exists transaction_items
(
    transaction_id      uuid           not null references transactions (id),
    description         varchar(110)   null,
    amount              numeric(30, 2) not null,
    category_account_id uuid           null references category_accounts (id),
    real_account_id     uuid           null references real_accounts (id),
    draft_status        varchar        not null default 'none', -- 'none' 'outstanding' 'cleared'
    budget_id           uuid           not null references budgets (id),
    constraint only_one_account_per_transaction_item check
        ((real_account_id is not null and category_account_id is null) or
         (category_account_id is not null and real_account_id is null))
);

create index if not exists lookup_category_account_transaction_items_by_account
    on transaction_items
        (category_account_id,
         budget_id)
    where category_account_id is not null;

create index if not exists lookup_real_account_transaction_items_by_account
    on transaction_items
        (real_account_id,
         budget_id)
    where real_account_id is not null;

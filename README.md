# BPS Budget

## One-Time Setup

### Set up the env for DB

Set the `BPS_BUDGET_POSTGRES_DATA_DIR` environment variable to something like `~/data/bps-budget/postgres`
and make sure that folder exists.

You'll need to have pulled the postgres image from some container registry:

```shell
podman pull docker.io/postgres:latest
```

### Start Postgres

Run the script to run Postgres in an OSI container.  (I use podman with docker as an alias to podman but docker
works fine, too, if you prefer.)

```shell
% ./scripts/startDb.sh
```

### Create DB and users

```
% psql -U admin -h localhost -f /home/benji/repos/benjishults/budget/scripts/setupDbAsAdmin.sql
Password for user admin:
CREATE DATABASE
CREATE ROLE
GRANT
GRANT
ALTER DATABASE
CREATE ROLE
% psql -U admin -h localhost -f /home/benji/repos/benjishults/budget/scripts/setupBudgetSchemasAsAdmin.sql
Password for user admin:
CREATE SCHEMA
CREATE SCHEMA
CREATE SCHEMA
CREATE SCHEMA
```

### Build the Application

```shell
./gradlew shadowJar
```

### Prepare to run for the first time

Create a file named `budget.yml` in your `~/.config/bps-budget` folder.

It should something like this:

```yaml
persistence:
    type: JDBC
    jdbc:
        budgetName: Budget # give your budget a custom name if you want
        dbProvider: postgresql
        port: 5432
        host: localhost # if your DB is running on a different machine, change this to its domain or IP
        schema: budget
        user: budget
        password: budget

budgetUser:
    defaultLogin: fake@fake.com # your email
    defaultTimeZone: America/New_York # your time zone
```

## Run the application

Make sure the DB is running. If it isn't running then start it with:

```shell
% ./scripts/startDb.sh
```

Once the DB is running, start the budget application with:

```shell
% ./scripts/budget.sh
```

## Troubleshooting

To connect to the Postgres DB running in the docker container, do

```shell
psql -U budget -h 127.0.0.1 -d budget
```

Data migrations can be run using `bps.budget.persistence.migration.DataMigrations`.

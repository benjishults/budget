# BPS Budget

## One-Time Setup

### Set up the env for DB

Set the `BPS_BUDGET_POSTGRES_DATA_DIR` environment variable to something like `~/data/bps-budget/postgres`
and make sure that folder exists.

You'll need to have pulled the postgres image from some container registry:

```shell
docker pull docker.io/postgres:latest
```

#### Quick aside on podman

If you use `podman` instead of
`docker`, [check this out](https://incredible-mountain-4be.notion.site/A-shell-hack-for-aliasing-docker-to-podman-13b777637b9d804d8fe0fe7d51b73926).

### Start Postgres

Run the script to run Postgres in an OSI container.  (I use podman with docker as an alias to podman but docker
works fine, too, if you prefer.)

```shell
./scripts/startDb.sh
```

### Create DB and users

This will create databases, users, and schemas for both production and testing.

```
% psql -U admin -h localhost -f ./scripts/setupDbAsAdmin.sql
Password for user admin:
CREATE DATABASE
CREATE ROLE
GRANT
GRANT
ALTER DATABASE
CREATE ROLE
% psql -U admin -d budget -h localhost -f ./scripts/setupBudgetSchemasAsAdmin.sql
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

## Run the Application

Currently, this isn't set up to be running in an open environment. The security on the DB is minimal to non-existent.

If you're just running this on your personal machine, and you have some reasonable router connecting you to the
internet (or no connection at all), you should be fine.

Make sure the DB is running. If it isn't running then start it with:

```shell
./scripts/startDb.sh
```

Once the DB is running, start the budget application with:

```shell
./scripts/budget.sh
```

## Run Tests

Make sure the DB is running. If it isn't running then start it with:

```shell
./scripts/startDb.sh
```

Run tests with:

```shell
./gradlew test
```

## Troubleshooting

To connect to the Postgres DB running in the docker container, do

```shell
psql -U budget -h 127.0.0.1 -d budget
```

Data migrations can be run using `bps.budget.persistence.migration.DataMigrations`.

## CI Docker Image Background

See [Dockerfile](ci/Dockerfile).

Everything you need to know should be in
the [GitHub action that builds and publishes the image](.github/workflows/publish-test-db-container.yml) and
the [GitHub action that runs tests](.github/workflows/test.yml).

To test manually, create the image:

```shell
cd ci
docker build -t pg-test .
```

Test run it with this

```shell
docker run -e POSTGRES_PASSWORD=test -e POSTGRES_USER=test -e POSTGRES_DB=budget --rm --name pg-test -p 5432:5432 -d pg-test:latest
```

and connect to it to ensure that the `test:test` user has access to two schemas: `test` and `clean_after_test`.

You can look at logs with

```shell
docker logs pg-test
```

The image is published by the [publish-test-db-container.yml](.github/workflows/publish-test-db-container.yml) action.

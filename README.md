# BPS Budget

## Build

```shell
./gradlew shadowJar
```

## Set up to run

Needs work to productize but here's how it works as of now.

Set the `BPS_BUDGET_POSTGRES_DATA_DIR` environment variable to something like `~/data/bps-budget/postgres`
and make sure that folder exists.

Then run with:

```shell
docker run -e POSTGRES_PASSWORD=admin -e POSTGRES_USER=admin -p 5432:5432 -v "$BPS_BUDGET_POSTGRES_DATA_DIR":
/var/lib/postgresql/data --rm --name postgres -d postgres
java -cp build/libs/budget-1.0-SNAPSHOT-all.jar bps.budget.Budget
```

To customize, create a file named `budget.yml` in your `~/.config/bps-budget` folder.

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

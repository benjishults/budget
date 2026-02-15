# BPS Budget

## One-Time Setup

### PostgreSQL Setup

See [another README](../budgetDao/README.md) for details.

### Build the Application

Building the application involves pulling dependencies from GitHub Packages. So, you'll need GitHub credentials.

The gradle properties `github.actor` (username) and `github.token` (a token with `packages:read` permissions)
need to be set in a file named `gradle.properties` in your `GRADLE_USER_HOME` folder (defaults to `~/.gradle/`).

```properties
github.actor=<your github login>
github.token=<token with packages:read>
```

Alternatively, you can set the environment variables `GITHUB_ACTOR` and `GITHUB_TOKEN`. Using env variables is
nice for the CI while using properties is nice for your IDE.

Then build the application with

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
        dbProvider: postgresql
        port: 5432
        host: localhost # if your DB is running on a different machine, change this to its domain or IP
        schema: budget
        user: budget
        password: budget
budget:
    name: Budget # give your budget a custom name if you want
budgetUser:
    defaultLogin: fake@fake.com # your email
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

## Backup and Restore Data

Use the `backup.sh` and `restore.sh` scripts to back up and restore your budget data.

## Contributing

### Running Tests

Make sure the DB is running. If it isn't running then start it with:

```shell
./scripts/startDb.sh
```

Run tests with:

```shell
./gradlew test
```

Again, be sure you have the gradle properties `github.actor` (username) and `github.token` (a token with `packages:read`
permissions) need to be set in a file named `gradle.properties` in your `GRADLE_USER_HOME` folder (defaults to
`~/.gradle/`).

```properties
github.actor=<your github login>
github.token=<token with packages:read>
```

## Troubleshooting

To connect to the Postgres DB running in the docker container, do

```shell
psql -U budget -h 127.0.0.1 -d budget
```

When I need to do a data migration, I whip that up here: `bps.budget.persistence.migration.DataMigrations`.

Application logs should be stored in `${user.home}/.local/share/bps-budget/logs`.

## CI Docker Image

See [another README](../ci/postgresql/README.md).

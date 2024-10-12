# set the BPS_BUDGET_POSTGRES_DATA_DIR to something like ~/data/bps-budget/postgres and make sure that folder exists
# podman run  -e POSTGRES_PASSWORD=admin -e POSTGRES_USER=admin -p 5432:5432 -v "$BPS_BUDGET_POSTGRES_DATA_DIR":/var/lib/postgresql/data --rm --name postgres -d postgres
java -cp ~/repos/benjishults/budget/build/libs/budget-1.0-SNAPSHOT-all.jar bps.budget.Budget

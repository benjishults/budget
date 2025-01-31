# set the BPS_BUDGET_DATA_DIR to something like ~/data/bps-budget and make sure that folder exists
# you'll need to have pulled postgres from an image registry
docker run -e POSTGRES_PASSWORD=admin -e POSTGRES_USER=admin -p 5432:5432 -v "$BPS_BUDGET_DATA_DIR"/postgres:/var/lib/postgresql/data --rm --name postgres -d postgres:17

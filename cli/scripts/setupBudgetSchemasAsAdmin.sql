-- connect to the budget DB as admin
CREATE SCHEMA if not exists budget authorization budget;
CREATE SCHEMA if not exists scratch authorization budget;
-- should have no tables when a test isn't running
CREATE SCHEMA if not exists clean_after_test authorization test;
-- should have empty tables when a test isn't running
CREATE SCHEMA if not exists test authorization test;

create database budget;
create user budget password 'budget';
GRANT ALL PRIVILEGES ON DATABASE budget TO budget;
GRANT USAGE ON SCHEMA public TO budget;
alter database budget owner to budget;
create user test password 'test';

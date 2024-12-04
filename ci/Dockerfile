FROM postgres:16

ENV PGPASSWORD=admin

COPY init.sql /docker-entrypoint-initdb.d/

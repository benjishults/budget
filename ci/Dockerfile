FROM postgres:16
LABEL authors="benji"

ENV PGPASSWORD=admin
ENV POSTGRES_PASSWORD=admin
ENV POSTGRES_USER=admin

COPY 10setupDbAsAdmin.sql 20setupBudgetSchemasAsAdmin.sql /docker-entrypoint-initdb.d/

#EXPOSE 5432

#VOLUME ["/var/lib/postgresql/data"]

#CMD ["/usr/lib/postgresql/16/bin/postgres", "-D", "/var/lib/postgresql/16/main", "-c", "config_file=/etc/postgresql/16/main/postgresql.conf"]
#ENTRYPOINT ["docker-entrypoint.sh"]
#CMD ["postgres"]

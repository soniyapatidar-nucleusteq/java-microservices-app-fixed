# PgBouncer: the shop's single connection to Postgres, shared by every service.
FROM alpine:3.20
RUN apk add --no-cache pgbouncer && adduser -D -H -s /sbin/nologin pgbouncer
COPY pgbouncer.ini userlist.txt /etc/pgbouncer/
RUN chown -R pgbouncer:pgbouncer /etc/pgbouncer
USER pgbouncer
EXPOSE 6432
CMD ["pgbouncer", "/etc/pgbouncer/pgbouncer.ini"]

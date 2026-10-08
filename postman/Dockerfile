# Newman with the collection baked in (no bind mounts): docker compose run --rm --build newman
FROM postman/newman:6-alpine
WORKDIR /etc/newman
COPY url-shortener.postman_collection.json local.postman_environment.json ./
# baseUrl points at the app on the compose network; local.postman_environment.json keeps
# http://localhost:8080 for Postman desktop users.
CMD ["run", "url-shortener.postman_collection.json", "-e", "local.postman_environment.json", "--env-var", "baseUrl=http://app:8080"]

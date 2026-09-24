# Developer entry points. `make help` lists them.
SHELL := /bin/bash
JAVA_HOME ?= $(shell /usr/libexec/java_home -F -v 21 2>/dev/null || echo $(HOME)/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home)
export JAVA_HOME
# Testcontainers on Colima: point it at the active Docker context's socket.
export DOCKER_HOST ?= $(shell docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null)
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE ?= /var/run/docker.sock
-include .env
export

.PHONY: help up down nuke run test verify fmt seed
help:       ## show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'
up:         ## start PostgreSQL + Mailpit (starts Colima first if needed)
	@colima status >/dev/null 2>&1 || colima start
	docker compose up -d --wait
down:       ## stop the containers, keep the data volume
	docker compose down
nuke:       ## stop the containers and delete the data volume
	docker compose down -v
run:        ## run the API with the dev profile (http://localhost:8080)
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
test:       ## fast unit + slice tests
	./mvnw -B test
verify:     ## everything CI runs: tests, coverage gate, format check
	./mvnw -B verify
fmt:        ## format the code (Spotless, TD-8)
	./mvnw spotless:apply
seed:       ## load the demo dataset into the dev database (TD-15)
	@echo "seed arrives with TD-15"

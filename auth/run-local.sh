#!/usr/bin/env bash
# Levanta el microservicio auth en local (perfil dev) cargando las
# variables de entorno definidas en .env (correo, etc.).
# Las credenciales viven en .env (gitignored), nunca en este script.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
exec ./mvnw spring-boot:run

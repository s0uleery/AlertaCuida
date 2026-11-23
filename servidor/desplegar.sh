#!/usr/bin/env bash
# Despliega en el servidor la versión de main que ya está descargada (docs/despliegue.md, "Despliegue automático").
#
# Lo corre GitHub Actions (.github/workflows/despliegue.yml) por SSH, con una llave que en el servidor solo puede
# hacer esto: su línea de ~/.ssh/authorized_keys primero descarga main (git pull) y después corre este script.
# También se puede correr a mano desde el servidor:  cd ~/alertacuida && git pull && servidor/desplegar.sh
#
# Pasos: respaldo de la base -> compilar y levantar -> esperar a que todo quede sano -> borrar imágenes viejas.
# Si un paso falla, se detiene y termina con error (GitHub Actions lo marca en rojo). Nunca borra datos.
#
# Todo va dentro de main(): bash lee la función completa antes de ejecutarla, así un git pull que cambie este
# archivo a mitad de camino no lo afecta.

main() {
	set -euo pipefail

	local repo="$HOME/alertacuida"
	local respaldos="$HOME/respaldos"
	local registros="$HOME/despliegues"
	# Respaldos de antes de cada despliegue que se guardan (los diarios de respaldar.sh van aparte)
	local respaldos_guardados=10
	# Cuánto esperar a que el backend quede sano (las migraciones van dentro de este tiempo)
	local espera_segundos=300

	mkdir -p "$respaldos" "$registros"
	local registro
	registro="$registros/$(date +%F-%H%M%S).log"
	# Todo lo que sigue va a la pantalla (GitHub Actions) y al registro del servidor
	exec > >(tee -a "$registro") 2>&1

	# Un despliegue a la vez: si llega otro mientras tanto, espera a que este termine
	exec 9>"$registros/.candado"
	if ! flock -w 1800 9; then
		echo "ERROR: otro despliegue lleva más de 30 minutos corriendo"
		return 1
	fi

	cd "$repo"
	echo "== Desplegando $(git log --oneline -1) ($(date '+%F %T'))"

	echo "== 1/4 Respaldo de la base"
	local respaldo
	respaldo="$respaldos/alertacuida-$(date +%F-%H%M%S)-antes-de-desplegar.dump"
	docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$respaldo"
	if [ ! -s "$respaldo" ]; then
		echo "ERROR: el respaldo quedó vacío; no se despliega"
		rm -f "$respaldo"
		return 1
	fi
	ls -lh "$respaldo"
	# Deja solo los últimos de antes de desplegar
	ls -1t "$respaldos"/*-antes-de-desplegar.dump 2>/dev/null | tail -n +$((respaldos_guardados + 1)) | xargs -r rm --

	echo "== 2/4 Compilar y levantar (puede tardar unos 10 minutos)"
	docker compose up -d --build --wait --wait-timeout "$espera_segundos"

	echo "== 3/4 Estado"
	docker compose ps --format 'table {{.Service}}\t{{.Status}}'

	echo "== 4/4 Borrar imágenes viejas"
	docker image prune -f | tail -1

	# Deja solo los últimos 30 registros de despliegues
	ls -1t "$registros"/*.log | tail -n +31 | xargs -r rm --
	echo "== Listo: $(git log --oneline -1)"
}

main "$@"
exit

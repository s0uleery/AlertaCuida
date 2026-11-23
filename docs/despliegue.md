# Despliegue en el servidor público (Azure for Students)

Cómo dejar AlertaCuida en internet, con su propia dirección y HTTPS, en una máquina virtual de Azure pagada con
el crédito de **Azure for Students** (GitHub Student Developer Pack). Usa [`docker-compose.servidor.yml`](../docker-compose.servidor.yml), distinto del de desarrollo:

| | Desarrollo (`docker-compose.yml`) | Servidor (`docker-compose.servidor.yml`) |
|---|---|---|
| Perfil | `dev`, con datos de ejemplo | normal, sin datos de ejemplo |
| Claves | de desarrollo, escritas en el repositorio | propias, solo en el `.env` del servidor |
| Correo | Mailpit (no sale nada) | Gmail real |
| Puertos abiertos | todos los de los contenedores | solo 80 y 443 (Caddy) |
| Dirección | `http://localhost:5173` | `https://alertacuida.app` |

```
Internet ──443──▶ Caddy (HTTPS automático)
                    ├─ /api/*  ──▶ backend:8080 ──▶ postgres (sin puerto público)
                    └─ resto   ──▶ frontend (nginx con el dashboard)
```

> **Datos reales.** El servidor guardará datos de personas reales: las claves del `.env` y los respaldos se
> tratan como secretos, nunca se suben al repositorio ni se mandan por chat o correo.

## 0. Lo que se necesita antes

- Una cuenta de **Azure for Students** (<https://azure.microsoft.com/free/students>, también enlazada desde el
  GitHub Student Developer Pack). Se verifica con el correo de la universidad, **sin tarjeta**, y trae un crédito
  (unos 100 USD por 12 meses, renovable mientras se sea estudiante). Al acabarse el crédito, Azure **detiene**
  los recursos en vez de cobrar.
- El dominio **`alertacuida.app`**, registrado en **[Name.com](https://www.name.com)** con la oferta del GitHub
  Student Pack (gratis el primer año; vence el 23-11-2027, renovación automática apagada, WHOIS Privacy activa).
  Los dominios `.app` exigen HTTPS siempre: no es problema, Caddy lo saca solo.
- La cuenta de **Gmail** que enviará los correos, con verificación en 2 pasos activada y una
  **contraseña de aplicación** creada en <https://myaccount.google.com/apppasswords>.
- En el computador, una llave SSH. Si no existe, crearla (PowerShell o Git Bash):

  ```bash
  ssh-keygen -t ed25519 -f ~/.ssh/alertacuida_servidor
  ```

  Crea dos archivos: `alertacuida_servidor` (privada, no se comparte nunca) y `alertacuida_servidor.pub`
  (pública).

## 1. Crear la máquina virtual

En <https://portal.azure.com>: **Virtual machines → Create → Azure virtual machine**.

**Pestaña Basics**

1. **Subscription:** Azure for Students. **Resource group:** crear uno nuevo, `alertacuida`. Agrupa la máquina,
   su disco, su IP y su red: borrar el grupo borra todo junto.
2. **Virtual machine name:** `alertacuida`.
3. **Region:** la más cercana disponible: *Chile Central* si aparece; si no, *Brazil South*. Si una región da
   error de capacidad o de política de la suscripción, probar con *East US 2*.
4. **Availability options:** No infrastructure redundancy required. **Security type:** Standard.
5. **Image:** Ubuntu Server 24.04 LTS – x64 Gen2.
6. **Size:** `Standard_B1ms` (1 vCPU, 2 GB de memoria, unos 15 USD al mes). Alcanza con el swap del paso 2.
   No elegir `B1s` (1 GB): queda corta para Spring y Postgres.
7. **Authentication type:** SSH public key. **Username:** `azureuser`. **SSH public key source:**
   *Use existing public key*, y pegar el contenido de `alertacuida_servidor.pub` (una sola línea).
8. **Public inbound ports:** *Allow selected ports* y marcar **SSH (22), HTTP (80) y HTTPS (443)**.
   Azure avisará que el 22 queda abierto a internet: está bien, porque solo se entra con la llave.

**Pestaña Disks:** OS disk type **Standard SSD** (el Premium cuesta más y no hace falta); 30 GB está bien.

**Pestaña Networking:** dejar que cree la red virtual y una **Public IP** nueva. Marcar *Delete public IP and
NIC when VM is deleted*.

**Pestaña Management:** revisar que **Enable auto-shutdown** esté **desmarcado**. En las suscripciones de
estudiante a veces viene activado y apagaría el servidor todas las noches.

**Review + create → Create.** Al terminar, entrar a la máquina y anotar la **Public IP address**.

**IP fija:** en la máquina → la IP pública → **Configuration** → *Assignment*: **Static** → Save. Así no cambia
aunque la máquina se reinicie o se detenga.

**Control del crédito:** en **Cost Management → Budgets**, crear un presupuesto (por ejemplo, 20 USD al mes)
con alerta por correo al 80 %. El crédito restante se ve en <https://www.microsoftazuresponsorships.com/Balance>.

## 2. Puertos y memoria

**Puertos:** con el paso 1 ya quedan abiertos el 22, el 80 y el 443 (Azure los pone en el *Network security
group* de la máquina: Networking → Network settings). Ubuntu en Azure no trae otro firewall activo, así que no
hay nada más que abrir. **No** agregar el 5432 ni el 8080: la base y el backend solo se alcanzan por dentro de
Docker.

**Swap de 2 GB:** compilar el backend y el dashboard en una máquina de 2 GB puede quedarse sin memoria. Después
de entrar al servidor (paso 4), correr una sola vez:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h        # la fila "Swap" debe mostrar 2.0Gi
```

## 3. Apuntar el dominio a la máquina

En Name.com: **My Domains → alertacuida.app → Manage DNS Records**. Borrar los registros que vengan de ejemplo
(por ejemplo, los que apuntan a un "parking") y agregar:

| Type | Host | Answer | TTL |
|---|---|---|---|
| A | *(vacío)* | la IP pública fija del paso 1 | 300 |

Comprobar desde el computador (puede tardar unos minutos):

```bash
nslookup alertacuida.app
```

Debe responder con la IP de la máquina. Si algún día se cambia de servidor, basta con cambiar este registro:
la dirección sigue siendo la misma.

## 4. Entrar al servidor (desde cualquier computador)

```bash
ssh -i ~/.ssh/alertacuida_servidor azureuser@alertacuida.app
```

**Atajo recomendado:** crear (o completar) el archivo `~/.ssh/config` del computador con:

```
Host alertacuida
  HostName alertacuida.app
  User azureuser
  IdentityFile ~/.ssh/alertacuida_servidor
  IdentitiesOnly yes
```

Desde ahí basta con `ssh alertacuida`, y para copiar archivos, `scp alertacuida:ruta/en/el/servidor .`.

**Desde otro computador:** crear ahí otra llave (paso 0) y, desde una sesión ya abierta en el servidor,
agregar la pública del computador nuevo:

```bash
echo "contenido-de-la-llave-.pub-del-otro-computador" >> ~/.ssh/authorized_keys
```

Así cada computador tiene su propia llave y se puede quitar una sin afectar a la otra. Sin SSH, también se
entra desde el navegador: en el portal de Azure, la máquina → **Connect → Serial console** (o *Run command*
para comandos sueltos).

Nada de la configuración vive en los computadores: todo queda en el disco del servidor, que sigue encendido
aunque se apaguen.

## 5. Instalar Docker

```bash
sudo apt update && sudo apt upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit
```

Volver a entrar (paso 4) para que tome el grupo `docker`, y comprobar con `docker run --rm hello-world`.
Docker queda configurado para arrancar solo cuando el servidor se reinicia, y los contenedores vuelven solos
(`restart: unless-stopped`).

## 6. Descargar el código

El repositorio es privado: el servidor necesita permiso de **solo lectura**. Crear una *deploy key*:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_alertacuida -N ""
cat ~/.ssh/github_alertacuida.pub
```

En GitHub: repositorio → Settings → Deploy keys → **Add deploy key**, pegar la llave y **no** marcar
"Allow write access". Después:

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/github_alertacuida
EOF
git clone git@github.com:s0uleery/AlertaCuida.git ~/alertacuida
cd ~/alertacuida
```

El servidor usa la rama `main`: lo que no está en `main` no llega al servidor.

## 7. El archivo `.env` del servidor

```bash
cp .env.servidor.example .env
chmod 600 .env
```

Generar las claves (cada comando da un valor distinto; copiar cada uno en su línea del `.env`):

```bash
echo "DB_CLAVE=$(openssl rand -base64 24)"
echo "RUN_CLAVE=$(openssl rand -base64 48)"
echo "JWT_CLAVE=$(openssl rand -base64 48)"
echo "CIFRADO_CLAVE=$(openssl rand -base64 32)"
echo "RESPUESTA_CLAVE=$(openssl rand -base64 48)"
```

Editar con `nano .env` y completar también:

- `SMTP_USUARIO`: la dirección de Gmail que envía.
- `SMTP_CLAVE`: la contraseña de aplicación de Google, sin espacios.
- `ADMIN_INICIAL_RUN`, `ADMIN_INICIAL_NOMBRE` y `ADMIN_INICIAL_CORREO`: los datos del primer administrador.

**Guardar una copia del `.env` fuera del servidor** (por ejemplo, en un gestor de contraseñas). Sin
`CIFRADO_CLAVE` los datos personales cifrados no se pueden leer, ni siquiera desde un respaldo.

## 8. Levantar todo

```bash
docker compose up -d --build
```

La primera vez tarda unos 10 minutos (compila el backend y el dashboard). Revisar:

```bash
docker compose ps                 # los servicios deben quedar "running" y el backend "healthy"
docker compose logs -f caddy      # debe decir que obtuvo el certificado ("certificate obtained successfully")
```

Abrir <https://alertacuida.app>: debe aparecer el inicio de sesión, con candado.

## 9. Activar el primer administrador

Al arrancar sin ningún administrador, el backend crea el de `ADMIN_INICIAL_*` y deja en el log el enlace
para elegir la contraseña:

```bash
docker compose logs backend | grep "Administrador inicial"
```

Abrir ese enlace, elegir una contraseña (al menos 12 caracteres) y entrar con el RUN y esa contraseña. El
enlace sirve una sola vez y vence en 48 horas. Si venció antes de usarlo, ver
[Problemas comunes](#problemas-comunes).

Desde ahí, el resto del equipo se crea en **Administración**, como siempre. Probar que el correo llega:
crear una cuenta de prueba con un correo propio y revisar la bandeja (y la carpeta de spam).

## Actualizar a una versión nueva

**Es automático:** cada merge a `main` se despliega solo si pasan las pruebas (ver
[Despliegue automático](#despliegue-automático)). No hay que entrar al servidor.

A mano (por ejemplo, si GitHub Actions no está disponible), desde el servidor:

```bash
cd ~/alertacuida
git pull
servidor/desplegar.sh
```

El script respalda la base, compila, espera a que todo quede sano y borra las imágenes viejas. Las migraciones
de Flyway se aplican solas al arrancar el backend.

## Despliegue automático

```
merge a main ──▶ CI (pruebas del backend y del dashboard) ──✔──▶ workflow "Despliegue" ──SSH──▶ servidor
                                                          ✘──▶ no se despliega
```

En el servidor, [`servidor/desplegar.sh`](../servidor/desplegar.sh):

1. respalda la base en `~/respaldos/…-antes-de-desplegar.dump` (guarda los últimos 10);
2. compila y levanta con `docker compose up -d --build --wait`: espera hasta 5 minutos a que todo quede sano;
3. muestra el estado y borra las imágenes viejas.

Si un paso falla, se detiene y el despliegue queda en rojo en GitHub (llega un correo a quien hizo el merge).
Cada despliegue deja su registro en `~/despliegues/` del servidor (los últimos 30). Mientras compila, la página
sigue funcionando con la versión anterior; el corte es de unos segundos, al reiniciar el backend.

**Ver un despliegue:** GitHub → **Actions** → **Despliegue**. Para volver a desplegar sin cambios:
**Run workflow**.

### La llave de GitHub Actions (ya configurada el 23-11-2026)

GitHub Actions entra con su propia llave, que en el servidor **solo puede desplegar**: su línea en
`~/.ssh/authorized_keys` fuerza el comando, así que cualquier otra cosa que pida se ignora:

```
command="cd ~/alertacuida && git pull --ff-only -q && exec bash servidor/desplegar.sh",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty ssh-ed25519 AAAA… alertacuida-github-actions
```

Si alguien la robara, solo podría volver a desplegar lo que ya está en `main`.

En GitHub → repositorio → **Settings → Secrets and variables → Actions** van dos secretos:

| Secreto | Valor |
|---|---|
| `DESPLIEGUE_SSH_LLAVE` | La llave **privada** completa, desde `-----BEGIN OPENSSH PRIVATE KEY-----` hasta `-----END OPENSSH PRIVATE KEY-----` |
| `DESPLIEGUE_SSH_HOST_CONOCIDO` | La línea de la llave del servidor: `ssh-keyscan -t ed25519 alertacuida.app` (comprobar que su huella sea la misma que se ve al entrar por SSH) |

**Cambiar la llave** (si se filtró o se quiere renovar): crear otra con
`ssh-keygen -t ed25519 -f despliegue -N "" -C alertacuida-github-actions`, reemplazar en el servidor la línea
`alertacuida-github-actions` de `authorized_keys` (misma restricción `command=…`, con la pública nueva) y
actualizar `DESPLIEGUE_SSH_LLAVE`. **Quitar el despliegue automático:** borrar esa línea del servidor.

### Si una versión nueva sale mal

1. Volver el código a la versión anterior con un *revert* del merge en GitHub (o un PR que lo corrija): al
   llegar a `main`, se despliega solo.
2. Si además una migración dañó datos, restaurar el respaldo de antes del despliegue (ver
   [Respaldos](#respaldos)) **antes** de desplegar la versión anterior, porque una base con una migración más
   nueva que el código no arranca.

## Respaldos

**Copia diaria de la base**, guardando las últimas 14. Crear el script:

```bash
mkdir -p ~/respaldos
cat > ~/respaldar.sh <<'EOF'
#!/bin/sh
# Copia de la base de AlertaCuida; deja solo las 14 más recientes
cd ~/alertacuida
docker compose exec -T postgres pg_dump -U alertacuida -Fc alertacuida > ~/respaldos/alertacuida-$(date +%F).dump
ls -1t ~/respaldos/alertacuida-*.dump | tail -n +15 | xargs -r rm
EOF
chmod +x ~/respaldar.sh
(crontab -l 2>/dev/null; echo "30 3 * * * ~/respaldar.sh") | crontab -
```

Corre todos los días a las 3:30. De vez en cuando, bajar una copia al computador:

```bash
scp -i ~/.ssh/alertacuida_servidor azureuser@alertacuida.app:respaldos/alertacuida-AAAA-MM-DD.dump .
```

**Restaurar** una copia (borra lo que haya en la base):

```bash
docker compose exec -T postgres pg_restore -U alertacuida -d alertacuida --clean --if-exists < ~/respaldos/alertacuida-AAAA-MM-DD.dump
docker compose restart backend
```

Además, Azure permite sacar una copia del disco completo (la máquina → Disks → el disco → **Create snapshot**),
útil antes de un cambio grande. Las copias se cobran del crédito según su tamaño: borrar las viejas.

## Problemas comunes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El navegador no carga y Caddy no obtiene el certificado | Puerto 80/443 cerrado o el registro A con otra IP | Revisar el *Network security group* (paso 2) y el `nslookup` del paso 3 |
| El backend no arranca: "Falta …" | Variable vacía en `.env` | `docker compose logs backend`, completar y repetir el paso 8 |
| El backend no arranca: "Falta ADMIN_INICIAL_…" | Vino el RUN pero no el nombre o el correo | Completar las tres o vaciar las tres |
| Los correos quedan fallidos | Contraseña de aplicación mala o con espacios | Crear otra en Google, actualizar `SMTP_CLAVE` y `docker compose up -d` |
| El enlace del administrador venció | Pasaron 48 horas | `docker compose exec postgres psql -U alertacuida -c "DELETE FROM usuario WHERE codigo_rol = 'ADMINISTRADOR' AND hash_contrasena IS NULL;"` y `docker compose restart backend`: se crea de nuevo con un enlace nuevo |
| La máquina está detenida ("Stopped (deallocated)") | Auto-shutdown activo o se acabó el crédito | Desactivar el auto-shutdown (máquina → Auto-shutdown), revisar el saldo y **Start**; los contenedores vuelven solos |
| La compilación del paso 8 se corta ("Killed" o "exit code 137") | Falta memoria | Revisar el swap del paso 2 (`free -h`) y repetir el paso 8 |

**Nunca** correr `docker compose down -v` en el servidor: borra la base de datos y los certificados.

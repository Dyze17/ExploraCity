# Despliegue en Google Cloud

La API corre en **Cloud Run** con PostgreSQL en **Cloud SQL** (ADR-04 y ADR-10). GitHub Actions la despliega con cada merge a `main` que cambie `api-ktor/` ([`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)): corre las pruebas, sube la imagen a Artifact Registry, la publica en Cloud Run y comprueba que responda.

Esta guía configura Google Cloud desde cero. Las cuentas y las claves son de quien la sigue: ningún valor secreto pasa por el repositorio ni por GitHub.

## Qué queda en Google Cloud

| Servicio | Nombre | Para qué |
|---|---|---|
| Cloud Run | `exploracity-api` | La API, en `https://exploracity-api-NÚMERO.us-east1.run.app`. Sin uso se apaga. |
| Cloud SQL | instancia `exploracity-db`, base y usuario `exploracity` | PostgreSQL 16 con PostGIS, db-f1-micro y 10 GB |
| Secret Manager | `database-password`, `jwt-secret`, `moderator-emails`, `mail-from`, `sendgrid-api-key`, `cloudinary-api-key`, `cloudinary-api-secret` y, opcional, `openrouter-api-key` | Lo secreto. Cloud Run se lo entrega a la API como variables de entorno |
| Artifact Registry | `exploracity` | Las imágenes de la API; guarda las 5 más recientes |
| Cuentas de servicio | `exploracity-api` y `github-deployer` | La de la API y la de GitHub Actions |
| Workload Identity Federation | grupo `github`, proveedor `exploracity` | GitHub Actions entra sin claves, solo desde `main` de este repositorio |

**Costos aproximados** (confírmalos en la [calculadora de precios](https://cloud.google.com/products/calculator)):
- Cloud SQL es lo único que cobra de verdad: unos US$10 al mes, aunque nadie use la app.
- Cloud Run, Artifact Registry y Secret Manager deberían quedar en cero o en centavos con el tráfico de este proyecto.

## Antes de empezar

- Una cuenta de Google con una cuenta de facturación.
- **SendGrid:** un remitente verificado (Single Sender) y una clave de API con permiso «Mail Send».
- **Cloudinary:** el nombre de la nube, la clave de API y el secreto de API, del panel de la cuenta.
- **OpenRouter** (opcional): una clave de API. Sin ella, la categoría se sugiere por palabras clave.

## 1. Proyecto, facturación y clave de Maps (consola web)

1. En [console.cloud.google.com](https://console.cloud.google.com), abre el selector de proyectos y elige «Proyecto nuevo». Anota el **ID del proyecto**: es único y no se puede cambiar.
2. En «Facturación», vincula la cuenta de facturación al proyecto.
3. En «Facturación › Presupuestos y alertas», crea un presupuesto para este proyecto (por ejemplo, US$15 al mes) con avisos al 50, 90 y 100 %. Solo avisa por correo: no detiene nada.
4. Clave de Maps:
   1. En «APIs y servicios › Biblioteca», habilita **Maps SDK for Android**. Si te muestra una clave nueva, usa esa.
   2. Si no, crea una en «APIs y servicios › Credenciales › Crear credenciales › Clave de API».
   3. Restringe la clave: en «Apps para Android», el paquete `co.edu.uniquindio.exploracity` con la huella SHA-1 de depuración de tu equipo (`keytool -list -v -keystore ~/.android/debug.keystore -storepass android`), y en «Restricciones de API», solo «Maps SDK for Android». La huella de producción se agrega cuando exista la firma.
   4. Ponla en `app-android/local.properties`, en la línea `MAPS_API_KEY=`.

## 2. Servicios (Cloud Shell)

Abre **Cloud Shell** con el ícono `>_` de arriba a la derecha de la consola: es una terminal con `gcloud` ya instalado y con tu sesión. Pega cada bloque en orden. Si Cloud Shell se reinicia, vuelve a definir `PROJECT_ID` y `REGION`.

```bash
PROJECT_ID=tu-id-de-proyecto
REGION=us-east1
gcloud config set project "$PROJECT_ID"
gcloud services enable run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com \
  artifactregistry.googleapis.com iamcredentials.googleapis.com sts.googleapis.com
```

### Artifact Registry

```bash
gcloud artifacts repositories create exploracity --repository-format=docker --location="$REGION" \
  --description="Imágenes de la API de ExploraCity"
cat > limpieza.json <<'EOF'
[
  {"name": "conservar-5", "action": {"type": "Keep"}, "mostRecentVersions": {"keepCount": 5}},
  {"name": "borrar-el-resto", "action": {"type": "Delete"}, "condition": {"tagState": "any"}}
]
EOF
gcloud artifacts repositories set-cleanup-policies exploracity --location="$REGION" --policy=limpieza.json --no-dry-run
```

### Cloud SQL

La instancia tarda unos 10 minutos en crearse. `--deletion-protection` impide borrarla por accidente.

```bash
gcloud sql instances create exploracity-db \
  --database-version=POSTGRES_16 --edition=ENTERPRISE --tier=db-f1-micro \
  --region="$REGION" --storage-type=SSD --storage-size=10 \
  --backup-start-time=08:00 --deletion-protection
gcloud sql databases create exploracity --instance=exploracity-db
```

La contraseña de la base se genera aquí y va directo a Secret Manager: nadie la ve.

```bash
DB_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=\n')
gcloud sql users create exploracity --instance=exploracity-db --password="$DB_PASSWORD"
printf %s "$DB_PASSWORD" | gcloud secrets create database-password --data-file=-
unset DB_PASSWORD
```

Las extensiones (PostGIS, pg_trgm y unaccent) y el esquema los crea Flyway la primera vez que arranca la API.

### Secretos

La clave del JWT también se genera aquí. Los demás valores los escribes tú: `secreto` los pide sin mostrarlos en pantalla ni guardarlos en el historial.

**Pega estas líneas de una en una**, no el bloque entero: cada `secreto` se queda esperando su valor, y si pegas todo junto, la línea siguiente se guardaría como el valor del anterior. Al pegar o escribir el valor no se ve nada en pantalla; es normal. Termina con Enter, y `gcloud` responde «Created version [1] of the secret».

```bash
printf %s "$(openssl rand -base64 48 | tr -d '/+=\n')" | gcloud secrets create jwt-secret --data-file=-
```

```bash
secreto() { read -rsp "$1: " valor; echo; printf %s "$valor" | gcloud secrets create "$1" --data-file=-; unset valor; }
```

| Línea | Valor que pide |
|---|---|
| `secreto moderator-emails` | Los correos de moderador, separados por comas |
| `secreto mail-from` | El remitente verificado en SendGrid, igual que allá |
| `secreto sendgrid-api-key` | La clave de SendGrid (`SG.…`), solo con «Mail Send» |
| `secreto cloudinary-api-key` | La clave de API de Cloudinary |
| `secreto cloudinary-api-secret` | El secreto de API de Cloudinary |
| `secreto openrouter-api-key` | Opcional: la clave de OpenRouter. Sáltala si no la usas |

Si un valor quedó mal, o el secreto ya existía («already exists»), agrega una versión nueva; la última es la que usa la API:

```bash
read -rsp "valor: " valor; echo; printf %s "$valor" | gcloud secrets versions add NOMBRE_DEL_SECRETO --data-file=-; unset valor
```

Para ver qué secretos hay, sin sus valores: `gcloud secrets list --format="value(name)"`.

### Cuentas de servicio

```bash
gcloud iam service-accounts create exploracity-api --display-name="ExploraCity: API en Cloud Run"
gcloud iam service-accounts create github-deployer --display-name="ExploraCity: despliegue desde GitHub"
API_SA="exploracity-api@$PROJECT_ID.iam.gserviceaccount.com"
DEPLOY_SA="github-deployer@$PROJECT_ID.iam.gserviceaccount.com"
# Una cuenta recién creada tarda unos segundos en existir para los permisos («Service account … does not exist»).
sleep 20

# La API: conectarse a Cloud SQL y leer sus secretos.
for role in roles/cloudsql.client roles/secretmanager.secretAccessor; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$API_SA" --role="$role" --condition=None
done

# GitHub Actions: publicar en Cloud Run (con acceso público), ver qué secretos existen (no sus valores),
# subir imágenes y poner a la API con su propia cuenta.
for role in roles/run.admin roles/secretmanager.viewer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$DEPLOY_SA" --role="$role" --condition=None
done
gcloud artifacts repositories add-iam-policy-binding exploracity --location="$REGION" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/artifactregistry.writer
gcloud iam service-accounts add-iam-policy-binding "$API_SA" \
  --member="serviceAccount:$DEPLOY_SA" --role=roles/iam.serviceAccountUser
```

Para comprobar los permisos del proyecto: la API debe tener `cloudsql.client` y `secretmanager.secretAccessor`, y GitHub Actions, `run.admin` y `secretmanager.viewer`. Si falta alguno, repite su línea.

```bash
gcloud projects get-iam-policy "$PROJECT_ID" --flatten="bindings[].members" \
  --filter="bindings.members~exploracity-api OR bindings.members~github-deployer" \
  --format="table(bindings.role,bindings.members)"
```

### GitHub sin claves (Workload Identity Federation)

GitHub Actions firma un token por cada ejecución y Google lo cambia por una sesión de `github-deployer`. Solo acepta los de este repositorio (`1384508796` es su id, que no cambia aunque se renombre) y de la rama `main`.

```bash
gcloud iam workload-identity-pools create github --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc exploracity --location=global \
  --workload-identity-pool=github --display-name="Dyze17/ExploraCity" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository_id == '1384508796' && assertion.ref == 'refs/heads/main'"
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository_id/1384508796"

echo "GCP_WIF_PROVIDER = projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/exploracity"
echo "Dirección de la API = https://exploracity-api-$PROJECT_NUMBER.$REGION.run.app"
```

## 3. Variables del repositorio (GitHub)

En GitHub, «Settings › Secrets and variables › Actions › Variables»:

| Variable | Valor |
|---|---|
| `GCP_PROJECT_ID` | El ID del proyecto |
| `GCP_REGION` | `us-east1` |
| `GCP_WIF_PROVIDER` | Lo que imprimió el último bloque |
| `CLOUDINARY_CLOUD_NAME` | El nombre de la nube en Cloudinary |
| `ANDROID_CERT_SHA256` | Cuando exista la firma de producción: su huella SHA-256 (para los App Links) |

Ninguna es secreta: el repositorio es público y el registro del despliegue las muestra. Por eso los correos y las claves van en Secret Manager. Desde una terminal con `gh`, también sirve `gh variable set GCP_PROJECT_ID --body "…"`.

## 4. Primer despliegue

En GitHub, «Actions › Despliegue › Run workflow» sobre `main`. Tarda unos minutos. Al arrancar por primera vez, la API crea el esquema en Cloud SQL (Flyway, V1 en adelante). Al final, el paso «Comprobación» llama a `/health` y a `/v1/city`.

## Día a día

- **Desplegar:** cada merge a `main` que cambie `api-ktor/` despliega solo. Para repetir un despliegue: «Actions › Despliegue › Run workflow».
- **Logs:** en la consola, «Cloud Run › exploracity-api › Registros», o en Cloud Shell:
  ```bash
  gcloud run services logs read exploracity-api --region us-east1 --limit 100
  ```
- **Volver a la versión anterior:** el despliegue siguiente vuelve a la más nueva.
  ```bash
  gcloud run revisions list --service exploracity-api --region us-east1
  gcloud run services update-traffic exploracity-api --region us-east1 --to-revisions NOMBRE_DE_LA_REVISIÓN=100
  ```
- **Cambiar un secreto** (por ejemplo, los correos de moderador): agrega una versión y repite el despliegue. Al arrancar, la API toma la lista nueva de moderadores.
  ```bash
  read -rsp "valor: " valor; echo; printf %s "$valor" | gcloud secrets versions add moderator-emails --data-file=-; unset valor
  ```
- **Pausar el costo de Cloud SQL:** `gcloud sql instances patch exploracity-db --activation-policy=NEVER`. El disco se sigue cobrando (unos US$2 al mes por los 10 GB). Para volver: `--activation-policy=ALWAYS`.
- **Borrar todo:** quita la protección de la base (`gcloud sql instances patch exploracity-db --no-deletion-protection`) y cierra el proyecto en «IAM y administración › Configuración». Un proyecto cerrado se puede restaurar durante 30 días.

## Lo que la API revisa al arrancar en producción

Con `APP_ENV=production`, que pone el despliegue, la API no arranca si falta SendGrid o Cloudinary, si `DEV_MAILBOX` está activo o si los enlaces del correo no son https. En ese caso, Cloud Run deja atendiendo la versión anterior y el registro dice qué falta.

## Enlaces del correo

`APP_LINK_BASE_URL` es la dirección de la API seguida de `/enlace`. Los enlaces de recuperación y de cambio de correo llegan como `https://…run.app/enlace/restablecer?token=…`, porque Gmail no deja tocar los enlaces `exploracity://`.

- En el navegador, abren una página con el botón «Abrir en ExploraCity», que le pasa el token a la app.
- Cuando la app de producción declare estos enlaces (App Links) y `ANDROID_CERT_SHA256` tenga la huella de su firma, Android los abrirá directo en la app. La API publica esa huella en `/.well-known/assetlinks.json`.

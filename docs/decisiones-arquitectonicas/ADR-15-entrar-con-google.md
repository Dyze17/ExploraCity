# ADR-15: Entrar y registrarse con Google

**Estado:** aceptada (octubre de 2026). Amplía ADR-06, que solo contemplaba correo y contraseña.

## Decisión

Además de correo y contraseña, las personas pueden entrar y registrarse con su cuenta de Google:

- La app usa **Credential Manager** de Android («Sign in with Google») y recibe un **ID token** de Google (OpenID Connect).
- La API verifica ese token y abre **la misma sesión de ADR-06**: un JWT de acceso de 15 minutos y un token de renovación de 30 días. Para verificarlo comprueba:
  - la firma RS256, con las claves públicas de Google;
  - el emisor, `accounts.google.com`;
  - la audiencia: el cliente OAuth «Web» del proyecto, `GOOGLE_WEB_CLIENT_ID`;
  - el vencimiento;
  - que Google haya verificado el correo.
- La cuenta guarda el identificador de la cuenta de Google (`sub`), único, y se reconoce por él, no por el correo.

Decisiones del plan:

- **B1 · Cuenta con contraseña y el mismo correo:** se vincula solo con esa contraseña, una vez (`POST /v1/auth/google/link`). La contraseña equivocada cuenta para el límite de intentos (A1).
- **C1 · Cuenta nueva:** la app abre el registro (4) en modo Google:
  - el nombre de Google, que se puede cambiar;
  - el correo fijo, sin contraseña;
  - la residencia;
  - la autorización de la Ley 1581, que nunca viene marcada.
- **D1 · Cuenta sin contraseña:** no inicia sesión con contraseña ni cambia el correo hasta que cree una con «¿Olvidaste tu contraseña?».

## Contexto

Crear una contraseña es el paso que más frena el registro en el teléfono, y casi todas las personas con Android tienen una cuenta de Google. Además, Google ya verificó ese correo, cosa que el registro con contraseña no hace.

## Alternativas

- **Firebase Authentication:** agrega otro proveedor y otro modelo de sesión junto al JWT propio de ADR-06.
- **OAuth con redirección en el navegador (AppAuth):** más pasos para la persona y para la app que el selector de cuentas de Credential Manager, que es lo que Android recomienda hoy.
- **Vincular por correo sin pedir la contraseña:** quien hubiera registrado antes el correo de otra persona con una contraseña conservaría el acceso a la cuenta. Por eso se descartó (B1).

## Justificación

- La sesión, los roles (`MODERATOR_EMAILS`) y el resto de la API no cambian.
- La verificación usa `java-jwt` y `jwks-rsa`, que ya llegan con `ktor-server-auth-jwt`.
- Los permisos que se piden son los básicos (`openid`, `email`, `profile`), que Google no revisa.

## Consecuencias

- Hay cuentas sin contraseña (`password_hash` opcional, V8). La API y la app tienen que contemplarlas: `hasPassword` y `googleLinked` en la cuenta.
- Google Cloud necesita la pantalla de consentimiento publicada y tres clientes OAuth: uno Web y uno Android por cada paquete y firma (producción y depuración). Los pasos están en [`docs/despliegue.md`](../despliegue.md#entrar-con-google).
- Si cambian la firma de la app o su paquete, hay que actualizar el cliente Android correspondiente.

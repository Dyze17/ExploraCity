# Cómo trabajar en ExploraCity

## Ramas y pull requests

- `main` siempre compila y pasa el CI. **No se hacen commits directos a `main`**: todo entra por pull request.
- Una rama por tarea, creada desde `main` actualizado:
  - `feature/<funcionalidad>`: pantallas y funciones nuevas (`feature/hoja-filtros`)
  - `fix/<problema>`: correcciones
  - `chore/<tarea>`: configuración, dependencias, CI
  - `docs/<tema>`: documentación

```bash
git switch main
git pull
git switch -c feature/hoja-filtros
# … commits …
git push            # la rama sube con su upstream (push.autoSetupRemote)
gh pr create --base main
```

Al abrir el PR se carga la plantilla (`.github/pull_request_template.md`). El CI de GitHub Actions compila y prueba
`app-android` y `api-ktor` en cada PR; se fusiona cuando está en verde y revisado, y la rama se borra después.

## Commits

- En español. Primera línea: qué cambia (≤ 72 caracteres). Cuerpo: por qué y qué debe saber quien revisa.
- Pequeños y con una sola intención; cada commit compila.

## Antes de abrir el PR

- `app-android`: `./gradlew assembleDebug testDebugUnitTest lintDebug` sin errores.
- `api-ktor`: `./gradlew test` si se tocó el backend.
- Revisión en dispositivo en tema claro, oscuro y con fuente al 200 %.

## Referencias

- **Diseño:** `design_handoff_exploracity/README.md` (fuera del repositorio). Los `.dc.html` son referencia visual,
  no código. Cada pantalla se nombra por su número del README.
- **Arquitectura:** documento C4 (SAD v1.1). Paquete `co.edu.uniquindio.exploracity`; capas `ui/`, `navigation/`,
  `viewmodel/`, `data/{remote,local,repository}`, `domain/` y `util/`.
- **Sistema de diseño en código:** colores, tipografía y formas solo desde `MaterialTheme` y
  `MaterialTheme.exploraColors`; `ThemeContrastTest` exige 4,5:1 en texto y 3:1 en contenido no textual.
- **Textos:** en `strings.xml` (español, `tools:locale="es"`), con plurales `one`, `many` y `other`.

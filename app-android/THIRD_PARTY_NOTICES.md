# Recursos de terceros

## Fuentes (`app/src/main/res/font/`)

| Archivo | Familia | Origen | Licencia |
|---|---|---|---|
| `outfit_variable.ttf` | Outfit (variable, eje `wght`) | `github.com/google/fonts` → `ofl/outfit/Outfit[wght].ttf` | SIL Open Font License 1.1 · © 2021 The Outfit Project Authors |
| `manrope_variable.ttf` | Manrope (variable, eje `wght`) | `github.com/google/fonts` → `ofl/manrope/Manrope[wght].ttf` | SIL Open Font License 1.1 · © 2019 The Manrope Project Authors |

Los archivos se renombraron solo para cumplir las reglas de nombres de recursos de Android. El aviso de copyright y la licencia van dentro de cada archivo (tabla `name`, campos 0, 13 y 14), como permite la OFL.

Uso según el sistema de diseño: Outfit para display, headline y titleLarge; Manrope para title medium/small, body y label.

## Iconos (`app/src/main/res/drawable/ic_*.xml`)

91 iconos Material Symbols Rounded (peso 400, relleno 0, grado 0, 24 dp): los que usa el diseño.

- Origen: `github.com/google/material-design-icons` → `symbols/android/<icono>/materialsymbolsrounded/<icono>_24px.xml`
- Licencia: Apache License 2.0 · © Google
- Cambio: se quitó `android:tint="?attr/colorControlNormal"` para que el color lo dé el tema (`Icon(tint = …)`).
- `notifications_none` y `place` del diseño son alias de `ic_notifications` e `ic_location_on`.

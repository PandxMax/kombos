# Kombos

App para Android que liquida campañas por lotes: suma pesos e ingresos, descuenta flete,
facturación, gasto de campaña y caja, y reparte la utilidad en partes iguales entre los socios.

## Descargar

**[⬇️ Descargar Kombos.apk](https://github.com/PandxMax/kombos/releases/latest/download/Kombos.apk)**

En el celular, abre el archivo y permite "instalar apps de fuentes desconocidas" si te lo pide.
Las versiones nuevas se instalan encima de la anterior sin perder los datos.

## Cómo calcula

| Paso | Fórmula |
|---|---|
| Peso total | suma de los pesos de los lotes (TM) |
| Ingreso bruto | suma de los ingresos de los lotes ($) |
| Flete | peso total × flete por TM |
| Facturación | ingreso bruto × % de facturación |
| Ingreso neto | bruto − flete − facturación |
| Neto en soles | neto × tipo de cambio |
| Utilidades | neto en soles − gasto de campaña − caja |
| Por socio | utilidades ÷ número de socios |

Cada paso se redondea a 2 decimales (el peso a 3) antes de seguir, igual que al hacerlo a mano.

## Funciones

- Varias campañas, cada una con sus lotes
- Resultado en vivo mientras escribes (acepta coma o punto decimal)
- Reporte para compartir por WhatsApp, correo, etc.
- Exportar e importar los datos en un archivo `.json` (copia de seguridad)
- Modo claro y oscuro

## Compilación

El APK se genera solo con GitHub Actions en cada cambio a `main` y se publica en
[Releases](https://github.com/PandxMax/kombos/releases). La versión se cambia en
`versionName` / `versionCode` de `app/build.gradle.kts`.

Hecho con Kotlin y Jetpack Compose. Requiere Android 8.0 o superior.

> `app/kombos.keystore` es una llave de firma para instalar el APK directamente.
> Para publicar en Google Play conviene usar una llave privada que no esté en el repositorio.

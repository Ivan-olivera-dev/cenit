# Publicar Cenit

Todo lo hace GitHub Actions. Tú solo creas una etiqueta.

## Preparación (una sola vez)

La clave de firma ya existe en `~/.android/cenit-release.jks` (alias `cenit`).

1. Guarda **el archivo `~/.android/cenit-release.jks` y su contraseña** en tu gestor de contraseñas o en una copia fuera del equipo.
2. Sube los 4 secretos al repositorio desde macOS (`gh secret set` pide el valor de forma interactiva cuando no recibe entrada):
   ```bash
   base64 -i ~/.android/cenit-release.jks | gh secret set CENIT_KEYSTORE_BASE64
   gh secret set CENIT_KEYSTORE_PASSWORD    # pide la contraseña
   gh secret set CENIT_KEY_ALIAS            # cenit
   gh secret set CENIT_KEY_PASSWORD         # misma contraseña
   ```

> **Importante:** si pierdes la clave, no podrás firmar actualizaciones con la misma
> firma. Android las rechazará y quien ya tenga Cenit tendrá que **desinstalar** la app
> (y perder sus lugares guardados y avisos) para instalar la nueva. Haz la copia de seguridad.

## Sacar una versión

```bash
git tag v0.1.0 && git push origin v0.1.0
```

En unos minutos aparece en **GitHub → Releases** con:

- `cenit-0.1.0.apk` (firmado)
- `cenit-0.1.0.apk.sha256`
- notas generadas a partir de los commits y la atestación SLSA en **Actions → Attestations**.

Antes de publicar, el workflow ejecuta los tests, comprueba el manifiesto del APK (sin permiso `INTERNET`) y verifica la firma con `apksigner`. Si falta algún secreto, falla con un mensaje claro y **no** publica nada sin firmar.

## Para los usuarios

Pueden descargar el APK desde Releases o seguir el repositorio con
[Obtainium](https://github.com/ImranR98/Obtainium), que avisa e instala cada versión nueva.

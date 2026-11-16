package com.pandxmax.kombos

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Un archivo listo para compartir o guardar. */
class Exportacion(val nombre: String, val mime: String, val bytes: ByteArray) {
    val esPdf get() = mime == MIME_PDF
}

fun nombreArchivo(base: String, extension: String): String {
    val limpio = base.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "Kombos" }
    return "$limpio.$extension"
}

fun excelDe(campanas: List<Campana>, base: String) =
    Exportacion(nombreArchivo(base, "xlsx"), MIME_XLSX, generarExcel(campanas))

fun pdfDe(c: Campana) =
    Exportacion(nombreArchivo("Liquidación ${c.nombre} ${c.fecha}", "pdf"), MIME_PDF, generarPdf(c))

/**
 * Diálogo para elegir qué hacer con el archivo: compartirlo (WhatsApp, correo, Drive…)
 * o guardarlo en el celular (Descargas u otra carpeta).
 */
@Composable
fun DialogoExportar(exp: Exportacion?, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var pendiente by remember { mutableStateOf<Exportacion?>(null) }

    val guardarExcel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { uri ->
        guardar(ctx, uri, pendiente)
        pendiente = null
    }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { uri ->
        guardar(ctx, uri, pendiente)
        pendiente = null
    }

    if (exp != null) {
        AlertDialog(
            onDismissRequest = onCerrar,
            title = { Text(if (exp.esPdf) "Exportar a PDF" else "Exportar a Excel") },
            text = { Text(exp.nombre) },
            confirmButton = {
                TextButton(onClick = {
                    compartirArchivo(ctx, exp)
                    onCerrar()
                }) { Text("Compartir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendiente = exp
                    if (exp.esPdf) guardarPdf.launch(exp.nombre) else guardarExcel.launch(exp.nombre)
                    onCerrar()
                }) { Text("Guardar en el celular") }
            }
        )
    }
}

private fun guardar(ctx: Context, uri: Uri?, exp: Exportacion?) {
    if (uri == null || exp == null) return
    val ok = runCatching {
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(exp.bytes) }
    }.isSuccess
    Toast.makeText(ctx, if (ok) "Archivo guardado" else "No se pudo guardar", Toast.LENGTH_LONG).show()
}

fun compartirArchivo(ctx: Context, exp: Exportacion) {
    val ok = runCatching {
        val dir = File(ctx.cacheDir, "exportes").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, exp.nombre)
        f.writeBytes(exp.bytes)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = exp.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, exp.nombre)
            clipData = ClipData.newRawUri(exp.nombre, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Compartir ${exp.nombre}"))
    }.isSuccess
    if (!ok) Toast.makeText(ctx, "No se pudo compartir el archivo", Toast.LENGTH_LONG).show()
}

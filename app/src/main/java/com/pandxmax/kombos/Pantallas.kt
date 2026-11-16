@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pandxmax.kombos

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode

// ---------- Navegación ----------

@Composable
fun KombosApp(repo: Repositorio) {
    var pantalla by rememberSaveable { mutableStateOf("lista") }
    var actualId by rememberSaveable { mutableLongStateOf(-1L) }
    val campana = repo.campanas.firstOrNull { it.id == actualId }

    when {
        pantalla == "editar" && campana != null -> EditarScreen(
            c = campana,
            onChange = { repo.actualizar(it) },
            onBack = { pantalla = "lista" },
            onDelete = {
                repo.eliminar(campana.id)
                pantalla = "lista"
            },
            onReporte = { pantalla = "reporte" }
        )

        pantalla == "reporte" && campana != null -> ReporteScreen(
            c = campana,
            onBack = { pantalla = "editar" }
        )

        else -> ListaScreen(
            repo = repo,
            onOpen = {
                actualId = it
                pantalla = "editar"
            },
            onNueva = {
                actualId = repo.nueva().id
                pantalla = "editar"
            }
        )
    }
}

// ---------- Lista de campañas ----------

@Composable
fun ListaScreen(repo: Repositorio, onOpen: (Long) -> Unit, onNueva: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var acerca by remember { mutableStateOf(false) }
    var exp by remember { mutableStateOf<Exportacion?>(null) }

    val exportar = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(repo.exportar().toByteArray()) }
            }.isSuccess
            aviso(ctx, if (ok) "Datos exportados" else "No se pudo exportar")
        }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val r = runCatching {
                val texto = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                repo.importar(texto)
            }
            aviso(
                ctx,
                r.fold(
                    onSuccess = { if (it == 0) "No había campañas nuevas en el archivo" else "Se importaron $it campañas" },
                    onFailure = { "El archivo no es válido" }
                )
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Campañas", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Liquidación de lotes por campaña",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Exportar todo a Excel") },
                                enabled = repo.campanas.isNotEmpty(),
                                onClick = {
                                    menu = false
                                    exp = excelDe(repo.campanas.toList(), "Kombos campañas ${hoy()}")
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Copia de seguridad (exportar)") },
                                onClick = {
                                    menu = false
                                    exportar.launch("kombos-${hoy()}.json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restaurar copia (importar)") },
                                onClick = {
                                    menu = false
                                    importar.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Acerca de") },
                                onClick = {
                                    menu = false
                                    acerca = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNueva,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva campaña") }
            )
        }
    ) { pad ->
        if (repo.campanas.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Aún no hay campañas.\nCrea la primera para registrar sus lotes.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(repo.campanas, key = { it.id }) { c ->
                    val r = calcular(c)
                    Card(
                        onClick = { onOpen(c.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.nombre.ifBlank { "Campaña" },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${fechaVista(c.fecha)} · ${c.lotes.size} lotes · ${fmt(r.peso, 3)} TM",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "S/ ${fmt(r.porSocio)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (r.porSocio.signum() < 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "por socio",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    DialogoExportar(exp) { exp = null }

    if (acerca) {
        AlertDialog(
            onDismissRequest = { acerca = false },
            confirmButton = { TextButton(onClick = { acerca = false }) { Text("Cerrar") } },
            title = { Text("Kombos") },
            text = {
                Text(
                    "Versión ${BuildConfig.VERSION_NAME}\n\n" +
                        "Liquidación de campañas: lotes, flete, facturación, gastos " +
                        "y reparto de utilidades entre socios.\n\n" +
                        "Los cálculos se redondean a 2 decimales en cada paso, igual que a mano.\n\n" +
                        "Novedades 1.1: exportar a Excel y PDF."
                )
            }
        )
    }
}

// ---------- Edición de una campaña ----------

@Composable
fun EditarScreen(
    c: Campana,
    onChange: (Campana) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onReporte: () -> Unit
) {
    BackHandler(onBack = onBack)
    val r = calcular(c)
    var borrar by remember { mutableStateOf(false) }
    var elegirFecha by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        c.nombre.ifBlank { "Campaña" },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { borrar = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar campaña",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = { BarraTotal(r.porSocio, "Ver reporte", onReporte) }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Tarjeta {
                Titulo("Datos de la campaña")
                Campo("Nombre", c.nombre) { onChange(c.copy(nombre = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoFecha(c.fecha, Modifier.weight(1f)) { elegirFecha = true }
                    CampoNum("Tipo de cambio", c.tc, Modifier.weight(1f)) { onChange(c.copy(tc = it)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoNum("Flete \$/TM", c.flete, Modifier.weight(1f)) { onChange(c.copy(flete = it)) }
                    CampoNum("Facturación %", c.fact, Modifier.weight(1f)) { onChange(c.copy(fact = it)) }
                }
            }

            Tarjeta {
                Titulo("Lotes")
                c.lotes.forEachIndexed { i, l ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lote ${i + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (c.lotes.size > 1) {
                            IconButton(onClick = {
                                onChange(c.copy(lotes = c.lotes.filterIndexed { j, _ -> j != i }))
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar lote ${i + 1}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Campo("Descripción", l.desc) { v ->
                        onChange(c.copy(lotes = c.lotes.mapIndexed { j, x -> if (j == i) x.copy(desc = v) else x }))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampoNum("Peso (TM)", l.peso, Modifier.weight(1f)) { v ->
                            onChange(c.copy(lotes = c.lotes.mapIndexed { j, x -> if (j == i) x.copy(peso = v) else x }))
                        }
                        CampoNum("Ingreso (\$)", l.ingreso, Modifier.weight(1f)) { v ->
                            onChange(c.copy(lotes = c.lotes.mapIndexed { j, x -> if (j == i) x.copy(ingreso = v) else x }))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onChange(c.copy(lotes = c.lotes + Lote("Lote ${c.lotes.size + 1}"))) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar lote")
                }
            }

            Tarjeta {
                Titulo("Descuentos y socios")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoNum("Gasto campaña (S/)", c.gasto, Modifier.weight(1f)) { onChange(c.copy(gasto = it)) }
                    CampoNum("Caja (S/)", c.caja, Modifier.weight(1f)) { onChange(c.copy(caja = it)) }
                }
                Text(
                    "Número de socios (partes iguales)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onChange(c.copy(socios = maxOf(1, c.socios - 1))) }) {
                        Text("−", fontSize = 20.sp)
                    }
                    Text(
                        "${c.socios}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { onChange(c.copy(socios = c.socios + 1)) }) {
                        Text("+", fontSize = 20.sp)
                    }
                }
            }

            Tarjeta {
                Titulo("Liquidación")
                Lineas(c, r)
            }
        }
    }

    if (borrar) {
        AlertDialog(
            onDismissRequest = { borrar = false },
            title = { Text("¿Eliminar esta campaña?") },
            text = { Text("Se borrarán \"${c.nombre}\" y todos sus lotes. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    borrar = false
                    onDelete()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } }
        )
    }

    if (elegirFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = fechaAMillis(c.fecha))
        DatePickerDialog(
            onDismissRequest = { elegirFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onChange(c.copy(fecha = millisAFecha(it))) }
                    elegirFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { elegirFecha = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = estado)
        }
    }
}

// ---------- Reporte ----------

@Composable
fun ReporteScreen(c: Campana, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val r = calcular(c)
    var exp by remember { mutableStateOf<Exportacion?>(null) }

    DialogoExportar(exp) { exp = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Reporte de liquidación", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a editar")
                    }
                },
                actions = {
                    IconButton(onClick = { compartir(ctx, c, r) }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${c.nombre} · ${fechaVista(c.fecha)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Tarjeta {
                Titulo("Lotes")
                FilaTabla("Lote", "Peso (TM)", "Ingreso (\$)", encabezado = true)
                c.lotes.forEach { l ->
                    FilaTabla(l.desc.ifBlank { "—" }, fmt(num(l.peso), 3), fmt(num(l.ingreso)))
                }
                FilaTabla("Total", fmt(r.peso, 3), fmt(r.bruto), encabezado = true, ultima = true)
            }
            Tarjeta {
                Titulo("Liquidación")
                Lineas(c, r)
            }
            Tarjeta {
                Titulo("Utilidad por socio")
                Text(
                    "S/ ${fmt(r.porSocio)}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (r.porSocio.signum() < 0) MaterialTheme.colorScheme.error else dorado()
                )
            }
            Text(
                "Exportar",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { exp = excelDe(listOf(c), "Liquidación ${c.nombre} ${c.fecha}") },
                    modifier = Modifier.weight(1f)
                ) { Text("Excel") }
                Button(
                    onClick = { exp = pdfDe(c) },
                    modifier = Modifier.weight(1f)
                ) { Text("PDF") }
            }
            OutlinedButton(onClick = { compartir(ctx, c, r) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir como texto")
            }
        }
    }
}

// ---------- Piezas comunes ----------

@Composable
fun Tarjeta(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Titulo(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
fun Campo(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    )
}

@Composable
fun CampoNum(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(limpiarDecimal(it)) },
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun CampoFecha(fecha: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier) {
        OutlinedTextField(
            value = fechaVista(fecha),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            singleLine = true,
            label = { Text("Fecha") },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
    }
}

@Composable
fun BarraTotal(porSocio: BigDecimal, boton: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Utilidad por socio", fontSize = 12.sp)
                Text("S/ ${fmt(porSocio)}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) { Text(boton) }
        }
    }
}

@Composable
fun Lineas(c: Campana, r: Resultado) {
    val pct = BigDecimal(100).divide(BigDecimal(r.socios), 1, RoundingMode.HALF_UP)
    Column {
        Linea("Peso total", "${fmt(r.peso, 3)} TM")
        Linea("Ingreso bruto", "\$ ${fmt(r.bruto)}")
        Linea("Flete (${fmt(num(c.flete))} \$/TM)", "− \$ ${fmt(r.flete)}", negativo = true)
        Linea("Facturación (${fmt(num(c.fact))} %)", "− \$ ${fmt(r.fact)}", negativo = true)
        Linea("Ingreso neto", "\$ ${fmt(r.neto)}", total = true)
        Linea("Neto en soles (TC ${fmt(num(c.tc))})", "S/ ${fmt(r.netoSoles)}")
        Linea("Gasto de campaña", "− S/ ${fmt(num(c.gasto))}", negativo = true)
        Linea("Caja", "− S/ ${fmt(num(c.caja))}", negativo = true)
        Linea("Utilidades", "S/ ${fmt(r.utilidades)}", total = true)
        Linea("Por socio (${r.socios} · ${fmt(pct, 1)} % c/u)", "S/ ${fmt(r.porSocio)}", total = true, ultima = true)
    }
}

@Composable
fun Linea(etiqueta: String, valor: String, negativo: Boolean = false, total: Boolean = false, ultima: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            etiqueta,
            modifier = Modifier.weight(1f),
            fontWeight = if (total) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            valor,
            fontWeight = if (total) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (negativo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
    if (!ultima) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun FilaTabla(a: String, b: String, c: String, encabezado: Boolean = false, ultima: Boolean = false) {
    val peso = if (encabezado) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(a, Modifier.weight(1.4f), fontSize = 14.sp, fontWeight = peso)
        Text(b, Modifier.weight(1f), fontSize = 14.sp, fontWeight = peso, textAlign = TextAlign.End)
        Text(c, Modifier.weight(1.2f), fontSize = 14.sp, fontWeight = peso, textAlign = TextAlign.End)
    }
    if (!ultima) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun compartir(ctx: Context, c: Campana, r: Resultado) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Liquidación · ${c.nombre}")
        putExtra(Intent.EXTRA_TEXT, textoReporte(c, r))
    }
    ctx.startActivity(Intent.createChooser(intent, "Compartir reporte"))
}

private fun aviso(ctx: Context, texto: String) {
    Toast.makeText(ctx, texto, Toast.LENGTH_LONG).show()
}

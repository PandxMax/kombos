package com.pandxmax.kombos

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un lote: los números se guardan como texto tal como los escribe el usuario. */
data class Lote(
    val desc: String = "",
    val peso: String = "",
    val ingreso: String = ""
)

data class Campana(
    val id: Long,
    val nombre: String,
    val fecha: String,              // yyyy-MM-dd (fecha local)
    val flete: String = "75",       // $ por TM
    val fact: String = "2",         // % sobre el ingreso bruto
    val tc: String = "3.34",        // S/ por $
    val gasto: String = "0",        // S/
    val caja: String = "0",         // S/
    val socios: Int = 5,
    val lotes: List<Lote> = listOf(Lote("Lote 1"))
)

data class Resultado(
    val peso: BigDecimal,
    val bruto: BigDecimal,
    val flete: BigDecimal,
    val fact: BigDecimal,
    val neto: BigDecimal,
    val netoSoles: BigDecimal,
    val utilidades: BigDecimal,
    val socios: Int,
    val porSocio: BigDecimal
)

/** Deja solo dígitos y un punto decimal. La coma se convierte en punto. */
fun limpiarDecimal(s: String): String {
    val sb = StringBuilder()
    var punto = false
    for (ch in s) {
        when {
            ch.isDigit() -> sb.append(ch)
            (ch == '.' || ch == ',') && !punto -> {
                sb.append('.')
                punto = true
            }
        }
    }
    return sb.toString()
}

fun num(s: String): BigDecimal = limpiarDecimal(s).toBigDecimalOrNull() ?: BigDecimal.ZERO

private fun BigDecimal.r(decimales: Int): BigDecimal = setScale(decimales, RoundingMode.HALF_UP)

/**
 * Igual que la hoja de cálculo a mano: cada paso se redondea a 2 decimales
 * (el peso a 3) antes de usarse en el siguiente.
 */
fun calcular(c: Campana): Resultado {
    val peso = c.lotes.fold(BigDecimal.ZERO) { acc, l -> acc + num(l.peso) }.r(3)
    val bruto = c.lotes.fold(BigDecimal.ZERO) { acc, l -> acc + num(l.ingreso) }.r(2)
    val flete = (peso * num(c.flete)).r(2)
    val fact = (bruto * num(c.fact)).movePointLeft(2).r(2)
    val neto = bruto - flete - fact
    val netoSoles = (neto * num(c.tc)).r(2)
    val utilidades = netoSoles - num(c.gasto).r(2) - num(c.caja).r(2)
    val ns = maxOf(1, c.socios)
    val porSocio = utilidades.divide(BigDecimal(ns), 2, RoundingMode.HALF_UP)
    return Resultado(peso, bruto, flete, fact, neto, netoSoles, utilidades, ns, porSocio)
}

private val simbolos = DecimalFormatSymbols(Locale.US) // punto decimal, coma de miles

fun fmt(v: BigDecimal, decimales: Int = 2): String {
    val patron = if (decimales > 0) "#,##0." + "0".repeat(decimales) else "#,##0"
    val df = DecimalFormat(patron, simbolos)
    df.roundingMode = RoundingMode.HALF_UP
    return df.format(v)
}

fun hoy(): String = LocalDate.now().toString()

fun fechaVista(f: String): String = try {
    LocalDate.parse(f).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
} catch (e: Exception) {
    f
}

fun fechaAMillis(f: String): Long {
    val d = try { LocalDate.parse(f) } catch (e: Exception) { LocalDate.now() }
    return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

fun millisAFecha(m: Long): String =
    Instant.ofEpochMilli(m).atZone(ZoneOffset.UTC).toLocalDate().toString()

/** Texto plano del reporte para compartir por WhatsApp, correo, etc. */
fun textoReporte(c: Campana, r: Resultado): String = buildString {
    appendLine("REPORTE DE LIQUIDACIÓN")
    appendLine("${c.nombre} · ${fechaVista(c.fecha)}")
    appendLine()
    appendLine("LOTES")
    c.lotes.forEach { l ->
        appendLine("• ${l.desc.ifBlank { "Lote" }}: ${fmt(num(l.peso), 3)} TM · \$ ${fmt(num(l.ingreso))}")
    }
    appendLine("Total: ${fmt(r.peso, 3)} TM · \$ ${fmt(r.bruto)}")
    appendLine()
    appendLine("LIQUIDACIÓN")
    appendLine("Ingreso bruto: \$ ${fmt(r.bruto)}")
    appendLine("Flete (${fmt(num(c.flete))} \$/TM): − \$ ${fmt(r.flete)}")
    appendLine("Facturación (${fmt(num(c.fact))} %): − \$ ${fmt(r.fact)}")
    appendLine("Ingreso neto: \$ ${fmt(r.neto)}")
    appendLine("Neto en soles (TC ${fmt(num(c.tc))}): S/ ${fmt(r.netoSoles)}")
    appendLine("Gasto de campaña: − S/ ${fmt(num(c.gasto))}")
    appendLine("Caja: − S/ ${fmt(num(c.caja))}")
    appendLine("Utilidades: S/ ${fmt(r.utilidades)}")
    appendLine()
    append("UTILIDAD POR SOCIO (${r.socios}): S/ ${fmt(r.porSocio)}")
}

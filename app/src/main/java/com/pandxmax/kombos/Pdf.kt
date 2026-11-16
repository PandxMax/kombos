package com.pandxmax.kombos

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode

const val MIME_PDF = "application/pdf"

/** Reporte de una campaña en PDF (tamaño A4), con el mismo contenido que la pantalla de reporte. */
fun generarPdf(c: Campana): ByteArray {
    val r = calcular(c)
    val ancho = 595
    val alto = 842
    val m = 48f
    val der = ancho - m

    val acento = Color.rgb(15, 107, 92)
    val tinta = Color.rgb(22, 33, 31)
    val gris = Color.rgb(93, 107, 103)
    val rojo = Color.rgb(179, 57, 47)

    fun pincel(tam: Float, color: Int = tinta, negrita: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tam
            this.color = color
            typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    val pTitulo = pincel(20f, acento, true)
    val pSub = pincel(11f, gris)
    val pSeccion = pincel(12f, acento, true)
    val pNormal = pincel(11f)
    val pGris = pincel(11f, gris)
    val pNegrita = pincel(11f, tinta, true)
    val pRojo = pincel(11f, rojo)
    val pPie = pincel(8f, gris)
    val pLinea = Paint().apply { color = Color.rgb(211, 217, 212); strokeWidth = 0.8f }
    val pAcento = Paint().apply { color = acento; strokeWidth = 1.5f }

    val doc = PdfDocument()
    var numPagina = 0
    var pagina: PdfDocument.Page? = null
    lateinit var cv: Canvas
    var y = 0f

    fun pie() {
        cv.drawText("Generado con Kombos · ${fechaVista(hoy())}", m, alto - 24f, pPie)
        val t = "Página $numPagina"
        cv.drawText(t, der - pPie.measureText(t), alto - 24f, pPie)
    }

    fun nuevaPagina() {
        pagina?.let {
            pie()
            doc.finishPage(it)
        }
        numPagina++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, numPagina).create())
        pagina = p
        cv = p.canvas
        y = m
    }

    /** Baja [alto] puntos; si no entra en la página, empieza otra. */
    fun bajar(h: Float) {
        if (y + h > alto - 56f) nuevaPagina()
        y += h
    }

    fun texto(t: String, x: Float, p: Paint) = cv.drawText(t, x, y, p)
    fun textoDer(t: String, x: Float, p: Paint) = cv.drawText(t, x - p.measureText(t), y, p)
    fun linea(p: Paint = pLinea) = cv.drawLine(m, y + 6f, der, y + 6f, p)
    fun cortar(t: String, max: Float, p: Paint): String {
        if (p.measureText(t) <= max) return t
        var s = t
        while (s.isNotEmpty() && p.measureText("$s…") > max) s = s.dropLast(1)
        return "$s…"
    }

    nuevaPagina()

    // Encabezado
    bajar(20f)
    texto("Reporte de liquidación", m, pTitulo)
    bajar(18f)
    texto(cortar("${c.nombre} · ${fechaVista(c.fecha)}", der - m, pSub), m, pSub)
    linea(pAcento)

    // Tabla de lotes
    val colPeso = der - 140f
    bajar(34f)
    texto("LOTES", m, pSeccion)
    bajar(20f)
    texto("Lote", m, pNegrita)
    textoDer("Peso (TM)", colPeso, pNegrita)
    textoDer("Ingreso (\$)", der, pNegrita)
    linea()
    c.lotes.forEach { l ->
        bajar(18f)
        texto(cortar(l.desc.ifBlank { "—" }, colPeso - m - 90f, pNormal), m, pNormal)
        textoDer(fmt(num(l.peso), 3), colPeso, pNormal)
        textoDer(fmt(num(l.ingreso)), der, pNormal)
        linea()
    }
    bajar(18f)
    texto("Total", m, pNegrita)
    textoDer(fmt(r.peso, 3), colPeso, pNegrita)
    textoDer(fmt(r.bruto), der, pNegrita)

    // Liquidación
    bajar(36f)
    texto("LIQUIDACIÓN", m, pSeccion)
    val pct = BigDecimal(100).divide(BigDecimal(r.socios), 1, RoundingMode.HALF_UP)
    val filas = listOf(
        Triple("Peso total", "${fmt(r.peso, 3)} TM", 0),
        Triple("Ingreso bruto", "\$ ${fmt(r.bruto)}", 0),
        Triple("Flete (${fmt(num(c.flete))} \$/TM)", "− \$ ${fmt(r.flete)}", 1),
        Triple("Facturación (${fmt(num(c.fact))} %)", "− \$ ${fmt(r.fact)}", 1),
        Triple("Ingreso neto", "\$ ${fmt(r.neto)}", 2),
        Triple("Neto en soles (TC ${fmt(num(c.tc))})", "S/ ${fmt(r.netoSoles)}", 0),
        Triple("Gasto de campaña", "− S/ ${fmt(num(c.gasto))}", 1),
        Triple("Caja", "− S/ ${fmt(num(c.caja))}", 1),
        Triple("Utilidades", "S/ ${fmt(r.utilidades)}", 2),
        Triple("Por socio (${r.socios} · ${fmt(pct, 1)} % c/u)", "S/ ${fmt(r.porSocio)}", 2)
    )
    filas.forEachIndexed { i, (etq, valor, tipo) ->
        bajar(20f)
        texto(etq, m, if (tipo == 2) pNegrita else pGris)
        textoDer(valor, der, when (tipo) { 1 -> pRojo; 2 -> pNegrita; else -> pNormal })
        if (i < filas.lastIndex) linea()
    }

    // Recuadro con la utilidad por socio
    bajar(28f)
    if (y + 64f > alto - 56f) {
        nuevaPagina()
    }
    val caja = RectF(m, y, der, y + 64f)
    cv.drawRoundRect(caja, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = acento })
    val pBlanco = pincel(11f, Color.WHITE)
    val pGrande = pincel(24f, Color.WHITE, true)
    y += 24f
    texto("Utilidad por socio", m + 16f, pBlanco)
    y += 28f
    texto("S/ ${fmt(r.porSocio)}", m + 16f, pGrande)

    pagina?.let {
        pie()
        doc.finishPage(it)
    }
    val out = ByteArrayOutputStream()
    doc.writeTo(out)
    doc.close()
    return out.toByteArray()
}

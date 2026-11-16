package com.pandxmax.kombos

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * Genera un archivo .xlsx (Excel) sin librerías externas.
 * Los cálculos se escriben como fórmulas de Excel (con el resultado ya guardado),
 * así el archivo se puede seguir editando y recalcula solo.
 */

const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

// Estilos (índices de cellXfs en styles.xml)
private const val E_NORMAL = 0
private const val E_NEGRITA = 1
private const val E_TM = 2
private const val E_MONTO = 3
private const val E_TM_N = 4
private const val E_MONTO_N = 5
private const val E_TITULO = 6
private const val E_ENC = 7
private const val E_ENC_DER = 8

private sealed class Celda(val estilo: Int) {
    class Txt(val s: String, estilo: Int = E_NORMAL) : Celda(estilo)
    class Num(val v: BigDecimal, estilo: Int = E_NORMAL) : Celda(estilo)
    class Fml(val f: String, val v: BigDecimal, estilo: Int = E_NORMAL) : Celda(estilo)
}

private class Hoja(val nombre: String, val anchos: List<Double>) {
    private val filas = sortedMapOf<Int, java.util.SortedMap<Int, Celda>>()

    fun set(fila: Int, col: Int, c: Celda) {
        filas.getOrPut(fila) { sortedMapOf() }[col] = c
    }

    fun xml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("<cols>")
        anchos.forEachIndexed { i, w ->
            append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        filas.forEach { (f, celdas) ->
            append("""<row r="$f">""")
            celdas.forEach { (col, c) ->
                val ref = "${letra(col)}$f"
                when (c) {
                    is Celda.Txt -> append(
                        """<c r="$ref" s="${c.estilo}" t="inlineStr"><is><t xml:space="preserve">${esc(c.s)}</t></is></c>"""
                    )
                    is Celda.Num -> append("""<c r="$ref" s="${c.estilo}"><v>${c.v.toPlainString()}</v></c>""")
                    is Celda.Fml -> append(
                        """<c r="$ref" s="${c.estilo}"><f>${esc(c.f)}</f><v>${c.v.toPlainString()}</v></c>"""
                    )
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }
}

private fun letra(col: Int): String = ('A' + (col - 1)).toString()

private fun esc(s: String): String = buildString {
    for (ch in s) {
        when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> {} // caracteres no válidos en XML
            else -> append(ch)
        }
    }
}

private fun dos(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP)

private fun hojaCampana(c: Campana, nombreHoja: String): Hoja {
    val r = calcular(c)
    val h = Hoja(nombreHoja, listOf(32.0, 16.0, 16.0))

    h.set(1, 1, Celda.Txt("Reporte de liquidación", E_TITULO))
    h.set(2, 1, Celda.Txt(c.nombre, E_NEGRITA))
    h.set(2, 2, Celda.Txt("Fecha"))
    h.set(2, 3, Celda.Txt(fechaVista(c.fecha)))

    // Lotes
    h.set(4, 1, Celda.Txt("Lote", E_ENC))
    h.set(4, 2, Celda.Txt("Peso (TM)", E_ENC_DER))
    h.set(4, 3, Celda.Txt("Ingreso (\$)", E_ENC_DER))
    var f = 5
    c.lotes.forEach { l ->
        h.set(f, 1, Celda.Txt(l.desc))
        h.set(f, 2, Celda.Num(num(l.peso), E_TM))
        h.set(f, 3, Celda.Num(num(l.ingreso), E_MONTO))
        f++
    }
    val ult = f - 1
    val t = f
    h.set(t, 1, Celda.Txt("Total", E_NEGRITA))
    h.set(t, 2, Celda.Fml("ROUND(SUM(B5:B$ult),3)", r.peso, E_TM_N))
    h.set(t, 3, Celda.Fml("ROUND(SUM(C5:C$ult),2)", r.bruto, E_MONTO_N))

    // Datos de la campaña (se pueden cambiar en Excel y todo recalcula)
    val p = t + 2
    h.set(p, 1, Celda.Txt("DATOS", E_ENC))
    h.set(p, 2, Celda.Txt("", E_ENC))
    val d = p + 1
    h.set(d, 1, Celda.Txt("Flete por TM (\$)"))
    h.set(d, 2, Celda.Num(num(c.flete), E_MONTO))
    h.set(d + 1, 1, Celda.Txt("Facturación (%)"))
    h.set(d + 1, 2, Celda.Num(num(c.fact)))
    h.set(d + 2, 1, Celda.Txt("Tipo de cambio (S/ por \$)"))
    h.set(d + 2, 2, Celda.Num(num(c.tc)))
    h.set(d + 3, 1, Celda.Txt("Gasto de campaña (S/)"))
    h.set(d + 3, 2, Celda.Num(num(c.gasto), E_MONTO))
    h.set(d + 4, 1, Celda.Txt("Caja (S/)"))
    h.set(d + 4, 2, Celda.Num(num(c.caja), E_MONTO))
    h.set(d + 5, 1, Celda.Txt("Número de socios"))
    h.set(d + 5, 2, Celda.Num(BigDecimal(r.socios)))

    // Liquidación con fórmulas (redondeo a 2 decimales en cada paso)
    val q = d + 7
    h.set(q, 1, Celda.Txt("LIQUIDACIÓN", E_ENC))
    h.set(q, 2, Celda.Txt("", E_ENC))
    h.set(q + 1, 1, Celda.Txt("Peso total (TM)"))
    h.set(q + 1, 2, Celda.Fml("B$t", r.peso, E_TM))
    h.set(q + 2, 1, Celda.Txt("Ingreso bruto (\$)"))
    h.set(q + 2, 2, Celda.Fml("C$t", r.bruto, E_MONTO))
    h.set(q + 3, 1, Celda.Txt("(−) Flete (\$)"))
    h.set(q + 3, 2, Celda.Fml("ROUND(B${q + 1}*B$d,2)", r.flete, E_MONTO))
    h.set(q + 4, 1, Celda.Txt("(−) Facturación (\$)"))
    h.set(q + 4, 2, Celda.Fml("ROUND(B${q + 2}*B${d + 1}/100,2)", r.fact, E_MONTO))
    h.set(q + 5, 1, Celda.Txt("Ingreso neto (\$)", E_NEGRITA))
    h.set(q + 5, 2, Celda.Fml("B${q + 2}-B${q + 3}-B${q + 4}", r.neto, E_MONTO_N))
    h.set(q + 6, 1, Celda.Txt("Neto en soles (S/)"))
    h.set(q + 6, 2, Celda.Fml("ROUND(B${q + 5}*B${d + 2},2)", r.netoSoles, E_MONTO))
    h.set(q + 7, 1, Celda.Txt("(−) Gasto de campaña (S/)"))
    h.set(q + 7, 2, Celda.Fml("ROUND(B${d + 3},2)", dos(num(c.gasto)), E_MONTO))
    h.set(q + 8, 1, Celda.Txt("(−) Caja (S/)"))
    h.set(q + 8, 2, Celda.Fml("ROUND(B${d + 4},2)", dos(num(c.caja)), E_MONTO))
    h.set(q + 9, 1, Celda.Txt("Utilidades (S/)", E_NEGRITA))
    h.set(q + 9, 2, Celda.Fml("B${q + 6}-B${q + 7}-B${q + 8}", r.utilidades, E_MONTO_N))
    h.set(q + 10, 1, Celda.Txt("Utilidad por socio (S/)", E_NEGRITA))
    h.set(q + 10, 2, Celda.Fml("ROUND(B${q + 9}/MAX(1,B${d + 5}),2)", r.porSocio, E_MONTO_N))
    return h
}

private fun hojaResumen(campanas: List<Campana>): Hoja {
    val h = Hoja("Resumen", listOf(30.0, 12.0, 8.0, 12.0, 16.0, 16.0, 8.0, 16.0))
    h.set(1, 1, Celda.Txt("Resumen de campañas", E_TITULO))
    val enc = listOf("Campaña", "Fecha", "Lotes", "Peso (TM)", "Ingreso bruto (\$)", "Utilidades (S/)", "Socios", "Por socio (S/)")
    enc.forEachIndexed { i, s -> h.set(3, i + 1, Celda.Txt(s, if (i == 0 || i == 1) E_ENC else E_ENC_DER)) }
    var f = 4
    var peso = BigDecimal.ZERO
    var bruto = BigDecimal.ZERO
    var ut = BigDecimal.ZERO
    campanas.forEach { c ->
        val r = calcular(c)
        h.set(f, 1, Celda.Txt(c.nombre))
        h.set(f, 2, Celda.Txt(fechaVista(c.fecha)))
        h.set(f, 3, Celda.Num(BigDecimal(c.lotes.size)))
        h.set(f, 4, Celda.Num(r.peso, E_TM))
        h.set(f, 5, Celda.Num(r.bruto, E_MONTO))
        h.set(f, 6, Celda.Num(r.utilidades, E_MONTO))
        h.set(f, 7, Celda.Num(BigDecimal(r.socios)))
        h.set(f, 8, Celda.Num(r.porSocio, E_MONTO))
        peso += r.peso
        bruto += r.bruto
        ut += r.utilidades
        f++
    }
    val ult = f - 1
    h.set(f, 1, Celda.Txt("Total", E_NEGRITA))
    h.set(f, 4, Celda.Fml("SUM(D4:D$ult)", peso, E_TM_N))
    h.set(f, 5, Celda.Fml("SUM(E4:E$ult)", bruto, E_MONTO_N))
    h.set(f, 6, Celda.Fml("SUM(F4:F$ult)", ut, E_MONTO_N))
    return h
}

/** Nombre válido para una hoja de Excel: máx. 31 caracteres, sin \ / : * ? [ ] y sin repetir. */
private fun nombreHoja(base: String, usados: MutableSet<String>): String {
    val limpio = base.replace(Regex("""[\\/:*?\[\]]"""), " ").replace(Regex("\\s+"), " ").trim()
        .trim('\'').ifBlank { "Campaña" }
    var n = limpio.take(31).trim()
    var i = 2
    while (usados.any { it.equals(n, ignoreCase = true) }) {
        val suf = " ($i)"
        n = limpio.take(31 - suf.length).trim() + suf
        i++
    }
    usados += n
    return n
}

/** Una campaña → una hoja. Varias → hoja "Resumen" + una hoja por campaña. */
fun generarExcel(campanas: List<Campana>): ByteArray {
    val usados = mutableSetOf<String>()
    val hojas = mutableListOf<Hoja>()
    if (campanas.size > 1) {
        hojas += hojaResumen(campanas)
        usados += "Resumen"
    }
    campanas.forEach { hojas += hojaCampana(it, nombreHoja(it.nombre, usados)) }

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        fun archivo(ruta: String, contenido: String) {
            zip.putNextEntry(ZipEntry(ruta))
            zip.write(contenido.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        archivo("[Content_Types].xml", buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            hojas.indices.forEach {
                append("""<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            append("</Types>")
        })

        archivo("_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                "</Relationships>"
        )

        archivo("xl/workbook.xml", buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            append("<sheets>")
            hojas.forEachIndexed { i, h ->
                append("""<sheet name="${esc(h.nombre)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
            }
            append("</sheets>")
            append("""<calcPr fullCalcOnLoad="1"/>""")
            append("</workbook>")
        })

        archivo("xl/_rels/workbook.xml.rels", buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            hojas.indices.forEach {
                append("""<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""")
            }
            append("""<Relationship Id="rId${hojas.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("</Relationships>")
        })

        archivo("xl/styles.xml", ESTILOS)

        hojas.forEachIndexed { i, h -> archivo("xl/worksheets/sheet${i + 1}.xml", h.xml()) }
    }
    return out.toByteArray()
}

private val ESTILOS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.000"/></numFmts>""" +
        """<fonts count="3">""" +
        """<font><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="14"/><color rgb="FF0F6B5C"/><name val="Calibri"/><family val="2"/></font>""" +
        """</fonts>""" +
        """<fills count="3">""" +
        """<fill><patternFill patternType="none"/></fill>""" +
        """<fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFCDE8E1"/><bgColor indexed="64"/></patternFill></fill>""" +
        """</fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="9">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
        """<xf numFmtId="4" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
        """<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="right"/></xf>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""

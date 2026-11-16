package com.pandxmax.kombos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ExportarTest {

    private val ejemplo = Campana(
        id = 1, nombre = "Campaña 1", fecha = "2026-11-15",
        flete = "75", fact = "2", tc = "3.34", gasto = "10000", caja = "10000", socios = 5,
        lotes = listOf(Lote("Lote 1", "9.590", "8437.51"), Lote("Lote 2", "13.500", "5993.84"))
    )

    @Test
    fun calculoIgualAlCuaderno() {
        val r = calcular(ejemplo)
        assertEquals("12410.97", r.neto.toPlainString())
        assertEquals("41452.64", r.netoSoles.toPlainString())
        assertEquals("21452.64", r.utilidades.toPlainString())
        assertEquals("4290.53", r.porSocio.toPlainString())
    }

    @Test
    fun comaDecimal() {
        assertEquals("9.59", limpiarDecimal("9,59"))
        assertEquals("10.0005", limpiarDecimal("10.000,5")) // solo vale el primer separador
    }

    @Test
    fun generaExcel() {
        val dir = File("build/test-exports").apply { mkdirs() }
        File(dir, "una-campana.xlsx").writeBytes(generarExcel(listOf(ejemplo)))
        val otra = ejemplo.copy(id = 2, nombre = "Campaña 2: prueba/[x] & <más>", socios = 3)
        File(dir, "varias-campanas.xlsx").writeBytes(generarExcel(listOf(ejemplo, otra, otra.copy(id = 3))))
    }
}

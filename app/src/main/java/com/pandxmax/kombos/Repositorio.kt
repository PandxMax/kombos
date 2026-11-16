package com.pandxmax.kombos

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda las campañas en un archivo JSON dentro de la app. */
class Repositorio(context: Context) {

    val campanas = mutableStateListOf<Campana>()
    private val archivo = File(context.filesDir, "campanas.json")

    init {
        val guardadas = try {
            if (archivo.exists()) desdeJson(archivo.readText()) else null
        } catch (e: Exception) {
            null
        }
        campanas.addAll(guardadas ?: listOf(ejemplo()))
    }

    private fun guardar() {
        val tmp = File(archivo.parentFile, "campanas.json.tmp")
        tmp.writeText(aJson(campanas))
        if (!tmp.renameTo(archivo)) {
            archivo.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun nueva(): Campana {
        val c = Campana(
            id = System.currentTimeMillis(),
            nombre = "Campaña ${campanas.size + 1}",
            fecha = hoy()
        )
        campanas.add(0, c)
        guardar()
        return c
    }

    fun actualizar(c: Campana) {
        val i = campanas.indexOfFirst { it.id == c.id }
        if (i >= 0) {
            campanas[i] = c
            guardar()
        }
    }

    fun eliminar(id: Long) {
        campanas.removeAll { it.id == id }
        guardar()
    }

    fun exportar(): String = aJson(campanas)

    /** Agrega las campañas del archivo que todavía no existen. Devuelve cuántas se agregaron. */
    fun importar(texto: String): Int {
        val nuevas = desdeJson(texto).filter { n -> campanas.none { it.id == n.id } }
        campanas.addAll(nuevas)
        if (nuevas.isNotEmpty()) guardar()
        return nuevas.size
    }

    private fun ejemplo() = Campana(
        id = 1,
        nombre = "Campaña 1 (ejemplo)",
        fecha = "2026-11-15",
        flete = "75",
        fact = "2",
        tc = "3.34",
        gasto = "10000",
        caja = "10000",
        socios = 5,
        lotes = listOf(
            Lote("Lote 1", "9.590", "8437.51"),
            Lote("Lote 2", "13.500", "5993.84")
        )
    )

    companion object {
        fun aJson(lista: List<Campana>): String {
            val arr = JSONArray()
            lista.forEach { c ->
                val lotes = JSONArray()
                c.lotes.forEach { l ->
                    lotes.put(JSONObject().put("d", l.desc).put("p", l.peso).put("i", l.ingreso))
                }
                arr.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("nombre", c.nombre)
                        .put("fecha", c.fecha)
                        .put("flete", c.flete)
                        .put("fact", c.fact)
                        .put("tc", c.tc)
                        .put("gasto", c.gasto)
                        .put("caja", c.caja)
                        .put("socios", c.socios)
                        .put("lotes", lotes)
                )
            }
            return JSONObject()
                .put("app", "kombos")
                .put("formato", 1)
                .put("campanas", arr)
                .toString(2)
        }

        /** Acepta el formato de Kombos o una lista simple (como la del prototipo HTML). */
        fun desdeJson(texto: String): List<Campana> {
            val t = texto.trim()
            val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("campanas")
            val base = System.currentTimeMillis()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ls = o.optJSONArray("lotes") ?: JSONArray()
                val lotes = (0 until ls.length()).map { j ->
                    val l = ls.getJSONObject(j)
                    Lote(l.optString("d"), l.optString("p"), l.optString("i"))
                }
                Campana(
                    id = o.optLong("id", base + i),
                    nombre = o.optString("nombre", "Campaña"),
                    fecha = o.optString("fecha", hoy()),
                    flete = o.optString("flete", "75"),
                    fact = o.optString("fact", "2"),
                    tc = o.optString("tc", "3.34"),
                    gasto = o.optString("gasto", "0"),
                    caja = o.optString("caja", "0"),
                    socios = maxOf(1, o.optInt("socios", 5)),
                    lotes = lotes.ifEmpty { listOf(Lote("Lote 1")) }
                )
            }
        }
    }
}

// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class Recente(val uri: String, val nome: String, val quando: Long)

/** Preferências do app (tema, cor de destaque, arquivos recentes). */
class Config(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hshpdf", Context.MODE_PRIVATE)

    var tema: String
        get() = sp.getString("tema", "sistema") ?: "sistema"
        set(v) { sp.edit().putString("tema", v).apply() }

    var acento: Int
        get() = sp.getInt("acento", 0xFFE84A4A.toInt())
        set(v) { sp.edit().putInt("acento", v).apply() }

    var miniAberto: Boolean
        get() = sp.getBoolean("mini_aberto", true)
        set(v) { sp.edit().putBoolean("mini_aberto", v).apply() }

    fun recentes(): List<Recente> {
        return try {
            val arr = JSONArray(sp.getString("recentes", "[]") ?: "[]")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Recente(o.getString("uri"), o.getString("nome"), o.optLong("quando"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun gravarRecentes(lista: List<Recente>) {
        val arr = JSONArray()
        lista.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("nome", it.nome).put("quando", it.quando))
        }
        sp.edit().putString("recentes", arr.toString()).apply()
    }

    fun adicionarRecente(uri: Uri, nome: String) {
        val resto = recentes().filter { it.uri != uri.toString() }
        gravarRecentes((listOf(Recente(uri.toString(), nome, System.currentTimeMillis())) + resto).take(15))
    }

    fun removerRecente(r: Recente) {
        gravarRecentes(recentes().filter { it.uri != r.uri })
    }
}

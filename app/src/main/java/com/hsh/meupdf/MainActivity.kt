// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val uriExterna = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uriExterna.value = intent?.data
        val cfg = Config(this)
        setContent { App(cfg, uriExterna) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uriExterna.value = intent.data
    }
}

@Composable
fun App(cfg: Config, uriExterna: MutableState<Uri?>) {
    val ctx = LocalContext.current
    var tema by remember { mutableStateOf(cfg.tema) }
    var acento by remember { mutableStateOf(cfg.acento) }
    var aberto by remember { mutableStateOf<DocAberto?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var recentes by remember { mutableStateOf(cfg.recentes()) }
    val escopo = rememberCoroutineScope()

    fun abrirUri(uri: Uri) {
        escopo.launch {
            carregando = true
            try {
                val novo = Abertura.abrir(ctx, uri)
                aberto?.fonte?.fechar()
                aberto = novo
                cfg.adicionarRecente(uri, novo.nome)
                recentes = cfg.recentes()
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = false
            }
        }
    }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            abrirUri(uri)
        }
    }

    LaunchedEffect(uriExterna.value) {
        uriExterna.value?.let {
            abrirUri(it)
            uriExterna.value = null
        }
    }

    BackHandler(enabled = aberto != null) {
        aberto?.fonte?.fechar()
        aberto = null
    }

    HshTema(tema, acento) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val doc = aberto
            if (doc == null) {
                InicioTela(
                    cfg = cfg, tema = tema, carregando = carregando, erro = erro, recentes = recentes,
                    onAbrir = { seletor.launch(arrayOf("*/*")) },
                    onRecente = { abrirUri(Uri.parse(it.uri)) },
                    onRemover = { cfg.removerRecente(it); recentes = cfg.recentes() },
                    onTema = { tema = it; cfg.tema = it },
                    onAcento = { acento = it; cfg.acento = it },
                    onFecharErro = { erro = null }
                )
            } else {
                LeitorTela(doc, cfg, onVoltar = { doc.fonte.fechar(); aberto = null })
            }
        }
    }
}

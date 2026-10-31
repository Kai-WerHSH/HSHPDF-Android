// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun compartilhar(ctx: Context, doc: DocAberto) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", doc.arquivo)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Compartilhar"))
}

@Composable
fun LeitorTela(doc: DocAberto, cfg: Config, onVoltar: () -> Unit) {
    val ctx = LocalContext.current
    val fonte = doc.fonte
    val escopo = rememberCoroutineScope()
    val lista = rememberLazyListState()
    var zoom by remember { mutableFloatStateOf(1f) }
    var miniAberto by remember { mutableStateOf(cfg.miniAberto) }
    var mostrarMini by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var buscaAberta by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Resultado>>(emptyList()) }
    var atual by remember { mutableIntStateOf(-1) }
    var buscando by remember { mutableStateOf(false) }
    var irPara by remember { mutableStateOf(false) }
    val paginaAtual by remember { derivedStateOf { lista.firstVisibleItemIndex + 1 } }
    val porPagina = remember(resultados) { resultados.groupBy { it.pagina } }

    fun buscar() {
        if (texto.isBlank()) return
        escopo.launch {
            buscando = true
            resultados = try { fonte.buscar(texto.trim()) } catch (e: Exception) { emptyList() }
            atual = if (resultados.isEmpty()) -1 else 0
            buscando = false
            if (atual >= 0) lista.animateScrollToItem(resultados[0].pagina)
        }
    }

    fun mover(d: Int) {
        if (resultados.isEmpty()) return
        atual = (atual + d + resultados.size) % resultados.size
        escopo.launch { lista.animateScrollToItem(resultados[atual].pagina) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---- barra superior ----
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onVoltar) { Text("‹ Voltar") }
                Text(doc.nome, modifier = Modifier.weight(1f), maxLines = 1, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                if (fonte.permiteBusca) TextButton(onClick = { buscaAberta = !buscaAberta }) { Text("Buscar") }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ir para a página...") }, onClick = { menu = false; irPara = true })
                        DropdownMenuItem(text = { Text(if (mostrarMini) "Ocultar miniaturas" else "Mostrar miniaturas") },
                            onClick = { menu = false; mostrarMini = !mostrarMini })
                        DropdownMenuItem(text = { Text("Zoom 100%") }, onClick = { menu = false; zoom = 1f })
                        DropdownMenuItem(text = { Text("Compartilhar") }, onClick = { menu = false; compartilhar(ctx, doc) })
                    }
                }
            }
        }
        // ---- busca ----
        if (buscaAberta) {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
              Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it }, singleLine = true,
                        modifier = Modifier.weight(1f), placeholder = { Text("Buscar no documento") }
                    )
                    TextButton(onClick = { buscar() }) { Text("OK") }
                    TextButton(onClick = { mover(-1) }) { Text("◀") }
                    TextButton(onClick = { mover(1) }) { Text("▶") }
                }
                if (buscando || resultados.isNotEmpty() || texto.isNotBlank()) {
                    Text(
                        text = when {
                            buscando -> "Buscando..."
                            resultados.isEmpty() -> ""
                            else -> "${atual + 1} de ${resultados.size}"
                        },
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
              }
            }
        }
        // ---- miniaturas + páginas ----
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (mostrarMini) {
                PainelMiniaturas(
                    fonte = fonte, aberto = miniAberto, atual = paginaAtual,
                    onAlternar = { miniAberto = !miniAberto; cfg.miniAberto = miniAberto },
                    onIr = { i -> escopo.launch { lista.animateScrollToItem(i) } },
                    modifier = Modifier.fillMaxHeight()
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val larguraBase = constraints.maxWidth
                    val dens = LocalDensity.current
                    val larguraPx = (larguraBase * zoom).toInt()
                    val rolagemH = rememberScrollState()
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    do {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        if (ev.changes.size >= 2) {
                                            val z = ev.calculateZoom()
                                            if (z != 1f) {
                                                zoom = (zoom * z).coerceIn(1f, 5f)
                                                ev.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (ev.changes.any { it.pressed })
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { zoom = if (zoom > 1.4f) 1f else 2.5f })
                            }
                            .horizontalScroll(rolagemH)
                    ) {
                        LazyColumn(
                            state = lista,
                            modifier = Modifier.width(with(dens) { larguraPx.toDp() }).fillMaxHeight()
                        ) {
                            items(count = fonte.paginas, key = { it }) { i ->
                                PaginaItem(
                                    fonte = fonte, i = i, larguraPx = larguraPx,
                                    resultados = porPagina[i] ?: emptyList(),
                                    atual = resultados.getOrNull(atual)
                                )
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.large, tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Text("$paginaAtual / ${fonte.paginas}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    if (irPara) {
        var entrada by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { irPara = false },
            title = { Text("Ir para a página") },
            text = {
                OutlinedTextField(
                    value = entrada, onValueChange = { entrada = it.filter { c -> c.isDigit() } }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("1 a ${fonte.paginas}") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = entrada.toIntOrNull()
                    irPara = false
                    if (n != null) escopo.launch { lista.animateScrollToItem((n - 1).coerceIn(0, fonte.paginas - 1)) }
                }) { Text("Ir") }
            },
            dismissButton = { TextButton(onClick = { irPara = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun PaginaItem(fonte: Fonte, i: Int, larguraPx: Int, resultados: List<Resultado>, atual: Resultado?) {
    val (w, h) = fonte.tamanho(i)
    val alturaPx = larguraPx * h / w
    val dens = LocalDensity.current
    var bmp by remember(i) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(i, larguraPx) {
        delay(80) // se o zoom ainda estiver mudando, esta tarefa é cancelada e só a última desenha
        bmp = try { fonte.renderizar(i, larguraPx) } catch (e: CancellationException) { throw e } catch (e: Exception) { bmp }
    }
    Column {
        Box(
            Modifier
                .width(with(dens) { larguraPx.toDp() })
                .height(with(dens) { alturaPx.toDp() })
                .background(Color.White)
        ) {
            bmp?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            if (resultados.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    for (r in resultados) {
                        drawRect(
                            color = if (r == atual) Color(0x99FF7A00) else Color(0x66FFD400),
                            topLeft = Offset(r.x0 * size.width, r.y0 * size.height),
                            size = Size((r.x1 - r.x0) * size.width, (r.y1 - r.y0) * size.height)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun PainelMiniaturas(
    fonte: Fonte, aberto: Boolean, atual: Int,
    onAlternar: () -> Unit, onIr: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val largura = if (aberto) 112.dp else 46.dp
    val listaMini: LazyListState = rememberLazyListState()
    LaunchedEffect(atual) { listaMini.animateScrollToItem((atual - 1).coerceAtLeast(0)) }
    Column(modifier.width(largura).background(MaterialTheme.colorScheme.surface)) {
        TextButton(onClick = onAlternar) { Text(if (aberto) "◀" else "▶") }
        LazyColumn(Modifier.fillMaxSize(), state = listaMini) {
            items(count = fonte.paginas, key = { it }) { i ->
                val destaque = (i + 1) == atual
                if (aberto) {
                    MiniaturaItem(fonte, i, destaque) { onIr(i) }
                } else {
                    Box(
                        Modifier.fillMaxWidth().clickable { onIr(i) }
                            .background(if (destaque) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", fontSize = 13.sp,
                            color = if (destaque) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniaturaItem(fonte: Fonte, i: Int, destaque: Boolean, onClick: () -> Unit) {
    var bmp by remember(i) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(i) { bmp = try { fonte.renderizar(i, 192) } catch (e: CancellationException) { throw e } catch (e: Exception) { null } }
    val (w, h) = fonte.tamanho(i)
    Column(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().height((88 * (h / w).coerceIn(0.5f, 2f)).dp)
                .background(Color.White)
                .border(if (destaque) 3.dp else 1.dp,
                    if (destaque) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Text("${i + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp))
    }
}

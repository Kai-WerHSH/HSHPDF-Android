// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Pixmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/** O MuPDF não é thread-safe: todo acesso a ele passa por esta única thread (com pilha grande). */
object Trabalho {
    val mupdf: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "mupdf", 32L * 1024 * 1024).apply { isDaemon = true }
    }.asCoroutineDispatcher()
}

/** Resultado de busca: posição como fração da página (0..1). */
data class Resultado(val pagina: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** Um documento aberto (PDF, EPUB, CBZ ou imagem) que sabe desenhar suas páginas. */
abstract class Fonte {
    abstract val paginas: Int
    abstract fun tamanho(i: Int): Pair<Float, Float>
    protected abstract fun desenhar(i: Int, larguraPx: Int): Bitmap
    open val despachante: CoroutineDispatcher get() = Trabalho.mupdf
    open val permiteBusca: Boolean get() = false
    open suspend fun buscar(texto: String): List<Resultado> = emptyList()
    abstract fun fechar()

    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Desenha a página com a largura pedida (arredondada para múltiplos de 64 px, para aproveitar o cache). */
    suspend fun renderizar(i: Int, larguraPx: Int): Bitmap {
        val q = ((larguraPx + 63) / 64) * 64
        val chave = "$i@$q"
        cache.get(chave)?.let { return it }
        val bmp = withContext(despachante) { desenhar(i, q) }
        cache.put(chave, bmp)
        return bmp
    }
}

/** PDF, EPUB, XPS e outros formatos que o MuPDF abre. */
class FontePdf(caminho: String) : Fonte() {
    private val doc: Document = Document.openDocument(caminho)
    private val tam: List<Pair<Float, Float>>
    override val paginas: Int

    init {
        if (doc.isReflowable()) doc.layout(480f, 720f, 11f)
        paginas = doc.countPages()
        val l = ArrayList<Pair<Float, Float>>(paginas)
        for (i in 0 until paginas) {
            val p = doc.loadPage(i)
            val b = p.getBounds()
            l.add(Pair(maxOf(1f, b.x1 - b.x0), maxOf(1f, b.y1 - b.y0)))
            p.destroy()
        }
        tam = l
    }

    override fun tamanho(i: Int) = tam[i]
    override val permiteBusca: Boolean get() = true

    override fun desenhar(i: Int, larguraPx: Int): Bitmap {
        val pagina = doc.loadPage(i)
        try {
            val b = pagina.getBounds()
            val s = larguraPx / (b.x1 - b.x0)
            val ctm = Matrix(s, 0f, 0f, s, -b.x0 * s, -b.y0 * s)
            val pix = pagina.toPixmap(ctm, ColorSpace.DeviceRGB, false, true)
            try {
                return paraBitmap(pix)
            } finally {
                pix.destroy()
            }
        } finally {
            pagina.destroy()
        }
    }

    private fun paraBitmap(pix: Pixmap): Bitmap {
        val w = pix.getWidth()
        val h = pix.getHeight()
        val n = pix.getNumberOfComponents()
        val stride = pix.getStride()
        val s = pix.getSamples()
        val px = IntArray(w * h)
        var o = 0
        for (y in 0 until h) {
            var p = y * stride
            for (x in 0 until w) {
                val r = s[p].toInt() and 0xFF
                val g = s[p + 1].toInt() and 0xFF
                val bl = s[p + 2].toInt() and 0xFF
                px[o++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                p += n
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    override suspend fun buscar(texto: String): List<Resultado> = withContext(Trabalho.mupdf) {
        val saida = ArrayList<Resultado>()
        for (i in 0 until paginas) {
            val p = doc.loadPage(i)
            try {
                val b = p.getBounds()
                val w = maxOf(1f, b.x1 - b.x0)
                val h = maxOf(1f, b.y1 - b.y0)
                val achados = p.search(texto)
                if (achados != null) {
                    for (grupo in achados) {
                        for (q in grupo) {
                            val xs = floatArrayOf(q.ul_x, q.ur_x, q.ll_x, q.lr_x)
                            val ys = floatArrayOf(q.ul_y, q.ur_y, q.ll_y, q.lr_y)
                            saida.add(Resultado(i, (xs.min() - b.x0) / w, (ys.min() - b.y0) / h,
                                (xs.max() - b.x0) / w, (ys.max() - b.y0) / h))
                        }
                    }
                }
            } finally {
                p.destroy()
            }
            if (saida.size > 3000) break
        }
        saida
    }

    override fun fechar() {
        try { doc.destroy() } catch (e: Exception) { }
    }
}

/** CBZ (ZIP de imagens) e imagens soltas. Usa o decodificador do Android, que lê WebP sem inflar. */
class FonteImagens private constructor(
    private val entradas: List<Entrada>,
    private val zip: ZipFile?
) : Fonte() {
    class Entrada(val nome: String, val abrir: () -> InputStream)

    private val tam: List<Pair<Float, Float>> = entradas.map { e ->
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        e.abrir().use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth > 0 && o.outHeight > 0) Pair(o.outWidth.toFloat(), o.outHeight.toFloat()) else Pair(1f, 1.4f)
    }
    override val paginas: Int get() = entradas.size
    override val despachante: CoroutineDispatcher get() = Dispatchers.Default

    override fun tamanho(i: Int) = tam[i]

    override fun desenhar(i: Int, larguraPx: Int): Bitmap {
        val w = tam[i].first
        var amostra = 1
        while (w / (amostra * 2) >= larguraPx) amostra *= 2
        val o = BitmapFactory.Options().apply {
            inSampleSize = amostra
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return entradas[i].abrir().use { BitmapFactory.decodeStream(it, null, o) }
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun fechar() {
        try { zip?.close() } catch (e: Exception) { }
    }

    companion object {
        private val extensoes = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

        fun deZip(arq: File): FonteImagens {
            val z = ZipFile(arq)
            val nomes = z.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { n ->
                    val base = n.substringAfterLast('/')
                    n.substringAfterLast('.', "").lowercase() in extensoes && !base.startsWith(".") && !n.contains("__MACOSX")
                }
                .sortedWith { a, b -> comparaNatural(a, b) }
                .toList()
            require(nomes.isNotEmpty()) { "Não encontrei imagens dentro do arquivo." }
            return FonteImagens(nomes.map { n -> Entrada(n) { z.getInputStream(z.getEntry(n)) } }, z)
        }

        fun deArquivo(arq: File): FonteImagens = FonteImagens(listOf(Entrada(arq.name) { arq.inputStream() }), null)
    }
}

/** Ordenação natural: 2.jpg vem antes de 10.jpg. */
fun comparaNatural(a: String, b: String): Int {
    val re = Regex("(\\d+)|(\\D+)")
    val pa = re.findAll(a.lowercase()).map { it.value }.toList()
    val pb = re.findAll(b.lowercase()).map { it.value }.toList()
    for (k in 0 until minOf(pa.size, pb.size)) {
        val x = pa[k]
        val y = pb[k]
        val nx = x.toLongOrNull()
        val ny = y.toLongOrNull()
        val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
        if (c != 0) return c
    }
    return pa.size.compareTo(pb.size)
}

class DocAberto(val nome: String, val fonte: Fonte, val arquivo: File)

object Abertura {
    private val mupdfExts = setOf("pdf", "epub", "xps", "oxps", "fb2", "mobi")
    private val imagemExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    /** Copia o arquivo para o cache do app e escolhe o leitor certo pela extensão. */
    suspend fun abrir(ctx: Context, uri: Uri): DocAberto = withContext(Dispatchers.IO) {
        var nome = "documento"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) nome = it.getString(0) ?: nome
        }
        val pasta = File(ctx.cacheDir, "abertos").apply { mkdirs() }
        pasta.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 6 * 3600 * 1000L }?.forEach { it.delete() }
        val seguro = nome.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destino = File(pasta, "${System.currentTimeMillis()}_$seguro")
        val entrada = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Não consegui ler o arquivo.")
        entrada.use { e -> destino.outputStream().use { s -> e.copyTo(s, 1 shl 16) } }
        val ext = nome.substringAfterLast('.', "").lowercase()
        val fonte: Fonte = when {
            ext in mupdfExts -> withContext(Trabalho.mupdf) { FontePdf(destino.absolutePath) }
            ext == "cbz" || ext == "zip" -> FonteImagens.deZip(destino)
            ext in imagemExts -> FonteImagens.deArquivo(destino)
            ext == "cbr" || ext == "rar" || ext == "cb7" ->
                throw IllegalStateException("CBR/RAR chega na próxima etapa. Por enquanto, converta para CBZ no app do computador.")
            else -> withContext(Trabalho.mupdf) { FontePdf(destino.absolutePath) }
        }
        DocAberto(nome, fonte, destino)
    }
}

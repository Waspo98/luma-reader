package com.example.lumareader.ui.reader

import android.content.Context
import android.graphics.PointF
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowInsetsCompat
import android.graphics.Bitmap
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import com.example.lumareader.data.model.ColumnLayoutMode
import com.example.lumareader.data.model.ImageHandlingMode
import com.example.lumareader.data.model.PageNavigationStyle
import com.example.lumareader.data.model.TextJustification
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.LumaThemeMode
import com.example.lumareader.data.model.ReadingPreferences
import com.example.lumareader.data.model.resolveDualColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import com.example.lumareader.data.model.AnnotationStyle
import com.example.lumareader.data.model.BookAnnotation
import com.example.lumareader.ui.reader.components.NoteComposerDialog
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.parser.epub.EpubParser
import android.content.ContextWrapper
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.compose.ui.graphics.toArgb
import com.example.lumareader.theme.SepiaBackground
import com.example.lumareader.theme.SlateBackground
import com.example.lumareader.ui.utils.LumaHapticFeedbackType
import com.example.lumareader.ui.utils.performLumaHaptic

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun findAllWebViews(view: android.view.View): List<android.webkit.WebView> {
    val list = mutableListOf<android.webkit.WebView>()
    if (view is android.webkit.WebView) {
        list.add(view)
    } else if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            list.addAll(findAllWebViews(view.getChildAt(i)))
        }
    }
    return list
}

private fun zipDirectory(sourceDir: File, zipFile: File) {
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        sourceDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relPath = file.relativeTo(sourceDir).path.replace('\\', '/')
                val entry = ZipEntry(relPath)
                zos.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}

private fun resolveEpubFile(context: Context, book: Book): File? {
    if (!book.epubFilePath.isNullOrBlank()) {
        val f = File(book.epubFilePath)
        if (f.exists() && f.isFile) return f
    }
    
    val booksDir = File(context.filesDir, "books")
    val persistentFile = File(booksDir, "${book.id}.epub")
    if (persistentFile.exists() && persistentFile.isFile) return persistentFile

    val matchingBook = booksDir.listFiles()?.firstOrNull {
        it.isFile && it.name.startsWith(book.id) && it.extension.equals("epub", ignoreCase = true)
    }
    if (matchingBook != null) return matchingBook

    val unzipped = File(book.unzippedDir)
    if (unzipped.exists()) {
        if (unzipped.isFile) return unzipped
        if (unzipped.isDirectory) {
            val repackDir = File(context.cacheDir, "repacked_epubs").apply { mkdirs() }
            val repackedEpub = File(repackDir, "${book.id}.epub")
            if (repackedEpub.exists() && repackedEpub.length() > 0) {
                return repackedEpub
            }
            try {
                zipDirectory(unzipped, repackedEpub)
                return repackedEpub
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return null
}

@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
private fun buildEpubPreferences(
    preferences: ReadingPreferences,
    isDark: Boolean,
    isFoldableOrWide: Boolean,
    responsiveMarginFactor: Float = 1.0f
): EpubPreferences {
    val effectiveTheme = when (preferences.themeMode) {
        LumaThemeMode.SYSTEM -> if (isDark) preferences.nightThemeMode else preferences.dayThemeMode
        else -> preferences.themeMode
    }

    val theme = when (effectiveTheme) {
        LumaThemeMode.LIGHT -> Theme.LIGHT
        LumaThemeMode.WARM_SEPIA -> Theme.SEPIA
        LumaThemeMode.SLATE_GRAY -> Theme.DARK
        LumaThemeMode.AMOLED_BLACK -> Theme.DARK
        LumaThemeMode.SYSTEM -> if (isDark) Theme.DARK else Theme.LIGHT
    }

    val bgColorInt = when (effectiveTheme) {
        LumaThemeMode.LIGHT -> android.graphics.Color.WHITE
        LumaThemeMode.WARM_SEPIA -> android.graphics.Color.parseColor("#F4ECD8")
        LumaThemeMode.SLATE_GRAY -> android.graphics.Color.parseColor("#1C2025")
        LumaThemeMode.AMOLED_BLACK -> android.graphics.Color.BLACK
        LumaThemeMode.SYSTEM -> if (isDark) android.graphics.Color.parseColor("#1C2025") else android.graphics.Color.WHITE
    }

    val textColorInt = when (effectiveTheme) {
        LumaThemeMode.LIGHT -> android.graphics.Color.parseColor("#121212")
        LumaThemeMode.WARM_SEPIA -> android.graphics.Color.parseColor("#3A2E24")
        LumaThemeMode.SLATE_GRAY -> android.graphics.Color.parseColor("#E2E8F0")
        LumaThemeMode.AMOLED_BLACK -> android.graphics.Color.parseColor("#F3F4F6")
        LumaThemeMode.SYSTEM -> if (isDark) android.graphics.Color.parseColor("#E2E8F0") else android.graphics.Color.parseColor("#121212")
    }

    val fontFamily = when {
        preferences.fontFamily == "Inter" -> FontFamily("Inter")
        preferences.fontFamily == "Literata" -> FontFamily("Literata")
        preferences.fontFamily == "Roboto" -> FontFamily("Roboto")
        preferences.fontFamily == "Google Sans" -> FontFamily("Google Sans")
        preferences.fontFamily == "serif" -> FontFamily.SERIF
        preferences.fontFamily == "sans-serif" -> FontFamily.SANS_SERIF
        preferences.fontFamily.startsWith("custom:") -> FontFamily(preferences.fontFamily.removePrefix("custom:").substringBeforeLast("."))
        else -> FontFamily(preferences.fontFamily)
    }

    // Column layout:
    // AUTO: 2 columns on unfolded foldable inner display or wide screen, 1 column on phone/folded cover screen
    // SINGLE: forced 1 column
    // DUAL: forced 2 columns
    // CONTINUOUS_SCROLL: strictly single column across all devices
    val useTwoColumns = preferences.resolveDualColumns(isFoldableOrWide)

    val columnCount = if (useTwoColumns) ColumnCount.TWO else ColumnCount.ONE
    val spread = if (useTwoColumns) Spread.ALWAYS else Spread.NEVER

    // For INVERT_BW, inversion is applied selectively to monochrome/BW images via readium-reflowable.js + CSS
    val imageFilter = when (preferences.imageHandlingMode) {
        ImageHandlingMode.INVERT_ALL -> org.readium.r2.navigator.preferences.ImageFilter.INVERT
        ImageHandlingMode.INVERT_BW -> null
        ImageHandlingMode.ORIGINAL -> null
    }

    val textAlign = when (preferences.textJustification) {
        TextJustification.LEFT -> org.readium.r2.navigator.preferences.TextAlign.LEFT
        TextJustification.RIGHT -> org.readium.r2.navigator.preferences.TextAlign.RIGHT
        TextJustification.FULL -> org.readium.r2.navigator.preferences.TextAlign.JUSTIFY
    }

    val scaledMargin = (preferences.marginLeftDp * responsiveMarginFactor).coerceAtLeast(8f)
    val pageMarginsValue = (scaledMargin.toDouble() / 24.0 * 0.5).coerceIn(0.15, 2.0)

    return EpubPreferences(
        fontFamily = fontFamily,
        fontSize = (preferences.fontSizeSp.toDouble() / 16.0).coerceIn(0.6, 2.8),
        lineHeight = preferences.lineSpacing.toDouble().coerceIn(1.0, 2.5),
        letterSpacing = preferences.letterSpacing.toDouble(),
        wordSpacing = preferences.wordSpacing.toDouble(),
        paragraphSpacing = preferences.paragraphSpacing.toDouble(),
        paragraphIndent = preferences.paragraphIndent.toDouble(),
        hyphens = preferences.hyphens,
        publisherStyles = preferences.publisherStyles,
        columnCount = columnCount,
        spread = spread,
        textAlign = textAlign,
        imageFilter = imageFilter,
        theme = theme,
        backgroundColor = org.readium.r2.navigator.preferences.Color(bgColorInt),
        textColor = org.readium.r2.navigator.preferences.Color(textColorInt),
        pageMargins = pageMarginsValue,
        scroll = preferences.navigationStyle == PageNavigationStyle.CONTINUOUS_SCROLL
    )
}

private data class NoteDialogTarget(
    val annotationId: String,
    val locator: Locator,
    val text: String,
    val initialNote: String = "",
    val initialColorHex: String,
    val initialStyle: AnnotationStyle,
    val isExisting: Boolean = false
)

@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
@Composable
actual fun ReadiumEpubReader(
    book: Book,
    preferences: ReadingPreferences,
    controller: ReadiumReaderController,
    onProgressChanged: (totalProgression: Float, chapterIndex: Int, chapterTitle: String?, locatorJson: String) -> Unit,
    onPageInfoChanged: (currentPage: Int, totalPages: Int, chapterPagesLeft: Int) -> Unit,
    onToggleUI: () -> Unit,
    onAddAnnotation: (BookAnnotation) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onUpdateAnnotation: (BookAnnotation) -> Unit,
    modifier: Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val windowWidthDp = maxWidth
        val windowHeightDp = maxHeight
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val activity = remember(context) { context.findFragmentActivity() }
        val density = LocalDensity.current

        // Dynamic responsive tiers for foldables, multi-window, and split-screen:
        // Multi-window / split-screen detection:
        // When multitasking alongside another app (1/4, 1/2, or 3/4 screen), AUTO mode must remain 1 column.
        val isInMultiWindow = activity?.isInMultiWindowMode == true
        // Foldable inner screens and large displays (Window size class Medium >= 600dp & Expanded >= 840dp)
        // trigger dual-column book layout in AUTO mode when full-screen:
        val isFoldableOrWide = !isInMultiWindow && windowWidthDp >= 600.dp
        val responsiveMarginFactor = when {
            windowWidthDp < 280.dp -> 0.4f  // 1/4 foldable window: ~8-10dp margin
            windowWidthDp < 360.dp -> 0.65f // narrow split: ~14-16dp margin
            else -> 1.0f                    // standard / wide: full user margin
        }

        val useTwoColumns = preferences.resolveDualColumns(isFoldableOrWide)
        val colCount = if (useTwoColumns) 2 else 1
        var pageTurnTransition by remember { mutableStateOf<PageTurnTransition?>(null) }
        val pageTurnProgress = remember { Animatable(0f) }

    val isDark = when (preferences.themeMode) {
        LumaThemeMode.LIGHT -> false
        LumaThemeMode.WARM_SEPIA -> false
        LumaThemeMode.SLATE_GRAY -> true
        LumaThemeMode.AMOLED_BLACK -> true
        LumaThemeMode.SYSTEM -> {
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    var publication by remember(book.id) { mutableStateOf<Publication?>(null) }
    var positions by remember(book.id) { mutableStateOf<List<Locator>>(emptyList()) }
    var activeFragment by remember(book.id) { mutableStateOf<EpubNavigatorFragment?>(null) }
    var loadError by remember(book.id) { mutableStateOf<String?>(null) }

    val currentBook by rememberUpdatedState(book)
    val currentOnToggleUI by rememberUpdatedState(onToggleUI)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnPageInfoChanged by rememberUpdatedState(onPageInfoChanged)
    val currentOnAddAnnotation by rememberUpdatedState(onAddAnnotation)
    val currentOnDeleteAnnotation by rememberUpdatedState(onDeleteAnnotation)
    val currentOnUpdateAnnotation by rememberUpdatedState(onUpdateAnnotation)
    val currentPreferences by rememberUpdatedState(preferences)
    var activeNoteTarget by remember { mutableStateOf<NoteDialogTarget?>(null) }
    val cachedChapterPages = remember { mutableMapOf<Int, Int>() }

    // Derive active Readium Decoration list from book annotations
    val decorationsFromBook = remember(book.annotations, preferences.defaultHighlightColorHex, preferences.annotationStyle) {
        book.annotations.mapNotNull { anno ->
            try {
                val locator = Locator.fromJSON(org.json.JSONObject(anno.locatorJson)) ?: return@mapNotNull null
                val colorInt = try {
                    android.graphics.Color.parseColor(anno.colorHex)
                } catch (_: Exception) {
                    0xFFFFE082.toInt()
                }
                val style = when (anno.style) {
                    AnnotationStyle.HIGHLIGHT -> Decoration.Style.Highlight(tint = colorInt)
                    AnnotationStyle.UNDERLINE -> Decoration.Style.Underline(tint = colorInt)
                }
                Decoration(id = anno.id, locator = locator, style = style)
            } catch (_: Exception) {
                null
            }
        }
    }

    // Load Publication asynchronously
    LaunchedEffect(book.id) {
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                val epubFile = resolveEpubFile(context, book)
                if (epubFile == null || !epubFile.exists()) {
                    loadError = "Could not locate EPUB archive for ${book.title}"
                    return@withContext
                }

                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
                val asset = assetRetriever.retrieve(epubFile).getOrNull()
                if (asset == null) {
                    loadError = "Failed to retrieve asset from ${epubFile.name}"
                    return@withContext
                }

                val parser = EpubParser()
                val builder = parser.parse(asset).getOrNull()
                if (builder == null) {
                    loadError = "Failed to parse publication: ${epubFile.name}"
                    return@withContext
                }

                val pub = builder.build()
                val posList = try { pub.positions() } catch (e: Exception) { e.printStackTrace(); emptyList() }
                withContext(Dispatchers.Main) {
                    publication = pub
                    positions = posList
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadError = "Error opening book: ${e.localizedMessage}"
                }
            }
        }
    }

    // Clean up publication on disposal
    DisposableEffect(book.id) {
        onDispose {
            publication?.close()
        }
    }

    if (loadError != null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = loadError ?: "Error loading book",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return@BoxWithConstraints
    }

    val pub = publication
    if (pub == null || activity == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return@BoxWithConstraints
    }

    val initialLocator = remember(book.id) {
        if (!book.lastLocatorJson.isNullOrBlank()) {
            try {
                Locator.fromJSON(JSONObject(book.lastLocatorJson))
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val containerId = remember(book.id) { android.view.View.generateViewId() }
    val fragmentTag = remember(book.id) { "readium_epub_navigator_${book.id}" }

    val performPageTurn: (Boolean) -> Unit = remember(preferences.navigationStyle, preferences.hapticsEnabled, activeFragment, useTwoColumns) {
        { isForward: Boolean ->
            val frag = activeFragment
            if (frag != null && frag.isAdded) {
                if (preferences.hapticsEnabled) {
                    val targetView = frag.view ?: activity.window.decorView
                    performLumaHaptic(targetView, LumaHapticFeedbackType.PAGE_TURN)
                }
                when (preferences.navigationStyle) {
                    PageNavigationStyle.HORIZONTAL_SLIDE -> {
                        try {
                            if (isForward) frag.goForward(animated = true)
                            else frag.goBackward(animated = true)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    PageNavigationStyle.PAGE_TURN -> {
                        if (pageTurnTransition == null) {
                            scope.launch {
                                val container = activity.findViewById<ViewGroup>(containerId) ?: frag.view
                                val bmp = captureViewBitmap(container, activity)
                                if (bmp != null) {
                                    pageTurnTransition?.bitmap?.recycle()
                                    pageTurnProgress.snapTo(0f)
                                    pageTurnTransition = PageTurnTransition(
                                        bitmap = bmp,
                                        isForward = isForward,
                                        isTwoColumns = useTwoColumns
                                    )
                                    if (isForward) frag.goForward(animated = false)
                                    else frag.goBackward(animated = false)
                                    pageTurnProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    pageTurnTransition = null
                                    bmp.recycle()
                                    pageTurnProgress.snapTo(0f)
                                } else {
                                    if (isForward) frag.goForward(animated = true)
                                    else frag.goBackward(animated = true)
                                }
                            }
                        }
                    }
                    PageNavigationStyle.INSTANT -> {
                        try {
                            if (isForward) frag.goForward(animated = false)
                            else frag.goBackward(animated = false)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    PageNavigationStyle.CONTINUOUS_SCROLL -> {
                        val direction = if (isForward) 1 else -1
                        val scrollJs = "(function() { var vh = window.innerHeight || 800; window.scrollBy({ top: vh * 0.8 * $direction, behavior: 'smooth' }); })()"
                        scope.launch {
                            try {
                                frag.evaluateJavascript(scrollJs)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }
    val currentPerformPageTurn by rememberUpdatedState(performPageTurn)

    // Connect controller to the active fragment
    LaunchedEffect(activeFragment, performPageTurn, positions) {
        val frag = activeFragment ?: return@LaunchedEffect
        if (!frag.isAdded || frag.activity == null) return@LaunchedEffect
        controller.goForward = { currentPerformPageTurn(true) }
        controller.goBackward = { currentPerformPageTurn(false) }
        controller.goToChapter = { spineIndex, href ->
            if (frag.isAdded) {
                scope.launch {
                    try {
                        val cleanHref = href.substringBefore("#").trimStart('/')
                        val targetLink = pub.readingOrder.getOrNull(spineIndex)
                            ?: pub.readingOrder.firstOrNull { it.url().toString().contains(cleanHref) }
                            ?: pub.tableOfContents.firstOrNull { it.url().toString().contains(cleanHref) }
                        if (targetLink != null) {
                            frag.go(targetLink, animated = true)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        controller.goToProgression = { prog ->
            if (frag.isAdded) {
                scope.launch {
                    try {
                        if (positions.isNotEmpty()) {
                            val targetIdx = (prog * (positions.size - 1)).toInt().coerceIn(0, positions.size - 1)
                            val targetLocator = positions[targetIdx]
                            frag.go(targetLocator, animated = true)
                        } else {
                            val readingOrder = pub.readingOrder
                            if (readingOrder.isNotEmpty()) {
                                val totalSpine = prog * readingOrder.size
                                val spineIdx = totalSpine.toInt().coerceIn(0, readingOrder.size - 1)
                                val spineProg = (totalSpine - spineIdx).toDouble().coerceIn(0.0, 0.99)
                                val targetLink = readingOrder[spineIdx]
                                val locator = pub.locatorFromLink(targetLink)?.copyWithLocations(progression = spineProg)
                                if (locator != null) {
                                    frag.go(locator, animated = true)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        controller.goToLocator = { locatorJson ->
            if (frag.isAdded) {
                scope.launch {
                    try {
                        val locator = Locator.fromJSON(JSONObject(locatorJson))
                        if (locator != null) {
                            frag.go(locator, animated = false)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    val effectiveThemeMode = remember(preferences.themeMode, preferences.dayThemeMode, preferences.nightThemeMode, isDark) {
        when (preferences.themeMode) {
            LumaThemeMode.SYSTEM -> if (isDark) preferences.nightThemeMode else preferences.dayThemeMode
            else -> preferences.themeMode
        }
    }

    val bgColorInt = remember(effectiveThemeMode) {
        when (effectiveThemeMode) {
            LumaThemeMode.LIGHT -> android.graphics.Color.WHITE
            LumaThemeMode.WARM_SEPIA -> SepiaBackground.toArgb()
            LumaThemeMode.SLATE_GRAY -> SlateBackground.toArgb()
            LumaThemeMode.AMOLED_BLACK -> android.graphics.Color.BLACK
            LumaThemeMode.SYSTEM -> android.graphics.Color.WHITE
        }
    }

    // Custom Font Base64 for WebView injection
    val customFontBase64 = remember(preferences.fontFamily) {
        if (preferences.fontFamily.startsWith("custom:")) {
            val fileName = preferences.fontFamily.removePrefix("custom:")
            val fontFile = java.io.File(context.filesDir, "fonts/$fileName")
            if (fontFile.exists()) {
                try {
                    val bytes = fontFile.readBytes()
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } catch (_: Exception) {
                    null
                }
            } else null
        } else null
    }

    // Image handling mode string for CSS
    val currentImageMode = remember(preferences.imageHandlingMode, isDark) {
        when (preferences.imageHandlingMode) {
            ImageHandlingMode.INVERT_ALL -> "invert-all"
            ImageHandlingMode.INVERT_BW -> if (isDark) "invert-bw" else "original"
            ImageHandlingMode.ORIGINAL -> "original"
        }
    }

    // Text alignment mode string for CSS
    val currentAlignMode = remember(preferences.textJustification) {
        when (preferences.textJustification) {
            TextJustification.LEFT -> "left"
            TextJustification.RIGHT -> "right"
            TextJustification.FULL -> "full"
        }
    }




    // Top inset padding for extendBehindNotch:
    // When extendBehindNotch is true -> 0 top padding (extends under notch)
    // When extendBehindNotch is false -> pad down safely below status bar / display cutout
    val topCutoutInsetDp = remember(preferences.extendBehindNotch, activity) {
        if (preferences.extendBehindNotch) {
            0
        } else {
            val rootInsets = activity.window?.decorView?.rootWindowInsets
            if (rootInsets != null) {
                val insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(rootInsets)
                val statusTop = insetsCompat.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val cutoutTop = insetsCompat.getInsets(WindowInsetsCompat.Type.displayCutout()).top
                val maxInsetPx = maxOf(statusTop, cutoutTop)
                (maxInsetPx / density.density).toInt().coerceAtLeast(24)
            } else {
                24
            }
        }
    }

    val effectiveTopMargin = topCutoutInsetDp + preferences.marginTopDp
    val effectiveBottomMargin = preferences.marginBottomDp
    val effectiveLeftMargin = (preferences.marginLeftDp * responsiveMarginFactor).toInt().coerceAtLeast(8)
    val effectiveRightMargin = (preferences.marginRightDp * responsiveMarginFactor).toInt().coerceAtLeast(8)
    val currentLineSpacing = preferences.lineSpacing

    // Dynamic styles synchronization with Readium WebView DOM
    val syncStylesJs = remember(
        colCount,
        effectiveTopMargin,
        effectiveBottomMargin,
        effectiveLeftMargin,
        effectiveRightMargin,
        currentImageMode,
        currentAlignMode,
        currentLineSpacing,
        customFontBase64,
        preferences.fontFamily,
        preferences.letterSpacing,
        preferences.wordSpacing,
        preferences.paragraphSpacing,
        preferences.paragraphIndent,
        preferences.fontSizeSp,
        preferences.publisherStyles,
        preferences.navigationStyle,
        preferences.annotationsEnabled
    ) {
        val isScroll = preferences.navigationStyle == PageNavigationStyle.CONTINUOUS_SCROLL
        val isInstant = preferences.navigationStyle == PageNavigationStyle.INSTANT
        val scrollAttr = if (isScroll) {
            "document.documentElement.setAttribute('data-luma-scroll', 'true'); "
        } else {
            "document.documentElement.removeAttribute('data-luma-scroll'); "
        }
        val instantAttr = if (isInstant) {
            "document.documentElement.setAttribute('data-luma-instant', 'true'); "
        } else {
            "document.documentElement.removeAttribute('data-luma-instant'); "
        }
        val isPageTurn = preferences.navigationStyle == PageNavigationStyle.PAGE_TURN
        val pageTurnAttr = if (isPageTurn) {
            "document.documentElement.setAttribute('data-luma-page-turn', 'true'); "
        } else {
            "document.documentElement.removeAttribute('data-luma-page-turn'); "
        }

        val alignRule = when (currentAlignMode) {
            "center" -> "body, p, div:not([class*=\\\"nav\\\"]), li { text-align: center !important; text-align-last: center !important; }"
            "right" -> "body, p, div:not([class*=\\\"nav\\\"]), li { text-align: right !important; text-align-last: right !important; }"
            "left" -> "body, p, div:not([class*=\\\"nav\\\"]), li { text-align: left !important; text-align-last: left !important; }"
            else -> "body, p, div:not([class*=\\\"nav\\\"]), li { text-align: justify !important; text-align-last: left !important; }"
        }
        val imgRule = "img, svg { display: block !important; max-width: 100% !important; width: 100% !important; height: auto !important; margin-left: auto !important; margin-right: auto !important; object-fit: contain !important; } figure { display: flex !important; flex-direction: column !important; align-items: center !important; justify-content: center !important; width: 100% !important; max-width: 100% !important; margin-left: auto !important; margin-right: auto !important; }"
        val fontRule = if (customFontBase64 != null) {
            val fName = preferences.fontFamily.removePrefix("custom:").substringBeforeLast(".")
            "@font-face { font-family: '$fName'; src: url('data:font/truetype;charset=utf-8;base64,$customFontBase64') format('truetype'); } body, p, div, li, span { font-family: '$fName' !important; }"
        } else ""

        val typoRule = if (!preferences.publisherStyles) {
            "body, p, div:not([class*=\\\"nav\\\"]):not([class*=\\\"toc\\\"]), li, span { " +
                "letter-spacing: ${preferences.letterSpacing}em !important; " +
                "word-spacing: ${preferences.wordSpacing}em !important; " +
                "line-height: ${currentLineSpacing} !important; " +
            "} " +
            "p { " +
                "margin-top: ${preferences.paragraphSpacing}em !important; " +
                "margin-bottom: ${preferences.paragraphSpacing}em !important; " +
                "text-indent: ${preferences.paragraphIndent}em !important; " +
            "} " +
            "p * { text-indent: 0 !important; }"
        } else ""

        val typoVars = if (!preferences.publisherStyles) {
            "document.documentElement.setAttribute('data-luma-advanced-typo', 'true'); " +
            "document.documentElement.setAttribute('data-luma-line-spacing', 'true'); " +
            "document.documentElement.style.setProperty('--luma-line-height', '$currentLineSpacing'); " +
            "document.documentElement.style.setProperty('--luma-letter-spacing', '${preferences.letterSpacing}em'); " +
            "document.documentElement.style.setProperty('--luma-word-spacing', '${preferences.wordSpacing}em'); " +
            "document.documentElement.style.setProperty('--luma-para-spacing', '${preferences.paragraphSpacing}em'); " +
            "document.documentElement.style.setProperty('--luma-para-indent', '${preferences.paragraphIndent}em'); " +
            "document.documentElement.style.setProperty('--USER__letterSpacing', '${preferences.letterSpacing}em'); " +
            "document.documentElement.style.setProperty('--USER__wordSpacing', '${preferences.wordSpacing}em'); " +
            "document.documentElement.style.setProperty('--USER__paraSpacing', '${preferences.paragraphSpacing}em'); " +
            "document.documentElement.style.setProperty('--USER__paraIndent', '${preferences.paragraphIndent}em'); " +
            "document.documentElement.style.setProperty('--USER__lineHeight', '$currentLineSpacing'); "
        } else {
            "document.documentElement.removeAttribute('data-luma-advanced-typo'); " +
            "document.documentElement.removeAttribute('data-luma-line-spacing'); "
        }

        val userSelectRule = if (!preferences.annotationsEnabled) {
            "body, p, div, li, span { -webkit-user-select: none !important; user-select: none !important; } "
        } else {
            "body, p, div, li, span { -webkit-user-select: text !important; user-select: text !important; } "
        }

        scrollAttr +
        instantAttr +
        pageTurnAttr +
        "document.documentElement.setAttribute('data-luma-col-count', '$colCount'); " +
        "document.documentElement.style.setProperty('--USER__colCount', '$colCount'); " +
        "document.documentElement.style.setProperty('--RS__colGap', '0px'); " +
        "document.documentElement.style.setProperty('--luma-gutter', 'calc(${effectiveLeftMargin}px + ${effectiveRightMargin}px)'); " +
        "document.documentElement.style.setProperty('--luma-margin-top', '${effectiveTopMargin}px'); " +
        "document.documentElement.style.setProperty('--luma-margin-bottom', '${effectiveBottomMargin}px'); " +
        "document.documentElement.style.setProperty('--luma-margin-left', '${effectiveLeftMargin}px'); " +
        "document.documentElement.style.setProperty('--luma-margin-right', '${effectiveRightMargin}px'); " +
        "document.documentElement.setAttribute('data-luma-align', '$currentAlignMode'); " +
        "document.documentElement.style.setProperty('--USER__fontSize', '${((preferences.fontSizeSp / 16.0) * 100).toInt()}%'); " +
        typoVars +
        "if (window.lumaSetImageMode) { window.lumaSetImageMode('$currentImageMode'); } else { document.documentElement.setAttribute('data-luma-image-mode', '$currentImageMode'); } " +
        "var lumaRuleEl = document.getElementById('luma-injected-rules'); " +
        "if (!lumaRuleEl) { lumaRuleEl = document.createElement('style'); lumaRuleEl.id = 'luma-injected-rules'; document.head.appendChild(lumaRuleEl); } " +
        "lumaRuleEl.innerHTML = '$alignRule $imgRule $fontRule $typoRule $userSelectRule';"
    }

    val currentSyncStylesJs by rememberUpdatedState(syncStylesJs)

    val syncAllWebViews = {
        try {
            val frag = activeFragment
            if (frag != null && frag.isAdded) {
                scope.launch {
                    try {
                        frag.evaluateJavascript(currentSyncStylesJs)
                    } catch (_: Exception) {}
                }
            }
            val container = activity.findViewById<ViewGroup>(containerId)
            if (container != null) {
                findAllWebViews(container).forEach { wv ->
                    wv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    wv.evaluateJavascript(currentSyncStylesJs, null)
                }
            }
        } catch (_: Exception) {}
    }

    // Synchronize image handling mode, text alignment, line spacing & dynamic margins to WebView DOM
    LaunchedEffect(activeFragment, syncStylesJs) {
        syncAllWebViews()
        delay(150)
        syncAllWebViews()
        delay(350)
        syncAllWebViews()
    }

    // Submit preferences whenever they change (debounced to preserve 60fps during rapid slider scrubbing or window resizing)
    LaunchedEffect(activeFragment, preferences, isDark, isFoldableOrWide, responsiveMarginFactor, windowWidthDp, windowHeightDp) {
        val frag = activeFragment ?: return@LaunchedEffect
        if (!frag.isAdded || frag.activity == null) return@LaunchedEffect
        try {
            delay(120)
            val newPrefs = buildEpubPreferences(preferences, isDark, isFoldableOrWide, responsiveMarginFactor)
            frag.submitPreferences(newPrefs)
            syncAllWebViews()
            try {
                frag.evaluateJavascript("window.dispatchEvent(new Event('resize'));")
            } catch (_: Exception) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Apply exact container background color
    LaunchedEffect(containerId, bgColorInt, activeFragment) {
        val container = activity.findViewById<android.view.View>(containerId)
        container?.setBackgroundColor(bgColorInt)
    }

    // Sync Readium decorations (annotations & highlighting)
    LaunchedEffect(activeFragment, preferences.annotationsEnabled, decorationsFromBook) {
        val frag = activeFragment ?: return@LaunchedEffect
        if (!frag.isAdded || frag.activity == null) return@LaunchedEffect
        try {
            if (preferences.annotationsEnabled) {
                frag.applyDecorations(decorationsFromBook, "highlights")
            } else {
                frag.applyDecorations(emptyList(), "highlights")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Track progression and location
    LaunchedEffect(activeFragment, positions) {
        val frag = activeFragment ?: return@LaunchedEffect
        if (!frag.isAdded || frag.activity == null) return@LaunchedEffect
        try {
            frag.currentLocator.collect { locator ->
                val hrefStr = locator.href.toString().trimStart('/')
                val spineIdx = book.spine.indexOfFirst {
                    it == hrefStr || it.endsWith(hrefStr) || hrefStr.endsWith(it)
                }.takeIf { it >= 0 } ?: book.currentSpineIndex

                val pos = locator.locations.position ?: run {
                    if (positions.isNotEmpty()) {
                        val matchingPositions = positions.filter {
                            val pRef = it.href.toString().trimStart('/')
                            pRef == hrefStr || pRef.endsWith(hrefStr) || hrefStr.endsWith(pRef)
                        }
                        if (matchingPositions.isNotEmpty()) {
                            val inProg = (locator.locations.progression ?: 0.0).toFloat().coerceIn(0f, 1f)
                            val idx = (inProg * (matchingPositions.size - 1)).toInt().coerceIn(0, matchingPositions.size - 1)
                            matchingPositions[idx].locations.position ?: (positions.indexOf(matchingPositions[idx]) + 1)
                        } else null
                    } else null
                }

                val totalBookPages = if (positions.isNotEmpty()) positions.size else (pub.readingOrder.size * 15).coerceAtLeast(pos ?: 1)
                val currentBookPage = (pos ?: run {
                    val fallbackProg = locator.locations.totalProgression?.toFloat()
                        ?: locator.locations.progression?.toFloat()
                        ?: 0f
                    ((fallbackProg * totalBookPages).toInt() + 1)
                }).coerceIn(1, totalBookPages)

                val chapterProg = (locator.locations.progression?.toFloat() ?: 0f).coerceIn(0f, 1f)
                val currentTitle = locator.title
                val locatorJson = locator.toJSON().toString()

                currentOnProgressChanged(chapterProg, spineIdx, currentTitle, locatorJson)

                val chapterPositions = if (positions.isNotEmpty()) {
                    positions.filter {
                        val pRef = it.href.toString().trimStart('/')
                        pRef == hrefStr || pRef.endsWith(hrefStr) || hrefStr.endsWith(pRef)
                    }
                } else emptyList()

                val chapterPagesLeft = if (chapterPositions.isNotEmpty()) {
                    chapterPositions.count { (it.locations.position ?: 0) > currentBookPage }
                } else {
                    val progInChapter = (locator.locations.progression ?: 0.0).toFloat().coerceIn(0f, 1f)
                    val lastKnownTotal = cachedChapterPages[spineIdx] ?: 15
                    val fallbackCur = (progInChapter * (lastKnownTotal - 1)).toInt() + 1
                    (lastKnownTotal - fallbackCur).coerceAtLeast(0)
                }

                // Emit immediate authentic page calculation
                currentOnPageInfoChanged(currentBookPage, totalBookPages, chapterPagesLeft)

                // Query exact DOM column metrics asynchronously for paginated modes
                val pageQueryJs = "(function() { " +
                    "try { " +
                        "var doc = document; " +
                        "var win = window; " +
                        "var iframes = document.getElementsByTagName('iframe'); " +
                        "if (iframes.length > 0 && iframes[0].contentDocument) { " +
                            "doc = iframes[0].contentDocument; " +
                            "win = iframes[0].contentWindow || window; " +
                        "} " +
                        "var el = doc.scrollingElement || doc.documentElement || doc.body; " +
                        "var sw = el ? el.scrollWidth : 0; " +
                        "var iw = win.innerWidth || window.innerWidth || 1; " +
                        "var sx = win.scrollX || win.pageXOffset || (doc.documentElement ? doc.documentElement.scrollLeft : 0) || 0; " +
                        "var total = Math.max(1, Math.round(sw / iw)); " +
                        "var cur = Math.min(total, Math.max(1, Math.round(sx / iw) + 1)); " +
                        "return cur + '/' + total; " +
                    "} catch(e) { " +
                        "return ''; " +
                    "} " +
                "})()"

                scope.launch {
                    try {
                        val result = frag.evaluateJavascript(pageQueryJs)
                        val clean = result?.replace("\"", "")?.trim()
                        if (!clean.isNullOrEmpty() && clean.contains("/")) {
                            val parts = clean.split("/")
                            val cur = parts.getOrNull(0)?.toIntOrNull() ?: 1
                            val total = parts.getOrNull(1)?.toIntOrNull() ?: 1
                            cachedChapterPages[spineIdx] = total
                            val left = (total - cur).coerceAtLeast(0)
                            currentOnPageInfoChanged(currentBookPage, totalBookPages, left)
                        }
                    } catch (_: Exception) {}
                }

                // Ensure image mode, text alignment, line spacing & margins are synced when turning pages
                syncAllWebViews()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(activeFragment, preferences.navigationStyle) {
        val frag = activeFragment ?: return@LaunchedEffect
        val view = frag.view ?: activity.findViewById<ViewGroup>(containerId)
        if (view != null) {
            val vp = findViewPager(view)
            setupViewPagerTouch(vp, preferences.navigationStyle)
        }
    }

    val pageTurnDragModifier = if (preferences.navigationStyle == PageNavigationStyle.PAGE_TURN) {
        Modifier.pointerInput(activeFragment, useTwoColumns) {
            var dragTotalX = 0f
            var isTurnInitiated = false
            var turnForward = false
            var pendingCommit: Boolean? = null

            detectHorizontalDragGestures(
                onDragStart = {
                    dragTotalX = 0f
                    isTurnInitiated = false
                    pendingCommit = null
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    dragTotalX += dragAmount
                    val screenW = size.width.toFloat()

                    if (!isTurnInitiated && kotlin.math.abs(dragTotalX) > 12f) {
                        isTurnInitiated = true
                        turnForward = dragTotalX < 0
                        val frag = activeFragment
                        val container = activity.findViewById<ViewGroup>(containerId) ?: frag?.view
                        if (frag != null && frag.isAdded && container != null) {
                            scope.launch {
                                val bmp = captureViewBitmap(container, activity)
                                if (bmp != null) {
                                    pageTurnTransition?.bitmap?.recycle()
                                    pageTurnProgress.snapTo(0f)
                                    pageTurnTransition = PageTurnTransition(
                                        bitmap = bmp,
                                        isForward = turnForward,
                                        isTwoColumns = useTwoColumns
                                    )
                                    if (turnForward) frag.goForward(animated = false)
                                    else frag.goBackward(animated = false)

                                    if (pendingCommit == true) {
                                        pageTurnProgress.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                        pageTurnTransition = null
                                        bmp.recycle()
                                        pageTurnProgress.snapTo(0f)
                                    } else if (pendingCommit == false) {
                                        pageTurnProgress.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                        if (turnForward) frag.goBackward(animated = false)
                                        else frag.goForward(animated = false)
                                        pageTurnTransition = null
                                        bmp.recycle()
                                        pageTurnProgress.snapTo(0f)
                                    }
                                }
                            }
                        }
                    }

                    if (isTurnInitiated && pageTurnTransition != null) {
                        val p = (kotlin.math.abs(dragTotalX) / screenW).coerceIn(0f, 1f)
                        scope.launch {
                            pageTurnProgress.snapTo(p)
                        }
                    }
                },
                onDragEnd = {
                    if (isTurnInitiated) {
                        val screenW = size.width.toFloat()
                        val currentP = (kotlin.math.abs(dragTotalX) / screenW).coerceIn(0f, 1f)
                        val shouldCommit = currentP > 0.18f || kotlin.math.abs(dragTotalX) > 80f

                        val transitionToFinish = pageTurnTransition
                        if (transitionToFinish != null) {
                            scope.launch {
                                if (shouldCommit) {
                                    pageTurnProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    pageTurnTransition = null
                                    transitionToFinish.bitmap.recycle()
                                    pageTurnProgress.snapTo(0f)
                                } else {
                                    pageTurnProgress.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    val frag = activeFragment
                                    if (frag != null && frag.isAdded) {
                                        if (turnForward) frag.goBackward(animated = false)
                                        else frag.goForward(animated = false)
                                    }
                                    pageTurnTransition = null
                                    transitionToFinish.bitmap.recycle()
                                    pageTurnProgress.snapTo(0f)
                                }
                            }
                        } else {
                            pendingCommit = shouldCommit
                        }
                    }
                    isTurnInitiated = false
                    dragTotalX = 0f
                },
                onDragCancel = {
                    if (isTurnInitiated) {
                        val transitionToCancel = pageTurnTransition
                        if (transitionToCancel != null) {
                            scope.launch {
                                pageTurnProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                                val frag = activeFragment
                                if (frag != null && frag.isAdded) {
                                    if (turnForward) frag.goBackward(animated = false)
                                    else frag.goForward(animated = false)
                                }
                                pageTurnTransition = null
                                transitionToCancel.bitmap.recycle()
                                pageTurnProgress.snapTo(0f)
                            }
                        } else {
                            pendingCommit = false
                        }
                    }
                    isTurnInitiated = false
                    dragTotalX = 0f
                }
            )
        }
    } else {
        Modifier
    }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(bgColorInt)
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val newW = right - left
                    val newH = bottom - top
                    val oldW = oldRight - oldLeft
                    val oldH = oldBottom - oldTop
                    if ((newW != oldW || newH != oldH) && newW > 0 && newH > 0) {
                        syncAllWebViews()
                    }
                }

                fun attachHierarchyListener(vg: ViewGroup) {
                    vg.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                        override fun onChildViewAdded(parent: android.view.View?, child: android.view.View?) {
                            if (child != null && child::class.java.name.contains("ViewPager") && child is ViewGroup) {
                                setupViewPagerTouch(child, preferences.navigationStyle)
                            }
                            if (child is android.webkit.WebView) {
                                child.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                child.evaluateJavascript(currentSyncStylesJs, null)
                                child.postDelayed({ child.evaluateJavascript(currentSyncStylesJs, null) }, 200)
                            } else if (child is ViewGroup) {
                                attachHierarchyListener(child)
                                findAllWebViews(child).forEach { wv ->
                                    wv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                    wv.evaluateJavascript(currentSyncStylesJs, null)
                                    wv.postDelayed({ wv.evaluateJavascript(currentSyncStylesJs, null) }, 200)
                                }
                            }
                        }
                        override fun onChildViewRemoved(parent: android.view.View?, child: android.view.View?) {}
                    })
                    for (i in 0 until vg.childCount) {
                        val child = vg.getChildAt(i)
                        if (child::class.java.name.contains("ViewPager") && child is ViewGroup) {
                            setupViewPagerTouch(child, preferences.navigationStyle)
                        }
                        if (child is ViewGroup) {
                            attachHierarchyListener(child)
                        }
                    }
                }
                attachHierarchyListener(this)

                val fragmentManager = activity.supportFragmentManager
                val factory = EpubNavigatorFactory(pub)
                val navConfig = EpubNavigatorFragment.Configuration().apply {
                    shouldApplyInsetsPadding = false
                    servedAssets = servedAssets + listOf("fonts/.*", "readium/.*")
                    addFontFamilyDeclaration(
                        fontFamily = FontFamily("Literata"),
                        alternates = listOf(FontFamily.SERIF)
                    ) {
                        addFontFace {
                            addSource("fonts/Literata-VariableFont_opsz,wght.ttf")
                            setFontStyle(FontStyle.NORMAL)
                            setFontWeight(200..900)
                        }
                        addFontFace {
                            addSource("fonts/Literata-Italic-VariableFont_opsz,wght.ttf")
                            setFontStyle(FontStyle.ITALIC)
                            setFontWeight(200..900)
                        }
                    }
                    addFontFamilyDeclaration(
                        fontFamily = FontFamily("Inter"),
                        alternates = listOf(FontFamily.SANS_SERIF)
                    ) {
                        addFontFace {
                            addSource("fonts/Inter-Regular.ttf")
                            setFontStyle(FontStyle.NORMAL)
                            setFontWeight(FontWeight.NORMAL)
                        }
                    }

                    val ACTION_HIGHLIGHT = 9901
                    val ACTION_NOTE = 9902
                    val ACTION_COPY = 9903
                    val ACTION_SHARE = 9904
                    val ACTION_SEARCH = 9905

                    selectionActionModeCallback = object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            if (!currentPreferences.annotationsEnabled) {
                                return false
                            }
                            menu.clear()

                            val highlightItem = menu.add(Menu.NONE, ACTION_HIGHLIGHT, 0, "Highlight")
                            highlightItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                            val noteItem = menu.add(Menu.NONE, ACTION_NOTE, 1, "Note")
                            noteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                            val copyItem = menu.add(Menu.NONE, ACTION_COPY, 2, "Copy")
                            copyItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                            val shareItem = menu.add(Menu.NONE, ACTION_SHARE, 3, "Share")
                            shareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                            val searchItem = menu.add(Menu.NONE, ACTION_SEARCH, 4, "Search")
                            searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                            if (!currentPreferences.annotationsEnabled) {
                                return false
                            }
                            if (menu.findItem(ACTION_HIGHLIGHT) == null) {
                                onCreateActionMode(mode, menu)
                            } else {
                                menu.findItem(ACTION_HIGHLIGHT)?.isVisible = true
                                menu.findItem(ACTION_NOTE)?.isVisible = true
                                menu.findItem(ACTION_COPY)?.isVisible = true
                                menu.findItem(ACTION_SHARE)?.isVisible = true
                                menu.findItem(ACTION_SEARCH)?.isVisible = true
                            }
                            return true
                        }

                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                            val frag = activeFragment ?: return false
                            scope.launch {
                                val selection = frag.currentSelection()
                                val selectedText: String = (selection?.locator?.text?.highlight?.takeIf { it.isNotBlank() }
                                    ?: run {
                                        try {
                                            val raw = frag.evaluateJavascript("(function() { return window.getSelection() ? window.getSelection().toString() : ''; })()")
                                            raw?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim()
                                        } catch (_: Exception) { "" }
                                    }).orEmpty()

                                when (item.itemId) {
                                    ACTION_HIGHLIGHT -> {
                                        if (selection != null) {
                                            val colorHex = currentPreferences.defaultHighlightColorHex
                                            val style = currentPreferences.annotationStyle
                                            val id = "anno_${System.currentTimeMillis()}_${(1000..9999).random()}"
                                            val annotation = BookAnnotation(
                                                id = id,
                                                bookId = currentBook.id,
                                                text = selectedText.ifBlank { "Highlighted text" },
                                                note = "",
                                                colorHex = colorHex,
                                                style = style,
                                                locatorJson = selection.locator.toJSON().toString(),
                                                spineIndex = currentBook.currentSpineIndex,
                                                progression = currentBook.currentProgression,
                                                timestamp = System.currentTimeMillis()
                                            )
                                            currentOnAddAnnotation(annotation)
                                        }
                                        frag.clearSelection()
                                        mode.finish()
                                    }
                                    ACTION_NOTE -> {
                                        if (selection != null) {
                                            activeNoteTarget = NoteDialogTarget(
                                                annotationId = "anno_${System.currentTimeMillis()}_${(1000..9999).random()}",
                                                locator = selection.locator,
                                                text = selectedText.ifBlank { "Selected passage" },
                                                initialNote = "",
                                                initialColorHex = currentPreferences.defaultHighlightColorHex,
                                                initialStyle = currentPreferences.annotationStyle,
                                                isExisting = false
                                            )
                                        }
                                        frag.clearSelection()
                                        mode.finish()
                                    }
                                    ACTION_COPY -> {
                                        if (selectedText.isNotBlank()) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Luma Reader", selectedText)
                                            clipboard?.setPrimaryClip(clip)
                                            withContext(Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        frag.clearSelection()
                                        mode.finish()
                                    }
                                    ACTION_SHARE -> {
                                        if (selectedText.isNotBlank()) {
                                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                val attribution = "\n\n— \"${currentBook.title}\"" + if (currentBook.author.isNotBlank()) " by ${currentBook.author}" else ""
                                                putExtra(android.content.Intent.EXTRA_TEXT, "\"$selectedText\"$attribution")
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Quote from ${currentBook.title}")
                                            }
                                            val chooser = android.content.Intent.createChooser(sendIntent, "Share quote").apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(chooser)
                                        }
                                        frag.clearSelection()
                                        mode.finish()
                                    }
                                    ACTION_SEARCH -> {
                                        if (selectedText.isNotBlank()) {
                                            val searchIntent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
                                                putExtra(android.app.SearchManager.QUERY, selectedText.trim())
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            try {
                                                context.startActivity(searchIntent)
                                            } catch (_: Exception) {
                                                val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(selectedText.trim(), "UTF-8")
                                                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(browserIntent)
                                            }
                                        }
                                        frag.clearSelection()
                                        mode.finish()
                                    }
                                }
                            }
                            return true
                        }

                        override fun onDestroyActionMode(mode: ActionMode) {}
                    }
                }

                val initialPrefs = buildEpubPreferences(preferences, isDark, isFoldableOrWide, responsiveMarginFactor)
                val fragmentFactory = factory.createFragmentFactory(
                    initialLocator = initialLocator,
                    initialPreferences = initialPrefs,
                    configuration = navConfig
                )
                fragmentManager.fragmentFactory = fragmentFactory

                val existingFragment = fragmentManager.findFragmentByTag(fragmentTag)
                if (existingFragment != null) {
                    try {
                        fragmentManager.beginTransaction()
                            .remove(existingFragment)
                            .commitNowAllowingStateLoss()
                    } catch (_: Exception) {}
                }

                val fragment = fragmentFactory.instantiate(
                    activity.classLoader,
                    EpubNavigatorFragment::class.java.name
                ) as EpubNavigatorFragment

                fragmentManager.beginTransaction()
                    .replace(containerId, fragment, fragmentTag)
                    .commitAllowingStateLoss()

                fragment.viewLifecycleOwnerLiveData.observe(activity) { viewOwner ->
                    if (viewOwner != null) {
                        fragment.addDecorationListener("highlights", object : DecorableNavigator.Listener {
                            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                                if (!currentPreferences.annotationsEnabled) return false
                                val tappedId = event.decoration.id
                                val existingAnno = currentBook.annotations.find { it.id == tappedId }
                                val quote = existingAnno?.text ?: event.decoration.locator.text.highlight ?: "Highlighted passage"
                                activeNoteTarget = NoteDialogTarget(
                                    annotationId = tappedId,
                                    locator = event.decoration.locator,
                                    text = quote,
                                    initialNote = existingAnno?.note ?: "",
                                    initialColorHex = existingAnno?.colorHex ?: currentPreferences.defaultHighlightColorHex,
                                    initialStyle = existingAnno?.style ?: currentPreferences.annotationStyle,
                                    isExisting = true
                                )
                                return true
                            }
                        })
                        fragment.addInputListener(object : InputListener {
                            override fun onTap(event: TapEvent): Boolean {
                                if (controller.isUiVisible) {
                                    if (preferences.hapticsEnabled) {
                                        val targetView = fragment.view ?: activity.window.decorView
                                        performLumaHaptic(targetView, LumaHapticFeedbackType.TAP)
                                    }
                                    currentOnToggleUI()
                                    return true
                                }
                                val point = event.point
                                val viewWidth = fragment.view?.width ?: 1
                                val viewHeight = fragment.view?.height ?: 1

                                return if (preferences.navigationStyle == PageNavigationStyle.CONTINUOUS_SCROLL) {
                                    val yRatio = point.y / viewHeight.toFloat()
                                    when {
                                        yRatio < 0.2f -> {
                                            currentPerformPageTurn(false)
                                            true
                                        }
                                        yRatio > 0.8f -> {
                                            currentPerformPageTurn(true)
                                            true
                                        }
                                        else -> {
                                            if (preferences.hapticsEnabled) {
                                                val targetView = fragment.view ?: activity.window.decorView
                                                performLumaHaptic(targetView, LumaHapticFeedbackType.TAP)
                                            }
                                            currentOnToggleUI()
                                            true
                                        }
                                    }
                                } else {
                                    val xRatio = point.x / viewWidth.toFloat()
                                    when {
                                        xRatio < 0.2f -> {
                                            currentPerformPageTurn(false)
                                            true
                                        }
                                        xRatio > 0.8f -> {
                                            currentPerformPageTurn(true)
                                            true
                                        }
                                        else -> {
                                            if (preferences.hapticsEnabled) {
                                                val targetView = fragment.view ?: activity.window.decorView
                                                performLumaHaptic(targetView, LumaHapticFeedbackType.TAP)
                                            }
                                            currentOnToggleUI()
                                            true
                                        }
                                    }
                                }
                            }
                        })
                        activeFragment = fragment
                    } else {
                        if (activeFragment == fragment) {
                            activeFragment = null
                        }
                    }
                }
            }
        },
        update = { view ->
            view.setBackgroundColor(bgColorInt)
        },
        modifier = modifier.fillMaxSize().then(pageTurnDragModifier)
    )

    DisposableEffect(book.id) {
        onDispose {
            try {
                val fm = activity.supportFragmentManager
                val frag = fm.findFragmentByTag(fragmentTag)
                if (frag != null && !activity.isFinishing && !activity.isDestroyed) {
                    fm.beginTransaction()
                        .remove(frag)
                        .commitAllowingStateLoss()
                }
            } catch (_: Exception) {}
            activeFragment = null
        }
    }

        pageTurnTransition?.let { transition ->
            PageTurnOverlay(
                transition = transition,
                progress = pageTurnProgress.value,
                maxWidth = maxWidth,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Note Composer & Viewer Dialog
        activeNoteTarget?.let { target ->
            NoteComposerDialog(
                quoteText = target.text,
                initialNote = target.initialNote,
                initialColorHex = target.initialColorHex,
                initialStyle = target.initialStyle,
                isExisting = target.isExisting,
                onSave = { note, colorHex, style ->
                    val annotation = BookAnnotation(
                        id = target.annotationId,
                        bookId = currentBook.id,
                        text = target.text,
                        note = note,
                        colorHex = colorHex,
                        style = style,
                        locatorJson = target.locator.toJSON().toString(),
                        spineIndex = currentBook.currentSpineIndex,
                        progression = currentBook.currentProgression,
                        timestamp = System.currentTimeMillis()
                    )
                    if (target.isExisting) {
                        currentOnUpdateAnnotation(annotation)
                    } else {
                        currentOnAddAnnotation(annotation)
                    }
                    activeNoteTarget = null
                },
                onDelete = if (target.isExisting) {
                    {
                        currentOnDeleteAnnotation(target.annotationId)
                        activeNoteTarget = null
                    }
                } else null,
                onDismiss = { activeNoteTarget = null }
            )
        }
    }
}

data class PageTurnTransition(
    val bitmap: Bitmap,
    val isForward: Boolean,
    val isTwoColumns: Boolean
)

private fun findViewPager(view: android.view.View): ViewGroup? {
    if (view::class.java.name.contains("ViewPager")) return view as? ViewGroup
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = findViewPager(view.getChildAt(i))
            if (child != null) return child
        }
    }
    return null
}

private fun setupViewPagerTouch(viewPager: ViewGroup?, navigationStyle: PageNavigationStyle) {
    if (viewPager == null) return
    if (navigationStyle == PageNavigationStyle.PAGE_TURN) {
        viewPager.setOnTouchListener { _, event ->
            // In PAGE_TURN mode, prevent ViewPager from consuming drag/move events
            // but let DOWN/UP pass for taps
            event.action == android.view.MotionEvent.ACTION_MOVE
        }
    } else {
        viewPager.setOnTouchListener(null)
    }
}

private suspend fun captureViewBitmap(view: android.view.View?, activity: FragmentActivity?): Bitmap? {
    if (view == null || view.width <= 0 || view.height <= 0) return null
    return withContext(Dispatchers.Main) {
        val width = view.width
        val height = view.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val window = activity?.window
        var pixelCopySuccess = false
        if (window != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val decorView = window.decorView
            val decorW = decorView.width
            val decorH = decorView.height
            val left = location[0].coerceIn(0, decorW)
            val top = location[1].coerceIn(0, decorH)
            val right = (location[0] + width).coerceIn(left, decorW)
            val bottom = (location[1] + height).coerceIn(top, decorH)
            val rect = android.graphics.Rect(left, top, right, bottom)
            if (rect.width() > 0 && rect.height() > 0) {
                try {
                    val latch = CompletableDeferred<Boolean>()
                    PixelCopy.request(
                        window,
                        rect,
                        bitmap,
                        { result ->
                            latch.complete(result == PixelCopy.SUCCESS)
                        },
                        android.os.Handler(android.os.Looper.getMainLooper())
                    )
                    pixelCopySuccess = withTimeoutOrNull(100) {
                        latch.await()
                    } == true
                } catch (_: Exception) {
                    pixelCopySuccess = false
                }
            }
        }
        if (pixelCopySuccess) {
            bitmap
        } else {
            try {
                val canvas = android.graphics.Canvas(bitmap)
                view.draw(canvas)
                bitmap
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Composable
private fun PageTurnOverlay(
    transition: PageTurnTransition,
    progress: Float,
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val imageBitmap = remember(transition.bitmap) { transition.bitmap.asImageBitmap() }
    val isForward = transition.isForward
    val isTwoColumns = transition.isTwoColumns
    val clampedP = progress.coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        if (!isTwoColumns) {
            // Single Column: 3D perspective page turn with dynamic curl and spine lighting
            val rotY = if (isForward) -clampedP * 90f else clampedP * 90f
            val origin = if (isForward) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
            val alpha = if (clampedP > 0.85f) ((1f - clampedP) / 0.15f).coerceIn(0f, 1f) else 1f

            // Ambient drop shadow cast onto the stationary underlying page
            val dropShadowBrush = if (isForward) {
                val shadowStartX = (1f - clampedP) * 0.7f
                Brush.horizontalGradient(
                    shadowStartX to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.25f * (1f - clampedP))
                )
            } else {
                val shadowEndX = clampedP * 0.7f
                Brush.horizontalGradient(
                    0.0f to Color.Black.copy(alpha = 0.25f * (1f - clampedP)),
                    shadowEndX to Color.Transparent
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dropShadowBrush)
            )

            // Spine crease shadow
            val spineBrush = if (isForward) {
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.22f * (1f - clampedP)),
                    40f * density.density to Color.Transparent
                )
            } else {
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.22f * (1f - clampedP))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(spineBrush)
            )

            // Rotating Page Sheet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = rotY
                        transformOrigin = origin
                        cameraDistance = 24f * density.density
                        this.alpha = alpha
                        shadowElevation = 16f * (1f - clampedP)
                    }
            ) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                )

                // Paper curl gradient highlight & shadow
                val curlShader = if (isForward) {
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.20f * clampedP),
                        0.7f to Color.White.copy(alpha = 0.08f * sin(clampedP * Math.PI.toFloat())),
                        1.0f to Color.Black.copy(alpha = 0.35f * clampedP)
                    )
                } else {
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.35f * clampedP),
                        0.3f to Color.White.copy(alpha = 0.08f * sin(clampedP * Math.PI.toFloat())),
                        1.0f to Color.Black.copy(alpha = 0.20f * clampedP)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(curlShader)
                )
            }
        } else {
            // Dual Column (Foldables unfolded / Tablets):
            // Center of screen aligns with the device's physical hinge/spine
            val halfWidthDp = maxWidth / 2
            val halfWidthPx = transition.bitmap.width / 2

            if (isForward) {
                // Forward turn: Right page folds over toward center spine / hinge
                // Left page catches shadow
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(halfWidthDp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.30f * clampedP)
                            )
                        )
                )

                // Right page rotating from center spine
                val alpha = if (clampedP > 0.85f) ((1f - clampedP) / 0.15f).coerceIn(0f, 1f) else 1f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(halfWidthDp)
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            rotationY = -clampedP * 90f
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = 24f * density.density
                            this.alpha = alpha
                            shadowElevation = 16f * (1f - clampedP)
                        }
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset(halfWidthPx, 0),
                            srcSize = IntSize(transition.bitmap.width - halfWidthPx, transition.bitmap.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.20f * clampedP),
                                    0.7f to Color.White.copy(alpha = 0.08f * sin(clampedP * Math.PI.toFloat())),
                                    1.0f to Color.Black.copy(alpha = 0.35f * clampedP)
                                )
                            )
                    )
                }
            } else {
                // Backward turn: Left page folds over toward center spine / hinge
                // Right page catches shadow
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(halfWidthDp)
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color.Black.copy(alpha = 0.30f * clampedP),
                                0.5f to Color.Transparent
                            )
                        )
                )

                // Left page rotating toward center spine
                val alpha = if (clampedP > 0.85f) ((1f - clampedP) / 0.15f).coerceIn(0f, 1f) else 1f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(halfWidthDp)
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            rotationY = clampedP * 90f
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            cameraDistance = 24f * density.density
                            this.alpha = alpha
                            shadowElevation = 16f * (1f - clampedP)
                        }
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset(0, 0),
                            srcSize = IntSize(halfWidthPx, transition.bitmap.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.35f * clampedP),
                                    0.3f to Color.White.copy(alpha = 0.08f * sin(clampedP * Math.PI.toFloat())),
                                    1.0f to Color.Black.copy(alpha = 0.20f * clampedP)
                                )
                            )
                    )
                }
            }

            // Center Foldable Hinge / Spine Crease Shadow
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp)
                    .align(Alignment.Center)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

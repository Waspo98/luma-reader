package com.example.lumareader.ui.reader

import android.content.Context
import java.io.File
import java.io.FileInputStream
import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.lumareader.data.model.Book
import com.example.lumareader.data.model.LumaThemeMode
import com.example.lumareader.data.model.ReadingPreferences

// Additional imports for beautiful image handling & pinch-to-zoom
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory


// Custom WebView subclass to expose protected scroll calculation methods
class LumaWebView(context: Context) : WebView(context) {
    fun getHorizontalScrollRange(): Int = computeHorizontalScrollRange()
    fun getVerticalScrollRange(): Int = computeVerticalScrollRange()
}


@Composable
actual fun ReaderWebView(
    book: Book,
    chapterPath: String,
    preferences: ReadingPreferences,
    initialProgression: Float,
    isUiVisible: Boolean,
    onProgressChanged: (Float) -> Unit,
    onPageInfoChanged: (currentPage: Int, totalPages: Int) -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onToggleUI: () -> Unit,
    onNavigateToChapter: (Int, String?) -> Unit,
    targetHash: String?,
    modifier: Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }
    var isStylingApplied by remember { mutableStateOf(false) }

    // Use rememberUpdatedState for callbacks to prevent stale lambda captures in WebView JS interface
    val currentOnToggleUI by rememberUpdatedState(onToggleUI)
    val currentOnNextChapter by rememberUpdatedState(onNextChapter)
    val currentOnPrevChapter by rememberUpdatedState(onPrevChapter)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnPageInfoChanged by rememberUpdatedState(onPageInfoChanged)
    val currentOnNavigateToChapter by rememberUpdatedState(onNavigateToChapter)

    val accentColor = remember(preferences.accentColorHex) {
        try {
            val hex = preferences.accentColorHex.removePrefix("#")
            val parsed = hex.toLong(16)
            if (hex.length == 6) Color(0xFF000000 or parsed) else Color(parsed)
        } catch (_: Exception) {
            Color(0xFFD45D42)
        }
    }

    // NOTE: Immersive mode (hiding/showing system bars) is managed at the ReaderScreen level
    // via ImmersiveModeEffect. It must NOT be managed here because ReaderWebView instances are
    // created per-page inside HorizontalPager. When a page is recycled during a swipe, the
    // dispose callback would incorrectly show the status bar mid-reading.



    // Automatically trigger double-columns if screen width is 600dp or greater
    val isLargeScreen = configuration.screenWidthDp >= 600
    val isDoubleColumn = isLargeScreen && !preferences.twoColumnLocked

    // Resolve dark state based on theme preferences and system properties
    val isDark = when (preferences.themeMode) {
        LumaThemeMode.LIGHT -> false
        LumaThemeMode.SLATE_GRAY -> true
        LumaThemeMode.AMOLED_BLACK -> true
        LumaThemeMode.SYSTEM -> {
            val systemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            systemDark
        }
    }
    
    val webBgColor = when (preferences.themeMode) {
        LumaThemeMode.LIGHT -> "#FFFFFF"
        LumaThemeMode.SLATE_GRAY -> "#1C2025"
        LumaThemeMode.AMOLED_BLACK -> "#000000"
        LumaThemeMode.SYSTEM -> if (isDark) "#1C2025" else "#FFFFFF"
    }

    val webTextColor = when (preferences.themeMode) {
        LumaThemeMode.LIGHT -> "#000000"
        LumaThemeMode.SLATE_GRAY -> "#E2E8F0"
        LumaThemeMode.AMOLED_BLACK -> "#F3F4F6"
        LumaThemeMode.SYSTEM -> if (isDark) "#E2E8F0" else "#000000"
    }

    // Keep a simple float array to track user-scroll initiated progression without triggering recompositions
    val lastScrollProgress = remember(book.id, chapterPath) { FloatArray(1) { -1f } }

    val webView = remember(book.id) {
        if ((context.applicationContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        LumaWebView(context).apply {
            alpha = 0.01f
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                domStorageEnabled = true
                useWideViewPort = false
                loadWithOverviewMode = false
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
            setBackgroundColor(AndroidColor.parseColor(webBgColor))
            
            // Set up WebChromeClient to route JS console logs straight to Kotlin for seamless diagnosis
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("LumaWebViewJS", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} -- Line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                    return true
                }
            }

            // Register LumaApp Javascript interface thread-safely. Progression is reported exclusively here!
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun toggleUI() {
                    this@apply.post { currentOnToggleUI() }
                }

                @android.webkit.JavascriptInterface
                fun nextChapter() {
                    this@apply.post { currentOnNextChapter() }
                }

                @android.webkit.JavascriptInterface
                fun prevChapter() {
                    this@apply.post { currentOnPrevChapter() }
                }

                @android.webkit.JavascriptInterface
                fun onPageProgress(progress: Float) {
                    this@apply.post {
                        lastScrollProgress[0] = progress
                        currentOnProgressChanged(progress)
                    }
                }

                @android.webkit.JavascriptInterface
                fun onPageInfo(currentPage: Int, totalPages: Int) {
                    this@apply.post {
                        currentOnPageInfoChanged(currentPage, totalPages)
                    }
                }

                @android.webkit.JavascriptInterface
                fun openImageViewer(src: String) {
                    android.util.Log.d("LumaWebView", "openImageViewer JS interface called with src: $src")
                    this@apply.post {
                        android.util.Log.d("LumaWebView", "openImageViewer setting activeImageUrl to: $src")
                        activeImageUrl = src
                    }
                }

                @android.webkit.JavascriptInterface
                fun onLayoutStable() {
                    this@apply.post {
                        android.util.Log.d("LumaWebView", "onLayoutStable JS interface called. Styling applied.")
                        isStylingApplied = true
                    }
                }
            }, "LumaApp")
        }
    }

    // Set WebView backdrop color reactively to prevent any flicker/white flash on reload
    LaunchedEffect(webBgColor) {
        webView.setBackgroundColor(AndroidColor.parseColor(webBgColor))
    }

    LaunchedEffect(isStylingApplied) {
        webView.alpha = if (isStylingApplied) 1f else 0.01f
    }

    // Destroy WebView when this composable leaves composition
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }


    LaunchedEffect(activeImageUrl) {
        android.util.Log.d("LumaWebView", "activeImageUrl state changed: $activeImageUrl")
    }

    // Reload content when fileUrl or column posture changes
    val fileUrl = "file://${book.unzippedDir}/$chapterPath"
    LaunchedEffect(fileUrl, isDoubleColumn) {
        if ((context.applicationContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.util.Log.d("LumaWebView", "LaunchedEffect triggered. Loading HTML for $chapterPath, isDoubleColumn: $isDoubleColumn")
        }
        isPageLoaded = false
        isStylingApplied = false
        webView.alpha = 0.01f
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                android.util.Log.d("LumaWebView", "onPageFinished fired. URL: $url")
                super.onPageFinished(view, url)
                isPageLoaded = true
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                android.util.Log.e("LumaWebView", "onReceivedError (legacy): $errorCode - $description for $failingUrl")
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                android.util.Log.e("LumaWebView", "onReceivedError: ${error?.errorCode} - ${error?.description} for ${request?.url}")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                android.util.Log.d("LumaWebView", "shouldOverrideUrlLoading: url=$url, host=${url.host}, path=${url.path}")
                if (url.host == "luma-reader") {
                    val fullPath = url.path?.removePrefix("/") ?: ""
                    val targetPath = fullPath.substringBefore("#")
                    val hash = if (fullPath.contains("#")) fullPath.substringAfter("#") else null
                    val spineIndex = book.spine.indexOfFirst { it == targetPath }
                    if (spineIndex >= 0) {
                        android.util.Log.d("LumaWebView", "Link override matched spine index $spineIndex with hash=$hash. Navigating via Compose callback.")
                        webView.post {
                            currentOnNavigateToChapter(spineIndex, hash)
                        }
                        return true
                    }
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url ?: return null
                android.util.Log.d("LumaWebView", "shouldInterceptRequest: url=$url, host=${url.host}, path=${url.path}")
                if (url.host == "luma-reader") {
                    try {
                        val path = url.path ?: return null
                        
                        // Handle bundled fonts virtual routing
                        if (path.startsWith("/__fonts/")) {
                            val fontName = path.substringAfter("/__fonts/").substringBefore(".").lowercase()
                            val extension = path.substringAfterLast(".").lowercase()
                            val resId = context.resources.getIdentifier(fontName, "font", context.packageName)
                            if (resId != 0) {
                                val stream = context.resources.openRawResource(resId)
                                val mimeType = when (extension) {
                                    "otf" -> "font/otf"
                                    "woff" -> "font/woff"
                                    "woff2" -> "font/woff2"
                                    else -> "font/ttf"
                                }
                                val response = android.webkit.WebResourceResponse(mimeType, null, stream)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    response.responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
                                }
                                return response
                            }
                        }

                        val file = File(book.unzippedDir, path.trimStart('/'))
                        android.util.Log.d("LumaWebView", "Resolved path: ${file.absolutePath}, exists: ${file.exists()}")
                        if (file.exists() && file.isFile) {
                            val mimeType = when (file.extension.lowercase()) {
                                "html", "xhtml" -> "text/html"
                                "css" -> "text/css"
                                "js" -> "application/javascript"
                                "jpg", "jpeg" -> "image/jpeg"
                                "png" -> "image/png"
                                "gif" -> "image/gif"
                                "svg" -> "image/svg+xml"
                                "ttf" -> "font/ttf"
                                "otf" -> "font/otf"
                                "woff" -> "font/woff"
                                "woff2" -> "font/woff2"
                                else -> "application/octet-stream"
                            }
                            android.util.Log.d("LumaWebView", "Serving virtual file: ${file.absolutePath} with mimeType: $mimeType")
                            val stream = FileInputStream(file)
                            val encoding = if (mimeType.startsWith("text/") || 
                                             mimeType.contains("xml") || 
                                             mimeType.contains("javascript")) "UTF-8" else null
                            val response = android.webkit.WebResourceResponse(mimeType, encoding, stream)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                response.responseHeaders = mapOf(
                                    "Access-Control-Allow-Origin" to "*",
                                    "Cache-Control" to "no-cache"
                                )
                            }
                            return response
                        } else {
                            android.util.Log.w("LumaWebView", "Virtual file not found or not a file: ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LumaWebView", "Error intercepting virtual file request: ${url}", e)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        
        try {
            val file = File(book.unzippedDir, chapterPath)
            var htmlContent = if (file.exists()) file.readText() else ""
            
            // FOUC Prevention: hide body/html instantly with correct background color prior to style injection
            val foucPreventionStyle = """
                <style id="luma-fouc-prevention">
                    html, body {
                        opacity: 0.01 !important;
                        background-color: $webBgColor !important;
                    }
                </style>
            """.trimIndent()
            
            if (htmlContent.contains("<head>")) {
                htmlContent = htmlContent.replace("<head>", "<head>$foucPreventionStyle")
            } else if (htmlContent.contains("<html>")) {
                htmlContent = htmlContent.replace("<html>", "<html><head>$foucPreventionStyle</head>")
            } else {
                htmlContent = foucPreventionStyle + htmlContent
            }
            
            val parentPath = chapterPath.substringBeforeLast("/", "")
            val baseUrl = if (parentPath.isNotEmpty()) "https://luma-reader/$parentPath/" else "https://luma-reader/"
            webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            android.util.Log.e("LumaWebView", "Error loading chapter content", e)
            webView.loadUrl(fileUrl)
        }
    }

    // Inject styles instantly when styling options or page finishes loading, with zero flash!
    LaunchedEffect(
        isPageLoaded,
        webBgColor,
        webTextColor,
        preferences.fontSizeSp,
        preferences.fontFamily,
        preferences.lineSpacing,
        preferences.marginLeftDp,
        preferences.marginRightDp,
        preferences.marginTopDp,
        preferences.marginBottomDp,
        preferences.dropCapEnabled
    ) {
        android.util.Log.d("LumaWebView", "LaunchedEffect style check triggered. isPageLoaded: $isPageLoaded")
        if (!isPageLoaded) return@LaunchedEffect
        
        // Neutralize density double-scaling: WebView with viewport width=device-width already maps CSS pixels to DP
        val fontSizePx = preferences.fontSizeSp
        val lineSpacing = preferences.lineSpacing
        val marginLeftPx = preferences.marginLeftDp.toFloat()
        val marginRightPx = preferences.marginRightDp.toFloat()
        val marginTopPx = preferences.marginTopDp.toFloat()
        val marginBottomPx = preferences.marginBottomDp.toFloat()
        val columnGapPx = marginLeftPx + marginRightPx
        val dropCapEnabled = preferences.dropCapEnabled
        
        val resolvedFontFamily = when (preferences.fontFamily) {
            "Inter" -> "'Inter', 'Roboto', sans-serif"
            "Literata" -> "'Literata', Georgia, serif"
            else -> "'${preferences.fontFamily}', serif"
        }

        // Drop-cap is contextually active for Literata and System Serif to guarantee sleek aesthetics
        val dropCapStyle = if (preferences.fontFamily == "Literata" || preferences.fontFamily == "serif") """
            p.luma-drop-cap::first-letter,
            p.luma-drop-cap > b:first-of-type::first-letter,
            p.luma-drop-cap > span:first-of-type::first-letter,
            p.luma-drop-cap > strong:first-of-type::first-letter,
            p.luma-drop-cap > em:first-of-type::first-letter,
            p.luma-drop-cap > i:first-of-type::first-letter {
                font-size: 3.2em !important;
                float: left !important;
                line-height: 0.85 !important;
                margin-top: 0.08em !important;
                margin-right: 0.12em !important;
                font-weight: 700 !important;
                color: ${preferences.accentColorHex} !important;
                font-family: $resolvedFontFamily !important;
            }
        """.trimIndent() else ""
        
        val cssStyles = """
            @font-face {
                font-family: 'Google Sans';
                src: url('https://luma-reader/__fonts/googlesans_regular.ttf') format('truetype');
                font-weight: normal;
                font-style: normal;
            }
            @font-face {
                font-family: 'Google Sans';
                src: url('https://luma-reader/__fonts/googlesans_bold.ttf') format('truetype');
                font-weight: bold;
                font-style: normal;
            }
            @font-face {
                font-family: 'Inter';
                src: url('https://luma-reader/__fonts/inter.ttf') format('truetype');
                font-weight: normal;
                font-style: normal;
            }
            @font-face {
                font-family: 'Literata';
                src: url('https://luma-reader/__fonts/literata.ttf') format('truetype');
                font-weight: normal;
                font-style: normal;
            }
            
            html, body {
                background-color: $webBgColor !important;
                color: $webTextColor !important;
                font-family: $resolvedFontFamily !important;
                font-size: ${fontSizePx}px !important;
                line-height: ${preferences.lineSpacing} !important;
                -webkit-font-smoothing: antialiased;
                -moz-osx-font-smoothing: grayscale;
                height: 100% !important;
                width: 100% !important;
                margin: 0 !important;
                padding: 0 !important;
                overflow: hidden !important; /* Force completely hidden to deactivate all browser scroll engines */
                opacity: 1 !important; /* Override FOUC prevention style opacity */
            }

            div[id="luma-reader-wrapper"] {
                box-sizing: border-box !important;
                width: 100% !important;
                height: 100% !important;
                max-height: 100% !important;
                margin: 0 !important;
                padding: ${marginTopPx}px ${marginRightPx}px ${marginBottomPx}px ${marginLeftPx}px !important;
                column-count: ${if (isDoubleColumn) "2" else "auto"} !important;
                column-width: ${if (isDoubleColumn) "auto" else "calc(100% - ${marginLeftPx + marginRightPx}px)"} !important;
                column-gap: ${columnGapPx}px !important;
                column-fill: auto !important;
                overflow: visible !important;
                transform: translateX(0);
            }

            /* Prevent standard trailing paddings/margins from spilling into ghost blank columns */
            p:last-child, div:last-child, section:last-child, blockquote:last-child {
                margin-bottom: 0 !important;
                padding-bottom: 0 !important;
            }

            ::-webkit-scrollbar {
                display: none !important;
                width: 0 !important;
                height: 0 !important;
                background: transparent !important;
            }
            
            /* High-priority typography reset to override dirty EPUB embedded styles */
            p, span, div, li, td, th, blockquote, pre, b, strong, i, em, u, small, sub, sup, section, article, main {
                color: ${webTextColor} !important;
                font-family: $resolvedFontFamily !important;
            }

            /* Parent container resets for robust multi-column flow and clipping prevention */
            div:not([id="luma-reader-wrapper"]):not([class*="cover"]):not([id*="cover"]):not(:has(svg)), section, article, main, p {
                display: block !important;
                max-width: 100% !important;
                height: auto !important;
                overflow: visible !important;
                break-inside: auto !important;
                page-break-inside: auto !important;
            }
            
            section, article, main {
                margin: 0 !important;
                padding: 0 !important;
            }

            /* Prevent columns blowout by restricting all elements inside wrapper */
            #luma-reader-wrapper * {
                max-width: 100% !important;
                box-sizing: border-box !important;
            }
            img, svg, image {
                max-width: 100% !important;
                box-sizing: border-box !important;
            }

            svg {
                display: block !important;
                max-width: 100% !important;
                max-height: 100% !important;
                height: auto !important;
                width: auto !important;
                margin: 0 auto !important;
            }

            div:has(> svg) {
                max-width: 100% !important;
                max-height: 100% !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
            }
            
            figure, picture {
                display: block !important;
                height: auto !important;
                max-width: 100% !important;
                max-height: 100% !important;
                overflow: visible !important;
                box-sizing: border-box !important;
                margin: 1.2em auto !important;
                padding: 0 !important;
                break-inside: avoid !important;
                page-break-inside: avoid !important;
            }

            div:has(> img), p:has(> img) {
                max-width: 100% !important;
                max-height: 100% !important;
                overflow: visible !important;
                box-sizing: border-box !important;
                break-inside: avoid !important;
                page-break-inside: avoid !important;
            }

            /* Allow the first illustration/image in the chapter to start immediately in the first column/page */
            #luma-reader-wrapper > :first-child img,
            #luma-reader-wrapper > :first-child figure,
            #luma-reader-wrapper > figure:first-child,
            #luma-reader-wrapper > section > figure:first-child,
            #luma-reader-wrapper > section > :first-child img {
                break-inside: auto !important;
                page-break-inside: auto !important;
            }
            
            p {
                font-size: 1rem !important;
                line-height: ${preferences.lineSpacing} !important;
                margin-top: 0;
                margin-bottom: 0.5em;
                text-indent: 1.5em;
                text-align: justify;
                text-justify: inter-word;
            }

            li {
                font-size: 1rem !important;
                line-height: ${preferences.lineSpacing} !important;
                margin-top: 0.2em !important;
                margin-bottom: 0.2em !important;
            }
            
            p:first-of-type {
                text-indent: 0 !important;
            }
            
            $dropCapStyle
            
            h1, h2, h3, h4, h5, h6 {
                display: block !important;
                color: ${if (isDark) "#E2E8F0" else "#1F2937"} !important;
                font-family: $resolvedFontFamily !important;
                font-weight: 700 !important;
                text-align: center !important;
                line-height: 1.3 !important;
                margin-top: 1.6em !important;
                margin-bottom: 0.8em !important;
                letter-spacing: -0.02em !important;
                break-inside: avoid !important;
                page-break-inside: avoid !important;
            }
            
            h1 { font-size: 1.85em !important; }
            h2 { font-size: 1.55em !important; }
            h3 { font-size: 1.3em !important; }
            
            h1 + p, h2 + p, h3 + p, h4 + p, .chapter-title + p, .chapter-subtitle + p {
                text-indent: 0 !important;
            }
            
            img {
                display: block !important;
                margin: 1.2em auto !important;
                max-width: 100% !important;
                max-height: 100% !important;
                width: auto !important;
                height: auto !important;
                object-fit: contain !important;
                break-inside: avoid !important;
                page-break-inside: avoid !important;
                cursor: pointer !important;
                border: none !important;
                border-radius: 0 !important;
                box-shadow: none !important;
                transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
            }
            img:active {
                transform: scale(0.98) !important;
            }
            /* Selective inversion for black & white / monochrome illustrations */
            img.luma-invert-image {
                filter: invert(0.88) hue-rotate(180deg) !important;
            }
            
            /* High priority inline typography decorator resets */
            span, b, strong, i, em, u {
                display: inline !important;
                color: inherit !important;
                font-family: inherit !important;
                font-size: inherit; /* Allow drop-cap override on nested inline tags */
                line-height: inherit !important;
                break-inside: auto !important;
            }

            a {
                color: inherit !important;
                text-decoration: none !important;
            }
            a[href] {
                color: ${preferences.accentColorHex} !important;
                text-decoration: underline !important;
                text-underline-offset: 3px !important;
                transition: color 0.2s ease !important;
            }
            a[href]:hover {
                filter: brightness(1.2) !important;
            }
            
            blockquote {
                margin: 1.5em 24px !important;
                padding-left: 16px !important;
                border-left: 3px solid ${if (isDark) "#4B5563" else "#D1D5DB"} !important;
                font-style: italic !important;
                color: ${if (isDark) "#9CA3AF" else "#4B5563"} !important;
                break-inside: avoid !important;
            }

            sub, sup {
                display: inline-block !important;
                font-size: 75% !important;
                line-height: 0 !important;
                position: relative !important;
                vertical-align: baseline !important;
            }
            sub { bottom: -0.25em !important; }
            sup { top: -0.5em !important; }
            small {
                font-size: 80% !important;
            }
        """.trimIndent()
        
        val injectStyleJs = """
            (function() {
                console.log("LUMA DEBUG: Script evaluation triggered. isDoubleColumn: " + $isDoubleColumn);
                
                // 1. Stable layout dimensions calculation and cache
                var currentH = window.innerHeight;
                var currentW = window.innerWidth;
                if (!window.lumaStableHeight || Math.abs(window.lumaStableHeight - currentH) > 150) {
                    window.lumaStableHeight = currentH;
                }
                if (!window.lumaStableWidth || Math.abs(window.lumaStableWidth - currentW) > 150) {
                    window.lumaStableWidth = currentW;
                }

                var stableH = window.lumaStableHeight;
                var stableW = window.lumaStableWidth;

                // 2. Grayscale/Monochrome canvas sampler analyzer
                window.isGrayscaleImage = function(img) {
                    try {
                        var canvas = document.createElement('canvas');
                        canvas.width = 32;
                        canvas.height = 32;
                        var ctx = canvas.getContext('2d');
                        ctx.drawImage(img, 0, 0, 32, 32);
                        var imgData = ctx.getImageData(0, 0, 32, 32).data;
                        
                        var colorPixelCount = 0;
                        var totalPixels = 32 * 32;
                        
                        for (var i = 0; i < imgData.length; i += 4) {
                            var r = imgData[i];
                            var g = imgData[i+1];
                            var b = imgData[i+2];
                            var a = imgData[i+3];
                            
                            if (a < 30) continue; // Skip highly transparent pixels
                            
                            var max = Math.max(r, g, b);
                            var min = Math.min(r, g, b);
                            var chroma = max - min;
                            
                            if (chroma > 25) {
                                colorPixelCount++;
                            }
                        }
                        
                        var colorRatio = colorPixelCount / totalPixels;
                        console.log("LUMA IMAGE ANALYSIS: src=" + img.src + ", colorRatio=" + colorRatio);
                        return colorRatio < 0.015;
                    } catch (e) {
                        console.error("LUMA IMAGE ANALYSIS CORS/SAFE FALLBACK: " + e);
                        return true; 
                    }
                };

                // Helper functions defined at the top
                window.getMaxScroll = function() {
                    var wrapper = document.getElementById('luma-reader-wrapper');
                    var wrapperScrollWidth = wrapper ? wrapper.scrollWidth : 0;
                    var docScrollWidth = document.documentElement.scrollWidth || 0;
                    var bodyScrollWidth = document.body.scrollWidth || 0;
                    var scrollWidth = Math.max(docScrollWidth, bodyScrollWidth, wrapperScrollWidth);
                    console.log("LUMA SCROLL DEBUG: wrapperScrollWidth=" + wrapperScrollWidth + ", docScrollWidth=" + docScrollWidth + ", bodyScrollWidth=" + bodyScrollWidth + ", maxScrollWidth=" + scrollWidth);
                    var pageWidth = window.lumaStableWidth || document.documentElement.clientWidth || window.innerWidth || 360;
                    if (pageWidth < 1) pageWidth = 1;
                    
                    var roundedScrollWidth = Math.ceil((scrollWidth - 15) / pageWidth) * pageWidth;
                    var max = roundedScrollWidth - pageWidth;
                    if (isNaN(max) || max < 0) return 0;
                    return max;
                };

                window.getTranslationX = function() {
                    var slideEl = document.getElementById('luma-reader-wrapper');
                    if (!slideEl) return 0;
                    try {
                        var style = window.getComputedStyle(slideEl);
                        var transform = style.transform || style.webkitTransform;
                        if (transform && transform !== 'none') {
                            var parts = transform.split('(')[1].split(')')[0].split(',');
                            if (parts.length === 6) {
                                var tx = parseFloat(parts[4]);
                                if (!isNaN(tx)) return -tx;
                            } else if (parts.length === 16) {
                                var tx = parseFloat(parts[12]);
                                if (!isNaN(tx)) return -tx;
                            }
                        }
                    } catch (e) {
                        console.error("LUMA ERROR parsing transform: " + e);
                    }
                    return window.currentTranslationX || 0;
                };

                window.adjustImagesAndSvgs = function() {
                    var h = window.lumaStableHeight || window.innerHeight || 800;
                    var maxHeight = h - $marginTopPx - $marginBottomPx - 40;
                    if (maxHeight < 100) maxHeight = h - 40;
                    if (maxHeight < 100) maxHeight = 400; // Safe fallback if layout hasn't completed
                    
                    var svgs = document.querySelectorAll('svg');
                    svgs.forEach(function(svg) {
                        var w = svg.getAttribute('width');
                        var hValAttr = svg.getAttribute('height');
                        if (w && hValAttr && !svg.getAttribute('viewBox')) {
                            var wVal = parseFloat(w);
                            var hVal = parseFloat(hValAttr);
                            if (!isNaN(wVal) && !isNaN(hVal)) {
                                svg.setAttribute('viewBox', '0 0 ' + wVal + ' ' + hVal);
                            }
                        }
                        svg.removeAttribute('width');
                        svg.removeAttribute('height');
                        svg.style.setProperty('max-width', '100%', 'important');
                        svg.style.setProperty('max-height', maxHeight + 'px', 'important');
                        svg.style.setProperty('width', 'auto', 'important');
                        svg.style.setProperty('height', 'auto', 'important');
                    });

                    var imageMode = "${preferences.imageHandlingMode.name}";
                    var isDark = $isDark;

                    var imgs = document.querySelectorAll('img');
                    imgs.forEach(function(img) {
                        var wAttr = img.getAttribute('width');
                        var hAttr = img.getAttribute('height');
                        if (wAttr && wAttr.indexOf('%') === -1) img.removeAttribute('width');
                        if (hAttr && hAttr.indexOf('%') === -1) img.removeAttribute('height');
                        
                        img.style.setProperty('max-width', '100%', 'important');
                        img.style.setProperty('max-height', maxHeight + 'px', 'important');
                        img.style.setProperty('object-fit', 'contain', 'important');
                        
                        // Selective dark-mode image inversion
                        img.classList.remove('luma-invert-image');
                        if (isDark && !img.src.includes('cover') && !img.className.includes('cover') && !img.id.includes('cover')) {
                            if (imageMode === "INVERT_ALL") {
                                img.classList.add('luma-invert-image');
                            } else if (imageMode === "INVERT_BW") {
                                if (img.complete && img.naturalWidth > 0) {
                                    if (window.isGrayscaleImage(img)) {
                                        img.classList.add('luma-invert-image');
                                    }
                                } else {
                                    img.addEventListener('load', function() {
                                        if (window.isGrayscaleImage(img)) {
                                            img.classList.add('luma-invert-image');
                                        }
                                    }, { once: true });
                                }
                            }
                        }
                        
                        // Set explicit pixel width and height on loaded images to prevent 0x0 collapse
                        // and ensure reliable rendering in WebKit's column layout engine.
                        if (img.complete && img.naturalWidth > 0 && img.naturalHeight > 0) {
                            var nw = img.naturalWidth;
                            var nh = img.naturalHeight;
                            
                            // Use viewport-based column width directly to avoid circular layout loops
                            var maxWidth = (window.lumaStableWidth || window.innerWidth) - $marginLeftPx - $marginRightPx;
                            if (window.isDoubleColumn) {
                                maxWidth = ((window.lumaStableWidth || window.innerWidth) - $marginLeftPx - $marginRightPx - $columnGapPx) / 2;
                            }
                            
                            var scale = Math.min(1.0, maxWidth / nw, maxHeight / nh);
                            var targetW = Math.round(nw * scale);
                            var targetH = Math.round(nh * scale);
                            
                            if (targetW > 50 && targetH > 50) {
                                img.style.setProperty('width', targetW + 'px', 'important');
                                img.style.setProperty('height', targetH + 'px', 'important');
                            } else {
                                img.style.setProperty('width', 'auto', 'important');
                                img.style.setProperty('height', 'auto', 'important');
                            }
                        } else {
                            // Re-run adjustment when the image finishes loading
                            img.addEventListener('load', function() {
                                adjustImagesAndSvgs();
                            }, { once: true });
                        }
                        
                        // Unlock any parent containers that have been given a fixed pixel height
                        // that is larger than the viewport — these clip images in column layout.
                        var parent = img.parentElement;
                        if (parent && parent.id !== 'luma-reader-wrapper') {
                            var parentStyle = window.getComputedStyle(parent);
                            var parentHeight = parentStyle.height;
                            if (parentHeight && parentHeight.indexOf('px') !== -1) {
                                var phVal = parseFloat(parentHeight);
                                if (phVal > maxHeight + 40) {
                                    parent.style.setProperty('height', 'auto', 'important');
                                    parent.style.setProperty('max-height', '100%', 'important');
                                }
                            }
                        }
                    });
                };

                window.applyColumnLayout = function() {
                    var currentH = window.innerHeight;
                    var currentW = window.innerWidth;
                    if (!window.lumaStableHeight || Math.abs(window.lumaStableHeight - currentH) > 150) {
                        window.lumaStableHeight = currentH;
                    }
                    if (!window.lumaStableWidth || Math.abs(window.lumaStableWidth - currentW) > 150) {
                        window.lumaStableWidth = currentW;
                    }

                    var stableH = window.lumaStableHeight;
                    var stableW = window.lumaStableWidth;
                    
                    var wrapper = document.getElementById('luma-reader-wrapper');
                    if (wrapper && stableH > 100) {
                        var heightPx = stableH + 'px';
                        var widthPx = stableW + 'px';
                        
                        wrapper.style.setProperty('height', heightPx, 'important');
                        wrapper.style.setProperty('max-height', heightPx, 'important');
                        wrapper.style.setProperty('width', widthPx, 'important');
                        
                        document.body.style.setProperty('height', heightPx, 'important');
                        document.body.style.setProperty('width', widthPx, 'important');
                        
                        document.documentElement.style.setProperty('height', heightPx, 'important');
                        document.documentElement.style.setProperty('width', widthPx, 'important');
                        
                        // Line-clipping prevention: Adjust bottom padding so column content height is an exact multiple of line-height!
                        var fontSizePx = $fontSizePx;
                        var lineSpacing = $lineSpacing;
                        var lineOuterHeight = fontSizePx * lineSpacing;
                        var columnContentHeight = stableH - $marginTopPx - $marginBottomPx;
                        var numLines = Math.floor(columnContentHeight / lineOuterHeight);
                        var adjustedContentHeight = numLines * lineOuterHeight;
                        var extraPaddingBottom = columnContentHeight - adjustedContentHeight;
                        var newPaddingBottom = $marginBottomPx + extraPaddingBottom;
                        
                        wrapper.style.setProperty('padding-top', $marginTopPx + 'px', 'important');
                        wrapper.style.setProperty('padding-bottom', newPaddingBottom + 'px', 'important');
                        wrapper.style.setProperty('padding-left', $marginLeftPx + 'px', 'important');
                        wrapper.style.setProperty('padding-right', $marginRightPx + 'px', 'important');
                        
                        var colWidthPx = (stableW - $marginLeftPx - $marginRightPx) + 'px';
                        var colGapPx = ($marginLeftPx + $marginRightPx) + 'px';
                        if ($isDoubleColumn) {
                            colWidthPx = ((stableW - $marginLeftPx - $marginRightPx - $columnGapPx) / 2) + 'px';
                            colGapPx = $columnGapPx + 'px';
                        }
                        wrapper.style.setProperty('column-width', colWidthPx, 'important');
                        wrapper.style.setProperty('column-gap', colGapPx, 'important');
                    }
                };

                window.hasConsumedInitialProgression = window.hasConsumedInitialProgression || false;

                window.snapProgress = function() {
                    window.applyColumnLayout();
                    adjustImagesAndSvgs();
                    var maxScroll = getMaxScroll();
                    var targetX = 0;
                    var pageWidth = window.lumaStableWidth || window.innerWidth || 360;
                    
                    if (window.targetHash) {
                        var anchor = document.getElementById(window.targetHash) || 
                                     document.querySelector('[name="' + window.targetHash + '"]');
                        if (anchor) {
                            var anchorX = anchor.getBoundingClientRect().left + (window.currentTranslationX || 0);
                            targetX = Math.floor(anchorX / pageWidth) * pageWidth;
                            if (maxScroll > 0) {
                                targetX = Math.max(0, Math.min(targetX, maxScroll));
                            }
                            var slideEl = document.getElementById('luma-reader-wrapper');
                            if (slideEl) {
                                slideEl.style.transition = 'none';
                                slideEl.style.transform = 'translateX(' + (-targetX) + 'px)';
                            }
                            window.currentTranslationX = targetX;
                            window.targetHash = null; // Clear hash to unlock swipes
                        } else {
                            if (maxScroll > 0) {
                                var progressionToUse = $initialProgression;
                                if (window.hasConsumedInitialProgression && window.lumaMaxScrollAtLastSnap > 0) {
                                    progressionToUse = (window.currentTranslationX || 0) / window.lumaMaxScrollAtLastSnap;
                                }
                                targetX = progressionToUse * maxScroll;
                                targetX = Math.round(targetX / pageWidth) * pageWidth;
                                var slideEl = document.getElementById('luma-reader-wrapper');
                                if (slideEl) {
                                    slideEl.style.transition = 'none';
                                    slideEl.style.transform = 'translateX(' + (-targetX) + 'px)';
                                }
                            }
                            window.currentTranslationX = targetX;
                        }
                    } else {
                        if (maxScroll > 0) {
                            var progressionToUse = $initialProgression;
                            if (window.hasConsumedInitialProgression && window.lumaMaxScrollAtLastSnap > 0) {
                                progressionToUse = (window.currentTranslationX || 0) / window.lumaMaxScrollAtLastSnap;
                            }
                            targetX = progressionToUse * maxScroll;
                            targetX = Math.round(targetX / pageWidth) * pageWidth;
                            var slideEl = document.getElementById('luma-reader-wrapper');
                            if (slideEl) {
                                slideEl.style.transition = 'none';
                                slideEl.style.transform = 'translateX(' + (-targetX) + 'px)';
                            }
                        }
                        window.currentTranslationX = targetX;
                    }
                    
                    window.hasConsumedInitialProgression = true;
                    window.lumaMaxScrollAtLastSnap = maxScroll;
                    
                    var progress = maxScroll > 0 ? targetX / maxScroll : 0;
                    var currentPage = Math.max(1, Math.round(targetX / pageWidth) + 1);
                    var totalPages = Math.max(1, Math.round(maxScroll / pageWidth) + 1);
                    
                    if (window.lastReportedX !== targetX) {
                        window.lastReportedX = targetX;
                        LumaApp.onPageProgress(progress);
                        LumaApp.onPageInfo(currentPage, totalPages);
                    }
                };

                // Inject or update style tag contents
                var style = document.getElementById('luma-reader-styles');
                if (!style) {
                    style = document.createElementNS('http://www.w3.org/1999/xhtml', 'style');
                    style.id = 'luma-reader-styles';
                    document.head.appendChild(style);
                }
                style.innerHTML = "/*<![CDATA[*/\n" + `${cssStyles}` + "\n/*]]>*/";

                // Remove FOUC prevention style tag
                var fouc = document.getElementById('luma-fouc-prevention');
                if (fouc) fouc.parentNode.removeChild(fouc);

                window.isDoubleColumn = $isDoubleColumn;
                window.targetHash = ${if (targetHash != null) "\"$targetHash\"" else "null"};
                
                // Update wrapper properties if it already exists
                var wrapper = document.getElementById('luma-reader-wrapper');

                // If one-time JS engine is already initialized, update and early return!
                if (window.lumaSetupDone) {
                    adjustImagesAndSvgs();
                    snapProgress();
                    return;
                }
                window.lumaSetupDone = true;

                // 4. One-time Setup logic
                if (document.body) {
                    if (!wrapper) {
                        wrapper = document.createElementNS('http://www.w3.org/1999/xhtml', 'div');
                        wrapper.id = 'luma-reader-wrapper';
                        while (document.body.firstChild) {
                            wrapper.appendChild(document.body.firstChild);
                        }
                        document.body.appendChild(wrapper);
                    }

                    var paragraphs = wrapper.querySelectorAll('p');
                    for (var i = 0; i < paragraphs.length; i++) {
                        paragraphs[i].classList.remove('luma-drop-cap');
                    }
                    if ($dropCapEnabled) {
                        for (var i = 0; i < paragraphs.length; i++) {
                            var p = paragraphs[i];
                            var pClass = p.className || "";
                            var text = p.textContent || "";
                            if (p.querySelector('img') || pClass.indexOf('centre') !== -1 || text.trim().length < 15) {
                                continue;
                            }
                            p.classList.add('luma-drop-cap');
                            break;
                        }
                    }
                }

                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElementNS('http://www.w3.org/1999/xhtml', 'meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

                window.currentTranslationX = 0;
                window.lastReportedX = -9999;

                var startX = 0;
                var startY = 0;
                var startTime = 0;
                var isDragging = false;
                var isSwipeDetermined = false;
                var startScrollX = 0;
                
                window.transitionActive = false;
                window.transitionTimeout = null;

                window.slideTo = function(targetX, duration, isChapterChange) {
                    var maxScroll = getMaxScroll();
                    
                    if (isChapterChange) {
                        if (targetX < -40) {
                            LumaApp.prevChapter();
                            return;
                        } else if (targetX > maxScroll + 40) {
                            LumaApp.nextChapter();
                            return;
                        }
                    }
                    
                    var pageWidth = window.lumaStableWidth || window.innerWidth || 360;
                    var coercedX = Math.round(targetX / pageWidth) * pageWidth;
                    coercedX = Math.max(0, Math.min(coercedX, maxScroll));
                    
                    window.currentTranslationX = coercedX;
                    
                    var slideEl = document.getElementById('luma-reader-wrapper');
                    if (slideEl) {
                        slideEl.style.transition = 'transform ' + duration + 'ms cubic-bezier(0.1, 0.9, 0.25, 1)';
                        slideEl.style.transform = 'translateX(' + (-coercedX) + 'px)';
                    }
                    
                    window.transitionActive = true;
                    if (window.transitionTimeout) {
                        clearTimeout(window.transitionTimeout);
                    }
                    
                    window.transitionTimeout = setTimeout(function() {
                        if (slideEl) {
                            slideEl.style.transition = 'none';
                        }
                        window.transitionActive = false;
                        window.transitionTimeout = null;
                        
                        var progress = maxScroll > 0 ? coercedX / maxScroll : 0;
                        var currentPage = Math.max(1, Math.round(coercedX / pageWidth) + 1);
                        var totalPages = Math.max(1, Math.round(maxScroll / pageWidth) + 1);
                        
                        if (window.lastReportedX !== coercedX) {
                            window.lastReportedX = coercedX;
                            LumaApp.onPageProgress(progress);
                            LumaApp.onPageInfo(currentPage, totalPages);
                        }
                    }, duration);
                };

                window.goNext = function() {
                    var maxScroll = getMaxScroll();
                    var curX = window.currentTranslationX;
                    var pageWidth = window.lumaStableWidth || window.innerWidth || 360;
                    if (curX >= maxScroll - 5) {
                        LumaApp.nextChapter();
                    } else {
                        window.slideTo(curX + pageWidth, 300, false);
                    }
                };

                window.goPrev = function() {
                    var curX = window.currentTranslationX;
                    var pageWidth = window.lumaStableWidth || window.innerWidth || 360;
                    if (curX <= 5) {
                        LumaApp.prevChapter();
                    } else {
                        window.slideTo(curX - pageWidth, 300, false);
                    }
                };

                window.addEventListener('touchstart', function(e) {
                    if (e.target && (e.target.tagName.toLowerCase() === 'img' || e.target.closest('img'))) return;
                    
                    if (e.touches.length === 1) {
                        startX = e.touches[0].clientX;
                        startY = e.touches[0].clientY;
                        startTime = Date.now();
                        startScrollX = window.currentTranslationX;
                        isDragging = true;
                        isSwipeDetermined = false;
                        
                        window.lumaMaxScroll = getMaxScroll();
                    }
                }, { passive: true });

                window.addEventListener('touchmove', function(e) {
                    if (e.target && (e.target.tagName.toLowerCase() === 'img' || e.target.closest('img'))) return;
                    if (!isDragging) return;
                    var currentX = e.touches[0].clientX;
                    var currentY = e.touches[0].clientY;
                    var deltaX = currentX - startX;
                    var deltaY = currentY - startY;

                    if (!isSwipeDetermined) {
                        if (Math.abs(deltaX) > 8 || Math.abs(deltaY) > 8) {
                            if (Math.abs(deltaX) > Math.abs(deltaY) * 1.3) {
                                isSwipeDetermined = true;
                                
                                if (window.transitionActive) {
                                    var currentTranslateX = getTranslationX();
                                    var slideEl = document.getElementById('luma-reader-wrapper');
                                    if (slideEl) {
                                        slideEl.style.transition = 'none';
                                        slideEl.style.transform = 'translateX(' + (-currentTranslateX) + 'px)';
                                        window.currentTranslationX = currentTranslateX;
                                    }
                                    window.transitionActive = false;
                                    if (window.transitionTimeout) {
                                        clearTimeout(window.transitionTimeout);
                                        window.transitionTimeout = null;
                                    }
                                    startScrollX = currentTranslateX;
                                }
                            } else {
                                isDragging = false;
                            }
                        }
                    }

                    if (isSwipeDetermined && isDragging) {
                        if (e.cancelable) e.preventDefault();
                        
                        var maxScroll = window.lumaMaxScroll !== undefined ? window.lumaMaxScroll : getMaxScroll();
                        var targetScroll = startScrollX - deltaX;
                        var visualDelta = deltaX;
                        
                        if (targetScroll < 0) {
                            visualDelta = deltaX * 0.35;
                        } else if (targetScroll > maxScroll) {
                            var excess = targetScroll - maxScroll;
                            visualDelta = deltaX - excess * 0.65;
                        }
                        
                        var slideEl = document.getElementById('luma-reader-wrapper');
                        if (slideEl) {
                            var currentTranslate = -startScrollX + visualDelta;
                            slideEl.style.transform = 'translateX(' + currentTranslate + 'px)';
                        }
                    }
                }, { passive: false });

                window.addEventListener('touchend', function(e) {
                    var deltaX = e.changedTouches[0].clientX - startX;
                    var deltaY = e.changedTouches[0].clientY - startY;
                    var elapsedTime = Date.now() - startTime;
                    var isGestureInProgress = isSwipeDetermined && isDragging;

                    if (!isGestureInProgress && e.target && (
                        e.target.tagName.toLowerCase() === 'img' || e.target.closest('img') ||
                        e.target.tagName.toLowerCase() === 'a' || e.target.closest('a') ||
                        e.target.tagName.toLowerCase() === 'image' || e.target.closest('image')
                    )) return;

                    if (elapsedTime < 300 && Math.abs(deltaX) < 15 && Math.abs(deltaY) < 15) {
                        var tapX = e.changedTouches[0].clientX;
                        var pageWidth = window.lumaStableWidth || window.innerWidth || 360;
                        if (tapX < pageWidth * 0.20) {
                            window.goPrev();
                        } else if (tapX > pageWidth * 0.80) {
                            window.goNext();
                        } else {
                            LumaApp.toggleUI();
                        }
                        isDragging = false;
                        return;
                    }

                    if (!isDragging) return;
                    isDragging = false;
                    
                    var maxScroll = window.lumaMaxScroll !== undefined ? window.lumaMaxScroll : getMaxScroll();
                    var pageWidth = window.lumaStableWidth || window.innerWidth || 360;

                    if (isSwipeDetermined) {
                        if (Math.abs(deltaX) > 60 || (Math.abs(deltaX) > 20 && elapsedTime < 250)) {
                            if (deltaX < 0) {
                                if (startScrollX >= maxScroll - 5) {
                                    window.slideTo(maxScroll, 0, false);
                                    LumaApp.nextChapter();
                                } else {
                                    window.slideTo(startScrollX + pageWidth, 300, false);
                                }
                            } else {
                                if (startScrollX <= 5) {
                                    window.slideTo(0, 0, false);
                                    LumaApp.prevChapter();
                                } else {
                                    window.slideTo(startScrollX - pageWidth, 300, false);
                                }
                            }
                        } else {
                            window.slideTo(startScrollX, 200, false);
                        }
                    } else {
                        window.slideTo(startScrollX, 150, false);
                    }
                    
                    window.lumaMaxScroll = undefined;
                }, { passive: true });

                // Smart debounced snapProgress caller to prevent thrashing
                var snapTimeout = null;
                function debouncedSnapProgress(delay) {
                    if (snapTimeout) clearTimeout(snapTimeout);
                    snapTimeout = setTimeout(function() {
                        snapProgress();
                        snapTimeout = null;
                        
                        // Notify layout stable
                        if (window.LumaApp && window.LumaApp.onLayoutStable) {
                            window.LumaApp.onLayoutStable();
                        }
                    }, delay || 0);
                }

                // Initial snap
                snapProgress();

                // Fonts ready snap
                if (document.fonts) {
                    document.fonts.ready.then(function() {
                        console.log("LUMA DEBUG: Fonts ready.");
                        debouncedSnapProgress(50);
                    });
                }

                // Wait for all images to complete loading
                var pendingImgs = [];
                var imgs = document.querySelectorAll('img, svg image, image');
                imgs.forEach(function(img) {
                    if (img.getAttribute('data-luma-bound')) return;
                    img.setAttribute('data-luma-bound', 'true');

                    var isSvgImage = img.tagName.toLowerCase() === 'image';
                    var src = isSvgImage ? (img.getAttribute('xlink:href') || img.getAttribute('href') || '') : img.src;
                    
                    console.log("LUMA IMG DEBUG: Initial state: tag=" + img.tagName + ", src=" + src);
                    
                    var isLoaded = isSvgImage ? true : img.complete;
                    
                    if (isLoaded) {
                        debouncedSnapProgress(50);
                    } else {
                        img.addEventListener('load', function() {
                            adjustImagesAndSvgs();
                            debouncedSnapProgress(50);
                        });
                        img.addEventListener('error', function() {
                            debouncedSnapProgress(50);
                        });
                    }

                    var imgStartX = 0;
                    var imgStartY = 0;
                    var imgStartTime = 0;
                    
                    img.addEventListener('touchstart', function(e) {
                        e.stopPropagation();
                        if (e.touches.length === 1) {
                            imgStartX = e.touches[0].clientX;
                            imgStartY = e.touches[0].clientY;
                            imgStartTime = Date.now();
                        }
                    }, { passive: true });

                    img.addEventListener('touchmove', function(e) {
                        e.stopPropagation();
                    }, { passive: true });

                    img.addEventListener('touchend', function(e) {
                        e.stopPropagation();
                        var imgDeltaX = e.changedTouches[0].clientX - imgStartX;
                        var imgDeltaY = e.changedTouches[0].clientY - imgStartY;
                        var imgElapsedTime = Date.now() - imgStartTime;
                        
                        if (imgElapsedTime < 350 && Math.abs(imgDeltaX) < 15 && Math.abs(imgDeltaY) < 15) {
                            if (e.cancelable) e.preventDefault();
                            console.log("LUMA IMG DEBUG: Direct image tap verified. Opening viewer: " + src);
                            if (window.LumaApp && window.LumaApp.openImageViewer && src) {
                                window.LumaApp.openImageViewer(src);
                            }
                        }
                    }, { passive: false });

                    img.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        console.log("LUMA IMG DEBUG: Image click fired: " + src);
                        if (window.LumaApp && window.LumaApp.openImageViewer && src) {
                            window.LumaApp.openImageViewer(src);
                        }
                    });
                    
                    if (!isSvgImage && !img.complete) {
                        pendingImgs.push(new Promise(function(resolve) {
                            img.addEventListener('load', resolve);
                            img.addEventListener('error', resolve);
                            setTimeout(resolve, 3000);
                        }));
                    }
                });

                if (pendingImgs.length > 0) {
                    Promise.all(pendingImgs).then(function() {
                        console.log("LUMA DEBUG: All pending images loaded.");
                        debouncedSnapProgress(50);
                    });
                } else {
                    // If no pending images, notify layout stable immediately after initial snap
                    if (window.LumaApp && window.LumaApp.onLayoutStable) {
                        window.LumaApp.onLayoutStable();
                    }
                }

                // Debounced ResizeObserver
                if (window.ResizeObserver && wrapper) {
                    var resizeObserver = new ResizeObserver(function() {
                        debouncedSnapProgress(200);
                    });
                    resizeObserver.observe(wrapper);
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(injectStyleJs) { result ->
            android.util.Log.d("LumaWebView", "evaluateJavascript done. Result: $result")
            // Safe 300ms fallback to guarantee the page becomes visible even if layout callbacks are blocked
            webView.postDelayed({
                if (!isStylingApplied) {
                    android.util.Log.w("LumaWebView", "onLayoutStable fallback triggered. Forcing isStylingApplied=true")
                    isStylingApplied = true
                }
            }, 300)
        }
    }

    // Scroll smoothly to external progress adjustments (slider, TOC) with feedback loop prevention
    LaunchedEffect(initialProgression) {
        if (!isPageLoaded) return@LaunchedEffect
        
        // If the update matches our last user scroll, skip to prevent feedback loop stuttering
        if (lastScrollProgress[0] >= 0f && kotlin.math.abs(initialProgression - lastScrollProgress[0]) < 0.01f) {
            return@LaunchedEffect
        }
        
        webView.evaluateJavascript("""
            (function() {
                function getMaxScroll() {
                    var wrapper = document.getElementById('luma-reader-wrapper');
                    var scrollWidth = wrapper ? wrapper.scrollWidth : window.innerWidth;
                    var pageWidth = window.lumaStableWidth || window.innerWidth || 360;
                    if (pageWidth < 1) pageWidth = 1;
                    var roundedScrollWidth = Math.ceil((scrollWidth - 15) / pageWidth) * pageWidth;
                    var max = roundedScrollWidth - pageWidth;
                    if (isNaN(max) || max < 0) return 0;
                    return max;
                }

                var max = getMaxScroll();
                if (max > 0) {
                    var targetX = $initialProgression * max;
                    var pageWidth = window.lumaStableWidth || window.innerWidth || 360;
                    targetX = Math.round(targetX / pageWidth) * pageWidth;
                    if (Math.abs(window.currentTranslationX - targetX) > 15) {
                        window.slideTo(targetX, 300, false);
                    }
                }
            })();
        """.trimIndent(), null)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(AndroidColor.parseColor(webBgColor)))
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isStylingApplied) 1f else 0.01f)
        )
        
        if (!isStylingApplied) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter),
                color = accentColor,
                trackColor = Color.Transparent
            )
        }

        // Premium Full-Screen Image Viewer Overlay
        AnimatedVisibility(
            visible = activeImageUrl != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            val currentSrc = activeImageUrl
            if (currentSrc != null) {
                val relativePath = remember(currentSrc) {
                    currentSrc
                        .removePrefix("https://luma-reader/")
                        .removePrefix("file://")
                        .trimStart('/')
                }
                val imageFile = remember(relativePath) {
                    File(book.unzippedDir, relativePath)
                }
                val bitmap = remember(imageFile) {
                    if (imageFile.exists()) {
                        try {
                            BitmapFactory.decodeFile(imageFile.absolutePath)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                // Smart initial inversion logic
                val isCover = remember(relativePath) {
                    relativePath.contains("cover", ignoreCase = true)
                }
                var localInvertEnabled by remember { mutableStateOf(isDark && !isCover) }

                // Interactive zoom/pan states
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                LaunchedEffect(currentSrc) {
                    scale = 1f
                    offset = androidx.compose.ui.geometry.Offset.Zero
                }

                val invertMatrix = remember {
                    ColorMatrix(
                        floatArrayOf(
                            -1f,  0f,  0f,  0f, 255f,
                             0f, -1f,  0f,  0f, 255f,
                             0f,  0f, -1f,  0f, 255f,
                             0f,  0f,  0f,  1f,   0f
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f))
                        .clickable(enabled = scale == 1f) { activeImageUrl = null } // Click backdrop to close
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Full Screen Zoomable Image",
                            colorFilter = if (localInvertEnabled) ColorFilter.colorMatrix(invertMatrix) else null,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        if (scale > 1f) {
                                            offset += pan
                                        } else {
                                            offset = androidx.compose.ui.geometry.Offset.Zero
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1f) {
                                                scale = 1f
                                                offset = androidx.compose.ui.geometry.Offset.Zero
                                            } else {
                                                scale = 2.5f
                                            }
                                        },
                                        onTap = {
                                            android.util.Log.d("LumaWebView", "Image tapped. Closing viewer overlay.")
                                            activeImageUrl = null
                                        }
                                    )
                                }
                        )
                    } else {
                        // Safe fallback message
                        Text(
                            text = "Failed to load image",
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Premium Glassmorphic Top Controls Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close button with circular semi-transparent black background
                        IconButton(
                            onClick = { activeImageUrl = null },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Viewer",
                                tint = Color.White
                            )
                        }

                        // Title metadata
                        val displayName = remember(relativePath) {
                            val name = relativePath.substringAfterLast("/").substringBeforeLast(".")
                            if (name.length > 20) name.take(17) + "..." else name
                        }
                        Text(
                            text = displayName.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        // Invert controls
                        IconButton(
                            onClick = { localInvertEnabled = !localInvertEnabled },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contrast,
                                contentDescription = "Toggle Inversion",
                                tint = if (localInvertEnabled) accentColor else Color.White
                            )
                        }
                    }

                    // Pinch-to-zoom interactive hint at the bottom
                    Text(
                        text = "Double tap or pinch to zoom • Drag to pan",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

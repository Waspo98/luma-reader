package com.example.lumareader.ui.reader

import android.content.Context
import android.content.MutableContextWrapper
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

object WebViewPool {
    private val pool: Queue<LumaWebView> = ConcurrentLinkedQueue()

    fun acquire(context: Context): LumaWebView {
        val webView = pool.poll()
        if (webView != null) {
            val wrapper = webView.context as? MutableContextWrapper
            wrapper?.baseContext = context
            webView.clearHistory()
            return webView
        }
        
        val wrapper = MutableContextWrapper(context)
        return LumaWebView(wrapper)
    }

    fun release(webView: LumaWebView) {
        val wrapper = webView.context as? MutableContextWrapper
        wrapper?.baseContext = webView.context.applicationContext

        webView.apply {
            stopLoading()
            webViewClient = android.webkit.WebViewClient()
            webChromeClient = null
            loadUrl("about:blank")
        }
        pool.offer(webView)
    }
}

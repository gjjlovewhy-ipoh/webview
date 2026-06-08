package com.iptv.tvplayer.player

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayerView(
    url: String,
    userAgentType: String = "DEFAULT",
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val container = FrameLayout(context)
            container.setBackgroundColor(android.graphics.Color.BLACK)

            val webView = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.setAllowFileAccess(true)
                
                when (userAgentType) {
                    "PC" -> settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    "MOBILE" -> settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        try {
                            val context = view?.context
                            if (context != null) {
                                val js = context.assets.open("webview_inject.js").bufferedReader().use { it.readText() }
                                view.evaluateJavascript(js, null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    private var customView: android.view.View? = null
                    private var customViewCallback: CustomViewCallback? = null

                    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                        super.onShowCustomView(view, callback)
                        if (customView != null) {
                            callback?.onCustomViewHidden()
                            return
                        }
                        customView = view
                        customViewCallback = callback
                        
                        view?.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        view?.setBackgroundColor(android.graphics.Color.BLACK)
                        
                        // Hide WebView, show custom view
                        this@apply.visibility = android.view.View.GONE
                        container.addView(view)
                    }
                    
                    override fun onHideCustomView() {
                        super.onHideCustomView()
                        if (customView == null) return
                        
                        customView?.visibility = android.view.View.GONE
                        container.removeView(customView)
                        customViewCallback?.onCustomViewHidden()
                        
                        customView = null
                        customViewCallback = null
                        
                        this@apply.visibility = android.view.View.VISIBLE
                    }
                }
            }
            
            container.addView(webView)
            
            // Disable focus on WebView and Container so DPAD events bubble up to Compose
            container.isFocusable = false
            container.isFocusableInTouchMode = false
            webView.isFocusable = false
            webView.isFocusableInTouchMode = false

            webView.loadUrl(url)
            
            // Return container instead of webView so it can host the fullscreen view
            container
        },
        update = { container: FrameLayout ->
            val webView = container.getChildAt(0) as? WebView
            if (webView?.url != url && url.isNotEmpty()) {
                webView?.loadUrl(url)
            }
        }
    )
}

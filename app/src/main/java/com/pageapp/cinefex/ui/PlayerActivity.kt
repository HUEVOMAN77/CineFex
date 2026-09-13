package com.pageapp.cinefex.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pageapp.cinefex.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var isStreamDetected = false
    private var initialHost: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val snifferTimeoutRunnable = Runnable {
        if (!isStreamDetected && !isFinishing) {
            fallbackToWebViewPlayer()
        }
    }

    companion object {
        const val EXTRA_EMBED_URL = "extra_embed_url"
        private const val SNIFFER_TIMEOUT_MS = 15000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableFullscreen()

        val embedUrl = intent.getStringExtra(EXTRA_EMBED_URL)
        if (embedUrl.isNullOrEmpty()) {
            finish()
            return
        }

        initialHost = Uri.parse(embedUrl).host

        setupVideoSniffer(embedUrl)
    }

    private fun enableFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupVideoSniffer(url: String) {
        binding.playerProgressBar.visibility = View.VISIBLE
        binding.tvSniffingStatus.visibility = View.VISIBLE

        mainHandler.postDelayed(snifferTimeoutRunnable, SNIFFER_TIMEOUT_MS)

        val webView = binding.webViewSniffer
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val tempWebView = WebView(this@PlayerActivity)
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                val lowerUrl = reqUrl.lowercase()

                if (!isStreamDetected && (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4"))) {
                    // Exclude minor segments if main m3u8 playlist exists
                    if (!lowerUrl.contains("key") && !lowerUrl.contains("init")) {
                        isStreamDetected = true
                        mainHandler.removeCallbacks(snifferTimeoutRunnable)

                        mainHandler.post {
                            stopSnifferWebView()
                            playNativeStream(reqUrl)
                        }

                        // Return empty response to intercept and cancel webview stream
                        return WebResourceResponse("text/plain", "utf-8", null)
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.loadUrl(url)
    }

    @OptIn(UnstableApi::class)
    private fun playNativeStream(streamUrl: String) {
        binding.playerProgressBar.visibility = View.GONE
        binding.tvSniffingStatus.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Error en ExoPlayer, cambiando a WebView...", Toast.LENGTH_SHORT).show()
                    fallbackToWebViewPlayer()
                }
            })
            prepare()
            playWhenReady = true
        }

        binding.playerView.player = exoPlayer
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fallbackToWebViewPlayer() {
        if (isFinishing) return
        stopSnifferWebView()

        binding.playerProgressBar.visibility = View.GONE
        binding.tvSniffingStatus.visibility = View.GONE
        binding.playerView.visibility = View.GONE
        binding.webViewFallback.visibility = View.VISIBLE

        val webView = binding.webViewFallback
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val tempWebView = WebView(this@PlayerActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        tempWebView.destroy()
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUri = request?.url ?: return false
                val targetHost = targetUri.host
                if (targetHost != null && initialHost != null && targetHost.contains(initialHost!!)) {
                    return false
                }
                return true
            }
        }

        val embedUrl = intent.getStringExtra(EXTRA_EMBED_URL) ?: return
        webView.loadUrl(embedUrl)
    }

    private fun stopSnifferWebView() {
        mainHandler.removeCallbacks(snifferTimeoutRunnable)
        binding.webViewSniffer.run {
            stopLoading()
            loadUrl("about:blank")
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(snifferTimeoutRunnable)
        exoPlayer?.release()
        exoPlayer = null

        binding.webViewSniffer.run {
            stopLoading()
            destroy()
        }
        binding.webViewFallback.run {
            stopLoading()
            destroy()
        }
        super.onDestroy()
    }
}

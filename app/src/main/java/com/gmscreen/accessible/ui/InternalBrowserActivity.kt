package com.gmscreen.accessible.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.gmscreen.accessible.R
import com.gmscreen.accessible.util.AccessibilityHelper

class InternalBrowserActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ASSET_FILE = "extra_asset_file"
        private const val EXTRA_TITLE = "extra_title"

        fun open(context: Context, assetFileName: String, title: String) {
            val intent = Intent(context, InternalBrowserActivity::class.java).apply {
                putExtra(EXTRA_ASSET_FILE, assetFileName)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_internal_browser)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarBrowser)
        val webView = findViewById<WebView>(R.id.webViewContent)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "راهنما"
        val assetFile = intent.getStringExtra(EXTRA_ASSET_FILE) ?: "help.htm"

        toolbar.title = title
        toolbar.setNavigationOnClickListener {
            finish()
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            defaultTextEncodingName = "utf-8"
        }

        val url = "file:///android_asset/help/$assetFile"
        webView.loadUrl(url)

        AccessibilityHelper.announce(toolbar, "صفحه $title باز شد")
    }
}

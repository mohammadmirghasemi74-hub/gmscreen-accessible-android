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
        private const val EXTRA_HTML_CONTENT = "extra_html_content"
        private const val EXTRA_FILE_PATH = "extra_file_path"
        private const val EXTRA_TITLE = "extra_title"

        fun open(context: Context, assetFileName: String, title: String) {
            val intent = Intent(context, InternalBrowserActivity::class.java).apply {
                putExtra(EXTRA_ASSET_FILE, assetFileName)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }

        fun openHtml(context: Context, htmlContent: String, title: String) {
            val intent = Intent(context, InternalBrowserActivity::class.java).apply {
                putExtra(EXTRA_HTML_CONTENT, htmlContent)
                putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }

        fun openFile(context: Context, filePath: String, title: String) {
            val intent = Intent(context, InternalBrowserActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
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
        val htmlContent = intent.getStringExtra(EXTRA_HTML_CONTENT)
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val assetFile = intent.getStringExtra(EXTRA_ASSET_FILE)

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

        if (!htmlContent.isNullOrBlank()) {
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        } else if (!filePath.isNullOrBlank()) {
            webView.loadUrl("file://$filePath")
        } else {
            val file = assetFile ?: "help.htm"
            val url = "file:///android_asset/help/$file"
            webView.loadUrl(url)
        }

        AccessibilityHelper.announce(toolbar, "صفحه $title باز شد")
    }
}

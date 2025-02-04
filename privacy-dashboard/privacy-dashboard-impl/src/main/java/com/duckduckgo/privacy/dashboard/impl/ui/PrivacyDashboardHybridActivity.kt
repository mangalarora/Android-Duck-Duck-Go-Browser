/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.privacy.dashboard.impl.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.webview.enableDarkMode
import com.duckduckgo.app.browser.webview.enableLightMode
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.tabId
import com.duckduckgo.browser.api.brokensite.BrokenSiteNav
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.store.AppTheme
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.privacy.dashboard.impl.databinding.ActivityPrivacyHybridDashboardBinding
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenURL
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class PrivacyDashboardHybridActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var rendererFactory: PrivacyDashboardRendererFactory

    @Inject
    lateinit var brokenSiteNav: BrokenSiteNav

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var appTheme: AppTheme

    private val binding: ActivityPrivacyHybridDashboardBinding by viewBinding()

    private val webView
        get() = binding.privacyDashboardWebview

    private val dashboardRenderer by lazy {
        rendererFactory.createRenderer(
            RendererViewHolder.WebviewRenderer(
                holder = webView,
                onPrivacyProtectionSettingChanged = { userChangedValues -> updateActivityResult(userChangedValues) },
                onPrivacyProtectionsClicked = { newValue ->
                    viewModel.onPrivacyProtectionsClicked(newValue)
                },
                onUrlClicked = { payload ->
                    viewModel.onUrlClicked(payload)
                },
                onBrokenSiteClicked = { viewModel.onReportBrokenSiteSelected() },
                onClose = { this@PrivacyDashboardHybridActivity.finish() },
            ),
        )
    }

    private val viewModel: PrivacyDashboardHybridViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureWebView()
        dashboardRenderer.loadDashboard(webView)
        configureObservers()
    }

    private fun configureObservers() {
        repository.retrieveSiteData(intent.tabId!!).observe(
            this,
        ) {
            viewModel.onSiteChanged(it)
        }

        lifecycleScope.launch {
            viewModel.commands()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest { processCommands(it) }
        }
    }

    private fun processCommands(it: Command) {
        when (it) {
            is LaunchReportBrokenSite -> {
                startActivity(brokenSiteNav.navigate(this, it.data))
            }
            is OpenURL -> openUrl(it.url)
        }
    }

    private fun openUrl(url: String) {
        startActivity(browserNav.openInNewTab(this, url))
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            builtInZoomControls = false
            javaScriptEnabled = true
            configureDarkThemeSupport(this)
        }

        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?,
            ): Boolean {
                return false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                return false
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)
                configViewStateObserver()
            }
        }
    }

    private fun configureDarkThemeSupport(webSettings: WebSettings) {
        when (appTheme.isLightModeEnabled()) {
            true -> webSettings.enableLightMode()
            false -> webSettings.enableDarkMode()
        }
    }

    private fun configViewStateObserver() {
        lifecycleScope.launch {
            viewModel.viewState()
                .flowWithLifecycle(lifecycle, STARTED)
                .collectLatest {
                    if (it == null) return@collectLatest
                    binding.loadingIndicator.hide()
                    dashboardRenderer.render(it)
                }
        }
    }

    private fun updateActivityResult(shouldClose: Boolean) {
        if (shouldClose) {
            setResult(RELOAD_RESULT_CODE)
            finish()
        } else {
            setResult(Activity.RESULT_OK)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val RELOAD_RESULT_CODE = 100

        fun intent(
            context: Context,
            tabId: String,
        ): Intent {
            val intent = Intent(context, PrivacyDashboardHybridActivity::class.java)
            intent.tabId = tabId
            return intent
        }
    }
}

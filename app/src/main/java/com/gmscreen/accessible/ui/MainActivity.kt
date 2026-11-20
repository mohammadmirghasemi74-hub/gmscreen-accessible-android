package com.gmscreen.accessible.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.StbRepository
import com.gmscreen.accessible.ui.fragment.BackupFragment
import com.gmscreen.accessible.ui.fragment.ChannelsFragment
import com.gmscreen.accessible.ui.fragment.ConnectionFragment
import com.gmscreen.accessible.ui.fragment.RecordFragment
import com.gmscreen.accessible.ui.fragment.RemoteFragment
import com.gmscreen.accessible.ui.fragment.SettingsFragment
import com.gmscreen.accessible.ui.fragment.VoiceFragment
import com.gmscreen.accessible.util.AccessibilityHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var btnActiveTab: MaterialButton
    private lateinit var viewPager: ViewPager2

    private val tabTitles = listOf(
        "اتصال به رسیور",
        "کانال‌ها و پخش زنده",
        "ریموت کنترل رسیور",
        "ضبط و PVR",
        "دستورات صوتی هوشمند",
        "تنظیمات و راهنما",
        "بکاپ و پشتیبان‌گیری"
    )

    companion object {
        var activeInstance: MainActivity? = null
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this
        setContentView(R.layout.activity_main)

        com.gmscreen.accessible.util.TtsHelper.init(this)
        com.gmscreen.accessible.voice.VoiceCustomizationManager.init(this)

        btnActiveTab = findViewById(R.id.btnActiveTab)
        viewPager = findViewById(R.id.viewPager)

        setupViewPager()
        setupTabSelectorDialog()
        setupBackPressedHandler()
        observeReconnectEvents()
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("خروج از برنامه یا اجرا در پس‌زمینه")
            .setMessage("آیا می‌خواهید برنامه برای اجرای زمان‌بندی‌های ضبط و اعلام صوتی تغییر کانال‌ها در پس‌زمینه فعال بماند، یا به طور کامل بسته شود؟")
            .setPositiveButton("اجرا در پس‌زمینه (فعال ماندن)") { _, _ ->
                com.gmscreen.accessible.service.GMScreenBackgroundService.startService(this)
                val msg = "برنامه در پس‌زمینه فعال باقی ماند"
                AccessibilityHelper.announce(btnActiveTab, msg)
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            }
            .setNegativeButton("خروج کامل از برنامه") { _, _ ->
                com.gmscreen.accessible.service.GMScreenBackgroundService.stopService(this)
                GMScreenApplication.instance.repository.disconnect()
                finishAffinity()
            }
            .setNeutralButton("انصراف", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    private fun observeReconnectEvents() {
        val repository = GMScreenApplication.instance.repository
        lifecycleScope.launch {
            repository.reconnectEvent.collect { event ->
                when (event) {
                    is StbRepository.ReconnectEvent.Attempting -> {
                        if (GMScreenApplication.instance.appConfig.soundCuesEnabled) {
                            AccessibilityHelper.playDisconnectSoundCue()
                        }
                        val msg = "ارتباط با رسیور قطع شد. در حال تلاش برای اتصال هوشمند مجدد..."
                        AccessibilityHelper.announce(btnActiveTab, msg)
                        AccessibilityHelper.vibrateWarning(this@MainActivity)
                    }
                    is StbRepository.ReconnectEvent.Reconnected -> {
                        val msg = "اتصال به رسیور مجدداً برقرار شد"
                        AccessibilityHelper.announce(btnActiveTab, msg)
                        AccessibilityHelper.vibrateSuccess(this@MainActivity)
                        if (GMScreenApplication.instance.appConfig.soundCuesEnabled) {
                            AccessibilityHelper.playSoundCue()
                        }
                    }
                    is StbRepository.ReconnectEvent.Failed -> {
                        if (GMScreenApplication.instance.appConfig.soundCuesEnabled) {
                            AccessibilityHelper.playDisconnectSoundCue()
                        }
                        val msg = "برنامه نتوانست مجدداً به رسیور متصل شود؛ بازگشت به صفحه اتصال"
                        AccessibilityHelper.announce(btnActiveTab, msg)
                        AccessibilityHelper.vibrateWarning(this@MainActivity)
                        switchToTab(0)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.gmscreen.accessible.util.RecordingsDialogHelper.shouldReopenOnResume) {
            com.gmscreen.accessible.util.RecordingsDialogHelper.shouldReopenOnResume = false
            com.gmscreen.accessible.util.RecordingsDialogHelper.reopenLastPlayedFolder(this)
        }
    }

    private fun setupViewPager() {
        val fragments: List<Fragment> = listOf(
            ConnectionFragment(),
            ChannelsFragment(),
            RemoteFragment(),
            RecordFragment(),
            VoiceFragment(),
            SettingsFragment(),
            BackupFragment()
        )

        val pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 6 // Keep all tabs alive to maintain state and zero lag

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateActiveTabUi(position)
            }
        })

        updateActiveTabUi(0)
    }

    private fun setupTabSelectorDialog() {
        btnActiveTab.setOnClickListener {
            val currentIndex = viewPager.currentItem
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_select_tab_title))
                .setSingleChoiceItems(tabTitles.toTypedArray(), currentIndex) { dialog, which ->
                    switchToTab(which)
                    dialog.dismiss()
                }
                .setNegativeButton("انصراف", null)
                .show()
        }
    }

    fun switchToTab(position: Int) {
        if (position in tabTitles.indices) {
            viewPager.setCurrentItem(position, true)
            updateActiveTabUi(position)
            val title = tabTitles[position]
            AccessibilityHelper.announce(btnActiveTab, "انتقال به سربرگ $title")
        }
    }

    private fun updateActiveTabUi(position: Int) {
        val title = tabTitles.getOrElse(position) { "سربرگ" }
        val buttonText = "سربرگ فعال: $title (برای تغییر کلیک کنید)"
        btnActiveTab.text = buttonText
        btnActiveTab.contentDescription = "سربرگ فعال: $title. برای تغییر سربرگ دو بار ضربه بزنید"
    }
}

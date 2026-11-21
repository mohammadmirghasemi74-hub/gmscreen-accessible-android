package com.gmscreen.accessible.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.gmscreen.accessible.util.AppLogger
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
        setupStatusAnnouncer()
        setupBackPressedHandler()
        observeReconnectEvents()

        checkAndRequestNotificationPermission()
    }

    private val requestNotificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        AppLogger.i("MainActivity", "POST_NOTIFICATIONS granted: $isGranted")
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

    private fun setupStatusAnnouncer() {
        btnActiveTab.setOnLongClickListener {
            com.gmscreen.accessible.util.StatusHelper.announceStatus(
                this,
                GMScreenApplication.instance.repository,
                btnActiveTab
            )
            true
        }

        androidx.core.view.ViewCompat.addAccessibilityAction(
            btnActiveTab,
            "خواندن گزارش وضعیت رسیور و برنامه"
        ) { _, _ ->
            com.gmscreen.accessible.util.StatusHelper.announceStatus(
                this,
                GMScreenApplication.instance.repository,
                btnActiveTab
            )
            true
        }
    }

    private var maxPointerCount = 0
    private var gestureStartTime = 0L
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var lastTapTime = 0L
    private var consecutiveTapCount = 0
    private var lastTapPointerCount = 0

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        val appConfig = GMScreenApplication.instance.appConfig
        if (appConfig.gestureStatusEnabled) {
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    maxPointerCount = 1
                    gestureStartTime = System.currentTimeMillis()
                    gestureStartX = ev.x
                    gestureStartY = ev.y
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    val count = ev.pointerCount
                    if (count > maxPointerCount) {
                        maxPointerCount = count
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - gestureStartTime
                    val dx = Math.abs(ev.x - gestureStartX)
                    val dy = Math.abs(ev.y - gestureStartY)
                    val isTap = duration < 450L && dx < 80f && dy < 80f

                    if (isTap) {
                        val currentPointerCount = maxPointerCount
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 450L && lastTapPointerCount == currentPointerCount) {
                            consecutiveTapCount++
                        } else {
                            consecutiveTapCount = 1
                        }
                        lastTapTime = now
                        lastTapPointerCount = currentPointerCount

                        val gestureType = appConfig.gestureStatusType
                        val triggered = when (gestureType) {
                            "2_fingers_1_tap" -> currentPointerCount == 2
                            "2_fingers_2_taps" -> currentPointerCount == 2 && consecutiveTapCount >= 2
                            "3_fingers_1_tap" -> currentPointerCount >= 3
                            "1_finger_3_taps" -> currentPointerCount == 1 && consecutiveTapCount >= 3
                            else -> currentPointerCount == 2
                        }

                        if (triggered) {
                            consecutiveTapCount = 0
                            com.gmscreen.accessible.util.StatusHelper.announceStatus(
                                this,
                                GMScreenApplication.instance.repository,
                                btnActiveTab
                            )
                        }
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    maxPointerCount = 0
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun updateActiveTabUi(position: Int) {
        val title = tabTitles.getOrElse(position) { "سربرگ" }
        val buttonText = "سربرگ فعال: $title (برای تغییر کلیک کنید)"
        btnActiveTab.text = buttonText
        btnActiveTab.contentDescription = "سربرگ فعال: $title. برای تغییر سربرگ دو بار ضربه بزنید. برای شنیدن گزارش وضعیت رسیور و ضبط، لمس طولانی کنید."
    }
}

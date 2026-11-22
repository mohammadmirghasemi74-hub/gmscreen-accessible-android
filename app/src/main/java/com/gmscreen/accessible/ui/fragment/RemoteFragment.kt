package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import com.gmscreen.accessible.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteFragment : Fragment() {

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_remote, container, false)
        setupButtons(view)
        return view
    }

    private fun setupButtons(rootView: View) {
        // 1. Restart STB Button with Confirmation
        rootView.findViewById<MaterialButton?>(R.id.btnRestart)?.setOnClickListener { btn ->
            if (!repository.isConnected.value) {
                val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(btn, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (appConfig.hapticFeedbackEnabled) {
                AccessibilityHelper.vibrateClick(requireContext())
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("راه‌اندازی مجدد رسیور")
                .setMessage("آیا می‌خواهید رسیور را راه‌اندازی مجدد (ری‌استارت) کنید؟")
                .setPositiveButton("بله، ری‌استارت شود") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        AppLogger.i("Remote", "Sending STB restart command (request 1043)")
                        val ok = repository.restartStb()
                        withContext(Dispatchers.Main) {
                            val msg = if (ok) "دستور ری‌استارت ارسال شد. رسیور در حال راه‌اندازی مجدد است." else "خطا در ارسال دستور ری‌استارت"
                            AccessibilityHelper.announce(btn, msg)
                        }
                    }
                }
                .setNegativeButton("انصراف", null)
                .show()
        }

        // 2. Power Button with Smart State Detection
        rootView.findViewById<MaterialButton?>(R.id.btnPower)?.setOnClickListener { btn ->
            if (!repository.isConnected.value) {
                val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(btn, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                if (appConfig.hapticFeedbackEnabled) {
                    AccessibilityHelper.vibrateClick(requireContext())
                }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val wasOn = repository.isStbPowerOn()
                        repository.sendRcuKey(RcuKey.POWER_TOGGLE)
                        val msg = if (wasOn) "رسیور خاموش شد (حالت استندبای)" else "رسیور روشن شد"
                        AppLogger.i("Remote", "Power toggle executed: wasOn=$wasOn, message=$msg")
                        withContext(Dispatchers.Main) {
                            AccessibilityHelper.announce(btn, msg)
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Remote", "Error toggling power: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            AccessibilityHelper.announce(btn, "پاور رسیور")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Remote", "Error on power click: ${e.message}", e)
            }
        }

        // 3. Mute Button with Smart State Query
        rootView.findViewById<MaterialButton?>(R.id.btnMute)?.setOnClickListener { btn ->
            if (!repository.isConnected.value) {
                val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                AccessibilityHelper.announce(btn, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!repository.isStbPowerOn.value) {
                val msg = "رسیور خاموش (آماده‌به‌کار) است. ابتدا کلید پاور را بزنید."
                AccessibilityHelper.announce(btn, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                if (appConfig.hapticFeedbackEnabled) {
                    AccessibilityHelper.vibrateClick(requireContext())
                }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        repository.sendRcuKey(RcuKey.MUTE)
                        delay(250)
                        val isMuted = repository.getMuteState()
                        val msg = when (isMuted) {
                            true -> "صدا قطع شد"
                            false -> "صدا وصل شد"
                            null -> "بی‌صدا"
                        }
                        AppLogger.i("Remote", "Mute toggle: isMuted=$isMuted, message=$msg")
                        withContext(Dispatchers.Main) {
                            AccessibilityHelper.announce(btn, msg)
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Remote", "Error toggling mute: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            AccessibilityHelper.announce(btn, "بی‌صدا")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Remote", "Error on mute click: ${e.message}", e)
            }
        }

        // 4. Standard Remote Keypad Mapping (Power, Mute, Restart handled separately above)
        val keyMap = mapOf(
            R.id.btnTvRadio to Pair(RcuKey.TV_RADIO, "تلویزیون و رادیو"),
            R.id.btnSat to Pair(RcuKey.SATELLITE, "ماهواره"),
            R.id.btnFav to Pair(RcuKey.FAVORITE, "کانال‌های دلخواه"),
            R.id.btnDigit1 to Pair(RcuKey.KEY_1, "۱"),
            R.id.btnDigit2 to Pair(RcuKey.KEY_2, "۲"),
            R.id.btnDigit3 to Pair(RcuKey.KEY_3, "۳"),
            R.id.btnDigit4 to Pair(RcuKey.KEY_4, "۴"),
            R.id.btnDigit5 to Pair(RcuKey.KEY_5, "۵"),
            R.id.btnDigit6 to Pair(RcuKey.KEY_6, "۶"),
            R.id.btnDigit7 to Pair(RcuKey.KEY_7, "۷"),
            R.id.btnDigit8 to Pair(RcuKey.KEY_8, "۸"),
            R.id.btnDigit9 to Pair(RcuKey.KEY_9, "۹"),
            R.id.btnDigit0 to Pair(RcuKey.KEY_0, "۰"),
            R.id.btnRecall to Pair(RcuKey.RECALL, "بازگشت"),
            R.id.btnSleep to Pair(RcuKey.SLEEP_TIMER, "تایمر خواب"),
            R.id.btnMenu to Pair(RcuKey.MENU, "منو"),
            R.id.btnEpg to Pair(RcuKey.EPG, "جدول برنامه‌ها"),
            R.id.btnInfo to Pair(RcuKey.INFO, "مشخصات کانال"),
            R.id.btnExit to Pair(RcuKey.EXIT, "خروج"),
            R.id.btnUp to Pair(RcuKey.UP, "جهت بالا"),
            R.id.btnDown to Pair(RcuKey.DOWN, "جهت پایین"),
            R.id.btnLeft to Pair(RcuKey.LEFT, "جهت چپ"),
            R.id.btnRight to Pair(RcuKey.RIGHT, "جهت راست"),
            R.id.btnOk to Pair(RcuKey.OK, "تأیید"),
            R.id.btnVolUp to Pair(RcuKey.VOL_UP, "افزایش صدا"),
            R.id.btnVolDown to Pair(RcuKey.VOL_DOWN, "کاهش صدا"),
            R.id.btnChNext to Pair(RcuKey.CH_NEXT, "کانال بعدی"),
            R.id.btnChPrev to Pair(RcuKey.CH_PREV, "کانال قبلی"),
            R.id.btnRed to Pair(RcuKey.RED, "قرمز"),
            R.id.btnGreen to Pair(RcuKey.GREEN, "سبز"),
            R.id.btnYellow to Pair(RcuKey.YELLOW, "زرد"),
            R.id.btnBlue to Pair(RcuKey.BLUE, "آبی"),
            R.id.btnRec to Pair(RcuKey.RECORD, "ضبط رسیور"),
            R.id.btnStop to Pair(RcuKey.STOP, "توقف"),
            R.id.btnPlay to Pair(RcuKey.PLAY, "پخش"),
            R.id.btnPause to Pair(RcuKey.PAUSE, "مکث"),
            R.id.btnRew to Pair(RcuKey.REWIND, "عقب"),
            R.id.btnFf to Pair(RcuKey.FAST_FORWARD, "جلو")
        )

        for ((viewId, info) in keyMap) {
            val (keyCode, desc) = info
            rootView.findViewById<MaterialButton?>(viewId)?.setOnClickListener { btn ->
                if (!repository.isConnected.value) {
                    val msg = "رسیور متصل نیست. لطفاً ابتدا به رسیور متصل شوید."
                    AccessibilityHelper.announce(btn, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!repository.isStbPowerOn.value) {
                    val msg = "رسیور خاموش (آماده‌به‌کار) است. ابتدا کلید پاور را بزنید."
                    AccessibilityHelper.announce(btn, msg)
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    AppLogger.i("Remote", "Key pressed: $desc (code=$keyCode)")
                    if (appConfig.hapticFeedbackEnabled) {
                        AccessibilityHelper.vibrateClick(requireContext())
                    }
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            repository.sendRcuKey(keyCode)
                        } catch (e: Exception) {
                            AppLogger.e("Remote", "Error sending key $keyCode: ${e.message}", e)
                        }
                    }
                    AccessibilityHelper.announce(btn, desc)
                } catch (e: Exception) {
                    AppLogger.e("Remote", "Error on button click $desc: ${e.message}", e)
                }
            }
        }
    }
}

package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.protocol.RcuKey
import com.gmscreen.accessible.util.AccessibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        val keyMap = mapOf(
            R.id.btnPower to Pair(RcuKey.POWER_TOGGLE, "پاور رسیور"),
            R.id.btnMute to Pair(RcuKey.MUTE, "بی‌صدا"),
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
                if (appConfig.hapticFeedbackEnabled) {
                    AccessibilityHelper.vibrateClick(requireContext())
                }
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    repository.sendRcuKey(keyCode)
                }
                AccessibilityHelper.announce(btn, desc)
            }
        }
    }
}

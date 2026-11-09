package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        setupRemoteButtons(view)
        return view
    }

    private fun setupRemoteButtons(rootView: View) {
        // Power & Mute
        bindRemoteKey(rootView, R.id.btnPower, RcuKey.POWER, "پاور رسیور زده شد")
        bindRemoteKey(rootView, R.id.btnMute, RcuKey.MUTE, "حالت بی‌صدا تغییر کرد")

        // Recall & Mode
        bindRemoteKey(rootView, R.id.btnRecall, RcuKey.RECALL, "کانال قبلی (Recall)")
        bindRemoteKey(rootView, R.id.btnTvRadio, RcuKey.TV_RADIO, "تغییر حالت تلویزیون و رادیو")

        // Menus
        bindRemoteKey(rootView, R.id.btnMenu, RcuKey.MENU, "منوی اصلی رسیور")
        bindRemoteKey(rootView, R.id.btnExit, RcuKey.EXIT, "خروج")
        bindRemoteKey(rootView, R.id.btnEpg, RcuKey.EPG, "جدول برنامه‌ها EPG")
        bindRemoteKey(rootView, R.id.btnInfo, RcuKey.INFO, "اطلاعات شبکه Info")

        // D-Pad Navigation (Strict ALi hardware mapping)
        // Up = Prev Channel (2), Down = Next Channel (1), Left = Vol- (3), Right = Vol+ (4), OK = (5)
        bindRemoteKey(rootView, R.id.btnNavUp, RcuKey.UP, "کانال قبلی")
        bindRemoteKey(rootView, R.id.btnNavDown, RcuKey.DOWN, "کانال بعدی")
        bindRemoteKey(rootView, R.id.btnNavLeft, RcuKey.LEFT, "کاهش صدا")
        bindRemoteKey(rootView, R.id.btnNavRight, RcuKey.RIGHT, "افزایش صدا")
        bindRemoteKey(rootView, R.id.btnNavOk, RcuKey.OK, "تأیید")

        // Volume & Channel Rockers
        bindRemoteKey(rootView, R.id.btnVolUp, RcuKey.VOL_UP, "افزایش صدا")
        bindRemoteKey(rootView, R.id.btnVolDown, RcuKey.VOL_DOWN, "کاهش صدا")
        bindRemoteKey(rootView, R.id.btnChNext, RcuKey.CH_NEXT, "کانال بعدی")
        bindRemoteKey(rootView, R.id.btnChPrev, RcuKey.CH_PREV, "کانال قبلی")

        // Color buttons
        bindRemoteKey(rootView, R.id.btnRed, RcuKey.RED, "دکمه قرمز: زبان و ترک صدا")
        bindRemoteKey(rootView, R.id.btnGreen, RcuKey.GREEN, "دکمه سبز: مدیا پلیر")
        bindRemoteKey(rootView, R.id.btnYellow, RcuKey.YELLOW, "دکمه زرد: نسبت تصویر")
        bindRemoteKey(rootView, R.id.btnBlue, RcuKey.BLUE, "دکمه آبی: لیست ماهواره‌ها")

        // Digits (0 to 9)
        bindRemoteKey(rootView, R.id.btnDigit0, RcuKey.KEY_0, "صفر")
        bindRemoteKey(rootView, R.id.btnDigit1, RcuKey.KEY_1, "یک")
        bindRemoteKey(rootView, R.id.btnDigit2, RcuKey.KEY_2, "دو")
        bindRemoteKey(rootView, R.id.btnDigit3, RcuKey.KEY_3, "سه")
        bindRemoteKey(rootView, R.id.btnDigit4, RcuKey.KEY_4, "چهار")
        bindRemoteKey(rootView, R.id.btnDigit5, RcuKey.KEY_5, "پنج")
        bindRemoteKey(rootView, R.id.btnDigit6, RcuKey.KEY_6, "شش")
        bindRemoteKey(rootView, R.id.btnDigit7, RcuKey.KEY_7, "هفت")
        bindRemoteKey(rootView, R.id.btnDigit8, RcuKey.KEY_8, "هشت")
        bindRemoteKey(rootView, R.id.btnDigit9, RcuKey.KEY_9, "نه")
    }

    private fun bindRemoteKey(rootView: View, viewId: Int, rcuKeyCode: Int, feedbackMessage: String) {
        val button = rootView.findViewById<MaterialButton?>(viewId) ?: return

        button.setOnClickListener {
            // 1. Trigger haptic feedback
            if (appConfig.hapticFeedbackEnabled) {
                AccessibilityHelper.vibrateClick(requireContext())
            }

            // 2. Announce to TalkBack
            AccessibilityHelper.announce(button, feedbackMessage)

            // 3. Send key to STB in background
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                repository.sendRcuKey(rcuKeyCode)
            }

            // 4. CRITICAL: Prevent focus jumping - keep focus firmly on this button!
            button.post {
                button.requestFocus()
            }
        }
    }
}

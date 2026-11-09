package com.gmscreen.accessible.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.StbDevice
import com.gmscreen.accessible.ui.MainActivity
import com.gmscreen.accessible.util.AccessibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionFragment : Fragment() {

    private lateinit var txtStatus: TextView
    private lateinit var txtStbModel: TextView
    private lateinit var btnAutoDiscover: MaterialButton
    private lateinit var btnToggleManualIp: MaterialButton
    private lateinit var cardManualIp: MaterialCardView
    private lateinit var edtIpAddress: TextInputEditText
    private lateinit var btnConnectManual: MaterialButton
    private lateinit var btnDisconnect: MaterialButton
    private lateinit var containerDiscovered: LinearLayout
    private lateinit var txtNoDevices: TextView

    private val repository get() = GMScreenApplication.instance.repository
    private val appConfig get() = GMScreenApplication.instance.appConfig

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_connection, container, false)

        txtStatus = view.findViewById(R.id.txtStatus)
        txtStbModel = view.findViewById(R.id.txtStbModel)
        btnAutoDiscover = view.findViewById(R.id.btnAutoDiscover)
        btnToggleManualIp = view.findViewById(R.id.btnToggleManualIp)
        cardManualIp = view.findViewById(R.id.cardManualIp)
        edtIpAddress = view.findViewById(R.id.edtIpAddress)
        btnConnectManual = view.findViewById(R.id.btnConnectManual)
        btnDisconnect = view.findViewById(R.id.btnDisconnect)
        containerDiscovered = view.findViewById(R.id.containerDiscovered)
        txtNoDevices = view.findViewById(R.id.txtNoDevices)

        setupListeners()
        observeConnectionState()

        val lastIp = appConfig.lastConnectedIp
        if (lastIp.isNotBlank()) {
            edtIpAddress.setText(lastIp)
        }

        return view
    }

    private fun setupListeners() {
        btnAutoDiscover.setOnClickListener {
            startDiscovery()
        }

        btnToggleManualIp.setOnClickListener {
            if (cardManualIp.visibility == View.VISIBLE) {
                cardManualIp.visibility = View.GONE
                AccessibilityHelper.announce(btnToggleManualIp, "بخش ورود دستی IP بسته شد")
            } else {
                cardManualIp.visibility = View.VISIBLE
                edtIpAddress.requestFocus()
                AccessibilityHelper.announce(cardManualIp, "بخش ورود دستی IP باز شد. لطفاً آدرس IP را وارد کنید")
            }
        }

        btnConnectManual.setOnClickListener {
            val ip = edtIpAddress.text?.toString()?.trim() ?: ""
            if (ip.isBlank()) {
                val msg = "لطفاً آدرس IP رسیور را وارد کنید"
                AccessibilityHelper.announce(txtStatus, msg)
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connectToStb(ip)
        }

        btnDisconnect.setOnClickListener {
            repository.disconnect()
            val msg = "اتصال به رسیور قطع شد"
            AccessibilityHelper.announce(txtStatus, msg)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeConnectionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.isConnected.collect { connected ->
                if (connected) {
                    val model = repository.deviceModel.value
                    val ip = repository.connectedIp.value
                    txtStatus.text = getString(R.string.stb_status_connected, ip)
                    txtStatus.setTextColor(resources.getColor(R.color.accent_green, null))
                    txtStbModel.text = "مدل رسیور: $model"
                    btnDisconnect.visibility = View.VISIBLE

                    val successMsg = "اتصال موفقیت‌آمیز به رسیور $model با آیپی $ip. انتقال به سربرگ کانال‌ها..."
                    AccessibilityHelper.announce(txtStatus, successMsg)

                    if (appConfig.soundCuesEnabled) {
                        AccessibilityHelper.playSoundCue()
                    }
                    if (appConfig.hapticFeedbackEnabled) {
                        AccessibilityHelper.vibrateSuccess(requireContext())
                    }

                    // Auto switch to Tab 2 (Channels)
                    (activity as? MainActivity)?.switchToTab(1)
                } else {
                    txtStatus.text = getString(R.string.stb_status_disconnected)
                    txtStatus.setTextColor(resources.getColor(R.color.accent_yellow, null))
                    txtStbModel.text = "مدل رسیور: متصل نیست"
                    btnDisconnect.visibility = View.GONE
                }
            }
        }
    }

    private fun startDiscovery() {
        val startMsg = "در حال جست‌وجوی خودکار رسیور در شبکه وای‌فای..."
        txtStatus.text = startMsg
        AccessibilityHelper.announce(txtStatus, startMsg)
        containerDiscovered.removeAllViews()
        txtNoDevices.visibility = View.VISIBLE
        txtNoDevices.text = "در حال پویش شبکه..."

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val devices = repository.discoverDevices()
            withContext(Dispatchers.Main) {
                if (devices.isEmpty()) {
                    val msg = "هیچ رسیوری در شبکه وای‌فای یافت نشد. می‌توانید با گزینه ورود دستی، IP را مستقیماً وارد کنید."
                    txtStatus.text = msg
                    txtNoDevices.text = msg
                    txtNoDevices.visibility = View.VISIBLE
                    AccessibilityHelper.announce(txtStatus, msg)
                } else {
                    val msg = "${devices.size} رسیور در شبکه پیدا شد"
                    txtStatus.text = msg
                    AccessibilityHelper.announce(txtStatus, msg)
                    txtNoDevices.visibility = View.GONE
                    showDiscoveredDevices(devices)
                }
            }
        }
    }

    private fun showDiscoveredDevices(devices: List<StbDevice>) {
        containerDiscovered.removeAllViews()
        for (device in devices) {
            val btn = MaterialButton(requireContext()).apply {
                text = "رسیور: ${device.productName} (${device.ip})"
                contentDescription = "اتصال به رسیور ${device.productName} با آیپی ${device.ip}"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 24
                ).apply {
                    setMargins(0, 6, 0, 8)
                }
                setBackgroundColor(resources.getColor(R.color.card_bg, null))
                setTextColor(resources.getColor(R.color.text_primary, null))
                textSize = 16f
                setOnClickListener {
                    edtIpAddress.setText(device.ip)
                    connectToStb(device.ip)
                }
            }
            containerDiscovered.addView(btn)
        }
    }

    private fun connectToStb(ip: String) {
        val connectingMsg = "در حال برقراری اتصال به رسیور با آدرس $ip..."
        txtStatus.text = connectingMsg
        AccessibilityHelper.announce(txtStatus, connectingMsg)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = repository.connect(ip)
            withContext(Dispatchers.Main) {
                if (result.isFailure) {
                    val err = "خطا در برقراری ارتباط با رسیور ($ip). لطفاً روشن بودن رسیور و اتصال به وای‌فای یکسان را بررسی کنید."
                    txtStatus.text = err
                    txtStatus.setTextColor(resources.getColor(R.color.accent_red, null))
                    AccessibilityHelper.announce(txtStatus, err)
                    Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

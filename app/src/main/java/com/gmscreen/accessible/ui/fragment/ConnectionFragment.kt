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
import com.google.android.material.textfield.TextInputEditText
import com.gmscreen.accessible.GMScreenApplication
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.model.StbDevice
import com.gmscreen.accessible.util.AccessibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionFragment : Fragment() {

    private lateinit var txtStatus: TextView
    private lateinit var txtStbModel: TextView
    private lateinit var btnAutoDiscover: MaterialButton
    private lateinit var edtIpAddress: TextInputEditText
    private lateinit var btnConnectManual: MaterialButton
    private lateinit var btnDisconnect: MaterialButton
    private lateinit var lblDiscovered: TextView
    private lateinit var containerDiscovered: LinearLayout

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
        edtIpAddress = view.findViewById(R.id.edtIpAddress)
        btnConnectManual = view.findViewById(R.id.btnConnectManual)
        btnDisconnect = view.findViewById(R.id.btnDisconnect)
        lblDiscovered = view.findViewById(R.id.lblDiscovered)
        containerDiscovered = view.findViewById(R.id.containerDiscovered)

        setupListeners()
        observeConnectionState()

        // Populate last connected IP
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

        btnConnectManual.setOnClickListener {
            val ip = edtIpAddress.text?.toString()?.trim() ?: ""
            if (ip.isBlank()) {
                AccessibilityHelper.announce(txtStatus, "لطفاً آدرس آی‌پی رسیور را وارد کنید")
                Toast.makeText(requireContext(), "لطفاً آدرس آی‌پی رسیور را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            connectToStb(ip)
        }

        btnDisconnect.setOnClickListener {
            repository.disconnect()
            AccessibilityHelper.announce(txtStatus, "اتصال با رسیور قطع شد")
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
                    btnConnectManual.text = "اتصال مجدد"
                    AccessibilityHelper.announce(txtStatus, "با موفقیت به رسیور $model با آی‌پی $ip متصل شدید")
                    if (appConfig.soundCuesEnabled) {
                        AccessibilityHelper.playSoundCue()
                    }
                    if (appConfig.hapticFeedbackEnabled) {
                        AccessibilityHelper.vibrateSuccess(requireContext())
                    }
                } else {
                    txtStatus.text = getString(R.string.stb_status_disconnected)
                    txtStatus.setTextColor(resources.getColor(R.color.accent_yellow, null))
                    txtStbModel.text = "مدل رسیور: متصل نیست"
                    btnDisconnect.visibility = View.GONE
                    btnConnectManual.text = getString(R.string.btn_connect_manual)
                }
            }
        }
    }

    private fun startDiscovery() {
        txtStatus.text = "در حال جستجوی خودکار رسیور در شبکه وای‌فای..."
        AccessibilityHelper.announce(txtStatus, "در حال جستجوی خودکار رسیور در شبکه وای‌فای...")
        containerDiscovered.removeAllViews()
        lblDiscovered.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val devices = repository.discoverDevices()
            withContext(Dispatchers.Main) {
                if (devices.isEmpty()) {
                    val msg = "هیچ رسیوری در شبکه وای‌فای پیدا نشد. لطفاً آی‌پی را دستی وارد کنید."
                    txtStatus.text = msg
                    AccessibilityHelper.announce(txtStatus, msg)
                } else {
                    val msg = "${devices.size} رسیور در شبکه پیدا شد"
                    txtStatus.text = msg
                    AccessibilityHelper.announce(txtStatus, msg)
                    lblDiscovered.visibility = View.VISIBLE
                    showDiscoveredDevices(devices)
                }
            }
        }
    }

    private fun showDiscoveredDevices(devices: List<StbDevice>) {
        containerDiscovered.removeAllViews()
        for (device in devices) {
            val btn = MaterialButton(requireContext()).apply {
                text = "${device.productName} (${device.ip})"
                contentDescription = "اتصال به رسیور ${device.productName} با آی‌پی ${device.ip}"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(android.R.dimen.app_icon_size) + 16
                ).apply {
                    setMargins(0, 4, 0, 8)
                }
                setBackgroundColor(resources.getColor(R.color.card_bg, null))
                setTextColor(resources.getColor(R.color.text_primary, null))
                setOnClickListener {
                    edtIpAddress.setText(device.ip)
                    connectToStb(device.ip)
                }
            }
            containerDiscovered.addView(btn)
        }
    }

    private fun connectToStb(ip: String) {
        txtStatus.text = getString(R.string.stb_status_connecting)
        AccessibilityHelper.announce(txtStatus, "در حال برقراری اتصال به رسیور با آی‌پی $ip")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = repository.connect(ip)
            withContext(Dispatchers.Main) {
                if (result.isFailure) {
                    val err = "خطا در برقراری ارتباط با رسیور ($ip)"
                    txtStatus.text = err
                    txtStatus.setTextColor(resources.getColor(R.color.accent_red, null))
                    AccessibilityHelper.announce(txtStatus, err)
                    Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

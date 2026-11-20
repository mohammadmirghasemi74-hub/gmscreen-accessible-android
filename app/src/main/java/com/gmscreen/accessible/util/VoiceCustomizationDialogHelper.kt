package com.gmscreen.accessible.util

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gmscreen.accessible.R
import com.gmscreen.accessible.data.StbRepository
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.voice.VoiceAction
import com.gmscreen.accessible.voice.VoiceCustomizationManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object VoiceCustomizationDialogHelper {

    fun syncChannels(context: Context, repository: StbRepository, anchorView: View? = null, onDismiss: (() -> Unit)? = null) {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            val msg = "هنوز کانالی از رسیور دریافت نشده است. لطفاً ابتدا به رسیور متصل شده و لیست شبکه‌ها را دریافت کنید."
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            anchorView?.let { AccessibilityHelper.announce(it, msg) }
            onDismiss?.invoke()
            return
        }

        val (addedCount, totalCount) = VoiceCustomizationManager.syncChannelsWithVoice(context, channels)
        val msg = if (addedCount > 0) {
            "همگام‌سازی با موفقیت انجام شد: تعداد $addedCount کانال جدید در رسیور شناسایی و به عنوان فرامین صوتی ثبت شد (مجموعاً $totalCount شبکه رسیور)."
        } else {
            "تعداد کل $totalCount شبکه رسیور بررسی شد. ۰ کانال جدید یافت شد (هیچ شبکه جدیدی به رسیور اضافه نشده است). تمامی شبکه‌ها قبلاً همگام شده‌اند."
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("همگام‌سازی کانال‌ها با فرامین صوتی")
            .setMessage(msg)
            .setPositiveButton("متوجه شدم") { _, _ ->
                onDismiss?.invoke()
            }
            .setOnDismissListener {
                onDismiss?.invoke()
            }
            .show()

        anchorView?.let { AccessibilityHelper.announce(it, msg) }
    }

    fun showVoiceCustomizationMenu(context: Context, repository: StbRepository) {
        val items = arrayOf(
            "۱. همگام‌سازی و افزودن خودکار کانال‌های رسیور به فرامین صوتی",
            "۲. مشاهده و شخصی‌سازی فرامین صوتی بر اساس دسته‌بندی",
            "۳. جستجو و ویرایش مستقیم میان کلیه فرامین صوتی",
            "۴. مدیریت نام‌های مستعار صوتی برای هر شبکه به صورت اختصاصی",
            "۵. بازنشانی کلیه فرامین صوتی به حالت پیش‌فرض اولیه"
        )

        MaterialAlertDialogBuilder(context)
            .setTitle("مدیریت و شخصی‌سازی فرامین صوتی")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> syncChannels(context, repository) {
                        showVoiceCustomizationMenu(context, repository)
                    }
                    1 -> showCategorySelectorDialog(context)
                    2 -> showAllActionsSearchDialog(context)
                    3 -> showChannelPickerForAliasesDialog(context, repository)
                    4 -> confirmResetAllVoiceCommands(context)
                }
            }
            .setNegativeButton("بازگشت", null)
            .show()
    }

    private fun showCategorySelectorDialog(context: Context) {
        val categories = VoiceCustomizationManager.DEFAULT_ACTIONS
            .map { it.category }
            .distinct()

        val items = categories.map { cat ->
            val count = VoiceCustomizationManager.DEFAULT_ACTIONS.count { it.category == cat }
            "$cat ($count قابلیت صوتی)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("انتخاب گروه فرامین صوتی")
            .setItems(items) { _, which ->
                val selectedCat = categories.getOrNull(which)
                if (selectedCat != null) {
                    showActionsInCategoryDialog(context, selectedCat)
                }
            }
            .setNegativeButton("بازگشت", null)
            .show()
    }

    private fun showActionsInCategoryDialog(context: Context, category: String) {
        val actions = VoiceCustomizationManager.DEFAULT_ACTIONS.filter { it.category == category }
        val items = actions.map { action ->
            val isEnabled = VoiceCustomizationManager.isActionEnabled(action.id)
            val statusTag = if (isEnabled) "[فعال]" else "[خاموش]"
            val phrases = VoiceCustomizationManager.getEffectivePhrases(action.id)
            val phraseText = if (phrases.isNotEmpty()) phrases.joinToString("، ") else "بدون عبارت"
            "$statusTag ${action.title}\nفرامین: $phraseText"
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("قابلیت‌های گروه: $category")
            .setItems(items) { _, which ->
                val selectedAction = actions.getOrNull(which)
                if (selectedAction != null) {
                    showActionDetailDialog(context, selectedAction) {
                        showActionsInCategoryDialog(context, category)
                    }
                }
            }
            .setNegativeButton("بازگشت") { _, _ ->
                showCategorySelectorDialog(context)
            }
            .show()
    }

    private fun showAllActionsSearchDialog(context: Context) {
        val actions = VoiceCustomizationManager.DEFAULT_ACTIONS
        val items = actions.map { action ->
            val isEnabled = VoiceCustomizationManager.isActionEnabled(action.id)
            val statusTag = if (isEnabled) "[فعال]" else "[خاموش]"
            val phrases = VoiceCustomizationManager.getEffectivePhrases(action.id)
            val phraseText = if (phrases.isNotEmpty()) phrases.joinToString("، ") else "بدون عبارت"
            "$statusTag [${action.category}] ${action.title}\nفرامین: $phraseText"
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("کلیه قابلیت‌های صوتی (${actions.size} قابلیت)")
            .setItems(items) { _, which ->
                val selected = actions.getOrNull(which)
                if (selected != null) {
                    showActionDetailDialog(context, selected) {
                        showAllActionsSearchDialog(context)
                    }
                }
            }
            .setNegativeButton("بازگشت", null)
            .show()
    }

    private fun showActionDetailDialog(
        context: Context,
        action: VoiceAction,
        onBack: () -> Unit
    ) {
        val currentPhrases = VoiceCustomizationManager.getEffectivePhrases(action.id)
        val isEnabled = VoiceCustomizationManager.isActionEnabled(action.id)

        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        scrollView.addView(container)

        // Description header
        val txtHeader = TextView(context).apply {
            text = "گروه: ${action.category}\nعنوان: ${action.title}\n\nبرای خاموش/روشن کردن، حذف یا ویرایش از گزینه‌های زیر استفاده کنید:"
            setTextColor(context.resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }
        container.addView(txtHeader)

        // 1. Toggle Enable / Disable Button
        val btnToggle = MaterialButton(context).apply {
            val statusLabel = if (isEnabled) "روشن (فعال)" else "خاموش (غیرفعال)"
            text = "وضعیت قابلیت: $statusLabel (کلیک برای تغییر)"
            contentDescription = "وضعیت قابلیت صوتی ${action.title}: $statusLabel. برای تغییر وضعیت دو بار ضربه بزنید"
            setBackgroundColor(if (isEnabled) context.resources.getColor(R.color.primary, null) else context.resources.getColor(R.color.card_bg, null))
            setTextColor(context.resources.getColor(android.R.color.white, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 16) }

            setOnClickListener {
                val newState = VoiceCustomizationManager.toggleAction(context, action.id)
                val announceMsg = if (newState) "فعال شد" else "غیرفعال شد"
                Toast.makeText(context, announceMsg, Toast.LENGTH_SHORT).show()
                AccessibilityHelper.announce(this, announceMsg)
                showActionDetailDialog(context, action, onBack)
            }
        }
        container.addView(btnToggle)

        if (currentPhrases.isEmpty()) {
            val txtEmpty = TextView(context).apply {
                text = "در حال حاضر هیچ عبارت صوتی برای این قابلیت ثبت نشده است."
                setTextColor(context.resources.getColor(R.color.text_secondary, null))
                textSize = 13f
                setPadding(0, 8, 0, 16)
            }
            container.addView(txtEmpty)
        } else {
            for (phrase in currentPhrases) {
                val btnPhrase = MaterialButton(context).apply {
                    text = "«$phrase»"
                    contentDescription = if (isEnabled) {
                        "عبارت صوتی: $phrase. برای ویرایش یا حذف ضربه بزنید"
                    } else {
                        "عبارت صوتی: $phrase (غیرفعال، زیرا این قابلیت خاموش است)"
                    }
                    textSize = 14f
                    isEnabled = isEnabled
                    alpha = if (isEnabled) 1.0f else 0.45f
                    setBackgroundColor(context.resources.getColor(R.color.card_bg, null))
                    setTextColor(context.resources.getColor(R.color.text_primary, null))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 6) }

                    setOnClickListener {
                        if (!isEnabled) return@setOnClickListener
                        showPhraseOptionsDialog(context, action, phrase) {
                            showActionDetailDialog(context, action, onBack)
                        }
                    }
                }
                container.addView(btnPhrase)
            }
        }

        // Add New Phrase Button
        val btnAdd = MaterialButton(context).apply {
            text = "+ افزودن عبارت صوتی جدید"
            contentDescription = if (isEnabled) {
                "افزودن عبارت صوتی جدید برای ${action.title}"
            } else {
                "افزودن عبارت صوتی جدید (غیرفعال، زیرا این قابلیت خاموش است)"
            }
            isEnabled = isEnabled
            alpha = if (isEnabled) 1.0f else 0.45f
            setBackgroundColor(context.resources.getColor(R.color.primary, null))
            setTextColor(context.resources.getColor(android.R.color.white, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 6) }

            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                showAddPhraseDialog(context, action) {
                    showActionDetailDialog(context, action, onBack)
                }
            }
        }
        container.addView(btnAdd)

        // Clear All Phrases Button
        if (currentPhrases.isNotEmpty()) {
            val btnClear = MaterialButton(context).apply {
                text = "خالی کردن کلیه عبارات این قابلیت"
                contentDescription = if (isEnabled) {
                    "پاک کردن تمام فرامین صوتی ثبت شده برای ${action.title}"
                } else {
                    "خالی کردن کلیه عبارات (غیرفعال، زیرا این قابلیت خاموش است)"
                }
                isEnabled = isEnabled
                alpha = if (isEnabled) 1.0f else 0.45f
                setBackgroundColor(context.resources.getColor(R.color.card_bg, null))
                setTextColor(context.resources.getColor(R.color.text_primary, null))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 6, 0, 6) }

                setOnClickListener {
                    if (!isEnabled) return@setOnClickListener
                    MaterialAlertDialogBuilder(context)
                        .setTitle("خالی کردن فرامین")
                        .setMessage("آیا می‌خواهید تمام عبارات صوتی «${action.title}» پاک شوند؟")
                        .setPositiveButton("بله، پاک شود") { _, _ ->
                            VoiceCustomizationManager.setPhrases(context, action.id, emptyList())
                            Toast.makeText(context, "عبارات صوتی این قابلیت خالی شد", Toast.LENGTH_SHORT).show()
                            showActionDetailDialog(context, action, onBack)
                        }
                        .setNegativeButton("انصراف", null)
                        .show()
                }
            }
            container.addView(btnClear)
        }

        // Reset to Default Button
        val btnReset = MaterialButton(context).apply {
            text = "بازنشانی این قابلیت به پیش‌فرض"
            contentDescription = "بازنشانی فرامین ${action.title} به حالت پیش‌فرض"
            setBackgroundColor(context.resources.getColor(R.color.card_bg, null))
            setTextColor(context.resources.getColor(R.color.secondary, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }

            setOnClickListener {
                VoiceCustomizationManager.resetAction(context, action.id)
                Toast.makeText(context, "فرامین این قابلیت به حالت پیش‌فرض بازگشت", Toast.LENGTH_SHORT).show()
                showActionDetailDialog(context, action, onBack)
            }
        }
        container.addView(btnReset)

        MaterialAlertDialogBuilder(context)
            .setTitle(action.title)
            .setView(scrollView)
            .setPositiveButton("بستن") { _, _ -> onBack() }
            .show()
    }

    private fun showPhraseOptionsDialog(
        context: Context,
        action: VoiceAction,
        phrase: String,
        onUpdated: () -> Unit
    ) {
        val items = arrayOf("ویرایش این عبارت", "حذف این عبارت")
        MaterialAlertDialogBuilder(context)
            .setTitle("عبارت: «$phrase»")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val input = EditText(context).apply {
                            setText(phrase)
                            hint = "عبارت جدید"
                        }
                        MaterialAlertDialogBuilder(context)
                            .setTitle("ویرایش عبارت صوتی")
                            .setView(input)
                            .setPositiveButton("ذخیره") { _, _ ->
                                val newText = input.text.toString().trim()
                                if (newText.isNotBlank()) {
                                    VoiceCustomizationManager.removePhrase(context, action.id, phrase)
                                    VoiceCustomizationManager.addPhrase(context, action.id, newText)
                                    Toast.makeText(context, "عبارت با موفقیت ویرایش شد", Toast.LENGTH_SHORT).show()
                                    onUpdated()
                                }
                            }
                            .setNegativeButton("انصراف", null)
                            .show()
                    }
                    1 -> {
                        VoiceCustomizationManager.removePhrase(context, action.id, phrase)
                        Toast.makeText(context, "عبارت صوتی حذف شد", Toast.LENGTH_SHORT).show()
                        onUpdated()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAddPhraseDialog(
        context: Context,
        action: VoiceAction,
        onUpdated: () -> Unit
    ) {
        val input = EditText(context).apply {
            hint = "عبارت صوتی را بنویسید (مثلاً: صدا رو ببر بالا)"
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("افزودن عبارت صوتی به ${action.title}")
            .setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    VoiceCustomizationManager.addPhrase(context, action.id, text)
                    Toast.makeText(context, "عبارت صوتی اضافه شد", Toast.LENGTH_SHORT).show()
                    onUpdated()
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showChannelPickerForAliasesDialog(context: Context, repository: StbRepository) {
        val channels = repository.channels.value
        if (channels.isEmpty()) {
            Toast.makeText(context, "هنوز کانالی از رسیور دریافت نشده است", Toast.LENGTH_SHORT).show()
            return
        }

        val aliasesMap = VoiceCustomizationManager.getChannelAliasesMap()
        val items = channels.map { ch ->
            val aliases = aliasesMap[ch.serviceId] ?: emptyList()
            val aliasSummary = if (aliases.isNotEmpty()) "${aliases.size} عبارت صوتی" else "بدون نام مستعار"
            "${ch.serviceName} ($aliasSummary)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle("انتخاب شبکه جهت مشاهده یا ثبت عبارت صوتی")
            .setItems(items) { _, which ->
                val ch = channels.getOrNull(which)
                if (ch != null) {
                    showChannelAliasDetailDialog(context, ch) {
                        showChannelPickerForAliasesDialog(context, repository)
                    }
                }
            }
            .setNegativeButton("بازگشت", null)
            .show()
    }

    private fun showChannelAliasDetailDialog(
        context: Context,
        channel: Channel,
        onBack: () -> Unit
    ) {
        val currentAliases = VoiceCustomizationManager.getChannelAliasesMap()[channel.serviceId] ?: emptyList()

        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        scrollView.addView(container)

        val txtHeader = TextView(context).apply {
            text = "شبکه: ${channel.serviceName}\nفرامین صوتی متصل به این شبکه:"
            setTextColor(context.resources.getColor(R.color.text_primary, null))
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        container.addView(txtHeader)

        if (currentAliases.isEmpty()) {
            val txtEmpty = TextView(context).apply {
                text = "هنوز نام مستعار صوتی اختصاصی برای این شبکه ثبت نشده است."
                setTextColor(context.resources.getColor(R.color.text_secondary, null))
                textSize = 13f
                setPadding(0, 8, 0, 16)
            }
            container.addView(txtEmpty)
        } else {
            for (alias in currentAliases) {
                val btnAlias = MaterialButton(context).apply {
                    text = "«$alias»"
                    contentDescription = "فرمان صوتی: $alias. برای حذف ضربه بزنید"
                    setBackgroundColor(context.resources.getColor(R.color.card_bg, null))
                    setTextColor(context.resources.getColor(R.color.text_primary, null))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 6) }

                    setOnClickListener {
                        VoiceCustomizationManager.removeChannelAlias(context, channel.serviceId, alias)
                        Toast.makeText(context, "عبارت صوتی حذف شد", Toast.LENGTH_SHORT).show()
                        showChannelAliasDetailDialog(context, channel, onBack)
                    }
                }
                container.addView(btnAlias)
            }
        }

        val btnAdd = MaterialButton(context).apply {
            text = "+ افزودن فرمان صوتی جدید برای این شبکه"
            setBackgroundColor(context.resources.getColor(R.color.primary, null))
            setTextColor(context.resources.getColor(android.R.color.white, null))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 16, 0, 6) }

            setOnClickListener {
                val input = EditText(context).apply {
                    hint = "مثلاً: ورزش اچ دی، یا کانال سه"
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle("ثبت فرمان صوتی برای ${channel.serviceName}")
                    .setView(input)
                    .setPositiveButton("افزودن") { _, _ ->
                        val text = input.text.toString().trim()
                        if (text.isNotBlank()) {
                            VoiceCustomizationManager.addChannelAlias(context, channel.serviceId, text)
                            Toast.makeText(context, "فرمان صوتی با موفقیت اضافه شد", Toast.LENGTH_SHORT).show()
                            showChannelAliasDetailDialog(context, channel, onBack)
                        }
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            }
        }
        container.addView(btnAdd)

        MaterialAlertDialogBuilder(context)
            .setTitle(channel.serviceName)
            .setView(scrollView)
            .setPositiveButton("بازگشت") { _, _ -> onBack() }
            .show()
    }

    private fun confirmResetAllVoiceCommands(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle("بازنشانی کلیه فرامین صوتی")
            .setMessage("آیا مطمئن هستید که می‌خواهید تمام فرامین صوتی سفارشی را حذف کرده و همه قابلیت‌ها را به حالت پیش‌فرض بازگردانید؟")
            .setPositiveButton("بله، بازنشانی همه") { _, _ ->
                VoiceCustomizationManager.resetAllActions(context)
                VoiceCustomizationManager.resetAllChannelAliases(context)
                val msg = "کلیه فرامین صوتی به حالت پیش‌فرض اولیه بازگردانده شدند"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}

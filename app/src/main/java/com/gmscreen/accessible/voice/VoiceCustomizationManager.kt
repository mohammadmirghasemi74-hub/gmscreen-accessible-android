package com.gmscreen.accessible.voice

import android.content.Context
import com.gmscreen.accessible.data.model.Channel
import com.gmscreen.accessible.util.AppLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class VoiceAction(
    val id: String,
    val category: String,
    val title: String,
    val defaultPhrases: List<String>
)

object VoiceCustomizationManager {

    private const val TAG = "VoiceCustomization"
    private const val FILE_ACTIONS = "custom_voice_commands.json"
    private const val FILE_CHANNEL_ALIASES = "custom_channel_aliases.json"
    private const val FILE_DISABLED_ACTIONS = "disabled_voice_actions.json"
    private const val FILE_SYNCED_CHANNEL_IDS = "synced_channel_ids.json"

    val DEFAULT_ACTIONS = listOf(
        // ۱. پیمایش سربرگ‌های برنامه
        VoiceAction(
            "tab_connection", "پیمایش سربرگ‌های برنامه", "رفتن به سربرگ اتصال به رسیور",
            listOf("سربرگ اتصال", "صفحه اتصال", "برو به اتصال", "بخش اتصال", "تب اتصال")
        ),
        VoiceAction(
            "tab_channels", "پیمایش سربرگ‌های برنامه", "رفتن به سربرگ کانال‌ها و پخش زنده",
            listOf("سربرگ کانال ها", "صفحه کانال ها", "برو به کانال ها", "لیست کانال ها", "لیست شبکه ها", "بخش کانال ها", "تب کانال ها")
        ),
        VoiceAction(
            "tab_remote", "پیمایش سربرگ‌های برنامه", "رفتن به سربرگ ریموت کنترل",
            listOf("سربرگ ریموت", "صفحه ریموت", "برو به ریموت", "کنترل رسیور", "بخش ریموت", "تب ریموت")
        ),
        VoiceAction(
            "tab_record", "پیمایش سربرگ‌های برنامه", "رفتن به سربرگ ضبط و PVR",
            listOf("سربرگ ضبط", "صفحه ضبط", "برو به ضبط", "بخش ضبط", "تب ضبط")
        ),
        VoiceAction(
            "tab_voice", "پیمایش سربرگ‌های برنامه", "رفتن به سربرگ فرامین صوتی",
            listOf("سربرگ صوتی", "صفحه صوتی", "برو به دستورات صوتی", "فرامین صوتی", "تب صوتی")
        ),
        VoiceAction(
            "tab_settings", "پیمایش سربرگ‌های برنامه", "رفتن به سربرگ تنظیمات و راهنما",
            listOf("سربرگ تنظیمات", "صفحه تنظیمات", "برو به تنظیمات", "بخش تنظیمات", "تب تنظیمات")
        ),
        VoiceAction(
            "tab_backup", "پیمایش سربرگ‌های برنامه", "رفتن به سربرگ بکاپ و پشتیبان‌گیری",
            listOf("سربرگ بکاپ", "صفحه بکاپ", "برو به بکاپ", "پشتیبان گیری", "تب بکاپ")
        ),

        // ۲. پاور و استندبای
        VoiceAction(
            "power", "مدیریت پاور و استندبای", "روشن یا خاموش کردن رسیور (پاور)",
            listOf("خاموش کردن رسیور", "روشن کردن رسیور", "خاموش کردن", "روشن کردن", "پاور رسیور", "دکمه پاور", "پاور", "سابر", "صابر", "هابر", "استندبای")
        ),
        VoiceAction(
            "restart_stb", "مدیریت پاور و استندبای", "راه‌اندازی مجدد (ری‌استارت) رسیور",
            listOf("ری استارت رسیور", "ری‌استارت رسیور", "راه‌اندازی مجدد رسیور", "دستگاه را ری‌استارت کن", "ری‌استارت", "ری استارت", "ریاستارت", "ریستارت", "ریست رسیور", "ریبوت رسیور", "رسیور را دوباره راه‌اندازی کن")
        ),
        VoiceAction(
            "sleep_timer", "مدیریت پاور و استندبای", "تنظیم تایمر خواب رسیور (Sleep Timer)",
            listOf("تایمر خواب", "اسلیپ", "تنظیم خواب رسیور", "اسلیپ رسیور")
        ),

        // ۳. جابجایی و انتخاب کانال‌ها
        VoiceAction(
            "ch_next", "جابجایی و انتخاب کانال‌ها", "رفتن به کانال بعدی",
            listOf("کانال بعد", "کانال بعدی", "شبکه بعد", "شبکه بعدی", "برو کانال بعد")
        ),
        VoiceAction(
            "ch_prev", "جابجایی و انتخاب کانال‌ها", "رفتن به کانال قبلی",
            listOf("کانال قبل", "کانال قبلی", "شبکه قبل", "شبکه قبلی", "برو کانال قبل")
        ),
        VoiceAction(
            "jump_first_channel", "جابجایی و انتخاب کانال‌ها", "پرش به ابتدای لیست کانال‌ها (کانال اول)",
            listOf("کانال اول", "شبکه اول", "برو به کانال اول", "ابتدای لیست کانال ها", "اولین کانال")
        ),
        VoiceAction(
            "jump_last_channel", "جابجایی و انتخاب کانال‌ها", "پرش به انتهای لیست کانال‌ها (کانال آخر)",
            listOf("کانال آخر", "شبکه آخر", "برو به کانال آخر", "انتهای لیست کانال ها", "آخرین کانال")
        ),
        VoiceAction(
            "filter_satellites", "جابجایی و انتخاب کانال‌ها", "باز کردن منوی فیلتر ماهواره‌ها",
            listOf("فیلتر ماهواره", "انتخاب ماهواره", "ماهواره ها", "لیست ماهواره ها")
        ),
        VoiceAction(
            "recall", "جابجایی و انتخاب کانال‌ها", "بازگشت به کانال قبلی رسیور (Recall)",
            listOf("کانال قبل تر", "شبکه قبلی رسیور", "برگشت کانال", "شبکه قبلی")
        ),
        VoiceAction(
            "tv_mode", "جابجایی و انتخاب کانال‌ها", "تغییر حالت به تلویزیون",
            listOf("حالت تلویزیون", "شبکه تلویزیونی", "حالت تلویزیونی", "برو به تلویزیون", "کانال های تلویزیونی")
        ),
        VoiceAction(
            "radio_mode", "جابجایی و انتخاب کانال‌ها", "تغییر حالت به رادیو",
            listOf("حالت رادیو", "شبکه رادیویی", "حالت رادیویی", "برو به رادیو", "کانال های رادیویی")
        ),
        VoiceAction(
            "refresh_channels", "جابجایی و انتخاب کانال‌ها", "به‌روزرسانی و دریافت مجدد کانال‌ها",
            listOf("به روزرسانی شبکه ها", "تازه سازی کانال ها", "بروزرسانی لیست کانال ها", "دریافت مجدد کانال ها")
        ),
        VoiceAction(
            "search_channels", "جابجایی و انتخاب کانال‌ها", "رفتن به کادر جستجوی کانال",
            listOf("جستجوی کانال", "یافتن کانال", "جستجوی شبکه", "کادر جستجو")
        ),

        // ۴. کنترل صدا و تصویر
        VoiceAction(
            "vol_up", "کنترل صدا و تصویر", "افزایش صدای رسیور",
            listOf("صدا زیاد", "صدای بیشتر", "افزایش صدا", "صدا بالا", "بلندتر", "صدا رو زیاد کن")
        ),
        VoiceAction(
            "vol_down", "کنترل صدا و تصویر", "کاهش صدای رسیور",
            listOf("صدا کم", "صدای کمتر", "کاهش صدا", "صدا پایین", "آهسته تر", "صدا رو کم کن")
        ),
        VoiceAction(
            "mute", "کنترل صدا و تصویر", "قطع یا وصل صدای رسیور (میوت)",
            listOf("قطع صدا", "وصل صدا", "بی صدا", "میوت", "حالت بی صدا")
        ),
        VoiceAction(
            "audio_track", "کنترل صدا و تصویر", "تغییر باند صدا و زبان فیلم",
            listOf("تغییر صدا", "باند صدا", "زبان فیلم", "تراک صوتی")
        ),
        VoiceAction(
            "subtitle", "کنترل صدا و تصویر", "روشن یا خاموش کردن زیرنویس شبکه",
            listOf("زیرنویس", "نمایش زیرنویس", "قطع زیرنویس")
        ),

        // ۵. منوها و ابزارهای ریموت
        VoiceAction(
            "menu", "منوها و ابزارهای ریموت", "باز کردن منوی اصلی رسیور",
            listOf("منو", "منوی رسیور", "منوی اصلی")
        ),
        VoiceAction(
            "ok", "منوها و ابزارهای ریموت", "دکمه تأیید ریموت کنترل (OK)",
            listOf("دکمه تایید", "اوکی", "دکمه اوکی", "تایید")
        ),
        VoiceAction(
            "rcu_exit", "منوها و ابزارهای ریموت", "خروج و بستن پنجره‌ها (Exit)",
            listOf("خروج", "بستن پنجره", "برگشت")
        ),
        VoiceAction(
            "epg", "منوها و ابزارهای ریموت", "جدول و راهنمای برنامه‌ها (EPG)",
            listOf("ای پی جی", "جدول برنامه", "راهنمای برنامه", "epg")
        ),
        VoiceAction(
            "info", "منوها و ابزارهای ریموت", "اطلاعات و مشخصات برنامه در حال پخش (Info)",
            listOf("اینفو", "اطلاعات شبکه", "مشخصات برنامه", "اطلاعات کانال")
        ),

        // ۶. دکمه‌های رنگی ریموت
        VoiceAction(
            "red_btn", "دکمه‌های رنگی ریموت", "دکمه قرمز ریموت کنترل",
            listOf("دکمه قرمز", "کلید قرمز")
        ),
        VoiceAction(
            "green_btn", "دکمه‌های رنگی ریموت", "دکمه سبز ریموت کنترل",
            listOf("دکمه سبز", "کلید سبز")
        ),
        VoiceAction(
            "yellow_btn", "دکمه‌های رنگی ریموت", "دکمه زرد ریموت کنترل",
            listOf("دکمه زرد", "کلید زرد")
        ),
        VoiceAction(
            "blue_btn", "دکمه‌های رنگی ریموت", "دکمه آبی ریموت کنترل",
            listOf("دکمه آبی", "کلید آبی")
        ),

        // ۷. ضبط و رسانه
        VoiceAction(
            "rec_stb", "ضبط و پخش رسانه", "شروع یا توقف ضبط روی فلش رسیور",
            listOf("ضبط رسیور", "ضبط روی فلش", "شروع ضبط رسیور", "ضبط فلش")
        ),
        VoiceAction(
            "stop_stb_rec", "ضبط و پخش رسانه", "توقف ضبط روی فلش مموری رسیور",
            listOf("توقف ضبط رسیور", "قطع ضبط رسیور", "استپ ضبط رسیور", "توقف ضبط فلش")
        ),
        VoiceAction(
            "rec_phone", "ضبط و پخش رسانه", "شروع یا توقف ضبط در گوشی",
            listOf("ضبط گوشی", "شروع ضبط گوشی", "ضبط کانال")
        ),
        VoiceAction(
            "stop_phone_rec", "ضبط و پخش رسانه", "توقف ضبط در حافظه گوشی",
            listOf("توقف ضبط گوشی", "قطع ضبط گوشی", "استپ ضبط گوشی")
        ),
        VoiceAction(
            "stream", "ضبط و پخش رسانه", "پخش زنده تصویر در گوشی",
            listOf("پخش زنده", "شروع پخش", "پخش تصویر", "استریم", "پخش زنده تصویر")
        ),
        VoiceAction(
            "stop_stream", "ضبط و پخش رسانه", "توقف و بستن پخش زنده",
            listOf("توقف پخش", "قطع پخش", "بستن پخش", "استپ پخش")
        ),
        VoiceAction(
            "open_rec", "ضبط و پخش رسانه", "باز کردن لیست فایل‌های ضبط شده",
            listOf("فایل های ضبط شده", "آرشیو ضبط", "لیست ضبط", "پوشه ضبط")
        ),
        VoiceAction(
            "open_stb_files", "ضبط و پخش رسانه", "مشاهده لیست فایل‌های ضبط شده فلش رسیور",
            listOf("فایل های فلش", "ضبط های رسیور", "فایل های رسیور", "لیست ضبط رسیور")
        ),
        VoiceAction(
            "add_timer", "ضبط و پخش رسانه", "تعریف زمان‌بندی جدید برای ضبط خودکار",
            listOf("زمان بندی جدید", "تایمر ضبط", "زمان بندی ضبط", "تعریف زمان بندی")
        ),
        VoiceAction(
            "reset_counter", "ضبط و پخش رسانه", "صفر کردن شمارنده فایل‌های ضبط",
            listOf("صفر کردن شمارنده", "ریست شمارنده ضبط", "شمارنده ضبط صفر")
        ),

        // ۸. ابزارهای هوشمند و برنامه
        VoiceAction(
            "minimize_app", "ابزارهای هوشمند", "اجرا در پس‌زمینه (فعال ماندن)",
            listOf("اجرا در پس زمینه", "برو به پس زمینه", "پس زمینه")
        ),
        VoiceAction(
            "close_app", "ابزارهای هوشمند", "بستن و خروج کامل از برنامه",
            listOf("بستن برنامه", "خروج از برنامه", "خروج کامل")
        ),
        VoiceAction(
            "open_settings", "ابزارهای هوشمند", "ورود به بخش تنظیمات",
            listOf("تنظیمات", "تنظیمات برنامه", "بخش تنظیمات")
        ),
        VoiceAction(
            "open_manual", "ابزارهای هوشمند", "کتابچه راهنمای جامع کاربری",
            listOf("کتابچه راهنما", "راهنمای نرم افزار", "راهنمای کاربری")
        ),
        VoiceAction(
            "open_voice_help", "ابزارهای هوشمند", "راهنمای فرامین صوتی",
            listOf("راهنمای صوتی", "فرامین صوتی", "دستورات صوتی")
        ),
        VoiceAction(
            "open_stb_info", "ابزارهای هوشمند", "مشخصات سخت‌افزاری و مدل رسیور",
            listOf("مشخصات رسیور", "اطلاعات دستگاه", "مدل رسیور")
        )
    )

    private var customActionsMap = mutableMapOf<String, List<String>>()
    private var customChannelAliasesMap = mutableMapOf<String, MutableList<String>>() // serviceId -> list of aliases
    private var disabledActionsSet = mutableSetOf<String>()
    private var syncedChannelIds = mutableSetOf<String>()
    private var manuallySyncedIds = mutableSetOf<String>()
    private var isLoaded = false

    fun init(context: Context) {
        if (!isLoaded) {
            loadCustomActions(context)
            loadChannelAliases(context)
            loadDisabledActions(context)
            loadSyncedChannelIds(context)
            isLoaded = true
        }
    }

    private fun loadCustomActions(context: Context) {
        try {
            val file = File(context.filesDir, FILE_ACTIONS)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val map: Map<String, List<String>> = Gson().fromJson(json, type)
                customActionsMap.clear()
                customActionsMap.putAll(map)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading custom voice actions: ${e.message}")
        }
    }

    fun saveCustomActions(context: Context) {
        try {
            val file = File(context.filesDir, FILE_ACTIONS)
            val json = Gson().toJson(customActionsMap)
            file.writeText(json)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error saving custom voice actions: ${e.message}")
        }
    }

    private fun loadDisabledActions(context: Context) {
        try {
            val file = File(context.filesDir, FILE_DISABLED_ACTIONS)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<Set<String>>() {}.type
                val set: Set<String> = Gson().fromJson(json, type)
                disabledActionsSet.clear()
                disabledActionsSet.addAll(set)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading disabled actions: ${e.message}")
        }
    }

    private fun saveDisabledActions(context: Context) {
        try {
            val file = File(context.filesDir, FILE_DISABLED_ACTIONS)
            val json = Gson().toJson(disabledActionsSet)
            file.writeText(json)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error saving disabled actions: ${e.message}")
        }
    }

    fun isActionEnabled(actionId: String): Boolean {
        return !disabledActionsSet.contains(actionId)
    }

    fun setActionEnabled(context: Context, actionId: String, enabled: Boolean) {
        if (enabled) {
            disabledActionsSet.remove(actionId)
        } else {
            disabledActionsSet.add(actionId)
        }
        saveDisabledActions(context)
    }

    fun toggleAction(context: Context, actionId: String): Boolean {
        val newState = !isActionEnabled(actionId)
        setActionEnabled(context, actionId, newState)
        return newState
    }

    private fun loadSyncedChannelIds(context: Context) {
        try {
            val file = File(context.filesDir, FILE_SYNCED_CHANNEL_IDS)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<Set<String>>() {}.type
                val set: Set<String> = Gson().fromJson(json, type)
                syncedChannelIds.clear()
                syncedChannelIds.addAll(set)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading synced channel IDs: ${e.message}")
        }
    }

    private fun saveSyncedChannelIds(context: Context) {
        try {
            val file = File(context.filesDir, FILE_SYNCED_CHANNEL_IDS)
            val json = Gson().toJson(syncedChannelIds)
            file.writeText(json)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error saving synced channel IDs: ${e.message}")
        }
    }

    private fun loadChannelAliases(context: Context) {
        try {
            val file = File(context.filesDir, FILE_CHANNEL_ALIASES)
            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val map: Map<String, List<String>> = Gson().fromJson(json, type)
                customChannelAliasesMap.clear()
                for ((k, v) in map) {
                    customChannelAliasesMap[k] = v.toMutableList()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading custom channel aliases: ${e.message}")
        }
    }

    fun saveChannelAliases(context: Context) {
        try {
            val file = File(context.filesDir, FILE_CHANNEL_ALIASES)
            val json = Gson().toJson(customChannelAliasesMap)
            file.writeText(json)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error saving custom channel aliases: ${e.message}")
        }
    }

    fun getEffectivePhrases(actionId: String): List<String> {
        val custom = customActionsMap[actionId]
        if (custom != null) {
            return custom
        }
        val defaultAction = DEFAULT_ACTIONS.find { it.id == actionId }
        return defaultAction?.defaultPhrases ?: emptyList()
    }

    fun setPhrases(context: Context, actionId: String, phrases: List<String>) {
        customActionsMap[actionId] = phrases.map { it.trim() }.filter { it.isNotBlank() }
        saveCustomActions(context)
    }

    fun addPhrase(context: Context, actionId: String, phrase: String) {
        val current = getEffectivePhrases(actionId).toMutableList()
        val clean = phrase.trim()
        if (clean.isNotBlank() && !current.contains(clean)) {
            current.add(clean)
            setPhrases(context, actionId, current)
        }
    }

    fun removePhrase(context: Context, actionId: String, phrase: String) {
        val current = getEffectivePhrases(actionId).toMutableList()
        current.remove(phrase.trim())
        setPhrases(context, actionId, current)
    }

    fun resetAction(context: Context, actionId: String) {
        customActionsMap.remove(actionId)
        disabledActionsSet.remove(actionId)
        saveCustomActions(context)
        saveDisabledActions(context)
    }

    fun resetAllActions(context: Context) {
        customActionsMap.clear()
        disabledActionsSet.clear()
        saveCustomActions(context)
        saveDisabledActions(context)
    }

    fun getChannelAliasesMap(): Map<String, List<String>> = customChannelAliasesMap

    fun addChannelAlias(context: Context, serviceId: String, alias: String) {
        val list = customChannelAliasesMap.getOrPut(serviceId) { mutableListOf() }
        val clean = alias.trim()
        if (clean.isNotBlank() && !list.contains(clean)) {
            list.add(clean)
            saveChannelAliases(context)
        }
    }

    fun removeChannelAlias(context: Context, serviceId: String, alias: String) {
        val list = customChannelAliasesMap[serviceId]
        if (list != null) {
            list.remove(alias.trim())
            if (list.isEmpty()) {
                customChannelAliasesMap.remove(serviceId)
            }
            saveChannelAliases(context)
        }
    }

    fun resetAllChannelAliases(context: Context) {
        customChannelAliasesMap.clear()
        syncedChannelIds.clear()
        saveChannelAliases(context)
        saveSyncedChannelIds(context)
    }

    /**
     * Updates channel number aliases for a channel, removing any obsolete numeric aliases
     * (e.g. previous channel number 9 when channel is now 485) and setting current number.
     */
    fun updateChannelNumberAliases(channel: Channel) {
        val list = customChannelAliasesMap[channel.serviceId] ?: return
        val regex = Regex("^(کانال|شبکه)\\s+\\d+$")
        list.removeAll { regex.matches(it.trim()) }
        if (channel.realChannelNum > 0) {
            val a1 = "کانال ${channel.realChannelNum}"
            val a2 = "شبکه ${channel.realChannelNum}"
            if (!list.contains(a1)) list.add(a1)
            if (!list.contains(a2)) list.add(a2)
        }
    }

    /**
     * Called automatically when STB channels are first loaded/received.
     * Silently indexes channels and updates existing numbers.
     */
    fun autoIndexChannels(context: Context, channels: List<Channel>) {
        if (channels.isEmpty()) return
        init(context)
        var changed = false
        val currentIds = channels.map { it.serviceId }.toSet()

        for (ch in channels) {
            if (!syncedChannelIds.contains(ch.serviceId)) {
                syncedChannelIds.add(ch.serviceId)
                val list = customChannelAliasesMap.getOrPut(ch.serviceId) { mutableListOf() }
                val generated = generateDefaultAliases(ch)
                for (a in generated) {
                    if (!list.contains(a)) list.add(a)
                }
                changed = true
            } else {
                // Ensure channel number is always fresh and obsolete numbers are purged
                updateChannelNumberAliases(ch)
                changed = true
            }
        }

        // Clean up removed channels from memory
        val removed = syncedChannelIds.filter { !currentIds.contains(it) }
        if (removed.isNotEmpty()) {
            for (id in removed) {
                syncedChannelIds.remove(id)
                customChannelAliasesMap.remove(id)
                manuallySyncedIds.remove(id)
            }
            changed = true
        }

        if (changed) {
            saveChannelAliases(context)
            saveSyncedChannelIds(context)
            AppLogger.i(TAG, "Auto-indexed ${channels.size} channels into voice vocabulary")
        }
    }

    /**
     * Manually triggers sync when user clicks the button in Voice Fragment.
     * Returns Pair(newChannelsCount, totalChannelsCount)
     */
    fun syncChannelsWithVoice(context: Context, channels: List<Channel>): Pair<Int, Int> {
        init(context)
        var newChannelsCount = 0
        val currentIds = channels.map { it.serviceId }.toSet()

        for (channel in channels) {
            val isNew = !manuallySyncedIds.contains(channel.serviceId)
            if (isNew) {
                newChannelsCount++
                manuallySyncedIds.add(channel.serviceId)
            }
            syncedChannelIds.add(channel.serviceId)

            val list = customChannelAliasesMap.getOrPut(channel.serviceId) { mutableListOf() }
            val generated = generateDefaultAliases(channel)
            for (a in generated) {
                if (!list.contains(a)) {
                    list.add(a)
                }
            }
            // Smartly purge old numbers and ensure current number is set
            updateChannelNumberAliases(channel)
        }

        // Remove deleted channels
        val removed = syncedChannelIds.filter { !currentIds.contains(it) }
        for (id in removed) {
            syncedChannelIds.remove(id)
            customChannelAliasesMap.remove(id)
            manuallySyncedIds.remove(id)
        }

        saveChannelAliases(context)
        saveSyncedChannelIds(context)
        AppLogger.i(TAG, "Manual voice sync: $newChannelsCount new channels detected among ${channels.size} total channels")
        return Pair(newChannelsCount, channels.size)
    }

    private fun generateDefaultAliases(channel: Channel): List<String> {
        val aliases = mutableListOf<String>()
        val raw = channel.serviceName.trim()
        if (raw.isBlank()) return aliases

        // 1. Raw name & prefix variants
        aliases.add(raw)
        val type = if (channel.isRadio) "رادیو" else "شبکه"
        aliases.add("$type $raw")
        aliases.add("کانال $raw")

        // 2. Cleaned name without HD/SD/4K/FHD
        val cleaned = raw.replace(Regex("(?i)\\b(hd|sd|4k|fhd|hevc|uhd)\\b"), "").trim()
        if (cleaned.isNotBlank() && cleaned != raw) {
            aliases.add(cleaned)
            aliases.add("$type $cleaned")
            aliases.add("کانال $cleaned")
        }

        // 3. Channel number aliases
        if (channel.realChannelNum > 0) {
            aliases.add("کانال ${channel.realChannelNum}")
            aliases.add("شبکه ${channel.realChannelNum}")
        }

        // 4. Common Persian transliterations & subbrands
        val lower = cleaned.lowercase()
        if (lower.contains("gem")) {
            val sub = lower.replace("gem", "").trim()
            if (sub.isNotBlank()) {
                aliases.add("جم $sub")
                aliases.add("شبکه جم $sub")
            } else {
                aliases.add("جم")
                aliases.add("شبکه جم")
                aliases.add("جم تیوی")
            }
        } else if (lower.contains("persiana")) {
            val sub = lower.replace("persiana", "").trim()
            if (sub.isNotBlank()) {
                aliases.add("پرشیانا $sub")
                aliases.add("شبکه پرشیانا $sub")
            } else {
                aliases.add("پرشیانا")
                aliases.add("شبکه پرشیانا")
            }
        }

        return aliases.distinct()
    }

    /**
     * Checks if a spoken query matches any enabled customizable voice action.
     */
    fun matchVoiceAction(query: String): String? {
        val norm = ChannelMatcher.normalizePersian(query)
        if (norm.isBlank()) return null

        for (action in DEFAULT_ACTIONS) {
            if (!isActionEnabled(action.id)) continue
            val phrases = getEffectivePhrases(action.id)
            for (p in phrases) {
                val pNorm = ChannelMatcher.normalizePersian(p)
                if (pNorm.isNotBlank()) {
                    if (norm == pNorm || norm.contains(pNorm)) {
                        return action.id
                    }
                    if (norm.length >= 4 && pNorm.contains(norm) && norm.length >= (pNorm.length * 0.75).toInt()) {
                        return action.id
                    }
                }
            }
        }
        return null
    }

    /**
     * Checks if a query directly matches custom channel aliases.
     */
    fun matchChannelAlias(query: String, channels: List<Channel>): Channel? {
        val norm = ChannelMatcher.normalizePersian(query)
        if (norm.isBlank() || channels.isEmpty()) return null

        for ((serviceId, aliases) in customChannelAliasesMap) {
            for (alias in aliases) {
                val aNorm = ChannelMatcher.normalizePersian(alias)
                if (aNorm.isNotBlank()) {
                    if (norm == aNorm || norm.contains(aNorm)) {
                        val found = channels.find { it.serviceId == serviceId }
                        if (found != null) return found
                    }
                    if (norm.length >= 4 && aNorm.contains(norm) && norm.length >= (aNorm.length * 0.75).toInt()) {
                        val found = channels.find { it.serviceId == serviceId }
                        if (found != null) return found
                    }
                }
            }
        }
        return null
    }
}

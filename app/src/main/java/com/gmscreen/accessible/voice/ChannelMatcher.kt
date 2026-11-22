package com.gmscreen.accessible.voice

import com.gmscreen.accessible.data.model.Channel

object ChannelMatcher {

    private val PERSIAN_TO_ENGLISH_DIGITS = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    private val WORD_TO_NUMBER = mapOf(
        "یک" to 1, "یکمی" to 1, "اول" to 1, "اولین" to 1,
        "دو" to 2, "دومی" to 2, "دوم" to 2, "دومین" to 2,
        "سه" to 3, "سومی" to 3, "سوم" to 3, "سومین" to 3,
        "چهار" to 4, "چهارمی" to 4, "چهارم" to 4, "چهارمین" to 4,
        "پنج" to 5, "پنجمی" to 5, "پنجم" to 5, "پنجمین" to 5,
        "شش" to 6, "شیش" to 6, "شیشمی" to 6, "ششم" to 6, "شیشم" to 6,
        "هفت" to 7, "هفتمی" to 7, "هفتم" to 7, "هفتمین" to 7,
        "هشت" to 8, "هشتمی" to 8, "هشتم" to 8, "هشتمین" to 8,
        "نه" to 9, "نهمی" to 9, "نهم" to 9, "نهمین" to 9,
        "ده" to 10, "دهمی" to 10, "دهم" to 10,
        "یازده" to 11, "یازدهم" to 11, "دوازده" to 12, "دوازدهم" to 12,
        "سیزده" to 13, "سیزدهم" to 13, "چهارده" to 14, "چهاردهم" to 14,
        "پانزده" to 15, "پونزده" to 15, "پانزدهم" to 15, "پونزدهم" to 15,
        "شانزده" to 16, "شونزده" to 16, "شانزدهم" to 16, "شونزدهم" to 16,
        "هفده" to 17, "هفدهم" to 17, "هیجده" to 18, "هجده" to 18, "هجدهم" to 18,
        "نوزده" to 19, "نوزدهم" to 19,
        "بیست" to 20, "بیستم" to 20, "سی" to 30, "چهل" to 40,
        "پنجاه" to 50, "شصت" to 60, "هفتاد" to 70, "هشتاد" to 80, "نود" to 90,
        "صد" to 100, "دویست" to 200, "سیصد" to 300, "چهارصد" to 400, "پانصد" to 500
    )

    private val GEM_SUBBRANDS = setOf(
        "drama", "series", "classic", "comedy", "bollywood", "action", "life",
        "junior", "kids", "fit", "food", "nature", "documentary", "doc", "arabia",
        "travel", "academy", "pixel", "film", "music", "sport", "sports", "fashion",
        "entertainment", "rubix", "river", "onyx", "mifa", "24b"
    )

    private val PERSIANA_SUBBRANDS = setOf(
        "cinema", "rap", "mix", "setmix", "comedy", "family", "junior", "nostalgia",
        "science", "documentary", "doc", "sports", "sport", "billboard", "korea",
        "series", "iranian", "folk", "medical", "fight", "china", "podcast", "reality",
        "teen", "voyage"
    )

    private val WORDS_MAP = listOf(
        // GEM Channels
        "جم" to "gem", "دراما" to "drama", "سریز" to "series", "سریال" to "series",
        "کلاسیک" to "classic", "کمدی" to "comedy", "بالیوود" to "bollywood", "اکشن" to "action",
        "لایف" to "life", "جونیور" to "junior", "کیدز" to "kids", "کودک" to "junior kids",
        "بچه ها" to "kids", "فیت" to "fit", "فیلم" to "film", "فود" to "food", "غذا" to "food",
        "مستند" to "documentary doc", "داکیومنتری" to "documentary doc", "نیچر" to "nature",
        "طبیعت" to "nature", "عربیا" to "arabia", "تراول" to "travel", "سفر" to "travel",
        "آکادمی" to "academy", "اکادمی" to "academy", "پیکسل" to "pixel", "پیکس" to "pixel",
        "فشن" to "fashion", "مد" to "fashion", "انترتینمنت" to "entertainment", "اینترتینمنت" to "entertainment",
        "روبیکس" to "rubix", "ریور" to "river", "اونیکس" to "onyx", "انیکس" to "onyx",
        "میفا" to "mifa", "پلاس" to "plus", "بیست و چهار بی" to "24b", "بیست و چهار" to "24",
        "بی" to "b",

        // Persiana Channels
        "پرشیانا ست میکس" to "persiana setmix", "پرشیانا ستمیکس" to "persiana setmix",
        "پرشیانا ست مکس" to "persiana setmix", "پرشیانا ست میک" to "persiana setmix",
        "پرشیانا میکس" to "persiana mix", "پرشیانا موزیک" to "persiana music",
        "پرشیانا رپ" to "persiana rap", "پرشیانا سینما" to "persiana cinema",
        "پرشیانا کمدی" to "persiana comedy", "پرشیانا کومدی" to "persiana comedy",
        "پرشیانا فامیلی" to "persiana family", "پرشیانا فمیلی" to "persiana family",
        "پرشیانا جونیور" to "persiana junior", "پرشیانا نوستالژی" to "persiana nostalgia",
        "پرشیانا ساینس" to "persiana science", "پرشیانا کره" to "persiana korea",
        "پرشیانا بیلبورد" to "persiana billboard", "پرشیانا ایرانی" to "persiana iranian",
        "پرشیانا ایرانیان" to "persiana iranian", "پرشیانا ورزش" to "persiana sports",
        "پرشیانا اسپورت" to "persiana sports", "پرشیانا اسپورتس" to "persiana sports",
        "پرشیانا مستند" to "persiana documentary", "پرشیانا پادکست" to "persiana podcast",
        "پرشیانا مدیکال" to "persiana medical", "پرشیانا چین" to "persiana china",
        "پرشیانا ویاژ" to "persiana voyage", "پرشیانا تین" to "persiana teen",
        "پرشیانا فولک" to "persiana folk", "پرشیانا فایت" to "persiana fight",
        "پرشیانا" to "persiana", "سینما" to "cinema", "رپ" to "rap", "میکس" to "mix",
        "ست میکس" to "setmix", "ستمیکس" to "setmix", "ست مکس" to "setmix",
        "ستمکس" to "setmix", "ست میک" to "setmix", "ست مک" to "setmix",
        "فامیلی" to "family", "فمیلی" to "family",
        "خانواده" to "family", "نوستالژی" to "nostalgia", "ساینس" to "science", "علمی" to "science",
        "فایت" to "fight", "رزمی" to "fight", "فولک" to "folk", "سنتی" to "folk",
        "ایرانی" to "iranian", "ایرانیان" to "iranian", "پادکست" to "podcast", "ریالیتی" to "reality",
        "تین" to "teen", "نوجوان" to "teen", "ویاژ" to "voyage", "کره" to "korea", "کره ای" to "korea",
        "مدیکال" to "medical", "پزشکی" to "medical", "چین" to "china", "چینی" to "china",
        "بیلبورد" to "billboard", "بیلورد" to "billboard",

        // Sports
        "ورزش" to "varzesh sport sports", "اسپورت" to "sport sports varzesh",
        "اسپورتس" to "sports sport varzesh", "فوتبال" to "football", "تاجیکستان" to "tajikistan",
        "ترکمنستان" to "turkmenistan", "افغانستان" to "afghanistan", "عربستان" to "ksa saudi",
        "صلح" to "solh", "رادیو" to "radio",

        // General & IRIB
        "امید دانا" to "omid dana", "امیددانا" to "omid dana", "زد تیوی" to "zed tv", "زد تی وی" to "zed tv",
        "ملیک" to "mellig", "ملیگ" to "mellig", "ملی" to "melli", "دانا" to "dana", "امید" to "omid",
        "ایران اینترنشنال" to "iran international intl", "ایران" to "iran",
        "اینترنشنال" to "international intl", "اینتر نشنال" to "international intl",
        "موزیک" to "music", "موسیقی" to "music", "خبر" to "khabar irinn news", "نسیم" to "nasim",
        "پویا" to "pooya pouya", "نهال" to "nahal", "نمایش" to "namayesh", "افق" to "ofogh ofoq",
        "تماشا" to "tamasha", "سلامت" to "salamat", "آموزش" to "amoozesh", "قرآن" to "quran", "قران" to "quran",
        "آی فیلم" to "ifilm", "آیفیلم" to "ifilm", "ای فیلم" to "ifilm", "ایفیلم" to "ifilm",
        "منوتو" to "manoto", "من و تو" to "manoto", "بی بی سی" to "bbc", "طپش" to "tapesh",
        "تپش" to "tapesh", "رادیو جوان" to "radio javan", "رادیو فردا" to "radio farda",
        "رادیو پیام" to "radio payam", "رادیو نما" to "radio nama", "پی ام سی" to "pmc",
        "تیوی" to "tv", "تی وی" to "tv",
        "یک" to "1", "دو" to "2", "سه" to "3", "چهار" to "4", "پنج" to "5",
        "شش" to "6", "شیش" to "6", "هفت" to "7", "هشت" to "8", "نه" to "9", "ده" to "10",
        "دری" to "dari",
        "ام بی سی" to "mbc persia mbc", "ام‌بی‌سی" to "mbc persia mbc",
        "نشنال جئوگرافیک" to "national geographic nat geo", "نشنال جئو" to "national geographic nat geo",
        "دیسکاوری" to "discovery", "یورونیوز" to "euronews", "یورو نیوز" to "euronews"
    ).sortedByDescending { it.first.length }

    fun normalizePersian(text: String): String {
        if (text.isBlank()) return ""
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(PERSIAN_TO_ENGLISH_DIGITS[ch] ?: ch)
        }
        var res = sb.toString()
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('\u200c', ' ')
            .replace(Regex("[‌​‎‏]"), " ")
        res = res.replace(Regex("\\s+"), " ").trim().lowercase()
        return res
    }

    fun cleanChannelText(text: String): String {
        if (text.isBlank()) return ""
        var t = normalizePersian(text)
            .replace('آ', 'ا')
            .replace('أ', 'ا')
            .replace('إ', 'ا')

        for ((persianWord, engVal) in WORDS_MAP) {
            val pattern = Regex("(?:\\b|(?<=\\s)|(?<=^))" + Regex.escape(persianWord) + "(?:\\b|(?=\\s)|(?=$))")
            t = t.replace(pattern, " $engVal ")
        }
        t = t.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        return t.replace(Regex("\\s+"), " ").trim().lowercase()
    }

    private fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val d = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) d[i][0] = i
        for (j in 0..s2.length) d[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            }
        }
        return d[s1.length][s2.length]
    }

    /**
     * Checks if the voice command matches a direct RCU/System action.
     */
    fun matchVoiceAction(query: String): String? {
        val customMatch = VoiceCustomizationManager.matchVoiceAction(query)
        if (customMatch != null) return customMatch

        val norm = normalizePersian(query)
        if (norm.isBlank()) return null

        // Power
        if (norm.contains("خاموش") || norm.contains("روشن") || norm.contains("پاور") || norm.contains("استندبای") ||
            norm.contains("دستگاه رو خاموش") || norm.contains("دستگاه رو روشن") || norm.contains("به خواب برو")
        ) {
            return "power"
        }
        // Channel Next / Prev
        if (norm.contains("کانال بعد") || norm.contains("شبکه بعد") || norm.contains("کانال بعدی") || norm.contains("شبکه بعدی") ||
            norm.contains("برو کانال بعد") || norm.contains("کانال جلو") || norm.contains("شبکه جلو")
        ) {
            return "ch_next"
        }
        if (norm.contains("کانال قبل") || norm.contains("شبکه قبل") || norm.contains("کانال قبلی") || norm.contains("شبکه قبلی") ||
            norm.contains("برو کانال قبل") || norm.contains("کانال عقب") || norm.contains("شبکه عقب")
        ) {
            return "ch_prev"
        }
        // Volume
        if (norm.contains("صدا زیاد") || norm.contains("صدای بیشتر") || norm.contains("افزایش صدا") || norm.contains("بلندتر") ||
            norm.contains("بلند تر") || norm.contains("ببر بالا") || norm.contains("صدا رو زیاد کن") || norm.contains("صدا بیشتر")
        ) {
            return "vol_up"
        }
        if (norm.contains("صدا کم") || norm.contains("صدای کمتر") || norm.contains("کاهش صدا") || norm.contains("آهسته تر") ||
            norm.contains("آروم تر") || norm.contains("اروم تر") || norm.contains("بیار پایین") || norm.contains("صدا رو کم کن") || norm.contains("صدا کمتر")
        ) {
            return "vol_down"
        }
        if (norm.contains("بی صدا") || norm.contains("بیصدا") || norm.contains("قطع صدا") || norm.contains("وصل صدا") ||
            norm.contains("میوت") || norm.contains("سایلنت") || norm.contains("صدا رو قطع کن") || norm.contains("صدا رو وصل کن")
        ) {
            return "mute"
        }
        // Menus & Navigation
        if (norm.contains("منو") || norm.contains("منوی رسیور") || norm.contains("فهرست")) {
            return "menu"
        }
        if (norm.contains("خروج") || norm.contains("بستن پنجره") || norm.contains("برگشت") || norm.contains("برگرد") || norm.contains("کنسل")) {
            return "exit"
        }
        if (norm.contains("تایید") || norm.contains("اوکی") || norm.contains("تأیید") || norm.contains("قبول")) {
            return "ok"
        }
        if (norm.contains("ای پی جی") || norm.contains("ای‌پی‌جی") || norm.contains("جدول برنامه") || norm.contains("راهنمای برنامه")) {
            return "epg"
        }
        if (norm.contains("اینفو") || norm.contains("اطلاعات شبکه") || norm.contains("مشخصات برنامه") || norm.contains("اطلاعات کانال")) {
            return "info"
        }
        // Recording
        if (norm.contains("شروع ضبط") || norm.contains("ضبط کن") || norm.contains("رکورد") || norm.contains("ضبط روی فلش")) {
            return "record_toggle"
        }
        if (norm.contains("توقف ضبط") || norm.contains("قطع ضبط") || norm.contains("پایان ضبط")) {
            return "record_stop"
        }
        return null
    }

    /**
     * Extracts channel number from query, if user asked for a specific channel number (e.g. "کانال ۳۵۵").
     */
    fun parseChannelNumber(query: String): Int? {
        val norm = normalizePersian(query)
        val match = Regex("\\b(\\d{1,4})\\b").find(norm)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
        for ((word, num) in WORD_TO_NUMBER) {
            if (norm.contains(word)) {
                return num
            }
        }
        return null
    }

    /**
     * Evaluates a single candidate query against all channels, returning the best matched Channel and its score.
     */
    fun scoreChannelForQuery(query: String, channels: List<Channel>): Pair<Channel?, Int> {
        if (query.isBlank() || channels.isEmpty()) return Pair(null, -10000)

        val aliasMatch = VoiceCustomizationManager.matchChannelAlias(query, channels)
        if (aliasMatch != null) return Pair(aliasMatch, 99999)

        var qClean = normalizePersian(query)
            .replace(Regex("^(?:برو\\s*به\\s*|برو\\s*روی\\s*|بزن\\s*روی\\s*|بزن\\s*به\\s*|بزار\\s*روی\\s*|بگذار\\s*روی\\s*|بذار\\s*روی\\s*|لطفا\\s*|لطفاً\\s*|بی\\s*زحمت\\s*|بی‌زحمت\\s*|میشه\\s*|می‌شه\\s*|کانال\\s+|شبکه\\s+|شبکه‌ی\\s+|کانالِ\\s+)+"), "")
            .replace(Regex("\\s+(?:رو\\s*بیار|بیار|رو\\s*باز\\s*کن|باز\\s*کن|رو\\s*پخش\\s*کن|پخش\\s*کن|رو\\s*نشون\\s*بده|نشون\\s*بده|بزار|بگذار|بذار|رو\\s*بزار|رو\\s*بگذار|رو\\s*بذار)$"), "")
            .trim()

        if (qClean.isBlank()) return Pair(null, -10000)

        val channelNum = parseChannelNumber(qClean)
        if (channelNum != null) {
            val directCh = channels.find { it.channelNumber == channelNum }
            if (directCh != null) return Pair(directCh, 50000)
        }

        val qTokenized = cleanChannelText(qClean)
        val qTokens = qTokenized.split(" ").filter { it.isNotBlank() }
        if (qTokens.isEmpty()) return Pair(null, -10000)

        val hasPlus = qTokens.contains("plus") || qClean.contains("+")
        val queryGemSubs = qTokens.filter { it in GEM_SUBBRANDS }.toSet()
        val queryPersianaSubs = qTokens.filter { it in PERSIANA_SUBBRANDS }.toSet()
        val hasGem = qTokens.contains("gem")
        val hasPersiana = qTokens.contains("persiana")
        val hasRadio = qTokens.contains("radio")
        val hasVarzesh = qTokens.any { it in listOf("varzesh", "sport", "sports") }
        val hasIfilm = qTokens.contains("ifilm")
        val hasTwo = qTokens.contains("2")
        val hasDari = qTokens.contains("dari")
        val hasDana = qTokens.contains("dana")
        val hasMellig = qTokens.contains("mellig")
        val hasIntl = qTokens.any { it in listOf("intl", "international") }
        val queryDigits = Regex("\\b\\d+\\b").findAll(qTokenized).map { it.value }.toSet()

        var bestScore = -10000
        var bestCh: Channel? = null

        for ((idx, ch) in channels.withIndex()) {
            val sName = ch.serviceName
            val nClean = cleanChannelText(sName)
            val nTokens = nClean.split(" ").filter { it.isNotBlank() }
            if (nTokens.isEmpty()) continue

            var score = 0
            var matchedTokens = 0

            for (qt in qTokens) {
                if (nTokens.contains(qt)) {
                    score += 200
                    matchedTokens++
                } else if (nTokens.any { it.contains(qt) }) {
                    score += 80
                    matchedTokens++
                } else if (nTokens.any { qt.contains(it) }) {
                    score += 40
                    matchedTokens++
                }
            }

            if (matchedTokens == 0) {
                // Check fuzzy edit distance for short names (e.g. "نسیم" vs "نسیمه")
                if (qTokenized.length >= 3 && nClean.length >= 3) {
                    val dist = levenshtein(qTokenized, nClean)
                    if (dist <= 2) {
                        score += 350
                        matchedTokens = 1
                    } else continue
                } else continue
            }

            if (matchedTokens >= qTokens.size) score += 300
            if (nClean == qTokenized) score += 5000
            if (sName.equals(qClean, ignoreCase = true)) score += 5000

            // Digits matching
            val chDigits = Regex("\\b\\d+\\b").findAll(nClean).map { it.value }.toSet()
            if (queryDigits.isNotEmpty()) {
                if (chDigits.containsAll(queryDigits)) {
                    score += 3500
                } else if (chDigits.isNotEmpty()) {
                    score -= 4000
                } else {
                    score -= 3000
                }
            } else if (chDigits.isNotEmpty()) {
                score -= 400
            }

            // GEM Disambiguation
            val chGemSubs = nTokens.filter { it in GEM_SUBBRANDS }.toSet()
            if (queryGemSubs.isNotEmpty()) {
                val matchedSubs = queryGemSubs.intersect(chGemSubs)
                if (matchedSubs.isNotEmpty()) {
                    score += 3500
                } else if (nTokens.contains("gem")) {
                    score -= 4000
                }
            } else if (hasGem) {
                if (chGemSubs.isNotEmpty()) {
                    score -= 3000
                } else if (nTokens.contains("tv")) {
                    score += 2500
                }
            }

            // Persiana Disambiguation
            val chPersianaSubs = nTokens.filter { it in PERSIANA_SUBBRANDS }.toSet()
            if (queryPersianaSubs.isNotEmpty()) {
                val matchedPSubs = queryPersianaSubs.intersect(chPersianaSubs)
                if (matchedPSubs.isNotEmpty()) {
                    score += 3500
                } else if (nTokens.contains("persiana")) {
                    score -= 4000
                }
            } else if (hasPersiana) {
                if (chPersianaSubs.isNotEmpty()) {
                    score -= 2000
                }
            }

            // Plus vs Non-Plus Disambiguation
            val chHasPlus = nTokens.contains("plus") || sName.contains("+")
            if (hasPlus) {
                if (chHasPlus) score += 2000 else score -= 1500
            } else {
                if (chHasPlus) score -= 300
            }

            // Omid Dana vs Omid
            if (hasDana) {
                if (nTokens.contains("dana")) score += 2000 else score -= 3000
            } else if (nTokens.contains("dana")) {
                score -= 1000
            }

            // Mellig TV
            if (hasMellig) {
                if (nTokens.contains("mellig")) score += 2000 else score -= 3000
            }

            // Sports / Varzesh
            if (hasVarzesh) {
                if (hasRadio) {
                    if (nTokens.contains("radio")) score += 3000 else score -= 3000
                } else {
                    if (nTokens.contains("radio")) score -= 3000
                    if (nTokens.contains("irib") || (nClean.startsWith("varzesh") && nTokens.size <= 2)) {
                        score += 1500
                    }
                }
            }

            // iFilm
            if (hasIfilm) {
                if (!nTokens.contains("ifilm")) {
                    score -= 5000
                } else if (hasTwo) {
                    if (nTokens.contains("2")) score += 3000 else score -= 3000
                } else if (hasDari) {
                    if (nTokens.contains("dari")) score += 3000 else score -= 3000
                } else {
                    if (nTokens.any { it in listOf("2", "dari") }) {
                        score -= 2500
                    } else {
                        score += 2000
                    }
                }
            }

            // Iran Intl
            if (hasIntl) {
                if (nTokens.any { it in listOf("intl", "international") }) score += 1500
                if (nTokens.contains("iran")) score += 500
            }

            // Channel position tie breaker
            score += maxOf(0, 10 - idx / 50)

            if (score > bestScore) {
                bestScore = score
                bestCh = ch
            }
        }

        return Pair(bestCh, bestScore)
    }

    /**
     * Evaluates ALL candidate hypotheses from speech recognition, finding the single channel with the highest score.
     */
    fun findBestMatchAcrossCandidates(candidates: List<String>, channels: List<Channel>): Pair<Channel, String>? {
        if (candidates.isEmpty() || channels.isEmpty()) return null

        var globalBestScore = -10000
        var globalBestChannel: Channel? = null
        var winningCandidate = candidates.first()

        for (candidate in candidates) {
            val (ch, score) = scoreChannelForQuery(candidate, channels)
            if (ch != null && score > globalBestScore) {
                globalBestScore = score
                globalBestChannel = ch
                winningCandidate = candidate
            }
        }

        return if (globalBestChannel != null && globalBestScore > 100) {
            Pair(globalBestChannel, winningCandidate)
        } else {
            null
        }
    }

    /**
     * Legacy single query search.
     */
    fun findBestChannel(query: String, channels: List<Channel>): Channel? {
        val (ch, score) = scoreChannelForQuery(query, channels)
        return if (score > 100) ch else null
    }
}

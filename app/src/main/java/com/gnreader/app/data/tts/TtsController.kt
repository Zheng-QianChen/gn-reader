package com.gnreader.app.data.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 系统默认 TTS。支持语速/语调，以及按字符进度的「真暂停」：
 * - 暂停：记住当前句内已读到的字符位置，恢复从该处继续
 * - 完全停止（退出朗读）：清空队列，下次从本页段首重新开始
 */
class TtsController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var initMessage: String? = null
    @Volatile private var engineLabel: String = "系统 TTS"
    private val initStarted = AtomicBoolean(false)
    private var queue: List<String> = emptyList()
    private var index = 0
    private var focusRequest: AudioFocusRequest? = null
    private var pendingParagraphs: List<String>? = null
    private var pendingStart = 0
    @Volatile private var activelySpeaking = false
    @Volatile private var paused = false

    /** 当前句内绝对字符偏移（相对 queue[index] 全文） */
    @Volatile private var charOffsetInUnit = 0
    /** 本次 speak 的文本是 queue[index].substring(rangeBaseOffset) */
    @Volatile private var rangeBaseOffset = 0
    private var utteranceStartMs = 0L
    private var utteranceSpeakLen = 0

    @Volatile var speechRate: Float = 1.0f
        private set
    @Volatile var pitch: Float = 1.0f
        private set

    var onState: ((speaking: Boolean, index: Int, message: String?) -> Unit)? = null
    var onPaused: (() -> Unit)? = null

    init {
        mainHandler.post { initSystemTts() }
    }

    private fun initSystemTts() {
        initStarted.set(true)
        ready = false
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null

        val listener = TextToSpeech.OnInitListener { status ->
            mainHandler.post {
                if (status != TextToSpeech.SUCCESS) {
                    ready = false
                    initMessage = "系统 TTS 初始化失败（请在系统设置中启用文字转语音）"
                    onState?.invoke(false, 0, initMessage)
                    return@post
                }
                val engine = tts
                if (engine == null) {
                    ready = false
                    initMessage = "系统 TTS 引擎为空"
                    onState?.invoke(false, 0, initMessage)
                    return@post
                }
                try {
                    engineLabel = try {
                        engine.defaultEngine?.takeIf { it.isNotBlank() } ?: "系统默认"
                    } catch (_: Exception) {
                        "系统默认"
                    }
                    tryApplyAudio(engine)
                    val langOk = setBestLanguage(engine)
                    applyVoiceParams(engine)
                    engine.setOnUtteranceProgressListener(progressListener())
                    ready = true
                    initMessage = if (langOk) {
                        "朗读就绪 · $engineLabel"
                    } else {
                        "朗读就绪 · $engineLabel（语言包可能不完整）"
                    }
                    onState?.invoke(false, 0, initMessage)
                    val pending = pendingParagraphs
                    if (pending != null) {
                        pendingParagraphs = null
                        val err = doSpeak(pending, pendingStart, resetOffset = true)
                        if (err != null) onState?.invoke(false, 0, err)
                    }
                } catch (e: Exception) {
                    ready = false
                    initMessage = "系统 TTS 配置失败: ${e.message}"
                    onState?.invoke(false, 0, initMessage)
                }
            }
        }

        tts = try {
            TextToSpeech(appContext, listener)
        } catch (e: Exception) {
            ready = false
            initMessage = "无法创建系统 TTS: ${e.message}"
            onState?.invoke(false, 0, initMessage)
            null
        }
    }

    private fun tryApplyAudio(engine: TextToSpeech) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(attrs)
        } catch (_: Exception) {
        }
    }

    private fun applyVoiceParams(engine: TextToSpeech) {
        try {
            engine.setSpeechRate(speechRate.coerceIn(0.4f, 2.5f))
        } catch (_: Exception) {
        }
        try {
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
        } catch (_: Exception) {
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.4f, 2.5f)
        tts?.let { applyVoiceParams(it) }
    }

    fun setPitch(value: Float) {
        pitch = value.coerceIn(0.5f, 2.0f)
        tts?.let { applyVoiceParams(it) }
    }

    private fun progressListener() = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val fromId = utteranceId?.removePrefix("gn-")?.toIntOrNull()
            mainHandler.post {
                if (fromId != null) index = fromId
                activelySpeaking = true
                paused = false
                utteranceStartMs = SystemClock.uptimeMillis()
                onState?.invoke(true, index, null)
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            // start/end 相对本次 speak 的文本
            charOffsetInUnit = rangeBaseOffset + start.coerceAtLeast(0)
        }

        override fun onDone(utteranceId: String?) {
            mainHandler.post {
                if (paused) return@post
                // 本句读完
                charOffsetInUnit = 0
                rangeBaseOffset = 0
                index += 1
                if (index < queue.size) speakNext()
                else {
                    activelySpeaking = false
                    abandonFocus()
                    onState?.invoke(false, index, null)
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            mainHandler.post {
                activelySpeaking = false
                abandonFocus()
                onState?.invoke(false, index, "朗读出错")
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            mainHandler.post {
                activelySpeaking = false
                abandonFocus()
                onState?.invoke(false, index, "朗读出错($errorCode)")
            }
        }
    }

    private fun setBestLanguage(engine: TextToSpeech): Boolean {
        val candidates = listOf(
            Locale.SIMPLIFIED_CHINESE,
            Locale.CHINA,
            Locale.CHINESE,
            Locale("zh", "CN"),
            Locale("zh"),
            Locale.getDefault(),
            Locale.US,
        )
        for (loc in candidates) {
            try {
                val r = engine.isLanguageAvailable(loc)
                if (r >= TextToSpeech.LANG_AVAILABLE) {
                    val set = engine.setLanguage(loc)
                    if (set >= TextToSpeech.LANG_AVAILABLE) return true
                }
            } catch (_: Exception) {
            }
        }
        try {
            engine.setLanguage(Locale.getDefault())
        } catch (_: Exception) {
        }
        return false
    }

    fun isReady(): Boolean = ready && tts != null

    fun isSpeaking(): Boolean = activelySpeaking && !paused

    fun isPaused(): Boolean = paused && queue.isNotEmpty()

    fun currentIndex(): Int = index

    /** 估算暂停点：优先用 onRangeStart；否则按耗时粗估 */
    private fun capturePauseOffset() {
        val full = queue.getOrNull(index) ?: return
        if (charOffsetInUnit > rangeBaseOffset) {
            charOffsetInUnit = charOffsetInUnit.coerceIn(0, full.length)
            return
        }
        // 无 range 回调时按时间估算（中文约 4~5 字/秒 × 语速）
        val elapsed = (SystemClock.uptimeMillis() - utteranceStartMs).coerceAtLeast(0L)
        val cps = (4.5f * speechRate).coerceIn(2f, 12f)
        val est = (elapsed / 1000f * cps).toInt()
        charOffsetInUnit = (rangeBaseOffset + est).coerceIn(0, full.length)
    }

    fun tryPauseFromTap(): Boolean {
        if (!activelySpeaking || paused) return false
        capturePauseOffset()
        paused = true
        activelySpeaking = false
        stopSpeakingOnly()
        abandonFocus()
        mainHandler.post {
            onState?.invoke(false, index, "已暂停")
            onPaused?.invoke()
        }
        return true
    }

    fun pause() {
        tryPauseFromTap()
    }

    /** 从暂停点继续（句内一半处接着读） */
    fun resume(): String? {
        if (!paused || queue.isEmpty()) return "没有可继续的朗读"
        paused = false
        val engine = tts ?: return "系统 TTS 为空"
        applyVoiceParams(engine)
        requestFocus()

        var full = queue.getOrNull(index) ?: return "队列为空"
        var from = charOffsetInUnit.coerceIn(0, full.length)
        if (from >= full.length) {
            // 暂停时已到句末 → 进下一句
            index += 1
            charOffsetInUnit = 0
            rangeBaseOffset = 0
            if (index >= queue.size) {
                activelySpeaking = false
                abandonFocus()
                onState?.invoke(false, index, null)
                return null
            }
            full = queue[index]
            from = 0
        }
        rangeBaseOffset = from
        val text = full.substring(from)
        utteranceSpeakLen = text.length
        val code = trySpeak(engine, text, "gn-$index")
        if (code == TextToSpeech.ERROR) {
            activelySpeaking = false
            abandonFocus()
            return "继续朗读失败"
        }
        activelySpeaking = true
        onState?.invoke(true, index, "朗读中 · $engineLabel")
        return null
    }

    fun speakParagraphs(paragraphs: List<String>, startAt: Int = 0): String? {
        val chunks = paragraphs
            .asSequence()
            .map { stripHtml(it).trim() }
            .filter { it.isNotEmpty() }
            .flatMap { splitForTts(it, maxLen = 3500) }
            .toList()
        if (chunks.isEmpty()) return "没有可朗读的文本"

        paused = false
        charOffsetInUnit = 0
        rangeBaseOffset = 0
        if (!isReady()) {
            pendingParagraphs = chunks
            pendingStart = startAt
            mainHandler.post { initSystemTts() }
            onState?.invoke(false, 0, "正在连接系统 TTS…")
            return null
        }
        return doSpeak(chunks, startAt, resetOffset = true)
    }

    private fun doSpeak(chunks: List<String>, startAt: Int, resetOffset: Boolean): String? {
        val engine = tts ?: return "系统 TTS 为空"
        stopSpeakingOnly()
        paused = false
        if (resetOffset) {
            charOffsetInUnit = 0
            rangeBaseOffset = 0
        }
        applyVoiceParams(engine)
        requestFocus()
        queue = chunks
        index = startAt.coerceIn(0, chunks.lastIndex)
        rangeBaseOffset = 0
        charOffsetInUnit = 0
        val text = chunks[index]
        utteranceSpeakLen = text.length
        val code = trySpeak(engine, text, "gn-$index")
        if (code == TextToSpeech.ERROR) {
            activelySpeaking = false
            abandonFocus()
            ready = false
            pendingParagraphs = chunks
            pendingStart = index
            mainHandler.post { initSystemTts() }
            return "朗读失败，正在重连系统 TTS…"
        }
        activelySpeaking = true
        onState?.invoke(true, index, "朗读中 · $engineLabel")
        return null
    }

    private fun trySpeak(engine: TextToSpeech, text: String, utteranceId: String): Int {
        return try {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            val r = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (r != TextToSpeech.ERROR) return r
            @Suppress("DEPRECATION")
            engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                hashMapOf(
                    TextToSpeech.Engine.KEY_PARAM_STREAM to AudioManager.STREAM_MUSIC.toString(),
                    TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId,
                ),
            )
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
    }

    fun speakText(text: String): String? = speakParagraphs(listOf(text))

    private fun speakNext() {
        val engine = tts ?: return
        applyVoiceParams(engine)
        rangeBaseOffset = 0
        charOffsetInUnit = 0
        val text = queue.getOrNull(index) ?: return
        utteranceSpeakLen = text.length
        if (trySpeak(engine, text, "gn-$index") == TextToSpeech.ERROR) {
            abandonFocus()
            onState?.invoke(false, index, "speak 调用失败")
        }
    }

    private fun splitForTts(text: String, maxLen: Int = 1800): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            var end = (i + maxLen).coerceAtMost(text.length)
            if (end < text.length) {
                val slice = text.substring(i, end)
                val cut = slice.indexOfLast { it == '。' || it == '！' || it == '？' || it == '\n' || it == '.' || it == '；' }
                if (cut > maxLen / 4) end = i + cut + 1
            }
            val part = text.substring(i, end).trim()
            if (part.isNotEmpty()) out += part
            i = end
        }
        return out
    }

    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")

    private fun requestFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = req
                audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun abandonFocus() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
        focusRequest = null
    }

    private fun stopSpeakingOnly() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    /** 完全退出朗读：清空进度，下次从本页段首开始 */
    fun stop() {
        pendingParagraphs = null
        paused = false
        activelySpeaking = false
        charOffsetInUnit = 0
        rangeBaseOffset = 0
        stopSpeakingOnly()
        abandonFocus()
        queue = emptyList()
        index = 0
        onState?.invoke(false, 0, null)
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
        initStarted.set(false)
    }

    companion object {
        fun openTtsSettings(context: Context) {
            val intents = listOf(
                Intent("com.android.settings.TTS_SETTINGS"),
                Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA),
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
            )
            for (intent in intents) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {
                }
            }
        }

        fun openInstallGoogleTts(context: Context) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("market://details?id=com.google.android.tts")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(
                            "https://play.google.com/store/apps/details?id=com.google.android.tts",
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
    }
}

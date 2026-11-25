package com.example.kidgamekz

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

// ==================== ЯЗЫК И ЭКРАНЫ ====================

enum class AppLang { RU, KK }

enum class GameScreen {
    MENU, ANIMALS, COLORS, SHAPES, COUNTING, NATIONAL
}

data class BiText(val ru: String, val kk: String) {
    fun get(lang: AppLang) = if (lang == AppLang.RU) ru else kk
}

// ==================== СИНТЕЗАТОР ЗВУКОВ ====================

object SoundFX {
    private fun playTone(freqHz: Double, durationMs: Int, decay: Double = 3.0, harmonic: Double = 0.3) {
        Thread {
            try {
                val sampleRate = 22050
                val count = (durationMs * sampleRate) / 1000
                val samples = ShortArray(count)
                for (i in 0 until count) {
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-decay * t)
                    val wave = sin(2.0 * PI * freqHz * t) +
                            harmonic * sin(2.0 * PI * (freqHz * 2.0) * t)
                    val normalized = (wave / (1.0 + harmonic)) * envelope
                    samples[i] = (normalized * Short.MAX_VALUE * 0.7).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.write(samples, 0, samples.size)
                    track.play()
                    Thread.sleep(durationMs.toLong() + 40L)
                    track.stop()
                }
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }

    fun playSuccess() {
        Thread {
            playTone(523.25, 130, 4.0) // До
            Thread.sleep(110)
            playTone(659.25, 130, 4.0) // Ми
            Thread.sleep(110)
            playTone(783.99, 240, 3.5) // Соль
        }.start()
    }

    fun playGentleError() {
        playTone(220.0, 220, 5.0, 0.1)
    }

    fun playPop() {
        playTone(620.0, 90, 10.0, 0.15)
    }

    fun playDombraNote(freq: Double) {
        playTone(freq, 550, decay = 4.5, harmonic = 0.55)
    }
}

// ==================== ОЗВУЧКА (TTS) ====================

class VoiceHelper(context: android.content.Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = (status == TextToSpeech.SUCCESS)
        }
    }

    fun speak(text: String, lang: AppLang) {
        if (!ready) return
        val targetLocale = if (lang == AppLang.RU) {
            Locale.forLanguageTag("ru-RU")
        } else {
            Locale.forLanguageTag("kk-KZ")
        }
        val res = tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (res >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = targetLocale
        } else {
            // Если казахский пакет не установлен на телефоне, fallback на русский движок
            tts?.language = Locale.forLanguageTag("ru-RU")
        }
        tts?.setSpeechRate(0.9f)
        tts?.setPitch(1.08f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

// ==================== ГЛАВНАЯ ACTIVITY ====================

class MainActivity : ComponentActivity() {
    private lateinit var voice: VoiceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceHelper(this)
        setContent {
            MaterialTheme {
                KidGameApp(voice = voice)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.shutdown()
    }
}

// ==================== КОРЕНЬ ПРИЛОЖЕНИЯ ====================

@Composable
fun KidGameApp(voice: VoiceHelper) {
    var lang by remember { mutableStateOf(AppLang.KK) }
    var screen by remember { mutableStateOf(GameScreen.MENU) }

    val bgColor = Color(0xFFFFF8E7)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            TopGameBar(
                screen = screen,
                lang = lang,
                onHome = {
                    SoundFX.playPop()
                    screen = GameScreen.MENU
                },
                onToggleLang = {
                    lang = if (lang == AppLang.RU) AppLang.KK else AppLang.RU
                    SoundFX.playSuccess()
                    val msg = if (lang == AppLang.RU) "Русский язык" else "Қазақ тілі"
                    voice.speak(msg, lang)
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (screen) {
                    GameScreen.MENU -> MainMenuScreen(lang, voice) { chosen ->
                        SoundFX.playPop()
                        screen = chosen
                    }
                    GameScreen.ANIMALS -> AnimalsGameScreen(lang, voice)
                    GameScreen.COLORS -> ColorsGameScreen(lang, voice)
                    GameScreen.SHAPES -> ShapesGameScreen(lang, voice)
                    GameScreen.COUNTING -> CountingGameScreen(lang, voice)
                    GameScreen.NATIONAL -> NationalGameScreen(lang, voice)
                }
            }
        }
    }
}

@Composable
fun TopGameBar(
    screen: GameScreen,
    lang: AppLang,
    onHome: () -> Unit,
    onToggleLang: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (screen != GameScreen.MENU) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFFFF7043))
                    .clickable { onHome() },
                contentAlignment = Alignment.Center
            ) {
                Text("🏠", fontSize = 28.sp)
            }
        } else {
            Text(
                text = BiText("Учись и играй", "Ойна да үйрен").get(lang),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4A3728)
            )
        }

        // Кнопка смены языка с защитой от случайного нажатия (удержание)
        Box(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(2.dp, Color(0xFF4FC3F7), RoundedCornerShape(24.dp))
                .pointerInput(lang) {
                    detectTapGestures(
                        onTap = {
                            val hint = if (lang == AppLang.RU)
                                "Удерживайте кнопку для смены языка"
                            else
                                "Тілді ауыстыру үшін басып тұрыңыз"
                            Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
                        },
                        onLongPress = { onToggleLang() }
                    )
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = if (lang == AppLang.RU) "🇷🇺 РУС" else "🇰🇿 ҚАЗ",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0277BD)
            )
        }
    }
}

// ==================== ГЛАВНОЕ МЕНЮ ====================

data class MenuItem(
    val screen: GameScreen,
    val emoji: String,
    val title: BiText,
    val color: Color
)

@Composable
fun MainMenuScreen(
    lang: AppLang,
    voice: VoiceHelper,
    onSelect: (GameScreen) -> Unit
) {
    val items = remember {
        listOf(
            MenuItem(GameScreen.ANIMALS, "🐄", BiText("Животные", "Жануарлар"), Color(0xFFA5D6A7)),
            MenuItem(GameScreen.COLORS, "🎨", BiText("Цвета", "Түстер"), Color(0xFF81D4FA)),
            MenuItem(GameScreen.SHAPES, "⭐", BiText("Фигуры", "Пішіндер"), Color(0xFFFFCC80)),
            MenuItem(GameScreen.COUNTING, "🍎", BiText("Счёт 1–5", "Санау 1–5"), Color(0xFFF48FB1)),
            MenuItem(GameScreen.NATIONAL, "⛺", BiText("Юрта и Домбра", "Киіз үй мен Домбыра"), Color(0xFFCE93D8))
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = BiText(
                "Выбери игру! (Язык: удерживай кнопку сверху)",
                "Ойынды таңда! (Тіл: жоғарыдағы түймені басып тұр)"
            ).get(lang),
            fontSize = 13.sp,
            color = Color(0xFF795548),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .shadow(6.dp, RoundedCornerShape(28.dp))
                        .clickable {
                            voice.speak(item.title.get(lang), lang)
                            onSelect(item.screen)
                        },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = item.color)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(item.emoji, fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.title.get(lang),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2E2E2E),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ==================== ИГРА 1: ЖИВОТНЫЕ ====================

data class AnimalItem(
    val id: String,
    val emoji: String,
    val name: BiText,
    val sound: BiText,
    val question: BiText,
    val bg: Color
)

@Composable
fun AnimalsGameScreen(lang: AppLang, voice: VoiceHelper) {
    val animals = remember {
        listOf(
            AnimalItem("cat", "🐱", BiText("Кошка", "Мысық"), BiText("Мяу-мяу!", "Мияу-мияу!"), BiText("Где кошка?", "Мысық қайда?"), Color(0xFFFFE0B2)),
            AnimalItem("dog", "🐶", BiText("Собака", "Ит"), BiText("Гав-гав!", "Аф-аф!"), BiText("Где собака?", "Ит қайда?"), Color(0xFFD7CCC8)),
            AnimalItem("cow", "🐄", BiText("Корова", "Сиыр"), BiText("Му-у!", "Мө-ө!"), BiText("Где корова?", "Сиыр қайда?"), Color(0xFFC8E6C9)),
            AnimalItem("horse", "🐴", BiText("Лошадь", "Жылқы"), BiText("И-го-го!", "И-го-го!"), BiText("Где лошадь?", "Жылқы қайда?"), Color(0xFFBBDEFB)),
            AnimalItem("sheep", "🐑", BiText("Овечка", "Қой"), BiText("Бе-е!", "Мә-ә!"), BiText("Где овечка?", "Қой қайда?"), Color(0xFFF8BBD0))
        )
    }

    var quizMode by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(animals.first()) }
    var bouncedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    quizMode = !quizMode
                    if (quizMode) {
                        target = animals.random()
                        voice.speak(target.question.get(lang), lang)
                    } else {
                        voice.speak(BiText("Нажми на животное", "Жануарды бас").get(lang), lang)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (quizMode) Color(0xFF66BB6A) else Color(0xFFFFA726)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.height(54.dp)
            ) {
                Text(
                    text = if (quizMode)
                        "❓ ${target.question.get(lang)}"
                    else
                        BiText("🎯 Режим: Найди животное!", "🎯 Ойын: Жануарды тап!").get(lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(animals) { animal ->
                val scale by animateFloatAsState(
                    targetValue = if (bouncedId == animal.id) 1.12f else 1f,
                    label = "animalScale"
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .scale(scale)
                        .shadow(6.dp, RoundedCornerShape(24.dp))
                        .clickable {
                            bouncedId = animal.id
                            scope.launch {
                                delay(300)
                                bouncedId = null
                            }
                            if (!quizMode) {
                                SoundFX.playPop()
                                voice.speak("${animal.name.get(lang)}. ${animal.sound.get(lang)}", lang)
                            } else {
                                if (animal.id == target.id) {
                                    SoundFX.playSuccess()
                                    val praise = BiText("Молодец! Это ${animal.name.ru}", "Жарайсың! Бұл ${animal.name.kk}").get(lang)
                                    voice.speak(praise, lang)
                                    scope.launch {
                                        delay(1400)
                                        target = animals.filter { it.id != target.id }.random()
                                        voice.speak(target.question.get(lang), lang)
                                    }
                                } else {
                                    SoundFX.playGentleError()
                                    voice.speak(BiText("Попробуй ещё", "Тағы байқап көр").get(lang), lang)
                                }
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = animal.bg)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(animal.emoji, fontSize = 62.sp)
                        Text(
                            text = animal.name.get(lang),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                        if (!quizMode) {
                            Text(
                                text = animal.sound.get(lang),
                                fontSize = 14.sp,
                                color = Color(0xFF555555)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== ИГРА 2: ЦВЕТА (DRAG & TAP) ====================

data class ColorBucket(
    val id: String,
    val color: Color,
    val name: BiText
)

data class ColorObject(
    val uid: Int,
    val colorId: String,
    val emoji: String
)

@Composable
fun ColorsGameScreen(lang: AppLang, voice: VoiceHelper) {
    val buckets = remember {
        listOf(
            ColorBucket("red", Color(0xFFEF5350), BiText("Красный", "Қызыл")),
            ColorBucket("blue", Color(0xFF42A5F5), BiText("Синий", "Көк")),
            ColorBucket("yellow", Color(0xFFFFCA28), BiText("Жёлтый", "Сары")),
            ColorBucket("green", Color(0xFF66BB6A), BiText("Зелёный", "Жасыл"))
        )
    }

    val allObjects = remember {
        listOf(
            ColorObject(1, "red", "🍎"),
            ColorObject(2, "blue", "🐳"),
            ColorObject(3, "yellow", "🍋"),
            ColorObject(4, "green", "🐸"),
            ColorObject(5, "red", "🍓"),
            ColorObject(6, "blue", "🚙"),
            ColorObject(7, "yellow", "☀️"),
            ColorObject(8, "green", "🐢")
        )
    }

    var roundSeed by remember { mutableIntStateOf(0) }
    val currentObjects = remember(roundSeed) {
        buckets.map { b -> allObjects.filter { it.colorId == b.id }.random() }.shuffled()
    }
    val matchedIds = remember(roundSeed) { mutableStateListOf<Int>() }
    var selectedObj by remember(roundSeed) { mutableStateOf<ColorObject?>(null) }
    val bucketRects = remember { mutableStateMapOf<String, Rect>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(roundSeed) {
        voice.speak(BiText("Разложи по цветам", "Түстер бойынша жина").get(lang), lang)
    }

    fun handleMatch(obj: ColorObject, bucketId: String) {
        if (obj.colorId == bucketId) {
            if (!matchedIds.contains(obj.uid)) {
                matchedIds.add(obj.uid)
                selectedObj = null
                SoundFX.playSuccess()
                val bName = buckets.first { it.id == bucketId }.name.get(lang)
                if (matchedIds.size == currentObjects.size) {
                    voice.speak(BiText("Молодец! Все цвета собраны!", "Жарайсың! Барлық түс жиналды!").get(lang), lang)
                    scope.launch {
                        delay(1800)
                        roundSeed++
                    }
                } else {
                    voice.speak(bName, lang)
                }
            }
        } else {
            SoundFX.playGentleError()
            voice.speak(BiText("Попробуй другую корзинку", "Басқа себетті көр").get(lang), lang)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = BiText("Перетащи или нажми на цвет!", "Сүйре немесе түсті таңда!").get(lang),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A3728),
            textAlign = TextAlign.Center
        )

        // Корзинки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            buckets.forEach { bucket ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .onGloballyPositioned { bucketRects[bucket.id] = it.boundsInRoot() }
                        .shadow(5.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(bucket.color)
                        .clickable {
                            val sel = selectedObj
                            if (sel != null) {
                                handleMatch(sel, bucket.id)
                            } else {
                                voice.speak(bucket.name.get(lang), lang)
                            }
                        }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧺", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bucket.name.get(lang),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Предметы для сортировки
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            currentObjects.forEach { obj ->
                val isMatched = matchedIds.contains(obj.uid)
                var slotCenter by remember(roundSeed, obj.uid) { mutableStateOf(Offset.Zero) }
                var dragOffset by remember(roundSeed, obj.uid) { mutableStateOf(Offset.Zero) }
                val latestSlotCenter by rememberUpdatedState(slotCenter)

                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .onGloballyPositioned { slotCenter = it.boundsInRoot().center },
                    contentAlignment = Alignment.Center
                ) {
                    if (!isMatched) {
                        val isSelected = selectedObj?.uid == obj.uid
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                                .size(78.dp)
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFFFFF59D) else Color.White)
                                .border(
                                    width = if (isSelected) 4.dp else 2.dp,
                                    color = if (isSelected) Color(0xFFFF9800) else Color(0xFFE0E0E0),
                                    shape = CircleShape
                                )
                                .clickable {
                                    SoundFX.playPop()
                                    selectedObj = obj
                                }
                                .pointerInput(roundSeed, obj.uid) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                        },
                                        onDragEnd = {
                                            val dropPoint = latestSlotCenter + dragOffset
                                            val hit = bucketRects.entries.firstOrNull {
                                                it.value.inflate(35f).contains(dropPoint)
                                            }
                                            if (hit != null) {
                                                handleMatch(obj, hit.key)
                                            }
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = { dragOffset = Offset.Zero }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(obj.emoji, fontSize = 42.sp)
                        }
                    } else {
                        Text("✅", fontSize = 32.sp)
                    }
                }
            }
        }
    }
}

// ==================== ИГРА 3: ФИГУРЫ И ТЕНИ ====================

enum class ShapeType { CIRCLE, SQUARE, TRIANGLE, STAR }

data class ShapeItem(
    val type: ShapeType,
    val color: Color,
    val name: BiText
)

@Composable
fun DrawShapeCanvas(
    type: ShapeType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = w * 0.12f
        when (type) {
            ShapeType.CIRCLE -> {
                drawCircle(color = color, radius = (w - pad * 2) / 2f)
            }
            ShapeType.SQUARE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad, pad),
                    size = Size(w - pad * 2, h - pad * 2),
                    cornerRadius = CornerRadius(18f, 18f)
                )
            }
            ShapeType.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(w / 2f, pad)
                    lineTo(w - pad, h - pad)
                    lineTo(pad, h - pad)
                    close()
                }
                drawPath(path, color = color)
            }
            ShapeType.STAR -> {
                val path = Path()
                val cx = w / 2f
                val cy = h / 2f
                val outerR = (w - pad * 2) / 2f
                val innerR = outerR * 0.45f
                for (i in 0 until 10) {
                    val angle = (i * 36.0 - 90.0) * PI / 180.0
                    val r = if (i % 2 == 0) outerR else innerR
                    val x = cx + (r * cos(angle)).toFloat()
                    val y = cy + (r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = color)
            }
        }
    }
}

@Composable
fun ShapesGameScreen(lang: AppLang, voice: VoiceHelper) {
    val shapes = remember {
        listOf(
            ShapeItem(ShapeType.CIRCLE, Color(0xFFEF5350), BiText("Круг", "Шеңбер")),
            ShapeItem(ShapeType.SQUARE, Color(0xFF42A5F5), BiText("Квадрат", "Шаршы")),
            ShapeItem(ShapeType.TRIANGLE, Color(0xFF66BB6A), BiText("Треугольник", "Үшбұрыш")),
            ShapeItem(ShapeType.STAR, Color(0xFFFFB300), BiText("Звезда", "Жұлдыз"))
        )
    }

    var roundSeed by remember { mutableIntStateOf(0) }
    val bottomShapes = remember(roundSeed) { shapes.shuffled() }
    val matched = remember(roundSeed) { mutableStateListOf<ShapeType>() }
    var selectedShape by remember(roundSeed) { mutableStateOf<ShapeItem?>(null) }
    val targetRects = remember { mutableStateMapOf<ShapeType, Rect>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(roundSeed) {
        voice.speak(BiText("Найди тень для фигуры", "Пішіннің көлеңкесін тап").get(lang), lang)
    }

    fun tryMatch(item: ShapeItem, targetType: ShapeType) {
        if (item.type == targetType) {
            if (!matched.contains(targetType)) {
                matched.add(targetType)
                selectedShape = null
                SoundFX.playSuccess()
                if (matched.size == shapes.size) {
                    voice.speak(BiText("Умница! Все фигуры на месте!", "Жарайсың! Барлық пішін табылды!").get(lang), lang)
                    scope.launch {
                        delay(1800)
                        roundSeed++
                    }
                } else {
                    voice.speak(item.name.get(lang), lang)
                }
            }
        } else {
            SoundFX.playGentleError()
            voice.speak(BiText("Попробуй ещё", "Тағы байқап көр").get(lang), lang)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = BiText("Подбери тень!", "Көлеңкесін тап!").get(lang),
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF4A3728)
        )

        // Тени
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            shapes.forEach { s ->
                val isDone = matched.contains(s.type)
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .onGloballyPositioned { targetRects[s.type] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFEEEEEE))
                        .clickable {
                            val sel = selectedShape
                            if (sel != null) {
                                tryMatch(sel, s.type)
                            } else {
                                voice.speak(s.name.get(lang), lang)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    DrawShapeCanvas(
                        type = s.type,
                        color = if (isDone) s.color else Color(0xFF424242).copy(alpha = 0.25f),
                        modifier = Modifier.size(68.dp)
                    )
                }
            }
        }

        // Цветные фигуры снизу
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bottomShapes.forEach { item ->
                val isDone = matched.contains(item.type)
                var slotCenter by remember(roundSeed, item.type) { mutableStateOf(Offset.Zero) }
                var dragOffset by remember(roundSeed, item.type) { mutableStateOf(Offset.Zero) }
                val latestCenter by rememberUpdatedState(slotCenter)

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .onGloballyPositioned { slotCenter = it.boundsInRoot().center },
                    contentAlignment = Alignment.Center
                ) {
                    if (!isDone) {
                        val isSelected = selectedShape?.type == item.type
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                                .size(80.dp)
                                .shadow(5.dp, RoundedCornerShape(18.dp))
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) Color(0xFFFFF9C4) else Color.White)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFFF9800) else Color(0xFFE0E0E0),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    SoundFX.playPop()
                                    selectedShape = item
                                    voice.speak(item.name.get(lang), lang)
                                }
                                .pointerInput(roundSeed, item.type) {
                                    detectDragGestures(
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                        },
                                        onDragEnd = {
                                            val drop = latestCenter + dragOffset
                                            val hit = targetRects.entries.firstOrNull {
                                                it.value.inflate(35f).contains(drop)
                                            }
                                            if (hit != null) {
                                                tryMatch(item, hit.key)
                                            }
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = { dragOffset = Offset.Zero }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            DrawShapeCanvas(
                                type = item.type,
                                color = item.color,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    } else {
                        Text("⭐", fontSize = 36.sp)
                    }
                }
            }
        }
    }
}

// ==================== ИГРА 4: СЧИТАЕМ ДО 5 ====================

@Composable
fun CountingGameScreen(lang: AppLang, voice: VoiceHelper) {
    val numbers = remember {
        listOf(
            BiText("Один", "Бір"),
            BiText("Два", "Екі"),
            BiText("Три", "Үш"),
            BiText("Четыре", "Төрт"),
            BiText("Пять", "Бес")
        )
    }

    var targetCount by remember { mutableIntStateOf(3) }
    var useBaursak by remember { mutableStateOf(true) }
    var placedCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun getTaskText(): String {
        val emojiName = if (useBaursak) {
            BiText(
                when (targetCount) {
                    1 -> "1 баурсак"
                    2, 3, 4 -> "$targetCount баурсака"
                    else -> "5 баурсаков"
                },
                "$targetCount бауырсақ"
            )
        } else {
            BiText(
                when (targetCount) {
                    1 -> "1 яблоко"
                    2, 3, 4 -> "$targetCount яблока"
                    else -> "5 яблок"
                },
                "$targetCount алма"
            )
        }
        return BiText(
            "Положи на тарелку: ${emojiName.ru}",
            "Тәрелкеге ${emojiName.kk} қой"
        ).get(lang)
    }

    LaunchedEffect(targetCount, useBaursak, lang) {
        voice.speak(getTaskText(), lang)
    }

    val foodEmoji = if (useBaursak) "🥟" else "🍎"

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECB3)),
            modifier = Modifier.clickable { voice.speak(getTaskText(), lang) }
        ) {
            Text(
                text = "🔊 ${getTaskText()}",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF4E342E),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        // Тарелка
        Box(
            modifier = Modifier
                .size(240.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(10.dp, Color(0xFF81D4FA), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (placedCount == 0) {
                Text(
                    text = BiText("Нажимай снизу!", "Төменнен бас!").get(lang),
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(minOf(placedCount, 3)) {
                            Text(foodEmoji, fontSize = 46.sp)
                        }
                    }
                    if (placedCount > 3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(placedCount - 3) {
                                Text(foodEmoji, fontSize = 46.sp)
                            }
                        }
                    }
                }
            }
        }

        // Продукты внизу (5 штук)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(5) { index ->
                val isUsed = index < placedCount
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(if (isUsed) 0.dp else 5.dp, CircleShape)
                        .clip(CircleShape)
                        .background(if (isUsed) Color(0xFFEEEEEE) else Color(0xFFFFF9C4))
                        .clickable(enabled = !isUsed && placedCount < targetCount) {
                            placedCount++
                            SoundFX.playPop()
                            voice.speak(numbers[placedCount - 1].get(lang), lang)
                            if (placedCount == targetCount) {
                                scope.launch {
                                    delay(900)
                                    SoundFX.playSuccess()
                                    voice.speak(BiText("Молодец!", "Жарайсың!").get(lang), lang)
                                    delay(1400)
                                    placedCount = 0
                                    targetCount = ((1..5).filter { it != targetCount }).random()
                                    useBaursak = !useBaursak
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!isUsed) {
                        Text(foodEmoji, fontSize = 36.sp)
                    }
                }
            }
        }
    }
}

// ==================== ИГРА 5: ЮРТА И ДОМБРА ====================

@Composable
fun NationalGameScreen(lang: AppLang, voice: VoiceHelper) {
    var tab by remember { mutableIntStateOf(0) } // 0 = Юрта, 1 = Домбра

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    tab = 0
                    SoundFX.playPop()
                    voice.speak(BiText("Собери юрту", "Киіз үйді жина").get(lang), lang)
                },
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tab == 0) Color(0xFF8E24AA) else Color(0xFFCE93D8)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("⛺ ${BiText("Юрта", "Киіз үй").get(lang)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Button(
                onClick = {
                    tab = 1
                    SoundFX.playPop()
                    voice.speak(BiText("Поиграй на домбре", "Домбыра тарт").get(lang), lang)
                },
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tab == 1) Color(0xFF8E24AA) else Color(0xFFCE93D8)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("🎵 ${BiText("Домбра", "Домбыра").get(lang)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tab == 0) {
            YurtBuilderGame(lang, voice)
        } else {
            DombraPlayGame(lang)
        }
    }
}

@Composable
fun YurtBuilderGame(lang: AppLang, voice: VoiceHelper) {
    val parts = remember {
        listOf(
            BiText("Кереге (стены)", "Кереге"),
            BiText("Есік (дверь)", "Есік"),
            BiText("Уық (купол)", "Уық"),
            BiText("Шаңырақ", "Шаңырақ")
        )
    }
    var step by remember { mutableIntStateOf(0) } // 0..4
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (step < 4)
                BiText("Нажми, чтобы построить: ${parts[step].ru}", "Құрастыру үшін бас: ${parts[step].kk}").get(lang)
            else
                BiText("Юрта готова! Красота!", "Киіз үй дайын! Тамаша!").get(lang),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A3728),
            textAlign = TextAlign.Center
        )

        // Холст юрты
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFB3E5FC))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Зелёная лужайка
                drawRect(
                    color = Color(0xFF81C784),
                    topLeft = Offset(0f, h * 0.72f),
                    size = Size(w, h * 0.28f)
                )

                // Контур подсказка
                val cx = w / 2f
                val baseBottom = h * 0.80f
                val baseTop = h * 0.48f
                val halfW = w * 0.28f

                // 1. Кереге (стены)
                if (step >= 1) {
                    drawRect(
                        color = Color(0xFFFFF8E1),
                        topLeft = Offset(cx - halfW, baseTop),
                        size = Size(halfW * 2, baseBottom - baseTop)
                    )
                    // Орнамент-сетка кереге
                    drawRect(
                        color = Color(0xFFD84315),
                        topLeft = Offset(cx - halfW, baseTop),
                        size = Size(halfW * 2, 16f)
                    )
                } else {
                    drawRect(
                        color = Color.White.copy(alpha = 0.4f),
                        topLeft = Offset(cx - halfW, baseTop),
                        size = Size(halfW * 2, baseBottom - baseTop),
                        style = Stroke(width = 4f)
                    )
                }

                // 2. Есік (дверь)
                if (step >= 2) {
                    drawRoundRect(
                        color = Color(0xFF8D6E63),
                        topLeft = Offset(cx - halfW * 0.25f, baseTop + 20f),
                        size = Size(halfW * 0.5f, baseBottom - baseTop - 20f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }

                // 3. Уық (купол)
                if (step >= 3) {
                    val domePath = Path().apply {
                        moveTo(cx - halfW - 10f, baseTop)
                        lineTo(cx - halfW * 0.35f, h * 0.22f)
                        lineTo(cx + halfW * 0.35f, h * 0.22f)
                        lineTo(cx + halfW + 10f, baseTop)
                        close()
                    }
                    drawPath(domePath, color = Color(0xFFFFECB3))
                    drawPath(domePath, color = Color(0xFFD84315), style = Stroke(width = 6f))
                }

                // 4. Шаңырақ
                if (step >= 4) {
                    val shCenter = Offset(cx, h * 0.22f)
                    val shR = halfW * 0.35f
                    drawCircle(color = Color(0xFF795548), radius = shR, center = shCenter)
                    drawCircle(color = Color(0xFFFFD54F), radius = shR * 0.72f, center = shCenter)
                    drawLine(
                        color = Color(0xFF795548),
                        start = Offset(shCenter.x - shR, shCenter.y),
                        end = Offset(shCenter.x + shR, shCenter.y),
                        strokeWidth = 6f
                    )
                    drawLine(
                        color = Color(0xFF795548),
                        start = Offset(shCenter.x, shCenter.y - shR),
                        end = Offset(shCenter.x, shCenter.y + shR),
                        strokeWidth = 6f
                    )
                }
            }
        }

        // Кнопки деталей снизу
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            parts.forEachIndexed { idx, label ->
                val isNext = (idx == step)
                val isPlaced = (idx < step)
                Button(
                    onClick = {
                        if (isNext) {
                            step++
                            SoundFX.playPop()
                            voice.speak(label.get(lang), lang)
                            if (step == 4) {
                                scope.launch {
                                    delay(900)
                                    SoundFX.playSuccess()
                                    voice.speak(BiText("Молодец! Юрта готова!", "Жарайсың! Киіз үй дайын!").get(lang), lang)
                                }
                            }
                        } else if (!isPlaced) {
                            SoundFX.playGentleError()
                            voice.speak(BiText("Сначала: ${parts[step].ru}", "Алдымен: ${parts[step].kk}").get(lang), lang)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isPlaced -> Color(0xFFA5D6A7)
                            isNext -> Color(0xFFFF9800)
                            else -> Color(0xFFE0E0E0)
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
                ) {
                    Text(
                        text = if (isPlaced) "✅" else "${idx + 1}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        if (step == 4) {
            Button(
                onClick = { step = 0 },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(BiText("🔄 Собрать ещё раз", "🔄 Қайта жинау").get(lang), fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun DombraPlayGame(lang: AppLang) {
    // 5 нот (пентатоника) для лёгкого приятного звучания при любом нажатии
    val notesTop = remember { listOf(293.66, 329.63, 392.00, 440.00, 523.25) }
    val notesBottom = remember { listOf(196.00, 220.00, 261.63, 293.66, 329.63) }
    var activeString by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = BiText("Нажимай на струны домбры!", "Домбыраның ішектерін бас!").get(lang),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4A3728)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFFF3E0))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val zone = ((offset.x / size.width) * 5).toInt().coerceIn(0, 4)
                        val isTop = offset.y < size.height / 2f
                        activeString = if (isTop) 1 else 2
                        val freq = if (isTop) notesTop[zone] else notesBottom[zone]
                        SoundFX.playDombraNote(freq)
                        scope.launch {
                            delay(180)
                            activeString = 0
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cy = h / 2f

                // Корпус домбры (справа)
                drawOval(
                    color = Color(0xFF8D6E63),
                    topLeft = Offset(w * 0.48f, h * 0.14f),
                    size = Size(w * 0.48f, h * 0.72f)
                )
                drawOval(
                    color = Color(0xFFA1887F),
                    topLeft = Offset(w * 0.51f, h * 0.18f),
                    size = Size(w * 0.42f, h * 0.64f)
                )
                // Отверстие
                drawCircle(
                    color = Color(0xFF3E2723),
                    radius = h * 0.08f,
                    center = Offset(w * 0.68f, cy)
                )

                // Гриф (слева)
                drawRoundRect(
                    color = Color(0xFF6D4C41),
                    topLeft = Offset(w * 0.04f, cy - h * 0.08f),
                    size = Size(w * 0.52f, h * 0.16f),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                // Лады на грифе
                for (i in 1..6) {
                    val x = w * 0.08f + i * (w * 0.07f)
                    drawLine(
                        color = Color(0xFFFFE082),
                        start = Offset(x, cy - h * 0.08f),
                        end = Offset(x, cy + h * 0.08f),
                        strokeWidth = 3f
                    )
                }

                // Две струны
                drawLine(
                    color = if (activeString == 1) Color(0xFFFFD54F) else Color.White,
                    start = Offset(w * 0.05f, cy - 14f),
                    end = Offset(w * 0.88f, cy - 14f),
                    strokeWidth = if (activeString == 1) 8f else 4f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = if (activeString == 2) Color(0xFFFFD54F) else Color.White,
                    start = Offset(w * 0.05f, cy + 14f),
                    end = Offset(w * 0.88f, cy + 14f),
                    strokeWidth = if (activeString == 2) 8f else 4f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Дополнительные крупные цветные ноты-клавиши для самых маленьких пальчиков
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val btnColors = listOf(
                Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFFFFEE58),
                Color(0xFF66BB6A), Color(0xFF42A5F5)
            )
            notesTop.forEachIndexed { idx, freq ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(btnColors[idx])
                        .clickable { SoundFX.playDombraNote(freq) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎶", fontSize = 22.sp)
                }
            }
        }
    }
}

package com.fenyacoding.music

import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import android.webkit.WebSettings
import android.widget.Toast
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect

private val Ink = Color(0xFF090611)
private val Panel = Color(0xFF171123)
private val Panel2 = Color(0xFF241934)
private val Purple = Color(0xFFB98AFF)
private val Pink = Color(0xFFE5A6FF)
private val Muted = Color(0xFFAA9BBE)

class MainActivity : ComponentActivity() {
    private lateinit var mediaSession: MediaSession
    var sessionPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaSession = MediaSession(this, "FenyaCoding").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { sessionPlayer?.let { try { if (!it.isPlaying) it.start() } catch (_: Exception) {} } }
                override fun onPause() { sessionPlayer?.let { try { if (it.isPlaying) it.pause() } catch (_: Exception) {} } }
                override fun onSeekTo(pos: Long) { sessionPlayer?.let { try { it.seekTo(pos.toInt().coerceIn(0, it.duration)) } catch (_: Exception) {} } }
                override fun onSkipToNext() { sessionPlayer?.let { try { it.seekTo((it.currentPosition + 15000).coerceAtMost(it.duration)) } catch (_: Exception) {} } }
                override fun onSkipToPrevious() { sessionPlayer?.let { try { it.seekTo((it.currentPosition - 15000).coerceAtLeast(0)) } catch (_: Exception) {} } }
            })
            isActive = true
        }
        setContent { FenyaApp() }
    }

    fun updateMediaSession(title: String, artist: String, playing: Boolean, duration: Int, position: Int, art: Bitmap?) {
        if (!::mediaSession.isInitialized) return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())
        if (art != null) metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, art)
        mediaSession.setMetadata(metadata.build())
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        mediaSession.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, position.toLong(), 1f).build())
    }

    override fun onDestroy() {
        if (::mediaSession.isInitialized) { mediaSession.isActive = false; mediaSession.release() }
        super.onDestroy()
    }
}

private enum class Tab(val title: String) {
    Home("Главная"), SoundCloud("SoundCloud"), Library("Библиотека"), Settings("Настройки")
}

@Composable
private fun FenyaApp() {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1900)
        showSplash = false
    }
    var tab by remember { mutableStateOf(Tab.Home) }
    var localUri by remember { mutableStateOf<Uri?>(null) }
    var localName by remember { mutableStateOf("Локальный трек не выбран") }
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackPosition by remember { mutableIntStateOf(0) }
    var playbackDuration by remember { mutableIntStateOf(0) }
    var trackArtist by remember { mutableStateOf("Неизвестный исполнитель") }
    var trackArtwork by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(player, isPlaying) {
        val active = player ?: return@LaunchedEffect
        playbackDuration = try { active.duration } catch (_: Exception) { 0 }
        while (isPlaying) {
            playbackPosition = try { active.currentPosition } catch (_: Exception) { playbackPosition }
            kotlinx.coroutines.delay(400)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            try {
                player?.release()
                player = MediaPlayer.create(context, uri)
                if (player == null) throw IllegalStateException("Не удалось открыть аудио")
                localUri = uri
                localName = uri.lastPathSegment?.substringAfterLast('/') ?: "Выбранный аудиофайл"
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    localName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: localName
                    trackArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?: "Неизвестный исполнитель"
                    trackArtwork = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    retriever.release()
                } catch (_: Exception) { trackArtist = "Локальный аудиофайл"; trackArtwork = null }
                isPlaying = false
                tab = Tab.Library
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось открыть этот аудиофайл", Toast.LENGTH_LONG).show()
            }
        }
    }

    SideEffect {
        (context as? MainActivity)?.let { activity ->
            activity.sessionPlayer = player
            activity.updateMediaSession(localName, trackArtist, isPlaying, playbackDuration, playbackPosition, trackArtwork)
        }
    }
    DisposableEffect(Unit) {
        onDispose { player?.release(); player = null; (context as? MainActivity)?.sessionPlayer = null }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Purple,
        onBackground = Color.White, onSurface = Color.White,
        secondary = Pink
    )) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            if (isPlaying && trackArtwork != null) {
                Image(bitmap = trackArtwork!!.asImageBitmap(), contentDescription = "Обложка текущего трека",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(0x99090611)))
            } else { BoykisserBackdrop() }
            StarField()
            if (isPlaying && localUri != null) {
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 24.dp, bottom = 104.dp).widthIn(max = 250.dp),
                    horizontalAlignment = Alignment.End) {
                    Text(localName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(trackArtist, color = Pink, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (showSplash) {
                SplashScreen()
            } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = Panel.copy(alpha = .97f), contentColor = Purple) {
                        Tab.entries.forEach { item ->
                            val icon = when (item) {
                                Tab.Home -> Icons.Filled.Home
                                Tab.SoundCloud -> Icons.Filled.Cloud
                                Tab.Library -> Icons.Filled.LibraryMusic
                                Tab.Settings -> Icons.Filled.Settings
                            }
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(icon, contentDescription = item.title) },
                                label = { Text(item.title, fontSize = 9.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Purple, selectedTextColor = Purple,
                                    indicatorColor = Panel2, unselectedIconColor = Muted,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    Tab.Home -> HomeScreen(
                        Modifier.padding(padding),
                        localName,
                        isPlaying,
                        onOpenSoundCloud = { tab = Tab.SoundCloud },
                        onChooseFile = { picker.launch(arrayOf("audio/*")) },
                        onToggle = {
                            val p = player
                            if (p != null) {
                                try {
                                    if (p.isPlaying) { p.pause(); isPlaying = false }
                                    else { p.start(); isPlaying = true }
                                } catch (_: Exception) { isPlaying = false }
                            } else tab = Tab.Library
                        }
                    )
                    Tab.SoundCloud -> SoundCloudScreen(Modifier.padding(padding))
                    Tab.Library -> LibraryScreen(
                        Modifier.padding(padding), localName, localUri != null, isPlaying,
                        playbackPosition, playbackDuration,
                        onChooseFile = { picker.launch(arrayOf("audio/*")) },
                        onSeek = { value -> player?.seekTo(value); playbackPosition = value },
                        onSkip = { delta -> player?.let { p -> p.seekTo((p.currentPosition + delta).coerceIn(0, p.duration)) } },
                        onToggle = {
                            val p = player
                            if (p != null) {
                                try {
                                    if (p.isPlaying) { p.pause(); isPlaying = false }
                                    else { p.start(); isPlaying = true }
                                } catch (_: Exception) { isPlaying = false }
                            }
                        }
                    )
                    Tab.Settings -> SettingsScreen(Modifier.padding(padding))
                }
            }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "splashGlow")
    val glow by transition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow"
    )
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF160B28), Ink))).padding(top = 76.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top
    ) {
        Text("Привет!", color = Color.White.copy(alpha = glow), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Добро пожаловать в FenyaCoding", color = Pink, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        CircularProgressIndicator(color = Purple, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BoykisserBackdrop() {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                URL("https://media1.tenor.com/m/PLqSqNk8nZIAAAAd/boykisser-%D0%B1%D0%BE%D0%B9%D0%BA%D0%B8%D1%81%D1%81%D0%B5%D1%80.gif")
                    .openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Boykisser purple background",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.13f,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun StarField() {
    val transition = rememberInfiniteTransition(label = "fallingStars")
    val progress by transition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8500, easing = LinearEasing), RepeatMode.Restart), label = "fallingMotion")
    val twinkle by transition.animateFloat(initialValue = .18f, targetValue = .68f,
        animationSpec = infiniteRepeatable(tween(2100), RepeatMode.Reverse), label = "softTwinkle")
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0 until 110) {
            val x = ((i * 73 + 19) % 997) / 997f * size.width
            val baseY = ((i * 137 + 41) % 991) / 991f * size.height
            val speed = 0.45f + (i % 5) * 0.12f
            val y = (baseY + progress * size.height * speed) % size.height
            val radius = if (i % 9 == 0) 3.8f else 1.7f
            val center = androidx.compose.ui.geometry.Offset(x, y)
            drawCircle(brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = twinkle), Purple.copy(alpha = twinkle * .24f), Color.Transparent),
                center = center, radius = radius * 4.5f), radius = radius * 4.5f, center = center)
            drawCircle(Color.White.copy(alpha = twinkle * .75f), radius = radius * .42f, center = center)
        }
    }
}

@Composable
private fun AppHeader(subtitle: String = "YOUR PURPLE MUSIC SPACE") {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("FenyaCoding", color = Purple, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, fontSize = 9.sp, letterSpacing = 1.8.sp)
        }

    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier, trackName: String, isPlaying: Boolean,
    onOpenSoundCloud: () -> Unit, onChooseFile: () -> Unit, onToggle: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 22.dp)) {
        item { AppHeader() }
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF573184), Color(0xFF21142F), Color(0xFF120D1D))))
                        .padding(22.dp)
                ) {
                    Column {
                        Text("WELCOME TO FENYA", color = Pink, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Твоя музыка.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        Text("Твоё пространство.", color = Pink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Слушай локальные файлы и открывай треки SoundCloud.", color = Color(0xFFD7C7E8), fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Button(onClick = onOpenSoundCloud, colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Ink)) {
                                Icon(Icons.Filled.Cloud, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("SoundCloud")
                            }
                            OutlinedButton(onClick = onChooseFile, border = androidx.compose.foundation.BorderStroke(1.dp, Purple.copy(alpha = .7f))) {
                                Icon(Icons.Filled.FolderOpen, null, tint = Purple, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("ТВОЙ ПЛЕЕР", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                TrackCard(trackName, isPlaying, onToggle)
                Spacer(Modifier.height(22.dp))
                Text("ИСТОЧНИКИ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                SourceCard(Icons.Filled.Cloud, "SoundCloud", "Открой публичную ссылку или встроенный плеер", onOpenSoundCloud)
                Spacer(Modifier.height(9.dp))
                SourceCard(Icons.Filled.FolderOpen, "Локальные файлы", "Выбери аудио на устройстве", onChooseFile)
            }
        }
    }
}

@Composable
private fun TrackCard(name: String, playing: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF43245E)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (name == "Локальный трек не выбран") "Пока ничего не играет" else name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (name == "Локальный трек не выбран") "Добавь музыку с устройства" else "Локальный аудиофайл", color = Muted, fontSize = 11.sp)
        }
        IconButton(onClick = onToggle) {
            Icon(if (playing) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle, null, tint = Purple, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun SourceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onClick).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun SoundCloudScreen(modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    var searchUrl by remember { mutableStateOf("https://soundcloud.com/discover") }
    var selectedType by remember { mutableStateOf("sounds") }
    var pageTitle by remember { mutableStateOf("Подборки для тебя") }
    Column(modifier.fillMaxSize()) {
        AppHeader("FENYA MUSIC SEARCH")
        Text("Найди свой звук", Modifier.padding(horizontal = 20.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Поиск треков, артистов и миксов SoundCloud", Modifier.padding(horizontal = 20.dp, vertical = 5.dp), color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Что хочешь послушать?") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Purple) },
                singleLine = true, shape = RoundedCornerShape(19.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple, unfocusedBorderColor = Panel2,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Purple
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val term = query.trim()
                    if (term.isNotEmpty()) {
                        searchUrl = "https://soundcloud.com/search/" + selectedType + "?q=" + Uri.encode(term)
                        pageTitle = "Результаты поиска"
                    }
                },
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(
                    Brush.linearGradient(listOf(Pink, Purple))
                )
            ) { Icon(Icons.Filled.Search, "Искать", tint = Ink) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Треки" to "sounds", "Артисты" to "users", "Плейлисты" to "playlists").forEach { (label, type) ->
                val selected = selectedType == type
                Surface(
                    modifier = Modifier.clickable { selectedType = type },
                    shape = RoundedCornerShape(50),
                    color = if (selected) Purple.copy(alpha = .22f) else Panel.copy(alpha = .88f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Purple.copy(alpha = .65f) else Panel2)
                ) { Text(label, Modifier.padding(horizontal = 13.dp, vertical = 8.dp), color = if (selected) Pink else Muted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(pageTitle, color = Pink, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.height(7.dp))
        key(searchUrl) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                view.evaluateJavascript(
                                    "(function(){var s=document.createElement('style');s.innerHTML='header,nav,[class*=header],[class*=Header],[class*=banner]{display:none!important} body{background:#100a19!important;color:#fff!important}';document.head.appendChild(s);})();",
                                    null
                                )
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl(searchUrl)
                    }
                },
                update = { },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp).clip(RoundedCornerShape(20.dp))
            )
        }
        Text("Поиск оформлен в FenyaCoding; каталоги и доступность треков предоставляет SoundCloud.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier, name: String, hasTrack: Boolean, playing: Boolean,
    position: Int, duration: Int, onChooseFile: () -> Unit,
    onToggle: () -> Unit, onSeek: (Int) -> Unit, onSkip: (Int) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        AppHeader("YOUR COLLECTION")
        Text("Библиотека", Modifier.padding(horizontal = 20.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel.copy(alpha = .94f)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LibraryMusic, null, tint = Purple, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (hasTrack) name else "Библиотека пока пуста", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (hasTrack) "Локальный аудиофайл" else "Добавь аудио с устройства", color = Muted, fontSize = 11.sp)
                }
            }
            if (hasTrack) {
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Purple, inactiveTrackColor = Panel2)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Muted, fontSize = 10.sp)
                    Text(formatTime(duration), color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSkip(-15000) }) { Icon(Icons.Filled.Replay10, "Назад на 15 секунд", tint = Pink, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(18.dp))
                    IconButton(onClick = onToggle, modifier = Modifier.size(58.dp).clip(CircleShape).background(Purple)) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Пауза или воспроизведение", tint = Ink, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(18.dp))
                    IconButton(onClick = { onSkip(15000) }) { Icon(Icons.Filled.Forward10, "Вперёд на 15 секунд", tint = Pink, modifier = Modifier.size(30.dp)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Ink)) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить аудиофайл")
        }
        Spacer(Modifier.height(12.dp))
        Text("Перемотка и управление доступны для локальных аудиофайлов.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

private fun formatTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun SettingsScreen(modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        AppHeader("APP PREFERENCES")
        Text("Настройки", Modifier.padding(horizontal = 20.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Palette, null, tint = Purple)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Фиолетовая тема", fontWeight = FontWeight.SemiBold)
                Text("Тёмный интерфейс FenyaCoding", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = Purple)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("FenyaCoding", fontWeight = FontWeight.SemiBold)
                Text("Музыкальный плеер • Android", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Создано для твоей музыки", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

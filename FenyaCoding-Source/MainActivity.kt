package com.fenyacoding.music

import android.media.MediaPlayer
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FenyaApp() }
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
    val context = LocalContext.current

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
                isPlaying = false
                tab = Tab.Library
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось открыть этот аудиофайл", Toast.LENGTH_LONG).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release(); player = null }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Purple,
        onBackground = Color.White, onSurface = Color.White,
        secondary = Pink
    )) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            StarField()
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
                        onChooseFile = { picker.launch(arrayOf("audio/*")) },
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
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF160B28), Ink))),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(116.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple, Color(0xFF54238A)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Headphones, null, tint = Color.White.copy(alpha = glow), modifier = Modifier.size(62.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Привет!", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Добро пожаловать в FenyaCoding", color = Pink, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        CircularProgressIndicator(color = Purple, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun StarField() {
    val transition = rememberInfiniteTransition(label = "spaceMotion")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "meteorMotion"
    )
    val twinkle by transition.animateFloat(
        initialValue = .35f, targetValue = .95f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "starTwinkle"
    )
    Canvas(Modifier.fillMaxSize()) {
        for (i in 0 until 76) {
            val x = ((i * 73 + 19) % 997) / 997f * size.width
            val y = ((i * 137 + 41) % 991) / 991f * size.height
            val r = if (i % 9 == 0) 2.3f else 1.1f
            drawCircle(if (i % 3 == 0) Pink.copy(alpha = twinkle) else Color.White.copy(alpha = twinkle * .72f), r, androidx.compose.ui.geometry.Offset(x, y))
        }
        for (i in 0 until 3) {
            val phase = (progress + i * .34f) % 1f
            val x = phase * (size.width + 180f) - 90f
            val y = size.height * (.16f + i * .29f)
            val tail = androidx.compose.ui.geometry.Offset(x - 72f, y + 46f)
            val head = androidx.compose.ui.geometry.Offset(x, y)
            drawLine(Purple.copy(alpha = .12f), tail, head, strokeWidth = 9f)
            drawLine(Color.White.copy(alpha = .75f), tail, head, strokeWidth = 2.1f)
            drawCircle(Pink.copy(alpha = .85f), 3.2f, head)
            drawCircle(Color.White.copy(alpha = .75f), 1.3f, head)
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
        Box(Modifier.size(46.dp).clip(CircleShape).background(Panel2), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Headphones, null, tint = Purple, modifier = Modifier.size(25.dp))
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
    var pageTitle by remember { mutableStateOf("Открой музыку SoundCloud") }
    Column(modifier.fillMaxSize()) {
        AppHeader("SOUNDCLOUD SEARCH")
        Text("Поиск музыки", Modifier.padding(horizontal = 20.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Ищи треки, исполнителей и плейлисты прямо в SoundCloud.", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Название трека или артиста") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Purple) },
                singleLine = true, shape = RoundedCornerShape(17.dp),
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
                        searchUrl = "https://soundcloud.com/search?q=" + Uri.encode(term)
                        pageTitle = "Результаты: $term"
                    }
                },
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Purple)
            ) { Icon(Icons.Filled.Search, "Искать", tint = Ink) }
        }
        Spacer(Modifier.height(10.dp))
        Text(pageTitle, color = Pink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.height(8.dp))
        key(searchUrl) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(searchUrl)
                    }
                },
                update = { },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp).clip(RoundedCornerShape(18.dp))
            )
        }
        Text("Результаты и воспроизведение предоставляет SoundCloud. Некоторые треки могут быть недоступны.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun LibraryScreen(modifier: Modifier, name: String, hasTrack: Boolean, playing: Boolean, onChooseFile: () -> Unit, onToggle: () -> Unit) {
    Column(modifier.fillMaxSize()) {
        AppHeader("YOUR COLLECTION")
        Text("Библиотека", Modifier.padding(horizontal = 20.dp), fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LibraryMusic, null, tint = Purple, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (hasTrack) name else "Библиотека пока пуста", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (hasTrack) "Локальный аудиофайл" else "Добавь аудио с устройства", color = Muted, fontSize = 11.sp)
            }
            if (hasTrack) IconButton(onClick = onToggle) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Purple) }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onChooseFile, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Ink)) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить аудиофайл")
        }
        Spacer(Modifier.height(12.dp))
        Text("Поддерживаются аудиофайлы, которые может открыть Android.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
    }
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

package com.zennyx.zentify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.zennyx.zentify.PlayerViewModel
import com.zennyx.zentify.data.Track

@Composable
fun ZentifyApp(vm: PlayerViewModel) {
    var showPlayer by remember { mutableStateOf(false) }
    GlassBackground(vm.current?.thumb) {
        HomeScreen(vm, openPlayer = { showPlayer = true })
        AnimatedVisibility(
            visible = showPlayer && vm.current != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            NowPlaying(vm, close = { showPlayer = false })
        }
    }
    BackHandler(enabled = showPlayer) { showPlayer = false }
}

@Composable
fun HomeScreen(vm: PlayerViewModel, openPlayer: () -> Unit) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("Zentify", color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
        Text("music & video, quietly", color = TextLo, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        // search
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), 0.09f).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = TextLo, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (vm.query.isEmpty()) Text("Search songs, artists, videos", color = TextLo, fontSize = 15.sp)
                BasicTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextHi, fontSize = 15.sp),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (vm.query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, null, tint = TextLo,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { vm.query = "" },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        ModeToggle(video = vm.videoMode, onChange = vm::setMode)
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Accent, strokeWidth = 2.dp)
                vm.results.isEmpty() -> Text(
                    vm.error ?: "Search to start listening.",
                    color = TextLo, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(vm.results, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, selected = vm.current?.id == t.id) { vm.play(i); openPlayer() }
                    }
                }
            }
        }

        if (vm.current != null && vm.results.isNotEmpty() && vm.error != null) {
            Text(vm.error.orEmpty(), color = Color(0xFFFF9D9D), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }

        if (vm.current != null) {
            MiniPlayer(vm, openPlayer)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
fun ModeToggle(video: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.glass(RoundedCornerShape(22.dp), 0.06f).padding(4.dp)) {
        listOf("Music" to false, "Video" to true).forEach { (label, isVideo) ->
            val on = video == isVideo
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Accent.copy(alpha = 0.28f) else Color.Transparent)
                    .clickable { onChange(isVideo) }
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) TextHi else TextLo, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun TrackRow(t: Track, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .glass(RoundedCornerShape(20.dp), if (selected) 0.15f else 0.06f)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = t.thumb, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.duration.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(t.duration, color = TextLo, fontSize = 12.sp)
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, open: () -> Unit) {
    val t = vm.current ?: return
    val frac = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), 0.12f).clickable(onClick = open)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = t.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = TextLo, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassCircle(40.dp, onClick = vm::toggle) {
                if (vm.buffering && !vm.isPlaying) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                } else {
                    CtlIcon(if (vm.isPlaying) Ctl.Pause else Ctl.Play, 18.dp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun NowPlaying(vm: PlayerViewModel, close: () -> Unit) {
    val t = vm.current ?: return
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f

    Column(
        Modifier.fillMaxSize().background(Color(0x990A0C12))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassCircle(42.dp, onClick = close) {
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = TextHi)
            }
            Spacer(Modifier.weight(1f))
            Text(if (vm.currentIsVideo) "VIDEO" else "MUSIC", color = TextLo, fontSize = 11.sp, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(42.dp))
        }

        Spacer(Modifier.weight(1f))

        val c = vm.controller
        if (vm.currentIsVideo && c != null) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { useController = false; player = c } },
                update = { it.player = c },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp)),
            )
        } else {
            AsyncImage(
                model = t.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(34.dp))
                    .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(34.dp)),
            )
        }

        Spacer(Modifier.weight(1f))

        Text(t.title, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(t.artist, color = TextLo, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        vm.error?.let { Text(it, color = Color(0xFFFF9D9D), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }

        Spacer(Modifier.height(14.dp))
        Slider(
            value = drag ?: frac,
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let(vm::seekFraction); drag = null },
            colors = SliderDefaults.colors(
                thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(vm.position), color = TextLo, fontSize = 11.sp)
            Text(fmt(vm.duration), color = TextLo, fontSize = 11.sp)
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            GlassCircle(54.dp, onClick = vm::previous) { CtlIcon(Ctl.Prev, 22.dp) }
            GlassCircle(76.dp, onClick = vm::toggle, strength = 0.2f) {
                if (vm.buffering && !vm.isPlaying) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Accent, strokeWidth = 2.dp)
                } else {
                    CtlIcon(if (vm.isPlaying) Ctl.Pause else Ctl.Play, 32.dp)
                }
            }
            GlassCircle(54.dp, onClick = vm::next) { CtlIcon(Ctl.Next, 22.dp) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

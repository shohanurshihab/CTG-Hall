package com.ctghall.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray

private val Bg = Color(0xFF0A0C10)
private val Panel = Color(0xFF161A22)
private val PanelHi = Color(0xFF232937)
private val Accent = Color(0xFFF2B33D)
private val Dim = Color(0xFFA9B0BD)
private val Danger = Color(0xFFFF8A80)

sealed interface Screen {
    object Home : Screen
    data class Library(val url: String, val title: String) : Screen
    data class Detail(val movie: Movie) : Screen
    object Settings : Screen
}

data class HeroInfo(val movie: Movie, val meta: Meta?)

/** Everything a home row needs from the home screen. */
class HomeCtx(
    val onFocus: (Movie, Meta?) -> Unit,
    val onOpen: (Movie) -> Unit,
    val restoreUrl: String?,
    val onRestored: () -> Unit,
)

/** Remembers scroll positions and the focused item so that Back lands where you left. */
object Memory {
    private val focus = HashMap<String, String>()
    private val lists = HashMap<String, LazyListState>()
    private val grids = HashMap<String, LazyGridState>()
    var restore: String? = null

    fun list(key: String): LazyListState = lists.getOrPut(key) { LazyListState() }
    fun grid(key: String): LazyGridState = grids.getOrPut(key) { LazyGridState() }
    fun setFocus(screen: String, url: String) {
        focus[screen] = url
    }

    fun getFocus(screen: String): String? = focus[screen]

    fun takeRestore(): String? {
        val r = restore
        restore = null
        return r
    }

    fun reset() {
        focus.clear()
        lists.clear()
        grids.clear()
        restore = null
    }
}

private fun focusKey(s: Screen): String = when (s) {
    is Screen.Home -> "home"
    is Screen.Library -> "lib:" + s.url
    else -> ""
}

private fun navIdFor(url: String): String = when {
    url.startsWith(Store.BASE_URL + "Movies/") -> "movies"
    url.startsWith(Store.BASE_URL + "TV-Series/") -> "tv"
    url.startsWith(Store.BASE_URL + "Video%20%26%20Documentary/") -> "videos"
    else -> "browse"
}

@Composable
fun App(onExit: () -> Unit) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }

    fun push(s: Screen) {
        Memory.restore = null
        stack.add(s)
    }

    fun navTo(s: Screen) {
        if (stack.last() == s) return
        Memory.reset()
        stack.clear()
        stack.add(Screen.Home)
        if (s != Screen.Home) stack.add(s)
    }

    BackHandler {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            Memory.restore = Memory.getFocus(focusKey(stack.last()))
        } else {
            onExit()
        }
    }

    val nav: (String) -> Unit = { id ->
        when (id) {
            "home" -> navTo(Screen.Home)
            "movies" -> navTo(Screen.Library(Store.BASE_URL + "Movies/", "Movies"))
            "tv" -> navTo(Screen.Library(Store.BASE_URL + "TV-Series/", "TV Series"))
            "videos" -> navTo(Screen.Library(Store.BASE_URL + "Video%20%26%20Documentary/", "Video & Documentary"))
            "browse" -> navTo(Screen.Library(Store.BASE_URL, "All files"))
            "settings" -> navTo(Screen.Settings)
            else -> {}
        }
    }

    when (val s = stack.last()) {
        is Screen.Home -> HomeScreen(nav = nav, onOpen = { push(Screen.Detail(it)) })
        is Screen.Library -> LibraryScreen(
            url = s.url,
            title = s.title,
            nav = nav,
            onEnter = { push(it) },
            onOpen = { push(Screen.Detail(it)) },
        )
        is Screen.Detail -> DetailScreen(s.movie)
        is Screen.Settings -> SettingsScreen(nav)
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
fun NavBar(current: String, onNav: (String) -> Unit) {
    val items = listOf(
        "home" to "Home",
        "movies" to "Movies",
        "tv" to "TV Series",
        "videos" to "Videos",
        "browse" to "All files",
        "settings" to "Settings",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "CTG HALL",
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 24.dp),
        )
        items.forEach { (id, label) ->
            NavTab(label, selected = id == current) { onNav(id) }
        }
    }
}

@Composable
fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color.White else if (selected) Color(0x33FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = if (focused) Color.Black else if (selected) Accent else Color.White,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
fun FocusButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else PanelHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color.White else if (selected) Accent else PanelHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (focused || selected) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
fun FocusRow(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) PanelHi else Panel)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
        if (trailing != null) {
            Text(
                trailing,
                color = if (highlight) Accent else Dim,
                fontSize = 14.sp,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
fun FolderTile(
    name: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onAutoFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(150)
            runCatching { fr.requestFocus() }
            onAutoFocused()
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    if (focused) listOf(Accent, Color(0xFFB8801F)) else listOf(PanelHi, Panel)
                )
            )
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            name,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Color.Black else Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PosterCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onAutoFocused: () -> Unit = {},
    progress: Float = 0f,
    onFocus: ((Movie, Meta?) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val meta by produceState<Meta?>(null, movie.url) { value = MetaRepo.get(movie) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "scale")
    val fr = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(150)
            runCatching { fr.requestFocus() }
            onAutoFocused()
        }
    }
    LaunchedEffect(focused, meta) {
        if (focused) onFocus?.invoke(movie, meta)
    }

    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .border(3.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(PanelHi),
            contentAlignment = Alignment.Center,
        ) {
            val poster = meta?.poster
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    movie.title,
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color(0x99000000))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(5.dp)
                            .background(Accent)
                    )
                }
            }
        }
        Column(Modifier.padding(top = 6.dp, start = 3.dp, end = 3.dp)) {
            Text(
                movie.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (focused) Color.White else Color(0xFFD0D5DE),
            )
            val sub = listOfNotNull(movie.year, if (movie.tv) "Series" else null).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 11.sp, color = Dim, maxLines = 1)
            }
        }
    }
}

@Composable
fun HeroBackdrop(url: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        Crossfade(targetState = url, animationSpec = tween(500), label = "backdrop") { u ->
            if (u != null) {
                AsyncImage(
                    model = u,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Bg, Color(0xCC0A0C10), Color(0x330A0C10))))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Bg)))
        )
    }
}

@Composable
fun HeroText(info: HeroInfo?, tip: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        if (info == null) {
            Text("Welcome", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                if (tip) "Add a free TMDB key in Settings to get posters and descriptions."
                else "Move down to pick something to watch.",
                color = Dim,
                fontSize = 14.sp,
            )
        } else {
            val m = info.movie
            val meta = info.meta
            Text(
                meta?.title ?: m.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val bits = listOfNotNull(
                m.year,
                if (m.tv) "Series" else null,
                meta?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                meta?.genres,
            )
            if (bits.isNotEmpty()) {
                Text(bits.joinToString("   •   "), color = Dim, fontSize = 14.sp)
            }
            val overview = meta?.overview
            if (overview != null) {
                Text(
                    overview,
                    fontSize = 14.sp,
                    color = Color(0xFFDDE1E8),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.6f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(nav: (String) -> Unit, onOpen: (Movie) -> Unit) {
    val roots = remember { Store.roots() }
    val recents = remember { Store.recents() }
    val listState = remember { Memory.list("home") }
    var hero by remember { mutableStateOf<HeroInfo?>(null) }
    val restoreUrl = remember { Memory.takeRestore() }
    var restored by remember { mutableStateOf(false) }
    val needKey = Store.tmdbKey.isBlank()

    val hc = remember(restored) {
        HomeCtx(
            onFocus = { m, meta ->
                Memory.setFocus("home", m.url)
                hero = HeroInfo(m, meta)
            },
            onOpen = onOpen,
            restoreUrl = if (restored) null else restoreUrl,
            onRestored = { restored = true },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        HeroBackdrop(
            hero?.meta?.backdrop,
            Modifier
                .fillMaxWidth()
                .height(400.dp)
        )
        Column(Modifier.fillMaxSize()) {
            NavBar("home", nav)
            HeroText(
                hero,
                needKey,
                Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxWidth()
                    .height(140.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (recents.isNotEmpty()) {
                    item(key = "recents") {
                        MovieRow("Recently watched", recents, "row:recents", true, hc)
                    }
                }
                items(roots, key = { it.url }) { r -> LibraryRow(r, hc) }
                if (roots.isEmpty() && recents.isEmpty()) {
                    item {
                        Text(
                            "No rows yet. Open Movies or TV Series, go into a folder and choose \"Add to Home\".",
                            color = Dim,
                            modifier = Modifier.padding(horizontal = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieRow(title: String, movies: List<Movie>, memKey: String, showProgress: Boolean, hc: HomeCtx) {
    val state = remember(memKey) { Memory.list(memKey) }
    Column {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp, top = 4.dp),
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(movies, key = { it.url }) { m ->
                PosterCard(
                    m,
                    modifier = Modifier.width(125.dp),
                    autoFocus = hc.restoreUrl == m.url,
                    onAutoFocused = hc.onRestored,
                    progress = if (showProgress) Store.getFrac(m.url) else 0f,
                    onFocus = hc.onFocus,
                ) { hc.onOpen(m) }
            }
        }
    }
}

@Composable
fun LibraryRow(root: Root, hc: HomeCtx) {
    var movies by remember(root.url) { mutableStateOf<List<Movie>?>(null) }
    var error by remember(root.url) { mutableStateOf<String?>(null) }
    var attempt by remember(root.url) { mutableStateOf(0) }

    LaunchedEffect(root.url, attempt) {
        error = null
        try {
            val all = Net.listDir(root.url, force = attempt > 0)
                .filter { !Names.isHidden(it.name) && (it.isDir || Names.isVideo(it.name)) }
                .sortedByDescending { it.modified }
            val titles = all.filter { !it.isDir || Names.looksLikeTitle(it.name) }
            movies = (if (titles.isNotEmpty()) titles else all).map { it.toMovie() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load"
        }
    }

    val list = movies
    val err = error
    when {
        list != null -> MovieRow(root.name, list, "row:" + root.url, false, hc)
        err != null -> Column(Modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
            Text(root.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Could not load this folder: $err", color = Danger, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            FocusButton("Retry") { attempt++ }
        }
        else -> Column(Modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
            Text(root.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Loading...", color = Dim, fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------- library (folders and poster grid)

@Composable
fun LibraryScreen(
    url: String,
    title: String,
    nav: (String) -> Unit,
    onEnter: (Screen) -> Unit,
    onOpen: (Movie) -> Unit,
) {
    var entries by remember(url) { mutableStateOf<List<DirEntry>?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var attempt by remember(url) { mutableStateOf(0) }
    var sortNew by remember(url) { mutableStateOf(false) }
    var query by remember(url) { mutableStateOf("") }
    var added by remember(url) { mutableStateOf(false) }
    var initialDone by remember(url) { mutableStateOf(false) }
    val restoreUrl = remember(url) { Memory.takeRestore() }
    val screenKey = "lib:$url"
    val gridState = remember(url) { Memory.grid(screenKey) }
    val rowName = remember(url) {
        url.trimEnd('/').split('/').takeLast(2).joinToString(" / ") { Uri.decode(it) }
    }
    val pathText = remember(url) {
        Uri.decode(url.removePrefix(Store.BASE_URL)).trim('/').ifEmpty { "ctghall.com/Data" }
    }

    LaunchedEffect(url, attempt) {
        error = null
        try {
            entries = Net.listDir(url, force = attempt > 0)
                .filter { !Names.isHidden(it.name) && (it.isDir || Names.isVideo(it.name)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Could not load"
        }
    }

    val list = entries
    val folders = remember(list, query, sortNew) {
        val f = list.orEmpty().filter {
            it.isDir && !Names.looksLikeTitle(it.name) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }
        if (sortNew) f.sortedByDescending { it.modified } else f.sortedBy { Names.natKey(it.name) }
    }
    val titles = remember(list, query, sortNew) {
        val t = list.orEmpty().filter {
            (!it.isDir || Names.looksLikeTitle(it.name)) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }
        val sorted = if (sortNew) t.sortedByDescending { it.modified } else t.sortedBy { Names.natKey(it.name) }
        sorted.map { it.toMovie() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        NavBar(navIdFor(url), nav)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pathText, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (error != null) {
                FocusButton("Retry") { attempt++ }
            }
            FocusButton(if (sortNew) "Sort: Newest" else "Sort: A-Z") { sortNew = !sortNew }
            if (url != Store.BASE_URL && titles.isNotEmpty()) {
                FocusButton(if (added) "Added to Home" else "Add to Home") {
                    Store.addRoot(Root(rowName, url))
                    added = true
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Filter") },
                modifier = Modifier.width(170.dp),
            )
        }

        when {
            error != null -> Text(
                "Could not load: $error",
                color = Danger,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
            )
            list == null -> Text(
                "Loading...",
                color = Dim,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
            )
            titles.isEmpty() && folders.isEmpty() -> Text(
                "Nothing here.",
                color = Dim,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
            )
            titles.isEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(210.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                gridItemsIndexed(folders, key = { _, f -> f.url }) { i, f ->
                    FolderTile(
                        f.name,
                        autoFocus = !initialDone && (if (restoreUrl != null) f.url == restoreUrl else i == 0),
                        onAutoFocused = { initialDone = true },
                    ) {
                        Memory.setFocus(screenKey, f.url)
                        onEnter(Screen.Library(f.url, f.name))
                    }
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (folders.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(folders, key = { it.url }) { f ->
                                FocusButton(f.name) { onEnter(Screen.Library(f.url, f.name)) }
                            }
                        }
                    }
                }
                gridItemsIndexed(titles, key = { _, m -> m.url }) { i, m ->
                    PosterCard(
                        m,
                        autoFocus = !initialDone && (if (restoreUrl != null) m.url == restoreUrl else i == 0),
                        onAutoFocused = { initialDone = true },
                        onFocus = { mv, _ -> Memory.setFocus(screenKey, mv.url) },
                    ) { onOpen(m) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- details, seasons, episodes

private fun startPlay(ctx: Context, movie: Movie, f: Folder, index: Int) {
    Store.pushRecent(movie)
    val subsJson = JSONArray(f.videos.map { v -> JSONArray(Names.subsFor(f, v).map { it.url }) })
    ctx.startActivity(
        Intent(ctx, PlayerActivity::class.java)
            .putStringArrayListExtra("urls", ArrayList(f.videos.map { it.url }))
            .putStringArrayListExtra("names", ArrayList(f.videos.map { Names.baseName(it.name) }))
            .putExtra("subs", subsJson.toString())
            .putExtra("start", index)
            .putExtra("key", movie.url)
    )
}

@Composable
fun DetailScreen(movie: Movie) {
    val ctx = LocalContext.current
    val meta by produceState<Meta?>(null, movie.url) { value = MetaRepo.get(movie) }
    var root by remember(movie.url) { mutableStateOf<Folder?>(null) }
    var error by remember(movie.url) { mutableStateOf<String?>(null) }
    var season by remember(movie.url) { mutableStateOf<DirEntry?>(null) }
    var seasonFolder by remember(movie.url) { mutableStateOf<Folder?>(null) }
    var focusedOnce by remember(movie.url) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(movie.url) {
        try {
            if (movie.isFolder) {
                val f = Net.folder(movie.url)
                root = f
                if (f.videos.isEmpty() && f.dirs.isNotEmpty()) {
                    season = f.dirs.sortedBy { Names.natKey(it.name) }.first()
                }
            } else {
                val parent = movie.url.substringBeforeLast('/') + "/"
                val f = Net.folder(parent)
                val me = f.videos.firstOrNull { it.url == movie.url }
                    ?: DirEntry(movie.rawName, movie.url, false, 0, 0)
                val base = Names.baseName(me.name)
                root = Folder(
                    listOf(me),
                    f.subs.filter { it.name.startsWith(base, ignoreCase = true) },
                    emptyList(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (movie.isFolder) {
                error = e.message ?: "Could not load folder"
            } else {
                root = Folder(
                    listOf(DirEntry(movie.rawName, movie.url, false, 0, 0)),
                    emptyList(),
                    emptyList(),
                )
            }
        }
    }

    LaunchedEffect(season?.url) {
        val s = season
        if (s != null) {
            seasonFolder = null
            try {
                seasonFolder = Net.folder(s.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not load season"
            }
        }
    }

    val r = root
    val seasons = remember(r) { r?.dirs.orEmpty().sortedBy { Names.natKey(it.name) } }
    val epFolder: Folder? = if (r != null && r.videos.isNotEmpty()) r else seasonFolder
    val focusIdx = remember(epFolder) {
        val i = epFolder?.videos?.indexOfFirst { Store.getPos(it.url) > 0 } ?: -1
        if (i >= 0) i else 0
    }

    LaunchedEffect(epFolder) {
        if (epFolder != null && !focusedOnce) {
            delay(250)
            runCatching { focus.requestFocus() }
            focusedOnce = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        HeroBackdrop(meta?.backdrop, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x990A0C10))
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Box(
                Modifier
                    .width(180.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PanelHi),
                contentAlignment = Alignment.Center,
            ) {
                val poster = meta?.poster
                if (poster != null) {
                    AsyncImage(
                        model = poster,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(movie.title, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text(
                    meta?.title ?: movie.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val bits = listOfNotNull(
                    movie.year,
                    if (movie.tv) "Series" else null,
                    meta?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                    meta?.genres,
                )
                if (bits.isNotEmpty()) {
                    Text(bits.joinToString("   •   "), color = Dim, fontSize = 14.sp)
                }
                val overview = meta?.overview
                if (overview != null) {
                    Text(
                        overview,
                        fontSize = 14.sp,
                        color = Color(0xFFDDE1E8),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))

                if (error != null) {
                    Text("Could not load: $error", color = Danger)
                } else if (r == null) {
                    Text("Loading files...", color = Dim)
                } else {
                    if (r.videos.isEmpty() && seasons.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 10.dp),
                        ) {
                            items(seasons, key = { it.url }) { s ->
                                Chip(s.name, selected = season?.url == s.url) { season = s }
                            }
                        }
                    }
                    val ef = epFolder
                    when {
                        ef == null && seasons.isEmpty() -> Text("No video files found here.", color = Dim)
                        ef == null -> Text("Loading episodes...", color = Dim)
                        ef.videos.isEmpty() -> Text("No video files found here.", color = Dim)
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            itemsIndexed(ef.videos, key = { _, v -> v.url }) { i, v ->
                                val pos = Store.getPos(v.url)
                                FocusRow(
                                    text = (if (ef.videos.size > 1) "${i + 1}.  " else "") + Names.baseName(v.name),
                                    modifier = if (i == focusIdx) Modifier.focusRequester(focus) else Modifier,
                                    trailing = if (pos > 0) "Resume ${fmtTime(pos)}" else fmtSize(v.size).ifEmpty { null },
                                    highlight = pos > 0,
                                ) { startPlay(ctx, movie, ef, i) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(nav: (String) -> Unit) {
    var key by remember { mutableStateOf(Store.tmdbKeyOverride) }
    var subs by remember { mutableStateOf(Store.subsDefault) }
    var roots by remember { mutableStateOf(Store.roots()) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        NavBar("settings", nav)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)

            Text("TMDB API key (v3), for posters and descriptions", fontSize = 16.sp)
            if (BuildConfig.TMDB_API_KEY.isNotBlank()) {
                Text("A key is already built into this app. Leave the box empty to use it.", color = Dim, fontSize = 13.sp)
            }
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    Store.tmdbKeyOverride = it
                    MetaRepo.authFailed = false
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.6f),
            )

            FocusButton("Subtitles on by default: " + (if (subs) "ON" else "OFF")) {
                subs = !subs
                Store.subsDefault = subs
            }
            FocusButton("Clear poster cache") {
                Store.clearMeta()
                MetaRepo.clearMem()
                note = "Poster cache cleared."
            }
            FocusButton("Clear watch history") {
                Store.clearHistory()
                note = "Watch history cleared. Reopen Home to see it."
            }
            FocusButton("Reset home rows to default") {
                Store.resetRoots()
                roots = Store.roots()
                note = "Home rows reset. Reopen Home to see them."
            }
            val n = note
            if (n != null) {
                Text(n, color = Accent, fontSize = 14.sp)
            }

            Text(
                "Home rows",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (roots.isEmpty()) {
                Text("No rows. Open a folder in Movies or TV Series and choose \"Add to Home\".", color = Dim)
            }
            roots.forEach { r ->
                Row(
                    Modifier.fillMaxWidth(0.6f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(r.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FocusButton("Remove") {
                        Store.removeRoot(r.url)
                        roots = Store.roots()
                    }
                }
            }
        }
    }
}

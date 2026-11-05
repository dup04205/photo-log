package com.dup04205.photolog

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PhotoLogApp() {
    val ctx = LocalContext.current
    val repo = remember { EntryRepo(ctx.applicationContext) }
    val entries = remember { mutableStateListOf<Entry>().apply { addAll(repo.load()) } }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun persist() = repo.save(entries.toList())

    fun addEntry(uri: Uri, info: PhotoInfo, open: Boolean = true) {
        val now = System.currentTimeMillis()
        val e = Entry(
            id = "p${now}_${entries.size}", uri = uri.toString(), date = info.date, note = "",
            lat = info.lat, lon = info.lon, created = now,
        )
        entries.add(e)
        persist()
        if (open) {
            selectedId = e.id
            val msg = if (e.lat != null) "사진 폴더에 저장하고 촬영 위치를 기록했습니다"
            else "사진 폴더에 저장했습니다 (위치 정보 없음)"
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** 갤러리·Google 포토에서 고른 사진(여러 장 가능)을 PHOTO-LOG 폴더로 복사한다. */
    fun importMany(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            busy = true
            val results = withContext(Dispatchers.IO) { uris.map { PhotoStore.importFrom(ctx, it) } }
            busy = false
            val ok = results.filterNotNull()
            if (ok.size == 1 && uris.size == 1) {
                addEntry(ok[0].first, ok[0].second, open = true)
                return@launch
            }
            ok.forEach { addEntry(it.first, it.second, open = false) }
            val failed = uris.size - ok.size
            val withPlace = ok.count { it.second.lat != null }
            val msg = when {
                ok.isEmpty() -> "사진을 가져오지 못했습니다"
                failed > 0 -> "${ok.size}장을 가져왔습니다 (위치 ${withPlace}장, 실패 ${failed}장)"
                else -> "${ok.size}장을 가져왔습니다 (위치 정보 ${withPlace}장)"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun deleteEntry(e: Entry) {
        scope.launch {
            val removed = withContext(Dispatchers.IO) { PhotoStore.delete(ctx, Uri.parse(e.uri)) }
            entries.removeAll { it.id == e.id }
            persist()
            if (selectedId == e.id) selectedId = null
            val msg = if (removed) "삭제했습니다"
            else "기록을 삭제했습니다. 폴더의 사진은 갤러리에서 지워 주세요"
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) { permissions.launch(PhotoStore.wantedPermissions()) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingCamera?.let { Uri.parse(it) }
        pendingCamera = null
        if (u == null) return@rememberLauncherForActivityResult
        if (!ok) {
            PhotoStore.discard(ctx, u)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            val info = withContext(Dispatchers.IO) { PhotoStore.finishCapture(ctx, u) }
            busy = false
            addEntry(u, info)
        }
    }

    // 갤러리(기기 사진): 여러 장 선택
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> importMany(uris) }

    // Google 포토 앱으로 바로 열기 (클라우드에만 있는 사진도 내려받아 가져온다)
    val googlePhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val uris = buildList {
            val clip = data.clipData
            if (clip != null) for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
            else data.data?.let { add(it) }
        }
        importMany(uris)
    }
    val hasGooglePhotos = remember {
        ctx.packageManager.getLaunchIntentForPackage(GOOGLE_PHOTOS) != null
    }

    val selected = entries.firstOrNull { it.id == selectedId }
    if (selected != null) {
        BackHandler { selectedId = null }
        DetailScreen(
            entry = selected,
            onBack = { selectedId = null },
            onSave = { note ->
                val i = entries.indexOfFirst { it.id == selected.id }
                if (i >= 0) {
                    entries[i] = entries[i].copy(note = note)
                    persist()
                }
            },
            onDelete = { deleteEntry(selected) },
        )
    } else {
        ListScreen(
            entries = entries,
            busy = busy,
            onOpen = { selectedId = it },
            onDelete = { deleteEntry(it) },
            onCamera = {
                val u = PhotoStore.newImageUri(ctx)
                if (u == null) {
                    Toast.makeText(ctx, "사진 폴더를 만들 수 없습니다", Toast.LENGTH_SHORT).show()
                } else {
                    pendingCamera = u.toString()
                    try {
                        camera.launch(u)
                    } catch (_: ActivityNotFoundException) {
                        PhotoStore.discard(ctx, u)
                        pendingCamera = null
                        Toast.makeText(ctx, "카메라 앱을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onPick = { picker.launch("image/*") },
            onGooglePhotos = if (hasGooglePhotos) {
                {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        setPackage(GOOGLE_PHOTOS)
                    }
                    try {
                        googlePhotos.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        picker.launch("image/*")
                    }
                }
            } else null,
        )
    }
}

/* ② 시작 화면: 연·월·일 아코디언 + 사진 그리드 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    entries: List<Entry>,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onDelete: (Entry) -> Unit,
    onCamera: () -> Unit,
    onPick: () -> Unit,
    onGooglePhotos: (() -> Unit)?,
) {
    val sorted = entries.sortedWith(
        compareByDescending<Entry> { it.date }.thenByDescending { it.created }
    )
    val byDay = sorted.groupBy { it.date }
    val headerIndex = LinkedHashMap<String, Int>()
    var idx = 0
    byDay.forEach { (d, list) ->
        headerIndex[d] = idx
        idx += 1 + list.size
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var addMenu by remember { mutableStateOf(false) }
    var shareFor by remember { mutableStateOf<Entry?>(null) }
    var editFor by remember { mutableStateOf<Entry?>(null) }
    var deleteFor by remember { mutableStateOf<Entry?>(null) }
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("photolog", 0) }
    var showIndex by remember { mutableStateOf(prefs.getBoolean("showIndex", true)) }

    fun jump(prefix: String) {
        val i = headerIndex.entries.firstOrNull { it.key.startsWith(prefix) }?.value ?: return
        scope.launch { gridState.animateScrollToItem(i) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Logo() },
                navigationIcon = {
                    IconButton(onClick = {
                        showIndex = !showIndex
                        prefs.edit().putBoolean("showIndex", showIndex).apply()
                    }) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = if (showIndex) "날짜 메뉴 숨기기" else "날짜 메뉴 보기",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("사진 추가") },
                )
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("카메라로 찍기") },
                        onClick = { addMenu = false; onCamera() },
                    )
                    DropdownMenuItem(
                        text = { Text("갤러리에서 가져오기") },
                        onClick = { addMenu = false; onPick() },
                    )
                    if (onGooglePhotos != null) {
                        DropdownMenuItem(
                            text = { Text("Google 포토에서 가져오기") },
                            onClick = { addMenu = false; onGooglePhotos() },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showIndex,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally(),
                ) {
                    Row {
                        DateIndex(byDay, onJump = { jump(it) })
                        VerticalDivider()
                    }
                }
                if (sorted.isEmpty()) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "아직 사진이 없습니다.\n아래 ＋ 사진 추가로 첫 기록을 남겨 보세요.\n사진은 사진/PHOTO-LOG 폴더에 저장됩니다.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (showIndex) 3 else 4),
                        state = gridState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        byDay.forEach { (day, list) ->
                            item(key = "h-$day", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    day.replace('-', '.'),
                                    fontFamily = IndexFont,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp, top = 10.dp, bottom = 2.dp),
                                )
                            }
                            items(list, key = { it.id }) { e ->
                                Tile(
                                    e = e,
                                    onClick = { onOpen(e.id) },
                                    onShare = { shareFor = e },
                                    onDelete = { deleteFor = e },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    shareFor?.let { e ->
        ShareDialog(
            entry = e,
            note = e.note,
            onDismiss = { shareFor = null },
            onEdit = { shareFor = null; editFor = e },
        )
    }
    editFor?.let { e -> ShareEditor(e, e.note) { editFor = null } }
    deleteFor?.let { e ->
        DeleteDialog(
            onConfirm = { deleteFor = null; onDelete(e) },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun Logo() {
    Text(
        "PHOTO-LOG",
        fontFamily = TitleFont,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        letterSpacing = 1.sp,
        fontSize = 23.sp,
    )
}

/* ③ 연 → 월 → 일 아코디언 메뉴 */
@Composable
fun DateIndex(days: Map<String, List<Entry>>, onJump: (String) -> Unit) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val years = days.keys.groupBy { it.substring(0, 4) }
    Column(
        Modifier
            .width(112.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(
            "연 · 월 · 일",
            fontSize = 12.sp,
            fontFamily = IndexFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(6.dp),
        )
        years.entries.forEachIndexed { yi, (year, yearDays) ->
            val yOpen = expanded[year] ?: (yi == 0)
            IndexRow(
                label = "${if (yOpen) "▾" else "▸"} $year",
                count = yearDays.sumOf { days[it]?.size ?: 0 },
                indent = 0,
                bold = true,
            ) { expanded[year] = !yOpen }
            if (yOpen) {
                val months = yearDays.groupBy { it.substring(5, 7) }
                months.entries.forEachIndexed { mi, (month, monthDays) ->
                    val key = "$year-$month"
                    val mOpen = expanded[key] ?: (yi == 0 && mi == 0)
                    IndexRow(
                        label = "${if (mOpen) "▾" else "▸"} ${month.toInt()}월",
                        count = monthDays.sumOf { days[it]?.size ?: 0 },
                        indent = 10,
                    ) {
                        expanded[key] = !mOpen
                        onJump(key)
                    }
                    if (mOpen) {
                        monthDays.forEach { d ->
                            IndexRow(
                                label = d.substring(5).replace('-', '.'),
                                count = days[d]?.size ?: 0,
                                indent = 22,
                                mono = true,
                            ) { onJump(d) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexRow(
    label: String,
    count: Int,
    indent: Int,
    bold: Boolean = false,
    mono: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = (6 + indent).dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = if (bold) 15.sp else if (mono) 13.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = IndexFont,
            color = if (mono) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            "$count",
            fontSize = 12.sp,
            fontFamily = IndexFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* 사진 칸: 누르면 열기, 꾹 누르면 공유·삭제 메뉴 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(e: Entry, onClick: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
                onLongClickLabel = "공유·삭제 메뉴",
            )
    ) {
        AsyncImage(
            model = Uri.parse(e.uri),
            contentDescription = "${e.date} 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (e.lat != null) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "위치 있음",
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(3.dp).size(14.dp),
            )
        }
        if (e.note.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Stamp)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("공유") },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = { menu = false; onShare() },
            )
            DropdownMenuItem(
                text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/* ① 상세 화면: 사진 · 위치 · 기록 · 저장 · 공유 · ⋯ 삭제 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    entry: Entry,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Logo() },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "목록으로")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "설정")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("삭제", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                // 키보드가 올라오면 그 높이만큼 화면을 줄여, 입력 중인 줄이 가려지지 않게 한다
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = Uri.parse(entry.uri),
                    contentDescription = "${entry.date} 사진",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                )
                FilmDateStamp(
                    entry.date,
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
                )
            }
            PlaceRow(entry)
            val noteLines = remember(note) { ShareImage.noteLineCount(note) }
            OutlinedTextField(
                value = note,
                onValueChange = { new ->
                    // 공유 이미지 기준 5줄을 넘는 입력은 받지 않는다(지우는 것은 언제나 허용)
                    val lines = ShareImage.noteLineCount(new)
                    if (lines <= ShareImage.MAX_NOTE_LINES || lines <= noteLines) note = new
                },
                label = { Text("기록") },
                placeholder = { Text("이 사진에 남길 이야기를 적어 두세요. (최대 5줄)") },
                supportingText = {
                    Text(
                        "$noteLines / ${ShareImage.MAX_NOTE_LINES}줄",
                        color = if (noteLines >= ShareImage.MAX_NOTE_LINES) Stamp
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                minLines = 5,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = NoteFont,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = { shareOpen = true }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("공유")
                }
                Button(
                    onClick = {
                        onSave(note.trim())
                        Toast.makeText(ctx, "저장했습니다", Toast.LENGTH_SHORT).show()
                    },
                    enabled = note.trim() != entry.note,
                ) { Text("저장") }
            }
        }
    }

    if (confirmDelete) {
        DeleteDialog(
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (shareOpen) {
        ShareDialog(
            entry = entry,
            note = note.trim(),
            onDismiss = { shareOpen = false },
            onEdit = { shareOpen = false; editOpen = true },
        )
    }
    if (editOpen) ShareEditor(entry, note.trim()) { editOpen = false }
}

/** 필름카메라 날짜 각인: '26 11 05 를 주황 7세그먼트 숫자 + 빛 번짐으로 */
@Composable
fun FilmDateStamp(date: String, modifier: Modifier = Modifier, fontSize: TextUnit = 13.sp) {
    Text(
        stampOf(date),
        modifier = modifier,
        style = TextStyle(
            color = FilmOrange,
            fontFamily = StampFont,
            fontSize = fontSize,
            letterSpacing = (fontSize.value * 0.06f).sp,
            shadow = Shadow(color = FilmGlow, blurRadius = fontSize.value * 0.8f),
        ),
    )
}

private val FilmOrange = Color(0xFFFF7A1A)
private val FilmGlow = Color(0xCCFF5A00)

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("삭제") },
        text = { Text("이 사진과 기록을 삭제합니다. 사진/PHOTO-LOG 폴더의 파일도 함께 지워지며 되돌릴 수 없습니다.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("삭제", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ShareDialog(entry: Entry, note: String, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("공유하기") },
        text = {
            Column {
                ShareOption("크기 조절해서 공유", "비율(1:1 · 4:5 · 9:16)과 보낼 부분을 고르고 기록과 함께") {
                    onEdit()
                }
                ShareOption("원본 그대로 공유", "사진 파일 + 기록 글 · 사진 속 위치 정보도 함께 전달됩니다") {
                    onDismiss()
                    Sharing.send(ctx, Uri.parse(entry.uri), entry, note)
                }
                ShareOption("기록 복사", "기록 · 날짜 · 지도 링크") {
                    onDismiss()
                    Sharing.copy(ctx, Sharing.text(entry, note))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun PlaceRow(e: Entry) {
    val ctx = LocalContext.current
    val lat = e.lat
    val lon = e.lon
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = if (lat != null) Stamp else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        if (lat != null && lon != null) {
            Text(
                String.format(Locale.US, "%.5f, %.5f", lat, lon),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { Sharing.openMap(ctx, lat, lon) }) { Text("지도에서 보기") }
        } else {
            Text(
                "이 사진에는 위치 정보가 없습니다",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShareOption(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val GOOGLE_PHOTOS = "com.google.android.apps.photos"

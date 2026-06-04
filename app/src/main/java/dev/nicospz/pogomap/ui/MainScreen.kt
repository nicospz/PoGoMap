package dev.nicospz.pogomap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import dev.nicospz.pogomap.R
import dev.nicospz.pogomap.data.MarkerTimeMode
import dev.nicospz.pogomap.data.SavedCameraPosition
import dev.nicospz.pogomap.domain.AdvancedFilterState
import dev.nicospz.pogomap.domain.MapFilter
import dev.nicospz.pogomap.domain.MarkerItem
import dev.nicospz.pogomap.domain.PogoMapObject
import dev.nicospz.pogomap.domain.RaidTypeFilter
import dev.nicospz.pogomap.domain.UiFilter
import dev.nicospz.pogomap.domain.ViewportBounds
import dev.nicospz.pogomap.domain.isEggRaid
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissStatus()
    }

    MaterialTheme {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                PogoGoogleMap(
                    markers = state.markers,
                    doneRaidIds = state.doneRaidIds,
                    markerTimeMode = state.markerTimeMode,
                    initialCameraPosition = state.savedCameraPosition,
                    onCameraIdle = viewModel::onCameraIdle,
                    onForegrounded = viewModel::onForegrounded,
                    onMarkerClick = viewModel::selectMarker,
                    modifier = Modifier.fillMaxSize(),
                )
                TopControls(
                    state = state,
                    onToggleFilter = viewModel::toggleFilter,
                    onOpenSettings = { viewModel.setSettingsVisible(true) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
                if (state.markers.size > MAX_RENDERED_MAP_MARKERS) {
                    ZoomHintPill(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 116.dp),
                    )
                }
                val filterSheetMode = state.filterSheetMode
                if (filterSheetMode != null) {
                    RaidFilterButton(
                        activeCount = filterSheetMode.activeCount(state.advancedFilters),
                        onClick = { showFilters = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 28.dp),
                    )
                }
                if (state.showSettings) {
                    SettingsSheet(
                        state = state,
                        onTimeModeSelected = viewModel::setMarkerTimeMode,
                        onSaveToken = viewModel::saveToken,
                        onClearToken = viewModel::clearToken,
                        onDismiss = { viewModel.setSettingsVisible(false) },
                    )
                }
                if (state.isLoading) {
                    MapStatusPill(
                        message = "Loading visible cells",
                        showProgress = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(18.dp),
                    )
                }
                CompactSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp),
                )
                state.selectedMarker?.let {
                    MarkerDetailsSheet(
                        marker = it,
                        isDone = it.objectId in state.doneRaidIds,
                        onToggleDone = { viewModel.toggleRaidDone(it) },
                        onDismiss = { viewModel.selectMarker(null) },
                    )
                }
                if (showFilters) {
                    filterSheetMode?.let { mode ->
                        FilterSheet(
                            state = state,
                            mode = mode,
                            onDismiss = { showFilters = false },
                            onApply = {
                                viewModel.applyAdvancedFilters(it, mode.targetFilter)
                                showFilters = false
                            },
                            onReset = viewModel::resetFilters,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        MapStatusPill(
            message = data.visuals.message,
        )
    }
}

@Composable
private fun MapStatusPill(
    message: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TopControls(
    state: MainUiState,
    onToggleFilter: (UiFilter) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PogoSurface.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 9.dp, end = 16.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PogoCategory.entries) { category ->
                CategoryPill(
                    category = category,
                    selected = state.filters == setOf(category.filter),
                    onClick = { onToggleFilter(category.filter) },
                )
            }
            item {
                SettingsTabButton(
                    objectCount = state.visibleObjects.size,
                    markerCount = state.markers.size,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun ZoomHintPill(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 330.dp),
        shape = RoundedCornerShape(40.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = ZoomInIcon, contentDescription = null, tint = PogoText)
            Column {
                Text(
                    "Zoom in to see more",
                    color = PogoText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Only a glimpse is shown",
                    color = PogoText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(
    category: PogoCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) PogoSelectedChip else PogoSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (category.iconResId == null) {
                Text(
                    text = category.badge,
                    color = category.tint,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )
            } else {
                Image(
                    painter = painterResource(category.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(category.iconSize),
                )
            }
            Text(
                text = category.label,
                color = PogoText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            if (selected) {
                Text("x", color = PogoText, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RaidFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = PogoControlShape,
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = SlidersIcon, contentDescription = null, tint = PogoText)
            Text("Filter", color = PogoText, style = MaterialTheme.typography.titleLarge)
            Surface(shape = PogoBadgeShape, color = PogoText) {
                Text(
                    text = activeCount.toString(),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsTabButton(
    objectCount: Int,
    markerCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = PogoControlShape,
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = SettingsIcon, contentDescription = null, tint = PogoText, modifier = Modifier.size(18.dp))
            Text("Settings", color = PogoText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Surface(shape = PogoBadgeShape, color = PogoText) {
                Text(
                    text = "$objectCount/$markerCount",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: MainUiState,
    mode: FilterSheetMode,
    onDismiss: () -> Unit,
    onApply: (AdvancedFilterState) -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(state.advancedFilters) { mutableStateOf(state.advancedFilters) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tiers = remember(state.visibleObjects) { MapFilter.raidTiers(state.visibleObjects) }
    val pokemon = remember(state.visibleObjects) { raidPokemonOptions(state.visibleObjects) }
    val maxPokemon = remember(state.visibleObjects) { MapFilter.maxPokemon(state.visibleObjects) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PogoSurface,
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.92f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        draft = AdvancedFilterState()
                        onReset()
                    },
                ) {
                    Text("RESET", color = Color(0xFF22C7A7), fontWeight = FontWeight.Bold)
                }
                Text(mode.title, color = PogoText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Surface(
                    modifier = Modifier.clickable(onClick = onDismiss),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = "x",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        color = Color(0xFF687278),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                if (mode == FilterSheetMode.Raids) {
                    item {
                        FilterSection(title = "Raid Type") {
                            FilterChip(
                                selected = RaidTypeFilter.ActiveRaids in draft.raidTypes,
                                onClick = { draft = draft.toggleRaidType(RaidTypeFilter.ActiveRaids) },
                                label = { Text("Active Raids") },
                            )
                            FilterChip(
                                selected = RaidTypeFilter.Eggs in draft.raidTypes,
                                onClick = { draft = draft.toggleRaidType(RaidTypeFilter.Eggs) },
                                label = { Text("Eggs") },
                            )
                        }
                    }
                    item {
                        FilterSection(title = "Tier") {
                            tiers.ifEmpty { listOf(1, 3, 4, 5, 6) }.forEach { tier ->
                                FilterChip(
                                    selected = tier in draft.raidTiers,
                                    onClick = { draft = draft.toggleTier(tier) },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(tier.toString())
                                            Text("R", fontWeight = FontWeight.Black)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Pokemon",
                                color = PogoText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (pokemon.isEmpty()) {
                                Text(
                                    text = "Move the map or enable Raids to populate Pokemon filters.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                val rows = (pokemon.size + 2) / 3
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.height((rows * 136).dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    userScrollEnabled = false,
                                ) {
                                    items(pokemon) { option ->
                                        PokemonFilterTile(
                                            option = option,
                                            selected = option.name in draft.raidPokemon,
                                            onClick = { draft = draft.togglePokemon(option.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        FilterSection(title = "Pokemon") {
                            if (maxPokemon.isEmpty()) {
                                Text(
                                    text = "Move the map or enable Max battles to populate Pokemon filters.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                maxPokemon.forEach { name ->
                                    FilterChip(
                                        selected = name in draft.maxPokemon,
                                        onClick = { draft = draft.toggleMaxPokemon(name) },
                                        label = { Text(name) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = PogoSurface, shadowElevation = 8.dp) {
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp).height(58.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PogoMint, contentColor = Color.White),
                ) {
                    Text("Apply Filters (${mode.activeCount(draft)})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: MainUiState,
    onTimeModeSelected: (MarkerTimeMode) -> Unit,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var token by remember(state.token) { mutableStateOf(state.token) }
    val typeCounts = remember(state.visibleObjects) {
        state.visibleObjects
            .groupingBy { it.mapObjectType ?: "UNKNOWN" }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    val markerCounts = remember(state.markers) {
        state.markers
            .groupingBy { it.category }
            .eachCount()
            .toList()
            .sortedBy { it.first.ordinal }
    }
    val markersByObject = remember(state.markers) { state.markers.groupBy { it.objectId } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PogoSurface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(0.92f),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "Settings",
                            color = PogoText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${state.visibleObjects.size} objects, ${state.markers.size} visible markers",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Surface(
                        modifier = Modifier.clickable(onClick = onDismiss),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            text = "x",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            color = Color(0xFF687278),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            item {
                DebugSection(title = "Time Labels") {
                    Text(
                        text = "Raid marker labels",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = state.markerTimeMode == MarkerTimeMode.Remaining,
                            onClick = { onTimeModeSelected(MarkerTimeMode.Remaining) },
                            label = { Text("Remaining time") },
                        )
                        FilterChip(
                            selected = state.markerTimeMode == MarkerTimeMode.RealTime,
                            onClick = { onTimeModeSelected(MarkerTimeMode.RealTime) },
                            label = { Text("Real time") },
                        )
                    }
                }
            }
            item {
                DebugSection(title = "Bearer Token") {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        minLines = 4,
                        maxLines = 7,
                        label = { Text("Paste token manually") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        OutlinedButton(onClick = onClearToken) {
                            Text("Clear")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onSaveToken(token) }, enabled = token.isNotBlank()) {
                            Text("Save")
                        }
                    }
                }
            }
            item {
                DebugSection(title = "Request Context") {
                    DebugLine("Token", if (state.token.isBlank()) "Missing" else "Present (${state.token.length} chars)")
                    DebugLine("Loading", state.isLoading.toString())
                    DebugLine("S2 cell level", state.s2CellLevel?.toString() ?: "Unknown")
                    DebugLine("S2 cells", state.s2CellCount.toString())
                    DebugLine("Filters", selectedFilterLabels(state).ifEmpty { listOf("All") }.joinToString())
                    DebugLine("Tier 5 boss", state.uniqueRaidBossHints.tier5Boss ?: "Unknown")
                    DebugLine("Shadow 5 boss", state.uniqueRaidBossHints.shadowTier5Boss ?: "Unknown")
                    DebugLine("Mega boss", state.uniqueRaidBossHints.megaBoss ?: "Unknown")
                }
            }
            item {
                DebugSection(title = "Backend Object Types") {
                    if (typeCounts.isEmpty()) {
                        DebugLine("Objects", "No loaded objects yet")
                    } else {
                        typeCounts.forEach { (type, count) -> DebugLine(type, count.toString()) }
                    }
                }
            }
            item {
                DebugSection(title = "Rendered Marker Categories") {
                    if (markerCounts.isEmpty()) {
                        DebugLine("Markers", "No markers for current filters")
                    } else {
                        markerCounts.forEach { (category, count) -> DebugLine(category.label, count.toString()) }
                    }
                }
            }
            items(state.visibleObjects, key = { it.id }) { obj ->
                DebugObjectCard(
                    obj = obj,
                    markers = markersByObject[obj.id].orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = PogoText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DebugObjectCard(
    obj: PogoMapObject,
    markers: List<MarkerItem>,
) {
    DebugSection(title = obj.mapObjectType ?: "UNKNOWN") {
        DebugLine("ID", obj.id)
        DebugLine("Markers", markers.map { it.category.label }.ifEmpty { listOf("None") }.joinToString())
        DebugLine("Lat/lng", obj.location?.let { "${it.latitude}, ${it.longitude}" } ?: "Missing")
        obj.pgoGym?.let { gym ->
            DebugLine("Gym team", gym.team ?: "Unknown")
            gym.raid?.let { raid ->
                DebugLine("Raid boss", MapFilter.raidDisplayName(raid))
                DebugLine("Raid rating", raid.rating?.toString() ?: "Unknown")
                DebugLine("Raid start", raid.startsAt.display())
                DebugLine("Raid hatch", raid.hatchAt.display())
                DebugLine("Raid end", raid.endsAt.display())
                DebugLine("Boss image", raid.bossImageUrl ?: "None")
                DebugLine("Egg image", raid.eggImageUrl ?: "None")
            }
        }
        obj.pgoPowerspot?.let { spot ->
            val battle = spot.overrideMaxBattle ?: spot.maxBattle
            DebugLine("Power spot battle", battle?.bossName ?: "None")
            DebugLine("Power spot rating", battle?.rating?.toString() ?: "Unknown")
            DebugLine("Power spot start", battle?.startsAt.display())
            DebugLine("Power spot end", battle?.endsAt.display())
        }
        obj.pgoRoute?.let { route ->
            DebugLine("Route distance", route.distanceMeters?.let { "${it.toInt()} m" } ?: "Unknown")
            DebugLine(
                "Route end",
                route.endPoi?.location?.let { "${it.latitude}, ${it.longitude}" } ?: "Missing",
            )
        }
        obj.event?.let { event ->
            DebugLine("Event title", event.title ?: "Untitled")
            DebugLine("Event start", event.startsAt.display())
            DebugLine("Event end", event.endsAt.display())
        }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            color = Color.DarkGray,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = PogoText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PokemonFilterTile(
    option: RaidPokemonOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(112.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(96.dp)
                .clickable(onClick = onClick)
                .then(
                    if (selected) {
                        Modifier.border(BorderStroke(2.dp, PogoTeal), RoundedCornerShape(8.dp))
                    } else {
                        Modifier.border(BorderStroke(1.dp, Color(0xFFD6E0DD)), RoundedCornerShape(8.dp))
                    },
                ),
            shape = RoundedCornerShape(8.dp),
            color = if (selected) Color(0xFFE8F4DE) else Color.White,
        ) {
            RemoteRaidImage(url = option.imageUrl, contentDescription = option.name)
        }
        Text(
            text = option.name,
            color = PogoText,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RemoteRaidImage(url: String?, contentDescription: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = url?.takeUnless { it.isBlank() }?.let { RaidImageCache.load(it) }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            Text("?", color = PogoText, style = MaterialTheme.typography.headlineMedium)
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

private fun selectedFilterLabels(state: MainUiState): List<String> {
    val categoryLabels = UiFilter.entries.filter { it in state.filters }.map { it.label }
    val detailLabels = buildList {
        if (RaidTypeFilter.ActiveRaids in state.advancedFilters.raidTypes) add("Active raids")
        if (RaidTypeFilter.Eggs in state.advancedFilters.raidTypes) add("Eggs")
        state.advancedFilters.raidTiers.sorted().forEach { add("Tier $it") }
        addAll(state.advancedFilters.raidPokemon.sorted())
        state.advancedFilters.maxPokemon.sorted().forEach { add("Max $it") }
    }
    return (categoryLabels + detailLabels).take(10)
}

private fun AdvancedFilterState.toggleRaidType(type: RaidTypeFilter): AdvancedFilterState {
    return copy(raidTypes = if (type in raidTypes) raidTypes - type else raidTypes + type)
}

private fun AdvancedFilterState.toggleTier(tier: Int): AdvancedFilterState {
    return copy(raidTiers = if (tier in raidTiers) raidTiers - tier else raidTiers + tier)
}

private fun AdvancedFilterState.togglePokemon(name: String): AdvancedFilterState {
    return copy(raidPokemon = if (name in raidPokemon) raidPokemon - name else raidPokemon + name)
}

private fun AdvancedFilterState.toggleMaxPokemon(name: String): AdvancedFilterState {
    return copy(maxPokemon = if (name in maxPokemon) maxPokemon - name else maxPokemon + name)
}

private val MainUiState.filterSheetMode: FilterSheetMode?
    get() = when (filters.singleOrNull()) {
        UiFilter.Raids -> FilterSheetMode.Raids
        UiFilter.DMax -> FilterSheetMode.DMax
        UiFilter.GMax -> FilterSheetMode.GMax
        else -> null
    }

private enum class FilterSheetMode(
    val title: String,
    val targetFilter: UiFilter,
) {
    Raids("Raids", UiFilter.Raids),
    DMax("D-Max", UiFilter.DMax),
    GMax("G-Max", UiFilter.GMax);

    fun activeCount(filters: AdvancedFilterState): Int = when (this) {
        Raids -> filters.raidActiveCount
        DMax, GMax -> filters.maxActiveCount
    }
}

@Composable
private fun PogoGoogleMap(
    markers: List<MarkerItem>,
    doneRaidIds: Set<String>,
    markerTimeMode: MarkerTimeMode,
    initialCameraPosition: SavedCameraPosition?,
    onCameraIdle: (ViewportBounds, Double, Double, Float) -> Unit,
    onForegrounded: () -> Unit,
    onMarkerClick: (MarkerItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            googleMap?.enableDeviceLocation(context)
            googleMap?.goToCurrentLocation(context)
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    MapLifecycle(mapView, onForegrounded)

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    googleMap = map
                    map.uiSettings.isMapToolbarEnabled = false
                    map.uiSettings.isCompassEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = false
                    if (hasLocationPermission) {
                        map.enableDeviceLocation(context)
                    }
                    map.setPadding(0, 0, 0, 0)
                    val startPosition = initialCameraPosition ?: SavedCameraPosition(35.681236, 139.767125, 13f)
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(startPosition.latitude, startPosition.longitude),
                            startPosition.zoom,
                        ),
                    )
                    map.setOnCameraIdleListener {
                        val bounds = map.projection.visibleRegion.latLngBounds
                        val center = map.cameraPosition.target
                        onCameraIdle(
                            ViewportBounds(
                                south = bounds.southwest.latitude,
                                west = bounds.southwest.longitude,
                                north = bounds.northeast.latitude,
                                east = bounds.northeast.longitude,
                            ),
                            center.latitude,
                            center.longitude,
                            map.cameraPosition.zoom,
                        )
                    }
                    map.setOnMarkerClickListener { marker ->
                        (marker.tag as? MarkerItem)?.let {
                            onMarkerClick(it)
                            true
                        } ?: false
                    }
                }
                mapView
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingActionButton(
                onClick = onForegrounded,
                shape = PogoControlShape,
                containerColor = Color.White,
                contentColor = PogoText,
            ) {
                Icon(
                    imageVector = RefreshIcon,
                    contentDescription = "Refresh map data",
                )
            }
            FloatingActionButton(
                onClick = {
                    if (context.hasLocationPermission()) {
                        hasLocationPermission = true
                        googleMap?.enableDeviceLocation(context)
                        googleMap?.goToCurrentLocation(context)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                shape = PogoControlShape,
                containerColor = Color.White,
                contentColor = PogoText,
            ) {
                Icon(
                    painter = painterResource(R.drawable.geo_locate),
                    contentDescription = "Current location",
                )
            }
        }
    }

    LaunchedEffect(googleMap, markers, doneRaidIds, markerTimeMode) {
        val map = googleMap ?: return@LaunchedEffect
        val renderedMarkers = markers.distributed(limit = MAX_RENDERED_MAP_MARKERS)
        map.clear()
        renderedMarkers.forEach { item ->
            val latLng = LatLng(item.coordinate.latitude, item.coordinate.longitude)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(item.title)
                    .snippet(item.snippet)
                    .icon(item.markerIcon(context, markerTimeMode))
                    .alpha(if (item.isDoneRaid(doneRaidIds)) 0.35f else 1f)
                    .anchor(0.5f, item.markerAnchorY),
            )
            marker?.tag = item
            if (item.category == UiFilter.Routes) {
                item.source.pgoRoute?.endPoi?.location?.let { end ->
                    map.addPolyline(
                        PolylineOptions()
                            .add(latLng, LatLng(end.latitude, end.longitude))
                            .color(0xff1976d2.toInt())
                            .width(5f),
                    )
                }
            }
        }
    }
}

private fun List<MarkerItem>.distributed(limit: Int): List<MarkerItem> {
    if (size <= limit) return this
    val step = (size - 1).toDouble() / (limit - 1).toDouble()
    return List(limit) { index -> this[(index * step).toInt()] }.distinctBy { it.objectId to it.category }
}

private const val MAX_RENDERED_MAP_MARKERS = 96

@SuppressLint("MissingPermission")
private fun GoogleMap.enableDeviceLocation(context: Context) {
    if (context.hasLocationPermission()) {
        isMyLocationEnabled = true
    }
}

@SuppressLint("MissingPermission")
private fun GoogleMap.goToCurrentLocation(context: Context) {
    if (!context.hasLocationPermission()) return
    val location = myLocation ?: context.bestLastKnownLocation()
    if (location == null) {
        Toast.makeText(context, "Current location unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val target = LatLng(location.latitude, location.longitude)
    val zoom = maxOf(cameraPosition.zoom, 15f)
    setPadding(0, 0, 0, 0)
    moveCamera(CameraUpdateFactory.newLatLngZoom(target, zoom))
}

private fun Context.hasLocationPermission(): Boolean {
    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("MissingPermission")
private fun Context.bestLastKnownLocation(): Location? {
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return manager.getProviders(true)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
}

@Composable
private fun MapLifecycle(mapView: MapView, onForegrounded: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView, onForegrounded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    onForegrounded()
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkerDetailsSheet(
    marker: MarkerItem,
    isDone: Boolean,
    onToggleDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(marker.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            DetailRow("Type", marker.category.label)
            DetailRow("Raw id", marker.objectId)
            DetailRow("Lat/lng", "${marker.coordinate.latitude}, ${marker.coordinate.longitude}")
            marker.source.pgoGym?.let { gym ->
                DetailRow("Team", gym.team ?: "Unknown")
                gym.raid?.let { raid ->
                    DetailRow("Boss", marker.title)
                    DetailRow("Rating", raid.rating?.toString() ?: "Unknown")
                    DetailRow("Start", raid.startsAt.display())
                    DetailRow("Hatch", raid.hatchAt.display())
                    DetailRow("End", raid.endsAt.display())
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { context.openGoogleMapsDirections(marker.coordinate.latitude, marker.coordinate.longitude) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Text(
                            text = "Directions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onToggleDone,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDone) PogoText else PogoMint,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            text = if (isDone) "Mark Not Done" else "Done",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            marker.source.pgoPowerspot?.let { spot ->
                val battle = spot.overrideMaxBattle ?: spot.maxBattle
                DetailRow("Boss", battle?.bossName ?: "Unknown")
                DetailRow("Rating", battle?.rating?.toString() ?: "Unknown")
                DetailRow("Start", battle?.startsAt.display())
                DetailRow("End", battle?.endsAt.display())
            }
            marker.source.pgoRoute?.distanceMeters?.let { DetailRow("Distance", "${it.toInt()} m") }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.width(92.dp), color = Color.DarkGray)
        Text(value, modifier = Modifier.weight(1f))
    }
}

private fun Context.openGoogleMapsDirections(latitude: Double, longitude: Double) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    startActivity(intent)
}

private enum class PogoCategory(
    val filter: UiFilter,
    val label: String,
    val badge: String,
    val tint: Color,
    val iconResId: Int? = null,
    val iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Raids(UiFilter.Raids, "Raids", "R", Color(0xFFFF7043), R.drawable.pgo_raid_boss, 22.dp),
    SuperMegaRaids(UiFilter.EventRaids, "Super Mega Raids", "S", Color(0xFF55C7CF), R.drawable.super_mega_level, 20.dp),
    Events(UiFilter.Events, "Events & Meetups", "E", Color(0xFFD65CF1), R.drawable.live_event, 20.dp),
    GMax(UiFilter.GMax, "G-Max", "G", Color(0xFFB568F2)),
    DMax(UiFilter.DMax, "D-Max", "D", Color(0xFF786BFF), R.drawable.pgo_dmax, 20.dp),
    Gyms(UiFilter.Gyms, "Gyms", "G", Color(0xFF4BA3FF), R.drawable.pgo_gym, 20.dp),
    Routes(UiFilter.Routes, "Routes", "R", Color(0xFF4DC784), R.drawable.pgo_route, 20.dp),
    Pokestops(UiFilter.Pokestops, "Pokestops", "P", Color(0xFF45B4FF), R.drawable.pgo_pokestop, 20.dp),
}

private data class RaidPokemonOption(
    val name: String,
    val imageUrl: String?,
)

private fun raidPokemonOptions(objects: Collection<PogoMapObject>): List<RaidPokemonOption> {
    return objects.mapNotNull { obj ->
        val raid = obj.pgoGym?.raid ?: return@mapNotNull null
        val name = raid.bossName?.trim()?.takeUnless { it.isBlank() } ?: return@mapNotNull null
        RaidPokemonOption(name = name, imageUrl = raid.bossImageUrl?.takeUnless { it.isBlank() })
    }.distinctBy { it.name }.sortedBy { it.name }
}

private val PogoSurface = Color(0xFFF7FCF6)
private val PogoSelectedChip = Color(0xFFE4F0D8)
private val PogoText = Color(0xFF44686C)
private val PogoMint = Color(0xFF82E0CF)
private val PogoTeal = Color(0xFF168FA1)
private val PogoControlShape = RoundedCornerShape(28.dp)
private val PogoBadgeShape = RoundedCornerShape(20.dp)

private val UiFilter.label: String
    get() = when (this) {
        UiFilter.Raids -> "Raids"
        UiFilter.EventRaids -> "Super Mega Raids"
        UiFilter.Events -> "Events & Meetups"
        UiFilter.GMax -> "G-Max"
        UiFilter.DMax -> "D-Max"
        UiFilter.Gyms -> "Gyms"
        UiFilter.Routes -> "Routes"
        UiFilter.Pokestops -> "Pokestops"
    }

private val MarkerItem.hue: Float
    get() = when (category) {
        UiFilter.Raids -> when (source.pgoGym?.raid?.rating ?: 0) {
            in 1..3 -> BitmapDescriptorFactory.HUE_ORANGE
            in 4..5 -> BitmapDescriptorFactory.HUE_RED
            else -> BitmapDescriptorFactory.HUE_ROSE
        }
        UiFilter.EventRaids -> BitmapDescriptorFactory.HUE_MAGENTA
        UiFilter.Events -> BitmapDescriptorFactory.HUE_YELLOW
        UiFilter.GMax, UiFilter.DMax -> BitmapDescriptorFactory.HUE_VIOLET
        UiFilter.Gyms -> when (source.pgoGym?.team?.uppercase()) {
            "VALOR", "RED" -> BitmapDescriptorFactory.HUE_RED
            "MYSTIC", "BLUE" -> BitmapDescriptorFactory.HUE_AZURE
            "INSTINCT", "YELLOW" -> BitmapDescriptorFactory.HUE_YELLOW
            else -> BitmapDescriptorFactory.HUE_GREEN
        }
        UiFilter.Routes -> BitmapDescriptorFactory.HUE_CYAN
        UiFilter.Pokestops -> BitmapDescriptorFactory.HUE_BLUE
    }

private val MarkerItem.markerAnchorY: Float
    get() = if (raidLabel() != null) 1f else 1f

private fun MarkerItem.isDoneRaid(doneRaidIds: Set<String>): Boolean {
    return (category == UiFilter.Raids || category == UiFilter.EventRaids) &&
        source.pgoGym?.raid != null &&
        objectId in doneRaidIds
}

private suspend fun MarkerItem.markerIcon(context: Context, markerTimeMode: MarkerTimeMode) = when {
    category == UiFilter.Pokestops -> BitmapDescriptorFactory.fromBitmap(
        createVectorMarkerBitmap(context, R.drawable.pgo_pokestop, 34),
    )
    category == UiFilter.Gyms -> BitmapDescriptorFactory.fromBitmap(
        createVectorMarkerBitmap(context, R.drawable.pgo_gym, 34, markerColorForHue(hue)),
    )
    category == UiFilter.Routes -> BitmapDescriptorFactory.fromBitmap(
        createVectorMarkerBitmap(context, R.drawable.pgo_route, 32),
    )
    category == UiFilter.Events -> BitmapDescriptorFactory.fromBitmap(
        createVectorMarkerBitmap(context, R.drawable.live_event, 32),
    )
    category == UiFilter.DMax -> BitmapDescriptorFactory.fromBitmap(
        createVectorMarkerBitmap(context, R.drawable.pgo_dmax, 34),
    )
    raidLabel(markerTimeMode) != null -> {
        val label = raidLabel(markerTimeMode).orEmpty()
        val isEgg = source.pgoGym?.raid.isEggRaid()
        val raidImage = raidImageUrl()?.let { RaidImageCache.load(it) }
        BitmapDescriptorFactory.fromBitmap(createRaidMarkerBitmap(context, label, hue, raidImage, isEgg))
    }
    else -> BitmapDescriptorFactory.defaultMarker(hue)
}

private fun MarkerItem.raidImageUrl(): String? {
    if (category != UiFilter.Raids && category != UiFilter.EventRaids) return null
    val raid = source.pgoGym?.raid ?: return null
    return if (raid.isEggRaid()) raid.eggImageUrl?.takeUnless { it.isBlank() } else raid.bossImageUrl?.takeUnless { it.isBlank() }
}

private fun MarkerItem.raidLabel(markerTimeMode: MarkerTimeMode = MarkerTimeMode.RealTime): String? {
    if (category != UiFilter.Raids && category != UiFilter.EventRaids) return null
    val raid = source.pgoGym?.raid ?: return null
    val isEgg = raid.isEggRaid()
    val bossName = title.trim().takeUnless { it.isBlank() }
        ?: raid.bossName?.trim()?.takeUnless { it.isBlank() }
    val boss = bossName ?: if (isEgg) "Egg" else "Raid"
    val now = Instant.now()
    val status = when {
        isEgg && raid.hatchAt != null && raid.hatchAt.isAfter(now) -> raid.hatchAt.formatMarkerStatus("Hatches", now, markerTimeMode)
        raid.startsAt != null && raid.startsAt.isAfter(now) -> raid.startsAt.formatMarkerStatus("Starts", now, markerTimeMode)
        raid.endsAt != null -> raid.endsAt.formatMarkerStatus("Ends", now, markerTimeMode)
        else -> null
    }
    return if (status == null) boss else "$boss\n$status"
}

private fun createRaidMarkerBitmap(
    context: Context,
    label: String,
    hue: Float,
    raidImage: Bitmap? = null,
    isEgg: Boolean = false,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(31, 31, 36)
        textSize = 12f * density
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val subTextPaint = Paint(textPaint).apply {
        textSize = 11f * density
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    val lines = label.lines().take(2)
    val textBounds = Rect()
    val maxTextWidth = lines.maxOf { line ->
        val paint = if (line == lines.first()) textPaint else subTextPaint
        paint.getTextBounds(line, 0, line.length, textBounds)
        textBounds.width()
    }
    val bubblePaddingX = (10f * density).toInt()
    val bubblePaddingY = (6f * density).toInt()
    val bubbleWidth = maxOf((72f * density).toInt(), maxTextWidth + bubblePaddingX * 2)
    val bubbleHeight = ((if (lines.size == 1) 28f else 42f) * density).toInt()
    val pinWidth = ((if (raidImage == null) 34f else 46f) * density).toInt()
    val pinHeight = ((if (raidImage == null) 46f else 50f) * density).toInt()
    val gap = (2f * density).toInt()
    val width = maxOf(bubbleWidth, pinWidth) + (6f * density).toInt()
    val height = bubbleHeight + gap + pinHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = width / 2f

    val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isEgg) android.graphics.Color.rgb(255, 218, 235) else android.graphics.Color.WHITE
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isEgg) {
            android.graphics.Color.argb(230, 199, 64, 124)
        } else {
            android.graphics.Color.argb(210, 70, 70, 86)
        }
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    val bubbleRect = RectF(
        centerX - bubbleWidth / 2f,
        0f,
        centerX + bubbleWidth / 2f,
        bubbleHeight.toFloat(),
    )
    canvas.drawRoundRect(bubbleRect, 10f * density, 10f * density, bubblePaint)
    canvas.drawRoundRect(bubbleRect, 10f * density, 10f * density, strokePaint)

    val firstBaseline = if (lines.size == 1) 18f * density else 17f * density
    canvas.drawText(lines.first(), centerX, firstBaseline, textPaint)
    if (lines.size > 1) {
        canvas.drawText(lines[1], centerX, 32f * density, subTextPaint)
    }

    val pinTop = bubbleHeight + gap
    if (raidImage == null) {
        drawPinMarker(canvas, centerX, pinTop.toFloat(), height.toFloat(), density, hue)
    } else {
        drawRaidImageMarker(canvas, raidImage, centerX, pinTop.toFloat(), density, hue)
    }
    return bitmap
}

private fun drawPinMarker(
    canvas: Canvas,
    centerX: Float,
    pinTop: Float,
    height: Float,
    density: Float,
    hue: Float,
) {
    val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = markerColorForHue(hue)
        style = Paint.Style.FILL
    }
    val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(190, 30, 30, 44)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    val circleRadius = 13f * density
    val circleCenterY = pinTop + 15f * density
    val path = Path().apply {
        addCircle(centerX, circleCenterY, circleRadius, Path.Direction.CW)
        moveTo(centerX - 8f * density, pinTop + 25f * density)
        lineTo(centerX, height)
        lineTo(centerX + 8f * density, pinTop + 25f * density)
        close()
    }
    canvas.drawPath(path, markerPaint)
    canvas.drawPath(path, markerStroke)
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(centerX, circleCenterY, 5f * density, it) }
}

private fun drawRaidImageMarker(
    canvas: Canvas,
    image: Bitmap,
    centerX: Float,
    pinTop: Float,
    density: Float,
    hue: Float,
) {
    val imageSize = 42f * density
    val left = centerX - imageSize / 2f
    val top = pinTop + 2f * density
    val imageRect = RectF(left, top, left + imageSize, top + imageSize)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(70, 0, 0, 0)
        style = Paint.Style.FILL
    }.also {
        canvas.drawOval(
            RectF(left + 5f * density, top + 30f * density, left + imageSize + 7f * density, top + 44f * density),
            it,
        )
    }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = markerColorForHue(hue)
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(centerX, top + imageSize / 2f, imageSize / 2f, it) }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }.also { canvas.drawCircle(centerX, top + imageSize / 2f, imageSize * 0.42f, it) }

    val clipPath = Path().apply {
        addCircle(centerX, top + imageSize / 2f, imageSize * 0.38f, Path.Direction.CW)
    }
    val saveCount = canvas.save()
    canvas.clipPath(clipPath)
    canvas.drawBitmap(image, null, imageRect, paint)
    canvas.restoreToCount(saveCount)
}

private fun createVectorMarkerBitmap(
    context: Context,
    drawableResId: Int,
    sizeDp: Int,
    tintColor: Int? = null,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val drawable: Drawable = context.resources.getDrawable(drawableResId, context.theme).mutate()
    tintColor?.let(drawable::setTint)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bitmap
}

private object RaidImageCache {
    private val client = OkHttpClient()
    private val bitmaps = mutableMapOf<String, Bitmap>()

    suspend fun load(url: String): Bitmap? {
        synchronized(bitmaps) {
            bitmaps[url]?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Image HTTP ${response.code}")
                    response.body?.byteStream()?.use(BitmapFactory::decodeStream)
                }
            }.getOrNull()
            if (bitmap != null) {
                synchronized(bitmaps) {
                    bitmaps[url] = bitmap
                }
            }
            bitmap
        }
    }
}

private fun markerColorForHue(hue: Float): Int {
    val hsv = floatArrayOf(hue, 0.78f, 0.93f)
    return android.graphics.Color.HSVToColor(hsv)
}

private val SlidersIcon: ImageVector
    get() {
        if (_slidersIcon != null) return _slidersIcon!!
        _slidersIcon = ImageVector.Builder(
            name = "Sliders",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color(0xFF44686C)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 6f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 18f)
                horizontalLineTo(20f)
                moveTo(9f, 4f)
                verticalLineTo(8f)
                moveTo(15f, 10f)
                verticalLineTo(14f)
                moveTo(11f, 16f)
                verticalLineTo(20f)
            }
        }.build()
        return _slidersIcon!!
    }

private var _slidersIcon: ImageVector? = null

private val ZoomInIcon: ImageVector
    get() {
        if (_zoomInIcon != null) return _zoomInIcon!!
        _zoomInIcon = ImageVector.Builder(
            name = "ZoomIn",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color(0xFF44686C)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19f, 11f)
                arcToRelative(8f, 8f, 0f, true, true, -16f, 0f)
                arcToRelative(8f, 8f, 0f, true, true, 16f, 0f)
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
                moveTo(11f, 8f)
                verticalLineTo(14f)
                moveTo(8f, 11f)
                horizontalLineTo(14f)
            }
        }.build()
        return _zoomInIcon!!
    }

private var _zoomInIcon: ImageVector? = null

private val RefreshIcon: ImageVector
    get() {
        if (_refreshIcon != null) return _refreshIcon!!
        _refreshIcon = ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color(0xFF44686C)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 11f)
                arcTo(8f, 8f, 0f, false, false, 6.1f, 5.6f)
                lineTo(4f, 8f)
                moveTo(4f, 4f)
                verticalLineTo(8f)
                horizontalLineTo(8f)
                moveTo(4f, 13f)
                arcTo(8f, 8f, 0f, false, false, 17.9f, 18.4f)
                lineTo(20f, 16f)
                moveTo(20f, 20f)
                verticalLineTo(16f)
                horizontalLineTo(16f)
            }
        }.build()
        return _refreshIcon!!
    }

private var _refreshIcon: ImageVector? = null

private val SettingsIcon: ImageVector
    get() {
        if (_settingsIcon != null) return _settingsIcon!!
        _settingsIcon = ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color(0xFF44686C)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 15f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
                moveTo(19.4f, 15f)
                arcTo(1.65f, 1.65f, 0f, false, false, 20f, 13.5f)
                arcTo(1.65f, 1.65f, 0f, false, false, 18.9f, 12f)
                arcTo(1.65f, 1.65f, 0f, false, false, 20f, 10.5f)
                arcTo(1.65f, 1.65f, 0f, false, false, 19.4f, 9f)
                lineTo(17.7f, 8f)
                arcTo(7.4f, 7.4f, 0f, false, false, 16.7f, 6.3f)
                lineTo(16.7f, 4.3f)
                arcTo(1.65f, 1.65f, 0f, false, false, 15.1f, 3f)
                horizontalLineTo(12.9f)
                arcTo(1.65f, 1.65f, 0f, false, false, 11.3f, 4.3f)
                lineTo(11.3f, 6.3f)
                arcTo(7.4f, 7.4f, 0f, false, false, 9.3f, 7.5f)
                lineTo(7.6f, 6.5f)
                arcTo(1.65f, 1.65f, 0f, false, false, 5.6f, 6.8f)
                lineTo(4.5f, 8.7f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.9f, 10.8f)
                lineTo(6.6f, 11.8f)
                arcTo(7.4f, 7.4f, 0f, false, false, 6.6f, 14.2f)
                lineTo(4.9f, 15.2f)
                arcTo(1.65f, 1.65f, 0f, false, false, 4.5f, 17.3f)
                lineTo(5.6f, 19.2f)
                arcTo(1.65f, 1.65f, 0f, false, false, 7.6f, 19.5f)
                lineTo(9.3f, 18.5f)
                arcTo(7.4f, 7.4f, 0f, false, false, 11.3f, 19.7f)
                lineTo(11.3f, 21.7f)
                arcTo(1.65f, 1.65f, 0f, false, false, 12.9f, 23f)
                horizontalLineTo(15.1f)
                arcTo(1.65f, 1.65f, 0f, false, false, 16.7f, 21.7f)
                lineTo(16.7f, 19.7f)
                arcTo(7.4f, 7.4f, 0f, false, false, 18.7f, 18.5f)
                lineTo(20.4f, 19.5f)
            }
        }.build()
        return _settingsIcon!!
    }

private var _settingsIcon: ImageVector? = null

private fun Instant.formatMarkerStatus(
    action: String,
    now: Instant,
    mode: MarkerTimeMode,
): String {
    return when (mode) {
        MarkerTimeMode.Remaining -> "$action in ${remainingMarkerTime(now)}"
        MarkerTimeMode.RealTime -> "$action at ${formatMarkerTime()}"
    }
}

private fun Instant.formatMarkerTime(): String {
    return markerTimeFormatter.withZone(ZoneId.systemDefault()).format(this)
}

private fun Instant.remainingMarkerTime(now: Instant): String {
    val totalMinutes = maxOf(0L, Duration.between(now, this).toMinutes())
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun Instant?.display(): String =
    this?.let { detailTimeFormatter.withZone(ZoneId.systemDefault()).format(it) } ?: "Unknown"

private val markerTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")

private val detailTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

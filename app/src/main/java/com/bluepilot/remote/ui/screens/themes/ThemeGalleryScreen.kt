package com.bluepilot.remote.ui.screens.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bluepilot.remote.ui.components.LocalReduceMotion
import com.bluepilot.remote.ui.components.carousel3D
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.theme.AppThemeSpec
import com.bluepilot.remote.ui.theme.BuiltInThemes
import com.bluepilot.remote.ui.theme.LocalAppTheme
import com.bluepilot.remote.viewmodel.SettingsViewModel
import kotlin.math.max

/**
 * SECTION 1 — Theme Gallery.
 *
 * Grid of theme cards. Each card contains a LIVE RENDERED MINI-PREVIEW of
 * the theme (miniature home screen: background orbs, status pill, tile
 * grid, nav dock — all painted from the actual AppThemeSpec tokens), the
 * theme name and an Apply button. The active theme is highlighted.
 * Applying persists via DataStore → whole app re-skins instantly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGalleryScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val app by viewModel.app.collectAsState()
    // SECTION 1 — search/filter, favorites, recents.
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val favorites = androidx.compose.runtime.remember(app.favoriteThemes) {
        com.bluepilot.remote.ui.theme.ThemeListCodec.decode(app.favoriteThemes)
    }
    val recents = androidx.compose.runtime.remember(app.recentThemes) {
        com.bluepilot.remote.ui.theme.ThemeListCodec.decode(app.recentThemes)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Themes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        // Gallery grouped by design family (one section per mockup set).
        val allFamilies: List<Pair<String, List<AppThemeSpec>>> = listOf(
            "AeroPad" to listOf(BuiltInThemes.AERO_GLASS),
            "Classic" to listOf(BuiltInThemes.PILOT_DARK, BuiltInThemes.PILOT_GLOW),
            "Liquid Glass" to listOf(BuiltInThemes.LIQUID_GLASS, BuiltInThemes.LIQUID_GLASS_LIGHT),
            "Glass × Material You" to listOf(BuiltInThemes.GLASS_YOU_DARK, BuiltInThemes.GLASS_YOU_LIGHT),
            "Hawaii Harmony" to listOf(BuiltInThemes.HAWAII_NIGHT, BuiltInThemes.HAWAII_DAY),
            "Cockpit HUD" to listOf(BuiltInThemes.COCKPIT_HUD, BuiltInThemes.DAY_FLIGHT),
            "Gaming" to listOf(BuiltInThemes.DARK_NEON, BuiltInThemes.CYBERPUNK, BuiltInThemes.SYNTHWAVE, BuiltInThemes.TACTICAL),
            "Ambient" to listOf(BuiltInThemes.GALAXY, BuiltInThemes.PASTEL, BuiltInThemes.CHROME),
            "More" to listOf(BuiltInThemes.OLED_BLACK, BuiltInThemes.MINIMAL_LIGHT)
        )
        // Search matches theme name, family name, or "dark"/"light".
        val families = if (query.isBlank()) allFamilies else allFamilies.mapNotNull { (family, specs) ->
            val q = query.trim()
            val matched = specs.filter { s ->
                s.name.contains(q, true) || family.contains(q, true) ||
                    (if (s.isDark) "dark" else "light").contains(q, true)
            }
            if (matched.isEmpty()) null else family to matched
        }

        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- Search field ----
            item(key = "search", span = { GridItemSpan(2) }) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search themes (name, family, dark/light)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // ---- Favorites quick row ----
            if (query.isBlank() && favorites.isNotEmpty()) {
                item(key = "favorites", span = { GridItemSpan(2) }) {
                    QuickThemeRow(
                        title = "★ Favorites",
                        ids = favorites,
                        activeId = app.themeId,
                        onApply = { viewModel.setThemeId(it) }
                    )
                }
            }
            // ---- Recently used quick row ----
            if (query.isBlank() && recents.isNotEmpty()) {
                item(key = "recents", span = { GridItemSpan(2) }) {
                    QuickThemeRow(
                        title = "Recently used",
                        ids = recents,
                        activeId = app.themeId,
                        onApply = { viewModel.setThemeId(it) }
                    )
                }
            }
            families.forEach { (family, specs) ->
                item(key = "header-" + family, span = { GridItemSpan(2) }) {
                    Text(
                        text = family,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(specs, key = { it.id }) { spec ->
                    // SECTION 6 - coverflow tilt from scroll position
                    val idx = BuiltInThemes.ALL.indexOfFirst { it.id == spec.id } / 2
                    ThemeCard(
                        spec = spec,
                        isActive = spec.id == app.themeId,
                        isFavorite = spec.id in favorites,
                        onToggleFavorite = { viewModel.toggleFavoriteTheme(spec.id) },
                        onApply = { viewModel.setThemeId(spec.id) },
                        carouselModifier = androidx.compose.ui.Modifier.carousel3D(
                            itemIndex = idx,
                            gridState = gridState,
                            itemHeightPx = 700f,
                            enabled = !LocalReduceMotion.current
                        )
                    )
                }
            }
        }
    }
}

/** SECTION 1 — horizontal quick-access chips (favorites / recents). */
@Composable
private fun QuickThemeRow(
    title: String,
    ids: List<String>,
    activeId: String,
    onApply: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ids.size, key = { ids[it] }) { i ->
                val spec = BuiltInThemes.byId(ids[i])
                val active = spec.id == activeId
                androidx.compose.material3.FilterChip(
                    selected = active,
                    onClick = { if (!active) onApply(spec.id) },
                    label = { Text(spec.name) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(spec.primary, CircleShape)
                                .border(1.dp, spec.outline, CircleShape)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ThemeCard(
    spec: AppThemeSpec,
    isActive: Boolean,
    onApply: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    carouselModifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val localTheme = LocalAppTheme.current
    val borderGlowColor = spec.glowColor ?: spec.primary
    val borderModifier = if (isActive) {
        Modifier.graphicsLayer {
            shadowElevation = 8.dp.toPx()
            shape = RoundedCornerShape(18.dp)
            clip = false
            spotShadowColor = borderGlowColor
            ambientShadowColor = borderGlowColor
        }.border(2.dp, borderGlowColor, RoundedCornerShape(18.dp))
    } else Modifier

    GlassCard(
        modifier = carouselModifier      // AUDIT FIX: coverflow tilt was computed but never applied
            .fillMaxWidth()
            .then(borderModifier),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
            ThemeMiniPreview(
                spec = spec,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (localTheme.monoFont) spec.name.uppercase() else spec.name,
                        style = if (localTheme.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (localTheme.monoFont) (if (spec.isDark) "DARK" else "LIGHT") else (if (spec.isDark) "Dark" else "Light"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // SECTION 1 — favorite/pin toggle.
                // SECTION 4: full 48dp touch target (icon stays 22dp).
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isFavorite) "Unpin theme" else "Pin theme",
                        tint = if (isFavorite) borderGlowColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (isActive) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Active theme",
                        tint = borderGlowColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                if (isActive) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(if (localTheme.monoFont) "APPLIED" else "Applied")
                    }
                } else {
                    Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                        Text(if (localTheme.monoFont) "APPLY" else "Apply")
                    }
                }
            }
        }
    }
}

/**
 * Live mini-preview (upgraded to match the v2-02 mockup):
 * a miniature phone UI painted from the spec's own tokens — status bar
 * "9:41", glass status pill with green dot + signal bars, four labeled
 * tiles each with a colored gel icon dot AND a text line, and a nav dock
 * with three icon dots. What you see is exactly what the theme produces.
 */
@Composable
private fun ThemeMiniPreview(spec: AppThemeSpec, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(spec.background, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxEdge = max(size.width, size.height)
            // Background orbs
            spec.backgroundOrbs.forEach { orb ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orb.color.copy(alpha = orb.alpha), orb.color.copy(alpha = 0f)),
                        center = Offset(size.width * orb.x, size.height * orb.y),
                        radius = maxEdge * orb.radius
                    ),
                    radius = maxEdge * orb.radius,
                    center = Offset(size.width * orb.x, size.height * orb.y)
                )
            }

            val pad = size.width * 0.07f
            val r = spec.cornerRadius * size.width / 360f * 2f
            val surface = spec.surface.copy(alpha = spec.surfaceAlpha)
            val corner = androidx.compose.ui.geometry.CornerRadius(r, r)
            val textDim = spec.onSurfaceVariant.copy(alpha = 0.85f)

            fun panel(x: Float, y: Float, w: Float, h: Float) {
                drawRoundRect(
                    color = surface,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = corner
                )
                if (spec.edgeGlow) {
                    drawRoundRect(
                        color = (spec.glowColor ?: spec.onSurface).copy(alpha = 0.30f),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = corner,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f)
                    )
                }
            }

            /** A tiny "text line" bar — mimics labels in the mockups. */
            fun textLine(x: Float, y: Float, w: Float, thickness: Float, color: androidx.compose.ui.graphics.Color) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(w, thickness),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thickness / 2f, thickness / 2f)
                )
            }

            // ---- Fake status bar: time line left, signal dots right ----
            val sbY = pad * 0.55f
            textLine(pad, sbY, size.width * 0.12f, size.height * 0.022f, textDim)
            for (i in 0..2) {
                drawCircle(
                    color = textDim,
                    radius = size.height * 0.011f,
                    center = Offset(size.width - pad - i * size.width * 0.045f, sbY + size.height * 0.011f)
                )
            }

            // ---- Status pill: green dot + device text line + signal bars ----
            val pillY = pad * 1.6f
            val pillH = size.height * 0.13f
            panel(pad, pillY, size.width - 2 * pad, pillH)
            drawCircle(
                color = spec.connected,
                radius = pillH * 0.18f,
                center = Offset(pad + pillH * 0.45f, pillY + pillH * 0.5f)
            )
            textLine(pad + pillH * 0.85f, pillY + pillH * 0.38f, size.width * 0.32f, pillH * 0.16f, spec.onSurface.copy(alpha = 0.9f))
            // signal bars
            for (i in 0..2) {
                val bh = pillH * (0.22f + 0.14f * i)
                drawRoundRect(
                    color = spec.primary,
                    topLeft = Offset(size.width - pad - pillH * 0.35f - i * pillH * 0.22f, pillY + pillH * 0.72f - bh),
                    size = androidx.compose.ui.geometry.Size(pillH * 0.12f, bh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                )
            }

            // ---- 2x2 tiles: gel icon square + label line each ----
            val gridTop = pillY + pillH + size.height * 0.05f
            val gap = size.width * 0.045f
            val tileW = (size.width - 2 * pad - gap) / 2f
            val tileH = size.height * 0.235f
            val gels = listOf(
                spec.primary, spec.secondary,
                spec.connected, spec.error
            )
            var i = 0
            for (row in 0..1) {
                for (col in 0..1) {
                    val x = pad + col * (tileW + gap)
                    val y = gridTop + row * (tileH + gap)
                    panel(x, y, tileW, tileH)
                    // gel icon: small rounded square, vivid
                    val gelSize = tileH * 0.34f
                    drawRoundRect(
                        color = gels[i % gels.size],
                        topLeft = Offset(x + tileW * 0.5f - gelSize / 2f, y + tileH * 0.16f),
                        size = androidx.compose.ui.geometry.Size(gelSize, gelSize),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(gelSize * 0.3f, gelSize * 0.3f)
                    )
                    // label line under the gel
                    textLine(x + tileW * 0.28f, y + tileH * 0.66f, tileW * 0.44f, tileH * 0.09f, textDim)
                    i++
                }
            }

            // ---- Nav dock with three icon dots ----
            val dockH = size.height * 0.095f
            val dockW = size.width * 0.56f
            val dockX = (size.width - dockW) / 2f
            val dockY = size.height - dockH - pad * 0.6f
            panel(dockX, dockY, dockW, dockH)
            for (d in 0..2) {
                drawCircle(
                    color = if (d == 0) spec.primary else textDim,
                    radius = dockH * 0.18f,
                    center = Offset(dockX + dockW * (0.25f + 0.25f * d), dockY + dockH * 0.5f)
                )
            }
        }
    }
}

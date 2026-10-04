package com.binc.gastapp.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.binc.gastapp.BuildConfig
import com.binc.gastapp.R
import com.binc.gastapp.ui.components.ScreenMargin
import com.binc.gastapp.ui.components.SectionHeader
import com.binc.gastapp.ui.components.TonalIcon
import com.binc.gastapp.ui.components.appear
import com.binc.gastapp.ui.components.rememberJustOpened
import com.binc.gastapp.ui.components.withExtra
import com.binc.gastapp.ui.format.dayMonthYear

private val LegalDocumentId.icon: ImageVector
    get() = when (this) {
        LegalDocumentId.Privacy -> Icons.Rounded.PrivacyTip
        LegalDocumentId.Terms -> Icons.Rounded.Description
    }

/** Lo esencial del aviso en cuatro lineas, para quien no va a leerlo completo. */
private val Essentials = listOf(
    Icons.Rounded.Block to R.string.legal_essential_no_sell,
    Icons.Rounded.Shield to R.string.legal_essential_no_tracking,
    Icons.Rounded.Key to R.string.legal_essential_password,
    Icons.Rounded.Sync to R.string.legal_essential_encrypted,
)

/**
 * Privacidad y legal: lo esencial, el aviso de privacidad, los terminos y el contacto.
 * Se abre desde el inicio (sin sesion), Registro y Ajustes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onBack: () -> Unit,
    onOpenDocument: (LegalDocumentId) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val opening = rememberJustOpened()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.privacy_and_legal)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding.withExtra(top = 4.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "esencial") { EssentialsCard(Modifier.appear(0, opening)) }

            item(key = "documentos-titulo") { SectionHeader(stringResource(R.string.documents), modifier = Modifier.appear(1, opening)) }
            item(key = "documentos") {
                Column(
                    Modifier
                        .padding(horizontal = ScreenMargin)
                        .appear(2, opening),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LegalDocumentId.entries.forEach { id ->
                        DocumentCard(legalDocument(id), onClick = { onOpenDocument(id) })
                    }
                }
            }

            item(key = "contacto-titulo") { SectionHeader(stringResource(R.string.questions_about_data), modifier = Modifier.appear(3, opening)) }
            item(key = "contacto") { ContactCard(Modifier.appear(4, opening)) }

            item(key = "version") {
                Text(
                    stringResource(R.string.legal_version_updated, BuildConfig.VERSION_NAME, dayMonthYear(LegalUpdated)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .appear(5, opening),
                )
            }
        }
    }
}

@Composable
private fun EssentialsCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLow)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TonalIcon(
                    Icons.Rounded.Lock,
                    containerColor = colors.primary,
                    color = colors.onPrimary,
                    size = 48.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.your_info_is_yours),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.essentials_short),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Essentials.forEach { (icon, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(document: LegalDocument, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TonalIcon(document.id.icon, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    document.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContactCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.legal_contact_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { uriHandler.openUri("mailto:$LegalContactEmail") },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(LegalContactEmail)
            }
        }
    }
}

/** Un documento completo: encabezado con fecha y resumen, y sus secciones numeradas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    documentId: LegalDocumentId,
    onBack: () -> Unit,
) {
    val document = legalDocument(documentId)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val opening = rememberJustOpened()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(document.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding.withExtra(top = 4.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "intro") { DocumentIntro(document, Modifier.appear(0, opening)) }
            itemsIndexed(document.sections, key = { _, section -> section.title }) { index, section ->
                SectionBlock(index + 1, section, Modifier.appear((index + 1).coerceAtMost(6), opening))
            }
            item(key = "contacto") {
                Spacer(Modifier.height(8.dp))
                ContactCard()
            }
        }
    }
}

@Composable
private fun DocumentIntro(document: LegalDocument, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TonalIcon(document.id.icon, containerColor = colors.primaryContainer, color = colors.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                Text(
                    stringResource(R.string.updated_on, dayMonthYear(LegalUpdated)),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text(document.intro, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
    }
}

@Composable
private fun SectionBlock(number: Int, section: LegalSection, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = ScreenMargin + 4.dp, end = ScreenMargin + 4.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
        }
        section.blocks.forEach { block ->
            when (block) {
                is LegalBlock.Paragraph -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                is LegalBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.items.forEach { item ->
                        Row {
                            Box(
                                Modifier
                                    .padding(top = 8.dp, start = 4.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

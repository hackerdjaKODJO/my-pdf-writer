package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Fixed-height PDF title bar used by single-page and continuous readers.
 *
 * Material TopAppBar keeps internal title/icon minimums and vertical padding
 * even when expandedHeight is reduced. This row owns the exact 42dp content
 * height, so the page navigator has no white gap below it.
 */
@Composable
fun PdfCompactTopBar(
    title: String,
    pageIndex: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPages: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onTitleClick: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onOpenMenu: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().statusBarsPadding(),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PdfCompactIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "뒤로",
                onClick = onBack,
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .then(
                            if (onTitleClick != null) {
                                Modifier
                                    .clickable(onClick = onTitleClick)
                                    .semantics {
                                        contentDescription = "$title, 최근 문서 열기"
                                    }
                            } else {
                                Modifier
                            },
                        ),
                )
                if (pageCount > 0) {
                    ViewerCompactPageNavigator(
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onOpenPages = onOpenPages,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            onSearch?.let { action ->
                PdfCompactIconButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = PdfTopChromeControl.SEARCH.label,
                    onClick = action,
                )
            }
            onOpenMenu?.let { action ->
                PdfCompactIconButton(
                    icon = Icons.Outlined.Menu,
                    contentDescription = PdfTopChromeControl.HUB.label,
                    onClick = action,
                )
            }
        }
    }
}

@Composable
private fun PdfCompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
    }
}

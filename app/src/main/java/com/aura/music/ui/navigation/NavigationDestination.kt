package com.aura.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

val FilledSearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FilledSearch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

enum class NavigationDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Principal", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("search", "Buscar", FilledSearchIcon, Icons.Outlined.Search),
    LIBRARY("library", "Biblioteca", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder)
}


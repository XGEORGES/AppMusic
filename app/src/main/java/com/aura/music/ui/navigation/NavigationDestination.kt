package com.aura.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Principal", Icons.Default.Home),
    SEARCH("search", "Buscar", Icons.Default.Search),
    LIBRARY("library", "Biblioteca", Icons.Default.Bookmark)
}

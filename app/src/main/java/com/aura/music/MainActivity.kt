package com.aura.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.aura.music.core.util.BatteryOptimizationHelper
import com.aura.music.ui.RootScreen
import com.aura.music.ui.explore.ExploreViewModel
import com.aura.music.ui.library.LibraryViewModel
import com.aura.music.ui.license.LicenseViewModel
import com.aura.music.ui.player.PlayerViewModel
import com.aura.music.ui.search.SearchViewModel
import com.aura.music.ui.theme.GeorgeMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val exploreViewModel: ExploreViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val licenseViewModel: LicenseViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Limpiar cualquier notificación remanente de guías anteriores
        BatteryOptimizationHelper.dismissGuideNotification(this)

        // Solicitar permiso de notificaciones en Android 13 (Tiramisu, API 33) o superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            GeorgeMusicTheme {
                RootScreen(
                    playerViewModel = playerViewModel,
                    exploreViewModel = exploreViewModel,
                    searchViewModel = searchViewModel,
                    libraryViewModel = libraryViewModel,
                    licenseViewModel = licenseViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Asegurar que no quede ninguna notificación de guía activa
        BatteryOptimizationHelper.dismissGuideNotification(this)
    }
}

package com.activaplan.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.activaplan.app.domain.model.ThemeMode
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

@Composable
actual fun ApplySystemAppearance(theme: ThemeMode, darkTheme: Boolean) {
    SideEffect {
        // Unspecified menyerahkan keputusan ke perangkat, jadi mode "Ikuti sistem" tetap ikut berubah.
        val style = when (theme) {
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            ThemeMode.SYSTEM -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
        }
        UIApplication.sharedApplication.windows.forEach { (it as? UIWindow)?.overrideUserInterfaceStyle = style }
    }
}

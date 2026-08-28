
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.HazeState
import org.kde.kdeconnect.di.jvmModule
import org.kde.kdeconnect.ui.LocalHazeState
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.context.startKoin


fun main() {
    startKoin {
        modules(jvmModule)
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "KDE Connect Desktop") {
            val navigator: Navigator = koinInject()
            CompositionLocalProvider(LocalHazeState provides HazeState()) {
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = { navigator.goBack() },
                    entryProvider = koinEntryProvider(),
                    transitionSpec = { fadeIn(tween()) togetherWith fadeOut(tween()) },
                    popTransitionSpec = { fadeIn(tween()) togetherWith fadeOut(tween()) },
                    predictivePopTransitionSpec = { fadeIn(tween()) togetherWith fadeOut(tween()) }
                )
            }
        }
    }
}

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "KDE Connect Desktop") {
        // Basic desktop entry point
        println("KDE Connect started on desktop")
    }
}

package org.kde.kdeconnect.ui.about

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.kde.kdeconnect.BuildConfig
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.alex_fiestas_task
import org.kde.kdeconnect.generated.resources.aniket_kumar_task
import org.kde.kdeconnect.generated.resources.apple_support
import org.kde.kdeconnect.generated.resources.bug_fixes_and_general_improvements
import org.kde.kdeconnect.generated.resources.developer
import org.kde.kdeconnect.generated.resources.everyone_else
import org.kde.kdeconnect.generated.resources.holger_kaelberer_task
import org.kde.kdeconnect.generated.resources.icon
import org.kde.kdeconnect.generated.resources.kde_connect
import org.kde.kdeconnect.generated.resources.maintainer_and_developer
import org.kde.kdeconnect.generated.resources.maxim_leshchenko_task
import org.kde.kdeconnect.generated.resources.saikrishna_arcot_task
import org.kde.kdeconnect.generated.resources.samoilenko_yuri_task
import org.kde.kdeconnect.generated.resources.shellwen_chen_task

data class AboutData(
    val name: StringResource = Res.string.kde_connect,
    val icon: DrawableResource = Res.drawable.icon,
    val versionName: String = BuildConfig.VERSION_NAME,
    val bugURL: String = "https://bugs.kde.org/enter_bug.cgi?product=kdeconnect&amp;component=android-application",
    val websiteURL: String = "https://kdeconnect.kde.org/",
    val sourceCodeURL: String = "https://invent.kde.org/network/kdeconnect-android/",
    val donateURL: String = "https://kde.org/community/donations/?app=kdeconnect-android",
    val authorsFooterText: StringResource = Res.string.everyone_else,
    val authors: List<AboutPerson> = listOf(
        AboutPerson("Albert Vaca Cintora", Res.string.maintainer_and_developer, "albertvaka+kde@gmail.com"),
        AboutPerson("Aleix Pol", Res.string.developer, "aleixpol@kde.org"),
        AboutPerson("Inoki Shaw", Res.string.apple_support, "veyx.shaw@gmail.com"),
        AboutPerson("Matthijs Tijink", Res.string.developer, "matthijstijink@gmail.com"),
        AboutPerson("Nicolas Fella", Res.string.developer, "nicolas.fella@gmx.de"),
        AboutPerson("Philip Cohn-Cort", Res.string.developer, "cliabhach@gmail.com"),
        AboutPerson("Piyush Aggarwal", Res.string.developer, "piyushaggarwal002@gmail.com"),
        AboutPerson("Simon Redman", Res.string.developer, "simon@ergotech.com"),
        AboutPerson("Erik Duisters", Res.string.developer, "e.duisters1@gmail.com"),
        AboutPerson("Isira Seneviratne", Res.string.developer, "isirasen96@gmail.com"),
        AboutPerson("Vineet Garg", Res.string.developer, "grg.vineet@gmail.com"),
        AboutPerson("Anjani Kumar", Res.string.bug_fixes_and_general_improvements, "anjanik012@gmail.com"),
        AboutPerson("Samoilenko Yuri", Res.string.samoilenko_yuri_task, "kinnalru@gmail.com"),
        AboutPerson("Aniket Kumar", Res.string.aniket_kumar_task, "anikketkumar786@gmail.com"),
        AboutPerson("Àlex Fiestas", Res.string.alex_fiestas_task, "afiestas@kde.org"),
        AboutPerson("Daniel Tang", Res.string.bug_fixes_and_general_improvements, "danielzgtg.opensource@gmail.com"),
        AboutPerson("Maxim Leshchenko", Res.string.maxim_leshchenko_task, "cnmaks90@gmail.com"),
        AboutPerson("Holger Kaelberer", Res.string.holger_kaelberer_task, "holger.k@elberer.de"),
        AboutPerson("Saikrishna Arcot", Res.string.saikrishna_arcot_task, "saiarcot895@gmail.com"),
        AboutPerson("ShellWen Chen", Res.string.shellwen_chen_task, "me@shellwen.com"),
    )
)

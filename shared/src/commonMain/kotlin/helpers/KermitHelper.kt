package org.kde.kdeconnect.helpers

import co.touchlab.kermit.Logger

val Any.LoggerTagged: Logger
    get() = Logger.withTag(this::class.simpleName ?: "KDEConnect")
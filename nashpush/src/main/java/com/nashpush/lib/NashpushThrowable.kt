package com.nashpush.lib

import java.lang.RuntimeException

internal class NashpushThrowable {
    internal class NashPushMainThreadException(message: String?) : RuntimeException(message)
}
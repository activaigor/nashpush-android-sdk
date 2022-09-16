package com.nashpush.sdk.notification.entity

data class MessageData(
    val clickActions: ArrayList<ClickActions>,
    val notification: NotificationBody?,
    val subscriberId: String?,
    val messageId: String?
)
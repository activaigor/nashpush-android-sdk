package com.nashpush.lib

data class MessageData(
    val clickActions: ArrayList<ClickActions>,
    val subscriberId: String,
    val messageId: String
)
/*

{"data":{"subscriber_id":"653","buttons":"[]","message_id":"NjUzfDYyZGE3NjZmZDRhNTRhYWRiN2JhNTNmOHwyODZ8Mjk4fDYxZjdjN2QzNTM2YzRkOTQwMTkxYTc1ZXw=",
"click_actions":"[{\"click_action_data\":\"https://google.com?from_source=653&sub=653&test=None&browser=Chrome&msg=NjUzfDYyZGE3NjZmZDRhNTRhYWRiN2JhNTNmOHwyODZ8Mjk4fDYxZjdjN2QzNTM2YzRkOTQwMTkxYTc1ZXw%3D\",\"click_action\":\"url\",\"action\":0,\"title\":null,\"icon\":null}]"
,"subscriber_token":"esGBvbM5AkY:APA91bHZfngdtroidHaK4Y9LgTlBwvpINM9yE1O4p5r5AgyfteZ0AXqCU0jKledzz-YQH4hF1Tn0iXurawmkwJUydOTRg_RWuDu8-yXs9tN2Coc5fiNacI1gXFcwgiqIrrcmGLTTBzFd"},
"from":"1070708186740","priority":"high",
"notification":{"title":"Welcome to None!","body":"Most likely you are under Mac OS X OS. Cool!","icon":"https://img.almightypush.com/image/d96da3057d8749aeb12ddb86278f781f/image.jpg","tag":"campaign-298","image":"https://img.almightypush.com/image/3d28fd0101404d39ad2505fb4d9b9762/image.jpg"},"fcmMessageId":"53f5ec92-f50c-45ef-a2df-0d31703c4fd3"}


 */
package com.stremio.mobile.auth

import com.facebook.CallbackManager

object FacebookLoginBridge {
    val callbackManager: CallbackManager = CallbackManager.Factory.create()
}

package com.openptt

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for OpenPTT.
 * @HiltAndroidApp triggers Hilt's code generation for dependency injection.
 */
@HiltAndroidApp
class OpenPTTApp : Application()

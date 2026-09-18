package com.esn.jarvis
import android.app.Application
class JarvisApplication:Application(){override fun onCreate(){super.onCreate();JarvisLlamaBackend.install()}}
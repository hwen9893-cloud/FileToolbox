package com.toolbox.filetoolbox

import android.app.Application

class FileToolboxApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: FileToolboxApp
            private set
    }
}

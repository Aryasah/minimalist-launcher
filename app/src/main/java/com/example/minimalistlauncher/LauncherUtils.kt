package com.example.minimalistlauncher

import android.content.Context
import android.content.Intent


fun Context.launchPackage(pkg: String) {
    val intent = packageManager.getLaunchIntentForPackage(pkg)
    intent?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(this)
    }
}
package com.cos.lspit.gesture.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        setContent {
            GestureSettingsScreen()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }
}
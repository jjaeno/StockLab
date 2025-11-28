package com.example.android

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

/**
 * StockLab Application 클래스
 * - Hilt 초기화
 * - Firebase 초기화
 */
@HiltAndroidApp
class StockLabApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase 초기화
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}


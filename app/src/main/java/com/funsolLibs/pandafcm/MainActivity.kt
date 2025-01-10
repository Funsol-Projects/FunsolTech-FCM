package com.funsolLibs.pandafcm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.funsol.fcm.FunsolFCM
import com.pandalibs.pandafcm.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FunsolFCM().setup(applicationContext, packageName)
    }
}
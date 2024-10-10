package com.saggitt.omega

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.launcher3.R


class FakeHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_home)
    }
}

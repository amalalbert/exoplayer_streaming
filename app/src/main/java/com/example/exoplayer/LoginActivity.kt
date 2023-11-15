package com.example.exoplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.bumptech.glide.Glide
import com.example.exoplayer.databinding.ActivityLoginBinding
import com.example.exoplayer.databinding.ActivityPlayerBinding

class LoginActivity : AppCompatActivity() {
    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityLoginBinding.inflate(layoutInflater)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        supportActionBar?.hide();
        Glide.with(viewBinding.root)
            .load(R.drawable.login_bg)
            .centerCrop()
            .into(viewBinding.ivLoginBg)
    }
}
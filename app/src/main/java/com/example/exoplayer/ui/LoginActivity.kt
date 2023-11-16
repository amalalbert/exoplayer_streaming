package com.example.exoplayer.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.exoplayer.R
import com.example.exoplayer.databinding.ActivityLoginBinding
import android.view.View.VISIBLE
import android.view.View.INVISIBLE
import android.view.View.GONE
import android.view.animation.AnimationUtils

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

        viewBinding.btFwd.setOnClickListener {
            viewBinding.clEmailWithButton.visibility = GONE
            viewBinding.clOtpWithButton.visibility = VISIBLE
        }

        viewBinding.backButton.setOnClickListener {
            viewBinding.clEmailWithButton.visibility = VISIBLE
            viewBinding.clOtpWithButton.visibility = GONE
        }
        viewBinding.submitButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this,R.anim.shake))
        }
    }
}
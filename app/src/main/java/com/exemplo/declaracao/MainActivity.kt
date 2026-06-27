package com.exemplo.declaracao

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.exemplo.declaracao.ui.DeclaracaoFragment
import com.exemplo.declaracao.ui.ZplConverterFragment
import com.exemplo.declaracao.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) loadFragment(ZplConverterFragment())
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_zpl -> { loadFragment(ZplConverterFragment()); true }
                R.id.nav_dec -> { loadFragment(DeclaracaoFragment()); true }
                else -> false
            }
        }
    }
    private fun loadFragment(f: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f).commit()
    }
}

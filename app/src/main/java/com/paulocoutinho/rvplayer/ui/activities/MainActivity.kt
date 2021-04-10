package com.paulocoutinho.rvplayer.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.ui.fragments.MainFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        setContentFragment()
    }

    private fun setContentFragment() {
        val fragment = MainFragment.newInstance()

        val fm = supportFragmentManager
        val ft = fm.beginTransaction()

        ft.replace(R.id.fragment_content, fragment)
        ft.commit()
    }

}
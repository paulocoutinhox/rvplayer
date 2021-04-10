package com.paulocoutinho.rvplayer.ui.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.ui.adapters.MyRecyclerAdapter
import com.paulocoutinho.rvplayer.ui.recyclerviews.MyRecyclerView
import com.paulocoutinho.rvplayer.util.Resources
import com.paulocoutinho.rvplayer.util.VerticalSpacingItemDecorator
import java.util.*

class MainActivity : AppCompatActivity() {

    private var list: MyRecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        list = findViewById(R.id.recycler_view)

        initRecyclerView()
    }

    private fun initRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        list?.layoutManager = layoutManager

        val itemDecorator = VerticalSpacingItemDecorator(10)
        list?.addItemDecoration(itemDecorator)

        val mediaObjects = ArrayList(listOf(*Resources.MEDIA_OBJECTS))
        list?.setListObjects(mediaObjects)

        val adapter = MyRecyclerAdapter(mediaObjects)
        list?.adapter = adapter

        Handler(Looper.getMainLooper()).postDelayed({
            list?.playVideo(false)
        }, 200)
    }

    override fun onDestroy() {
        list?.releasePlayer()

        super.onDestroy()
    }

}
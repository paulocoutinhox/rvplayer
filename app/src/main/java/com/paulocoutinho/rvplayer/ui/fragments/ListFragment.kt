package com.paulocoutinho.rvplayer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.ui.adapters.RVPRecyclerAdapter
import com.paulocoutinho.rvplayer.ui.interfaces.FragmentLifecycle
import com.paulocoutinho.rvplayer.ui.recyclerviews.MyRecyclerView
import com.paulocoutinho.rvplayer.ui.recyclerviews.RVPRecyclerView
import com.paulocoutinho.rvplayer.util.Logger
import com.paulocoutinho.rvplayer.util.Resources
import com.paulocoutinho.rvplayer.util.VerticalSpacingItemDecorator

class ListFragment : Fragment(), FragmentLifecycle {

    private var list: MyRecyclerView? = null
    private var adapter: RVPRecyclerAdapter? = null
    private var listStarted = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Logger.d("[ListFragment : onViewCreated]")

        list = view.findViewById(R.id.list)
        list?.autoPlayFirstState = RVPRecyclerView.AutoPlayState.ON
        list?.initialVolumeState = RVPRecyclerView.VolumeState.AUTO

        initRecyclerView()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Logger.d("[ListFragment : onCreateView]")

        return inflater.inflate(R.layout.fragment_list, container, false)
    }

    private fun initRecyclerView() {
        Logger.d("[ListFragment : initRecyclerView]")

        val layoutManager = LinearLayoutManager(context)
        list?.layoutManager = layoutManager
        list?.setHasFixedSize(true)

        val itemDecorator = VerticalSpacingItemDecorator(10)
        list?.addItemDecoration(itemDecorator)

        val mediaObjects = ArrayList(listOf(*Resources.MEDIA_OBJECTS))
        list?.setObjectList(mediaObjects)

        adapter = RVPRecyclerAdapter(mediaObjects)
        list?.adapter = adapter
        list?.videoPlayerSystemStart()
    }

    override fun onDestroy() {
        Logger.d("[ListFragment : onDestroy]")
        list?.videoPlayerSystemStop()
        super.onDestroy()
    }

    override fun onStop() {
        Logger.d("[ListFragment : onStop]")
        list?.videoPlayerPause()
        super.onStop()
    }

    override fun onResumeFragment() {
        Logger.d("[ListFragment : onResumeFragment]")

        if (listStarted) {
            list?.videoPlayerSystemRestart()
        } else {
            listStarted = true
            list?.videoPlayerSystemStart()
        }
    }

    override fun onPauseFragment() {
        Logger.d("[ListFragment : onPauseFragment]")
        list?.videoPlayerSystemStop()
    }

    companion object {

        @JvmStatic
        fun newInstance(): ListFragment {
            Logger.d("[ListFragment : newInstance]")

            return ListFragment().apply {
                arguments = Bundle().apply {
                    // ignore
                }
            }
        }
    }
}

package com.paulocoutinho.rvplayer.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.paulocoutinho.rvplayer.R
import com.paulocoutinho.rvplayer.ui.activities.MainActivity
import com.paulocoutinho.rvplayer.ui.adapters.FragmentAdapter
import com.paulocoutinho.rvplayer.ui.interfaces.FragmentLifecycle

class MainFragment : Fragment() {

    private lateinit var adapter: FragmentAdapter
    private lateinit var viewPager: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).setSupportActionBar(view.findViewById(R.id.toolbar))

        // view pager
        adapter = FragmentAdapter((activity as MainActivity))
        adapter.add(ListFragment.newInstance())
        adapter.add(ListFragment.newInstance())

        viewPager = view.findViewById(R.id.pager)
        viewPager.adapter = adapter

        // tab layout
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = "List ${(position + 1)}"
        }.attach()

        // lifecycle
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            var currentPosition = 0

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                val viewPagerPosition = viewPager.currentItem
                val fragmentLifecycleToShow = adapter.getItem(viewPagerPosition) as FragmentLifecycle

                if (currentPosition != viewPagerPosition) {
                    val fragmentLifecycleToHide = adapter.getItem(currentPosition) as FragmentLifecycle
                    fragmentLifecycleToHide.onPauseFragment()
                }

                fragmentLifecycleToShow.onResumeFragment()

                currentPosition = position
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    companion object {

        @JvmStatic
        fun newInstance(): MainFragment {
            return MainFragment().apply {
                arguments = Bundle().apply {
                    // ignore
                }
            }
        }
    }
}

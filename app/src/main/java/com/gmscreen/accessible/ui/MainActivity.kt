package com.gmscreen.accessible.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.gmscreen.accessible.R
import com.gmscreen.accessible.ui.fragment.ChannelsFragment
import com.gmscreen.accessible.ui.fragment.ConnectionFragment
import com.gmscreen.accessible.ui.fragment.RemoteFragment
import com.gmscreen.accessible.ui.fragment.SettingsFragment
import com.gmscreen.accessible.ui.fragment.VoiceFragment
import com.gmscreen.accessible.util.AccessibilityHelper

class MainActivity : AppCompatActivity() {

    private lateinit var topToolbar: MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        topToolbar = findViewById(R.id.topToolbar)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        setSupportActionBar(topToolbar)

        setupViewPagerAndTabs()
    }

    private fun setupViewPagerAndTabs() {
        val fragments: List<Fragment> = listOf(
            ConnectionFragment(),
            ChannelsFragment(),
            RemoteFragment(),
            VoiceFragment(),
            SettingsFragment()
        )

        val tabTitles = listOf(
            getString(R.string.tab_connection),
            getString(R.string.tab_channels),
            getString(R.string.tab_remote),
            getString(R.string.tab_voice),
            getString(R.string.tab_settings)
        )

        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 4 // Keep all 5 tabs in memory to prevent reload or focus loss

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
            tab.contentDescription = "سربرگ ${position + 1} از ۵: ${tabTitles[position]}"
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val title = tabTitles[position]
                topToolbar.subtitle = title
                AccessibilityHelper.announce(topToolbar, "ورود به سربرگ $title")
            }
        })
    }
}

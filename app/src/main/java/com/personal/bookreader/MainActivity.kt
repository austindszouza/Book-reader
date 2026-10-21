package com.personal.bookreader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.navigation.compose.rememberNavController
import com.personal.bookreader.ui.navigation.BookReaderNavGraph
import org.readium.r2.navigator.epub.EpubNavigatorFragment

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Register dummy factory BEFORE super.onCreate so Android can restore any fragment
        // during recreation or process death without throwing InstantiationException.
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()

        super.onCreate(savedInstanceState)

        // Readium's EpubNavigatorFragment cannot be restored with DummyPublication (it throws
        // RestorationNotSupportedException in onResume). Since ReaderScreen and ReaderViewModel
        // manage the publication and navigator lifecycle dynamically in Compose, remove any
        // restored dummy fragments before onResume. ReaderScreen will mount the real navigator.
        val dummyFragments = supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>()
        if (dummyFragments.isNotEmpty()) {
            supportFragmentManager.commitNow {
                dummyFragments.forEach { remove(it) }
            }
        }

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            BookReaderNavGraph(navController = navController)
        }
    }
}

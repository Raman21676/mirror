package com.mirror.host.gallery

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mirror.host.R

/**
 * Activity for browsing target device gallery.
 */
class GalleryBrowserActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_browser)
    }
}

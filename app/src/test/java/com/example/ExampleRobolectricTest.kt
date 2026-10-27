package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.XtreamAccount
import com.example.data.repository.XtreamRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Foul TV", appName)
    }

    @Test
    fun `xtream library stream candidates follow mp4 then mkv then m3u8 order`() {
        val repo = XtreamRepository()
        val account = XtreamAccount(
            id = "1",
            name = "Test",
            host = "http://grace4k.com/",
            username = "user1",
            password = "pass1"
        )
        val candidates = repo.buildLibraryStreamCandidates(
            account = account,
            streamId = "1501766",
            isSeries = false
        )
        assertEquals(
            listOf(
                "http://grace4k.com/movie/user1/pass1/1501766.mp4",
                "http://grace4k.com/movie/user1/pass1/1501766.mkv",
                "http://grace4k.com/movie/user1/pass1/1501766.m3u8"
            ),
            candidates
        )
    }
}

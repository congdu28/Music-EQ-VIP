package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Music EQ", appName)
  }

  @Test
  fun `test lrc parser with timestamps`() {
    val lrc = """
      [ti:Việt Nam Ơi]
      [ar:Minh Beta]
      [00:00.00]Giai điệu mở đầu
      [00:05.50]Việt Nam hỡi, Việt Nam ơi
      [00:12.00]Tự hào hát mãi lên Việt Nam ơi
    """.trimIndent()

    val parsed = LrcParser.parse(lrc, 0L)
    assertEquals(3, parsed.lines.size)
    assertEquals("Việt Nam Ơi", parsed.title)
    assertEquals("Minh Beta", parsed.artist)
    assertEquals(5500L, parsed.lines[1].timeMs)

    val activeIdx = LrcParser.findActiveLineIndex(parsed.lines, 7000L)
    assertEquals(1, activeIdx)
  }
}


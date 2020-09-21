/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.SettingProvider
import com.github.k1rakishou.chan.Chan.instance
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.model.SiteBoards
import com.github.k1rakishou.chan.core.net.JsonReaderRequest
import com.github.k1rakishou.chan.core.site.http.HttpCallManager
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.json.JsonSettings
import com.github.k1rakishou.json.JsonSettingsProvider
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import java.security.SecureRandom
import java.util.*
import kotlin.coroutines.CoroutineContext

abstract class SiteBase : Site, CoroutineScope {
  private val job = SupervisorJob()

  protected val httpCallManager: HttpCallManager by lazy { instance(HttpCallManager::class.java) }
  protected val okHttpClient: ProxiedOkHttpClient by lazy { instance(ProxiedOkHttpClient::class.java) }
  protected val siteManager: SiteManager by lazy { instance(SiteManager::class.java) }
  protected val imageLoaderV2: ImageLoaderV2 by lazy { instance(ImageLoaderV2::class.java) }
  protected val archivesManager: ArchivesManager by lazy { instance(ArchivesManager::class.java) }
  protected val boardManager: BoardManager by lazy { instance(BoardManager::class.java) }
  protected val postFilterManager: PostFilterManager by lazy { instance(PostFilterManager::class.java) }
  protected val mockReplyManager: MockReplyManager by lazy { instance(MockReplyManager::class.java) }

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("SiteBase")

  @JvmField
  protected var settingsProvider: SettingProvider? = null
  private var userSettings: JsonSettings? = null
  private var initialized = false

  override fun initialize(userSettings: JsonSettings) {
    if (initialized) {
      throw IllegalStateException("Already initialized")
    }

    this.userSettings = userSettings

    settingsProvider = JsonSettingsProvider(userSettings) {
      siteManager.updateUserSettings(this@SiteBase.siteDescriptor(), userSettings)
    }

    initializeSettings()

    initialized = true
  }

  override fun loadBoardInfo(callback: ((ModularResult<JsonReaderRequest.JsonReaderResponse<SiteBoards>>) -> Unit)?) {
    if (!enabled()) {
      val response = JsonReaderRequest.JsonReaderResponse.Success(SiteBoards(siteDescriptor(), emptyList()))
      callback?.invoke(ModularResult.value(response))
      return
    }

    if (!boardsType().canList) {
      val response = JsonReaderRequest.JsonReaderResponse.Success(SiteBoards(siteDescriptor(), emptyList()))
      callback?.invoke(ModularResult.value(response))
      return
    }

    launch(Dispatchers.IO) {
      val result = ModularResult.Try {
        boardManager.awaitUntilInitialized()
        Logger.d(TAG, "Requesting boards for site ${name()}")

        val readerResponse = actions().boards()

        when (readerResponse) {
          is JsonReaderRequest.JsonReaderResponse.Success -> {
            boardManager.createOrUpdateBoards(readerResponse.result.boards)

            Logger.d(TAG, "Got the boards for site ${readerResponse.result.siteDescriptor.siteName}, " +
              "boards count = ${readerResponse.result.boards.size}")
          }
          is JsonReaderRequest.JsonReaderResponse.ServerError -> {
            Logger.e(TAG, "Couldn't get site boards, bad status code: ${readerResponse.statusCode}")
          }
          is JsonReaderRequest.JsonReaderResponse.UnknownServerError -> {
            Logger.e(TAG, "Couldn't get site boards, unknown server error", readerResponse.error)
          }
          is JsonReaderRequest.JsonReaderResponse.ParsingError -> {
            Logger.e(TAG, "Couldn't get site boards, parsing error", readerResponse.error)
          }
        }

        return@Try readerResponse
      }

      if (callback != null) {
        callback.invoke(result)
        return@launch
      }

      if (result is ModularResult.Error) {
        Logger.e(TAG, "loadBoardInfo error", result.error)
        throw result.error
      }
    }
  }

  override fun board(code: String): ChanBoard? {
    // TODO(KurobaEx): wait until initialized
    val boardDescriptor = BoardDescriptor.create(siteDescriptor(), code)
    return boardManager.byBoardDescriptor(boardDescriptor)
  }

  override fun settings(): List<SiteSetting> {
    return ArrayList()
  }

  open fun initializeSettings() {
    // no-op
  }

  override suspend fun createBoard(boardName: String, boardCode: String): ModularResult<ChanBoard?> {
    val existing = board(boardCode)
    if (existing != null) {
      return ModularResult.value(existing)
    }

    val boardDescriptor = BoardDescriptor.create(siteDescriptor(), boardCode)
    val board = ChanBoard.create(boardDescriptor, boardName)

    val created = boardManager.createOrUpdateBoards(listOf(board))
    if (!created) {
      return ModularResult.value(null)
    }

    return ModularResult.value(board)
  }

  companion object {
    private const val TAG = "SiteBase"
    val secureRandom: Random = SecureRandom()

    @JvmStatic
    fun containsMediaHostUrl(desiredSiteUrl: HttpUrl, mediaHosts: Array<String>): Boolean {
      val host = desiredSiteUrl.host
      for (mediaHost in mediaHosts) {
        if (host == mediaHost) {
          return true
        }

        if (host == "www.$mediaHost") {
          return true
        }
      }

      return false
    }
  }
}
package com.github.k1rakishou.model.data.descriptor

import com.github.k1rakishou.common.mutableListWithCap

sealed class ChanDescriptor {
  abstract fun isThreadDescriptor(): Boolean
  abstract fun isCatalogDescriptor(): Boolean

  abstract fun siteName(): String
  abstract fun boardCode(): String
  abstract fun threadDescriptorOrNull(): ThreadDescriptor?
  abstract fun catalogDescriptor(): CatalogDescriptor
  abstract fun threadNoOrNull(): Long?

  abstract fun boardDescriptor(): BoardDescriptor
  abstract fun siteDescriptor(): SiteDescriptor
  abstract fun serializeToString(): String

  @JvmOverloads
  fun toThreadDescriptor(threadNo: Long? = null): ThreadDescriptor {
    return when (this) {
      is ThreadDescriptor -> {
        check(this.threadNo == threadNo) {
          "Attempt to convert thread descriptor (${this.threadNo}) " +
                  "into another thread descriptor (${threadNo})"
        }

        this
      }
      is CatalogDescriptor -> {
        ThreadDescriptor.create(boardDescriptor, threadNo!!)
      }
    }
  }

  class ThreadDescriptor private constructor(
    val boardDescriptor: BoardDescriptor,
    val threadNo: Long
  ) : ChanDescriptor() {

    override fun isThreadDescriptor(): Boolean = true
    override fun isCatalogDescriptor(): Boolean = false

    override fun siteName(): String = boardDescriptor.siteDescriptor.siteName
    override fun boardCode(): String = boardDescriptor.boardCode
    override fun threadDescriptorOrNull(): ThreadDescriptor? = this
    override fun catalogDescriptor(): CatalogDescriptor = CatalogDescriptor.create(boardDescriptor)
    override fun threadNoOrNull(): Long? = threadNo
    override fun siteDescriptor(): SiteDescriptor = boardDescriptor.siteDescriptor
    override fun boardDescriptor(): BoardDescriptor = boardDescriptor

    override fun serializeToString(): String {
      return "TD_${boardDescriptor.siteName()}_${boardDescriptor.boardCode}_${threadNo}"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is ThreadDescriptor) return false

      if (boardDescriptor != other.boardDescriptor) return false
      if (threadNo != other.threadNo) return false

      return true
    }

    override fun hashCode(): Int {
      var result = boardDescriptor.hashCode()
      result = 31 * result + threadNo.hashCode()
      return result
    }

    override fun toString(): String {
      return "TD{${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}/$threadNo}"
    }

    companion object {
      // TODO(KurobaEx v0.7.0): use a growable array instead of array list here to get rid of @Synchronized
      private val CACHE = mutableListWithCap<ThreadDescriptor>(256)

      @JvmStatic
      fun create(siteName: String, boardCode: String, threadNo: Long): ThreadDescriptor {
        require(threadNo > 0) { "Bad threadId: $threadNo" }

        return create(BoardDescriptor.create(siteName, boardCode), threadNo)
      }

      @JvmStatic
      fun create(chanDescriptor: ChanDescriptor, threadNo: Long): ThreadDescriptor {
        return create(chanDescriptor.boardDescriptor(), threadNo)
      }

      @JvmStatic
      @Synchronized
      fun create(boardDescriptor: BoardDescriptor, threadNo: Long): ThreadDescriptor {
        require(threadNo > 0) { "Bad threadId: $threadNo" }

        for (threadDescriptor in CACHE) {
          if (threadDescriptor.boardDescriptor === boardDescriptor
            && threadDescriptor.threadNo == threadNo
          ) {
            return threadDescriptor
          }
        }

        val newThreadDescriptor = ThreadDescriptor(
          boardDescriptor,
          threadNo
        )

        CACHE.add(newThreadDescriptor)
        return newThreadDescriptor
      }

      fun fromDescriptorParcelable(descriptorParcelable: DescriptorParcelable): ThreadDescriptor {
        require(descriptorParcelable.isThreadDescriptor()) { "Not a thread descriptor type" }

        return create(
          descriptorParcelable.siteName,
          descriptorParcelable.boardCode,
          descriptorParcelable.threadNo!!
        )
      }
    }
  }

  class CatalogDescriptor private constructor(
    val boardDescriptor: BoardDescriptor
  ) : ChanDescriptor() {
    override fun isThreadDescriptor(): Boolean = false
    override fun isCatalogDescriptor(): Boolean = true

    override fun siteName(): String = boardDescriptor.siteDescriptor.siteName
    override fun boardCode(): String = boardDescriptor.boardCode
    override fun threadDescriptorOrNull(): ThreadDescriptor? = null
    override fun catalogDescriptor(): CatalogDescriptor = this
    override fun threadNoOrNull(): Long? = null
    override fun siteDescriptor(): SiteDescriptor = boardDescriptor.siteDescriptor
    override fun boardDescriptor(): BoardDescriptor = boardDescriptor

    override fun serializeToString(): String {
      return "CD_${boardDescriptor.siteName()}_${boardDescriptor.boardCode}"
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is CatalogDescriptor) return false

      if (boardDescriptor != other.boardDescriptor) return false

      return true
    }

    override fun hashCode(): Int {
      return boardDescriptor.hashCode()
    }

    override fun toString(): String {
      return "CD{${boardDescriptor.siteDescriptor.siteName}/${boardDescriptor.boardCode}}"
    }

    companion object {
      // TODO(KurobaEx v0.7.0): use a growable array instead of array list here to get rid of @Synchronized
      private val CACHE = mutableListWithCap<CatalogDescriptor>(64)

      fun fromDescriptorParcelable(descriptorParcelable: DescriptorParcelable): CatalogDescriptor {
        require(!descriptorParcelable.isThreadDescriptor()) { "Not a catalog descriptor type" }

        return create(
          descriptorParcelable.siteName,
          descriptorParcelable.boardCode
        )
      }

      @JvmStatic
      fun create(boardDescriptor: BoardDescriptor): CatalogDescriptor {
        return create(boardDescriptor.siteName(), boardDescriptor.boardCode)
      }

      @JvmStatic
      @Synchronized
      fun create(siteNameInput: String, boardCodeInput: String): CatalogDescriptor {
        val siteName = siteNameInput.intern()
        val boardCode = boardCodeInput.intern()

        for (catalogDescriptor in CACHE) {
          if (catalogDescriptor.boardDescriptor.siteName() === siteName
            && catalogDescriptor.boardDescriptor.boardCode === boardCode
          ) {
            return catalogDescriptor
          }
        }

        val newCatalogDescriptor = CatalogDescriptor(
          BoardDescriptor.create(siteName, boardCode)
        )

        CACHE.add(newCatalogDescriptor)
        return newCatalogDescriptor
      }
    }
  }
}
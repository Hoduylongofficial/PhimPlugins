package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

class NguonCProvider : MainAPI() {

    // ── Cấu hình cơ bản ──────────────────────────────────────────
    override var mainUrl              = "https://phim.nguonc.com"
    override var name                 = "NguonC"
    override val hasMainPage          = true
    override var lang                 = "vi"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    // ────────────────────────────────────────────────────────────
    // DATA CLASSES — map chính xác JSON thực tế của NguonC
    // ────────────────────────────────────────────────────────────

    data class NguonCListResponse(
        @JsonProperty("status")   val status: String,
        @JsonProperty("paginate") val paginate: Paginate?,
        @JsonProperty("items")    val items: List<NguonCItem>
    )

    data class Paginate(
        @JsonProperty("current_page")    val currentPage: Int,
        @JsonProperty("total_page")      val totalPage: Int,
        @JsonProperty("total_items")     val totalItems: Int,
        @JsonProperty("items_per_page")  val itemsPerPage: Int
    )

    data class NguonCItem(
        @JsonProperty("id")               val id: String?,
        @JsonProperty("name")             val name: String,
        @JsonProperty("slug")             val slug: String,
        @JsonProperty("original_name")    val originalName: String?,
        @JsonProperty("thumb_url")        val thumbUrl: String?,
        @JsonProperty("poster_url")       val posterUrl: String?,
        @JsonProperty("description")      val description: String?,
        @JsonProperty("total_episodes")   val totalEpisodes: Int?,
        @JsonProperty("current_episode")  val currentEpisode: String?,
        @JsonProperty("time")             val time: String?,
        @JsonProperty("quality")          val quality: String?,
        @JsonProperty("language")         val language: String?,
        @JsonProperty("director")         val director: String?,
        @JsonProperty("casts")            val casts: String?
    )

    // ── Chi tiết phim ────────────────────────────────────────────
    data class NguonCDetailResponse(
        @JsonProperty("status") val status: String,
        @JsonProperty("movie")  val movie: NguonCMovieDetail?
    )

    data class NguonCMovieDetail(
        @JsonProperty("id")               val id: String?,
        @JsonProperty("name")             val name: String,
        @JsonProperty("slug")             val slug: String,
        @JsonProperty("original_name")    val originalName: String?,
        @JsonProperty("thumb_url")        val thumbUrl: String?,
        @JsonProperty("poster_url")       val posterUrl: String?,
        @JsonProperty("description")      val description: String?,
        @JsonProperty("total_episodes")   val totalEpisodes: Int?,
        @JsonProperty("current_episode")  val currentEpisode: String?,
        @JsonProperty("time")             val time: String?,
        @JsonProperty("quality")          val quality: String?,
        @JsonProperty("language")         val language: String?,
        @JsonProperty("director")         val director: String?,
        @JsonProperty("casts")            val casts: String?,
        // category là object với key "1","2","3","4" — dùng Map
        @JsonProperty("category")         val category: Map<String, CategoryGroup>?,
        @JsonProperty("episodes")         val episodes: List<EpisodeServer>?
    )

    data class CategoryGroup(
        @JsonProperty("group") val group: GroupInfo?,
        @JsonProperty("list")  val list: List<CategoryItem>?
    )

    data class GroupInfo(
        @JsonProperty("id")   val id: String?,
        @JsonProperty("name") val name: String
    )

    data class CategoryItem(
        @JsonProperty("id")   val id: String?,
        @JsonProperty("name") val name: String
    )

    data class EpisodeServer(
        @JsonProperty("server_name") val serverName: String,
        @JsonProperty("items")       val items: List<EpisodeItem>
    )

    data class EpisodeItem(
        @JsonProperty("name")  val name: String,   // "1", "2", ... hoặc "FULL"
        @JsonProperty("slug")  val slug: String,
        @JsonProperty("embed") val embed: String?,  // link iframe
        @JsonProperty("m3u8")  val m3u8: String?    // link HLS trực tiếp ✅
    )

    // ────────────────────────────────────────────────────────────
    // MAIN PAGE — Các danh mục hiển thị trên trang chủ
    // ────────────────────────────────────────────────────────────
    override val mainPage = mainPageOf(
        "$mainUrl/api/films/phim-moi-cap-nhat?page="              to "🆕 Phim Mới Cập Nhật",
        "$mainUrl/api/films/danh-sach/phim-dang-chieu?page="      to "🎬 Phim Đang Chiếu",
        "$mainUrl/api/films/danh-sach/phim-le?page="              to "🎥 Phim Lẻ",
        "$mainUrl/api/films/danh-sach/phim-bo?page="              to "📺 Phim Bộ",
        "$mainUrl/api/films/danh-sach/hoat-hinh?page="            to "🎨 Hoạt Hình",
        "$mainUrl/api/films/the-loai/hanh-dong?page="             to "💥 Hành Động",
        "$mainUrl/api/films/the-loai/tinh-cam?page="              to "❤️ Tình Cảm",
        "$mainUrl/api/films/quoc-gia/han-quoc?page="              to "🇰🇷 Phim Hàn",
        "$mainUrl/api/films/quoc-gia/trung-quoc?page="            to "🇨🇳 Phim Trung",
        "$mainUrl/api/films/quoc-gia/au-my?page="                 to "🇺🇸 Phim Âu Mỹ",
        "$mainUrl/api/films/nam-phat-hanh/2025?page="             to "📅 Phim 2025",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = request.data + page
        val response = app.get(url)
        val data     = parseJson<NguonCListResponse>(response.text)

        val homeItems = data.items.map { it.toSearchResponse() }
        // Kiểm tra còn trang tiếp theo không
        val hasNextPage = (data.paginate?.currentPage ?: 1) < (data.paginate?.totalPage ?: 1)

        return newHomePageResponse(
            name        = request.name,
            list        = homeItems,
            hasNext     = hasNextPage
        )
    }

    // ────────────────────────────────────────────────────────────
    // TÌM KIẾM
    // ────────────────────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> {
        val url      = "$mainUrl/api/films/search?keyword=${query.encodeUrl()}"
        val response = app.get(url)
        val data     = parseJson<NguonCListResponse>(response.text)
        return data.items.map { it.toSearchResponse() }
    }

    // ────────────────────────────────────────────────────────────
    // CHI TIẾT PHIM + DANH SÁCH TẬP
    // ────────────────────────────────────────────────────────────
    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val data     = parseJson<NguonCDetailResponse>(response.text)
        val movie    = data.movie ?: throw ErrorLoadingException("Không tải được thông tin phim")

        // Parse thể loại từ category map
        val genres = movie.category?.values
            ?.filter { it.group?.name == "Thể loại" }
            ?.flatMap { it.list ?: emptyList() }
            ?.map { it.name }

        // Parse năm từ category
        val yearStr = movie.category?.values
            ?.filter { it.group?.name == "Năm" }
            ?.flatMap { it.list ?: emptyList() }
            ?.firstOrNull()?.name
        val year = yearStr?.toIntOrNull()

        // Parse diễn viên
        val actors = movie.casts
            ?.split(",")
            ?.map { ActorData(Actor(it.trim())) }

        // Xác định loại phim: lẻ hay bộ
        val isMovie = movie.totalEpisodes == 1 ||
                      movie.currentEpisode?.uppercase()?.contains("FULL") == true

        // ── Phim lẻ ──────────────────────────────────────────────
        if (isMovie) {
            val videoLink = movie.episodes
                ?.firstOrNull()
                ?.items
                ?.firstOrNull()
                ?.let { ep -> ep.m3u8?.ifEmpty { null } ?: ep.embed }
                ?: ""

            return newMovieLoadResponse(
                name    = movie.name,
                url     = url,
                type    = TvType.Movie,
                dataUrl = videoLink
            ) {
                this.posterUrl   = movie.posterUrl ?: movie.thumbUrl
                this.plot        = movie.description?.stripHtml()
                this.year        = year
                this.tags        = genres
                this.actors      = actors
                addActors(actors)
            }
        }

        // ── Phim bộ — map từng server + từng tập ─────────────────
        val episodes = movie.episodes?.flatMap { server ->
            server.items.mapIndexed { index, ep ->
                // Ưu tiên M3U8, fallback sang embed
                val videoData = ep.m3u8?.ifEmpty { null } ?: ep.embed ?: ""

                Episode(
                    data        = videoData,
                    name        = "Tập ${ep.name}",
                    episode     = ep.name.toIntOrNull() ?: (index + 1),
                    description = server.serverName  // ghi rõ server nào
                )
            }
        } ?: emptyList()

        return newTvSeriesLoadResponse(
            name     = movie.name,
            url      = url,
            type     = TvType.TvSeries,
            episodes = episodes
        ) {
            this.posterUrl = movie.posterUrl ?: movie.thumbUrl
            this.plot      = movie.description?.stripHtml()
            this.year      = year
            this.tags      = genres
            this.actors    = actors
        }
    }

    // ────────────────────────────────────────────────────────────
    // LOAD LINKS — Phát video
    // ────────────────────────────────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        return when {
            // ✅ Link M3U8 trực tiếp — phát ngay không cần extractor
            data.contains(".m3u8") -> {
                callback(
                    ExtractorLink(
                        source   = name,
                        name     = "$name HLS",
                        url      = data,
                        referer  = mainUrl,
                        quality  = Qualities.Unknown.value,
                        isM3u8   = true
                    )
                )
                true
            }
            // 🔗 Link embed — dùng extractor tự động của CloudStream
            data.contains("embed") || data.startsWith("http") -> {
                loadExtractor(data, mainUrl, subtitleCallback, callback)
                true
            }
            else -> false
        }
    }

    // ────────────────────────────────────────────────────────────
    // HELPER — Convert NguonCItem → SearchResponse
    // ────────────────────────────────────────────────────────────
    private fun NguonCItem.toSearchResponse(): SearchResponse {
        val detailUrl = "$mainUrl/api/film/$slug"
        val type      = if (totalEpisodes == 1 ||
                            currentEpisode?.uppercase()?.contains("FULL") == true)
                            TvType.Movie else TvType.TvSeries

        return newMovieSearchResponse(
            name = name,
            url  = detailUrl,
            type = type
        ) {
            this.posterUrl = posterUrl ?: thumbUrl
        }
    }
}

package com.phimplugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

class NguonCProvider : MainAPI() {
    override var mainUrl            = "https://phim.nguonc.com"
    override var name               = "NguonC"
    override val hasMainPage        = true
    override var lang               = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes     = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    data class ListResponse(
        @JsonProperty("status")   val status: String,
        @JsonProperty("paginate") val paginate: Paginate?,
        @JsonProperty("items")    val items: List<MovieItem>
    )
    data class Paginate(
        @JsonProperty("current_page") val currentPage: Int,
        @JsonProperty("total_page")   val totalPage: Int
    )
    data class MovieItem(
        @JsonProperty("name")            val name: String,
        @JsonProperty("slug")            val slug: String,
        @JsonProperty("thumb_url")       val thumbUrl: String?,
        @JsonProperty("poster_url")      val posterUrl: String?,
        @JsonProperty("total_episodes")  val totalEpisodes: Int?,
        @JsonProperty("current_episode") val currentEpisode: String?
    )
    data class DetailResponse(
        @JsonProperty("status") val status: String,
        @JsonProperty("movie")  val movie: MovieDetail?
    )
    data class MovieDetail(
        @JsonProperty("name")            val name: String,
        @JsonProperty("slug")            val slug: String,
        @JsonProperty("thumb_url")       val thumbUrl: String?,
        @JsonProperty("poster_url")      val posterUrl: String?,
        @JsonProperty("description")     val description: String?,
        @JsonProperty("total_episodes")  val totalEpisodes: Int?,
        @JsonProperty("current_episode") val currentEpisode: String?,
        @JsonProperty("casts")           val casts: String?,
        @JsonProperty("category")        val category: Map<String, CategoryGroup>?,
        @JsonProperty("episodes")        val episodes: List<EpisodeServer>?
    )
    data class CategoryGroup(
        @JsonProperty("group") val group: GroupInfo?,
        @JsonProperty("list")  val list: List<CategoryItem>?
    )
    data class GroupInfo(@JsonProperty("name") val name: String)
    data class CategoryItem(@JsonProperty("name") val name: String)
    data class EpisodeServer(
        @JsonProperty("server_name") val serverName: String,
        @JsonProperty("items")       val items: List<EpisodeItem>
    )
    data class EpisodeItem(
        @JsonProperty("name")  val name: String,
        @JsonProperty("slug")  val slug: String,
        @JsonProperty("embed") val embed: String?,
        @JsonProperty("m3u8")  val m3u8: String?
    )

    private fun MovieItem.toSearchResponse(): SearchResponse {
        val url = "$mainUrl/api/film/$slug"
        return newMovieSearchResponse(name, url, TvType.Movie) {
            this.posterUrl = this@toSearchResponse.posterUrl ?: thumbUrl
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/api/films/phim-moi-cap-nhat?page="   to "🆕 Mới Cập Nhật",
        "$mainUrl/api/films/danh-sach/phim-le?page="   to "🎬 Phim Lẻ",
        "$mainUrl/api/films/danh-sach/phim-bo?page="   to "📺 Phim Bộ",
        "$mainUrl/api/films/danh-sach/hoat-hinh?page=" to "🎨 Hoạt Hình",
        "$mainUrl/api/films/the-loai/hanh-dong?page="  to "💥 Hành Động",
        "$mainUrl/api/films/the-loai/tinh-cam?page="   to "❤️ Tình Cảm",
        "$mainUrl/api/films/quoc-gia/han-quoc?page="   to "🇰🇷 Hàn Quốc",
        "$mainUrl/api/films/quoc-gia/au-my?page="      to "🇺🇸 Âu Mỹ",
        "$mainUrl/api/films/quoc-gia/trung-quoc?page=" to "🇨🇳 Trung Quốc",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data    = parseJson<ListResponse>(app.get(request.data + page).text)
        val hasNext = (data.paginate?.currentPage ?: 1) < (data.paginate?.totalPage ?: 1)
        return newHomePageResponse(request.name, data.items.map { it.toSearchResponse() }, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val data = parseJson<ListResponse>(
            app.get("$mainUrl/api/films/search?keyword=$encoded").text
        )
        return data.items.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val data  = parseJson<DetailResponse>(app.get(url).text)
        val movie = data.movie ?: throw ErrorLoadingException("Không tải được phim")
        val actors = movie.casts?.split(",")?.map { ActorData(Actor(it.trim())) }
        val tags   = movie.category?.values?.flatMap { it.list?.map { c -> c.name } ?: emptyList() }

        val isSingle = (movie.totalEpisodes ?: 0) <= 1

        if (isSingle) {
            val link = movie.episodes
                ?.firstOrNull()?.items?.firstOrNull()
                ?.let { it.m3u8?.ifEmpty { null } ?: it.embed } ?: ""
            return newMovieLoadResponse(movie.name, url, TvType.Movie, link) {
                posterUrl   = movie.posterUrl ?: movie.thumbUrl
                plot        = movie.description?.replace(Regex("<[^>]*>"), "")
                this.year   = null
                this.tags   = tags
                this.actors = actors
            }
        } else {
            val episodes = movie.episodes?.flatMap { server ->
                server.items.mapIndexed { idx, ep ->
                    newEpisode(ep.m3u8?.ifEmpty { null } ?: ep.embed ?: "") {
                        name    = ep.name
                        episode = idx + 1
                    }
                }
            } ?: emptyList()
            return newTvSeriesLoadResponse(movie.name, url, TvType.TvSeries, episodes) {
                posterUrl   = movie.posterUrl ?: movie.thumbUrl
                plot        = movie.description?.replace(Regex("<[^>]*>"), "")
                this.year   = null
                this.tags   = tags
                this.actors = actors
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.contains(".m3u8")) {
            callback(
                newExtractorLink(name, name, data) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                    this.isM3u8  = true
                }
            )
        } else {
            loadExtractor(data, mainUrl, subtitleCallback, callback)
        }
        return true
    }
}

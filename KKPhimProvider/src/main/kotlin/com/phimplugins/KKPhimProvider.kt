package com.phimplugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

class KKPhimProvider : MainAPI() {
    override var mainUrl            = "https://phimapi.com"
    override var name               = "KKPhim"
    override val hasMainPage        = true
    override var lang               = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes     = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    data class ListResponse(
        @JsonProperty("status")     val status: Boolean,
        @JsonProperty("items")      val items: List<MovieItem>,
        @JsonProperty("pagination") val pagination: Pagination?
    )
    data class Pagination(
        @JsonProperty("currentPage") val currentPage: Int,
        @JsonProperty("totalPages")  val totalPages: Int
    )
    data class MovieItem(
        @JsonProperty("name")       val name: String,
        @JsonProperty("slug")       val slug: String,
        @JsonProperty("thumb_url")  val thumbUrl: String?,
        @JsonProperty("poster_url") val posterUrl: String?,
        @JsonProperty("year")       val year: Int?,
        @JsonProperty("type")       val type: String?
    )
    data class DetailResponse(
        @JsonProperty("status")   val status: Boolean,
        @JsonProperty("movie")    val movie: MovieDetail?,
        @JsonProperty("episodes") val episodes: List<EpisodeServer>?
    )
    data class MovieDetail(
        @JsonProperty("name")       val name: String,
        @JsonProperty("slug")       val slug: String,
        @JsonProperty("thumb_url")  val thumbUrl: String?,
        @JsonProperty("poster_url") val posterUrl: String?,
        @JsonProperty("content")    val content: String?,
        @JsonProperty("year")       val year: Int?,
        @JsonProperty("type")       val type: String?,
        @JsonProperty("actor")      val actor: List<String>?,
        @JsonProperty("category")   val category: List<Category>?
    )
    data class Category(@JsonProperty("name") val name: String)
    data class EpisodeServer(
        @JsonProperty("server_name") val serverName: String,
        @JsonProperty("server_data") val serverData: List<EpisodeItem>
    )
    data class EpisodeItem(
        @JsonProperty("name")       val name: String,
        @JsonProperty("link_embed") val linkEmbed: String,
        @JsonProperty("link_m3u8")  val linkM3u8: String
    )

    private fun MovieItem.toSearchResponse(): SearchResponse {
        val url = "$mainUrl/v1/api/phim/$slug"
        return newMovieSearchResponse(name, url, TvType.Movie) {
            this.posterUrl = this@toSearchResponse.posterUrl ?: thumbUrl
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/v1/api/danh-sach/phim-moi?page="  to "🆕 Phim Mới",
        "$mainUrl/v1/api/danh-sach/phim-le?page="   to "🎬 Phim Lẻ",
        "$mainUrl/v1/api/danh-sach/phim-bo?page="   to "📺 Phim Bộ",
        "$mainUrl/v1/api/danh-sach/hoat-hinh?page=" to "🎨 Hoạt Hình",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data    = parseJson<ListResponse>(app.get(request.data + page).text)
        val hasNext = (data.pagination?.currentPage ?: 1) < (data.pagination?.totalPages ?: 1)
        return newHomePageResponse(request.name, data.items.map { it.toSearchResponse() }, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val data = parseJson<ListResponse>(
            app.get("$mainUrl/v1/api/tim-kiem?keyword=$encoded").text
        )
        return data.items.map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val data  = parseJson<DetailResponse>(app.get(url).text)
        val movie = data.movie ?: throw ErrorLoadingException("Không tải được phim")
        val actors = movie.actor?.map { ActorData(Actor(it)) }

        if (movie.type == "single") {
            val link = data.episodes
                ?.firstOrNull()?.serverData?.firstOrNull()
                ?.let { it.linkM3u8.ifEmpty { null } ?: it.linkEmbed } ?: ""
            return newMovieLoadResponse(movie.name, url, TvType.Movie, link) {
                posterUrl = movie.posterUrl ?: movie.thumbUrl
                plot      = movie.content?.replace(Regex("<[^>]*>"), "")
                year      = movie.year
                tags      = movie.category?.map { it.name }
                this.actors = actors
            }
        } else {
            val episodes = data.episodes?.flatMap { server ->
                server.serverData.mapIndexed { idx, ep ->
                    newEpisode(ep.linkM3u8.ifEmpty { ep.linkEmbed }) {
                        name    = ep.name
                        episode = idx + 1
                    }
                }
            } ?: emptyList()
            return newTvSeriesLoadResponse(movie.name, url, TvType.TvSeries, episodes) {
                posterUrl = movie.posterUrl ?: movie.thumbUrl
                plot      = movie.content?.replace(Regex("<[^>]*>"), "")
                year      = movie.year
                tags      = movie.category?.map { it.name }
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

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val data = parseJson<ListResponse>(
            app.get("$mainUrl/v1/api/tim-kiem?keyword=$encoded").text
        )
        return data.items.map { it.toSearchResponse() }
    }
}

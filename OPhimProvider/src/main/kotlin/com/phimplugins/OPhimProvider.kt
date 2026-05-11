package com.phimplugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty

class OPhimProvider : MainAPI() {
    override var mainUrl            = "https://ophim1.com"
    override var name               = "OPhim"
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
        @JsonProperty("name")            val name: String,
        @JsonProperty("slug")            val slug: String,
        @JsonProperty("thumb_url")       val thumbUrl: String?,
        @JsonProperty("poster_url")      val posterUrl: String?,
        @JsonProperty("year")            val year: Int?,
        @JsonProperty("type")            val type: String?,
        @JsonProperty("episode_current") val episodeCurrent: String?
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

    override val mainPage = mainPageOf(
        "$mainUrl/api/v1/movie?sort_field=_id&sort_type=desc&page=" to "🆕 Phim Mới",
        "$mainUrl/api/v1/movie?type=single&page="                   to "🎬 Phim Lẻ",
        "$mainUrl/api/v1/movie?type=series&page="                   to "📺 Phim Bộ",
        "$mainUrl/api/v1/movie?type=hoathinh&page="                 to "🎨 Hoạt Hình",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data    = parseJson<ListResponse>(app.get(request.data + page).text)
        val hasNext = (data.pagination?.currentPage ?: 1) < (data.pagination?.totalPages ?: 1)
        return newHomePageResponse(request.name, data.items.map { it.toSearchResponse() }, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = parseJson<ListResponse>(
            app.get("$mainUrl/api/v1/movie?keyword=${query.encodeUrl()}").text
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
                posterUrl   = movie.posterUrl ?: movie.thumbUrl
                plot        = movie.content?.stripHtml()
                year        = movie.year
                tags        = movie.category?.map { it.name }
                this.actors = actors
            }
        }

        val episodes = data.episodes?.flatMap { server ->
            server.serverData.mapIndexed { idx, ep ->
                Episode(
                    data        = ep.linkM3u8.ifEmpty { ep.linkEmbed },
                    name        = "Tập ${ep.name}",
                    episode     = ep.name.toIntOrNull() ?: (idx + 1),
                    description = server.serverName
                )
            }
        } ?: emptyList()

        return newTvSeriesLoadResponse(movie.name, url, TvType.TvSeries, episodes) {
            posterUrl   = movie.posterUrl ?: movie.thumbUrl
            plot        = movie.content?.stripHtml()
            year        = movie.year
            tags        = movie.category?.map { it.name }
            this.actors = actors
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        return if (data.contains(".m3u8")) {
            callback(ExtractorLink(name, "$name HLS", data, mainUrl, Qualities.Unknown.value, true))
            true
        } else {
            loadExtractor(data, mainUrl, subtitleCallback, callback)
            true
        }
    }

    private fun MovieItem.toSearchResponse(): SearchResponse {
        val posterFull = when {
            posterUrl?.startsWith("http") == true -> posterUrl
            posterUrl != null -> "$mainUrl$posterUrl"
            else -> null
        }
        return newMovieSearchResponse(
            name = name,
            url  = "$mainUrl/phim/$slug",
            type = if (type == "single") TvType.Movie else TvType.TvSeries
        ) { posterUrl = posterFull }
    }
}

package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class OPhimProvider : MainAPI() {

    // ── Cấu hình cơ bản ──────────────────────────────────────────
    override var mainUrl              = "https://ophim1.com"
    override var name                 = "OPhim"
    override val hasMainPage          = true
    override var lang                 = "vi"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    // ── Data classes map JSON response ───────────────────────────
    data class OPhimMovie(
        val _id: String,
        val name: String,
        val slug: String,
        val origin_name: String?,
        val poster_url: String?,
        val thumb_url: String?,
        val year: Int?,
        val episode_current: String?,
        val type: String?   // "single" = phim lẻ, "series" = phim bộ
    )

    data class OPhimListResponse(
        val status: Boolean,
        val items: List<OPhimMovie>,
        val pagination: Pagination?
    )

    data class Pagination(
        val totalItems: Int,
        val totalItemsPerPage: Int,
        val currentPage: Int,
        val totalPages: Int
    )

    data class OPhimDetail(
        val status: Boolean,
        val movie: MovieDetail?,
        val episodes: List<EpisodeServer>?
    )

    data class MovieDetail(
        val _id: String,
        val name: String,
        val slug: String,
        val origin_name: String?,
        val content: String?,
        val poster_url: String?,
        val thumb_url: String?,
        val year: Int?,
        val actor: List<String>?,
        val director: List<String>?,
        val category: List<Category>?,
        val country: List<Country>?,
        val type: String?,
        val episode_current: String?,
        val episode_total: String?,
        val quality: String?,
        val lang: String?
    )

    data class EpisodeServer(
        val server_name: String,
        val server_data: List<EpisodeData>
    )

    data class EpisodeData(
        val name: String,       // "Tập 1", "Full"
        val slug: String,
        val filename: String?,
        val link_embed: String, // Link iframe
        val link_m3u8: String   // Link M3U8 trực tiếp
    )

    data class Category(val id: String, val name: String, val slug: String)
    data class Country(val id: String, val name: String, val slug: String)

    // ── Trang chủ: Hiển thị các danh mục ─────────────────────────
    override val mainPage = mainPageOf(
        "$mainUrl/api/v1/movie?sort_field=_id&sort_type=desc&page=" to "Phim Mới Cập Nhật",
        "$mainUrl/api/v1/movie?type=single&page="                   to "Phim Lẻ",
        "$mainUrl/api/v1/movie?type=series&page="                   to "Phim Bộ",
        "$mainUrl/api/v1/movie?type=hoathinh&page="                 to "Hoạt Hình",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url      = request.data + page
        val response = app.get(url)
        val data     = parseJson<OPhimListResponse>(response.text)

        val homeItems = data.items.map { movie ->
            val posterUrl = when {
                movie.poster_url?.startsWith("http") == true -> movie.poster_url
                movie.poster_url != null -> "$mainUrl${movie.poster_url}"
                else -> null
            }
            newMovieSearchResponse(
                name    = movie.name,
                url     = "$mainUrl/phim/${movie.slug}",
                type    = if (movie.type == "single") TvType.Movie else TvType.TvSeries
            ) {
                this.posterUrl = posterUrl
                this.year      = movie.year
            }
        }
        return newHomePageResponse(request.name, homeItems)
    }

    // ── Tìm kiếm phim ────────────────────────────────────────────
    override suspend fun search(query: String): List<SearchResponse> {
        val url      = "$mainUrl/api/v1/movie?keyword=${query.encodeUrl()}"
        val response = app.get(url)
        val data     = parseJson<OPhimListResponse>(response.text)

        return data.items.map { movie ->
            newMovieSearchResponse(
                name = movie.name,
                url  = "$mainUrl/phim/${movie.slug}",
                type = if (movie.type == "single") TvType.Movie else TvType.TvSeries
            ) {
                this.posterUrl = movie.poster_url
            }
        }
    }

    // ── Chi tiết phim + danh sách tập ────────────────────────────
    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val data     = parseJson<OPhimDetail>(response.text)
        val movie    = data.movie!!

        val posterUrl = when {
            movie.poster_url?.startsWith("http") == true -> movie.poster_url
            movie.poster_url != null -> "$mainUrl${movie.poster_url}"
            else -> null
        }

        // Phim lẻ
        if (movie.type == "single") {
            val episodeLink = data.episodes
                ?.firstOrNull()
                ?.server_data
                ?.firstOrNull()
                ?.link_m3u8
                ?: data.episodes?.firstOrNull()?.server_data?.firstOrNull()?.link_embed
                ?: ""

            return newMovieLoadResponse(
                name  = movie.name,
                url   = url,
                type  = TvType.Movie,
                dataUrl = episodeLink
            ) {
                this.posterUrl   = posterUrl
                this.plot        = movie.content
                this.year        = movie.year
                this.tags        = movie.category?.map { it.name }
                this.actors      = movie.actor?.map { ActorData(Actor(it)) }
            }
        }

        // Phim bộ — map từng tập
        val episodes = data.episodes?.flatMap { server ->
            server.server_data.mapIndexed { index, ep ->
                Episode(
                    data    = ep.link_m3u8.ifEmpty { ep.link_embed },
                    name    = ep.name,
                    episode = index + 1
                )
            }
        } ?: emptyList()

        return newTvSeriesLoadResponse(
            name     = movie.name,
            url      = url,
            type     = TvType.TvSeries,
            episodes = episodes
        ) {
            this.posterUrl = posterUrl
            this.plot      = movie.content
            this.year      = movie.year
            this.tags      = movie.category?.map { it.name }
        }
    }

    // ── Load link video để phát ──────────────────────────────────
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.contains(".m3u8")) {
            // Link M3U8 trực tiếp
            callback(
                ExtractorLink(
                    source  = name,
                    name    = name,
                    url     = data,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8  = true
                )
            )
            return true
        }
        // Nếu là link embed → dùng extractor
        loadExtractor(data, mainUrl, subtitleCallback, callback)
        return true
    }
}

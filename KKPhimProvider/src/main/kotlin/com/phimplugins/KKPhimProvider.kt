package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class KKPhimProvider : MainAPI() {

    override var mainUrl            = "https://phimapi.com"
    override var name               = "KKPhim"
    override val hasMainPage        = true
    override var lang               = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes     = setOf(TvType.Movie, TvType.TvSeries)

    // KKPhim dùng cùng cấu trúc JSON với OPhim
    // Chỉ khác Base URL và một số field nhỏ

    override val mainPage = mainPageOf(
        "$mainUrl/danh-sach/phim-moi?page=" to "Phim Mới",
        "$mainUrl/danh-sach/phim-le?page="  to "Phim Lẻ",
        "$mainUrl/danh-sach/phim-bo?page="  to "Phim Bộ",
    )

    data class KKMovie(
        val _id: String,
        val name: String,
        val slug: String,
        val thumb_url: String?,
        val poster_url: String?,
        val year: Int?,
        val type: String?
    )

    data class KKListResponse(
        val status: Boolean,
        val items: List<KKMovie>
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url  = request.data + page
        val data = parseJson<KKListResponse>(app.get(url).text)

        val items = data.items.map { movie ->
            newMovieSearchResponse(
                name = movie.name,
                url  = "$mainUrl/phim/${movie.slug}",
                type = if (movie.type == "single") TvType.Movie else TvType.TvSeries
            ) {
                this.posterUrl = movie.poster_url
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url  = "$mainUrl/v1/api/tim-kiem?keyword=${query.encodeUrl()}"
        val data = parseJson<KKListResponse>(app.get(url).text)
        return data.items.map { movie ->
            newMovieSearchResponse(
                name = movie.name,
                url  = "$mainUrl/phim/${movie.slug}",
                type = if (movie.type == "single") TvType.Movie else TvType.TvSeries
            ) { this.posterUrl = movie.poster_url }
        }
    }

    // load() và loadLinks() tương tự OPhimProvider ở trên
    // (tái sử dụng cùng logic parse episodes + M3U8)
}

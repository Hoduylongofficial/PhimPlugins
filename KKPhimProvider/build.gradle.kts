#!/usr/bin/env kotlin

version = 1

cloudstream {
    language    = "vi"
    description = "Xem phim từ OPhim - HD Vietsub"
    authors     = listOf("YourName")
    status      = 1   // 1=OK, 0=Down, 2=Slow, 3=Beta

    tvTypes = listOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )
    iconUrl = "https://ophim1.com/favicon.ico"
}

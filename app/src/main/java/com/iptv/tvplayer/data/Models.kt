package com.iptv.tvplayer.data

data class Channel(
    val name: String,
    val urls: List<String>,
    val logo: String? = null
)

data class Playlist(
    val categories: List<String>,
    val channelsMap: Map<String, List<Channel>>
)

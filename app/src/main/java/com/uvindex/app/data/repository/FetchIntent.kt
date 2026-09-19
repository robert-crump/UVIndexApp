package com.uvindex.app.data.repository

/** What a caller wants from [WeatherRepository.getUVForecast]; the repository decides about the network. */
enum class FetchIntent {
    /** Always go to the network (falls back to cache on failure). */
    Fresh,

    /** Go to the network only if the fetch policy says the cache is stale. */
    FreshIfStale,

    /** Never touch the network or location; use whatever the cache holds. */
    CachedOnly,
}

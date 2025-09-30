package com.example.juka.data.repositories

import com.example.juka.core.database.FishDatabase
import com.example.juka.core.database.FishInfo

class FishRepository(private val fishDatabase: FishDatabase) {

    suspend fun initialize() {
        fishDatabase.initialize()
    }

    fun getFishSpeciesDB(): Map<String, FishInfo> {
        return fishDatabase.fishSpeciesDB
    }

    fun findFishByKeyword(keyword: String): FishInfo? {
        return fishDatabase.findFishByKeyword(keyword)
    }
}

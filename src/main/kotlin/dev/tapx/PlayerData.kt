package dev.tapx

/** Everything saved for one player. Plain fields so Gson can (de)serialize it. */
class PlayerData {
    var name = ""
    var gold = 0.0
    var gems = 100L
    var relics = 0L
    var stage = 1
    var wave = 0
    var maxStage = 1
    var farming = false
    var tapLevel = 1
    var heroes = mutableMapOf<String, Int>()
    var skills = mutableMapOf<String, Int>()
    var artifacts = mutableMapOf<String, Int>()
    var mana = 30.0
    var buffs = mutableMapOf<String, Double>()
    var gear = mutableMapOf<String, Int>()
    var equipped = mutableMapOf<String, String>()
    var pets = mutableMapOf<String, PetData>()
    var activePet: String? = null
    var petCharge = 0
    var achievements = mutableSetOf<String>()
    var freeChestAt = 0L
    var deals = Deals()
    var stats = Stats()
    var sound = true
    /** Buy multiplier: 1, 10, 100, or 0 for MAX. */
    var mult = 1
    var lastSeen = System.currentTimeMillis()
    /** Where to send the player back to when they leave the game screen ("world;x;y;z;yaw;pitch"). */
    var returnTo: String? = null
    /** Game mode to restore when leaving the game screen. */
    var returnMode: String? = null
    /** Per-player screen size (camera distance factor); 0 = not set up yet. */
    var screenZoom = 0.0
}

class Stats {
    var taps = 0L
    var kills = 0L
    var bosses = 0L
    var crits = 0L
    var goldEarned = 0.0
    var prestiges = 0
    var casts = 0L
    var playTime = 0.0
    var bestStage = 1
    var relicsEarned = 0L
    var wisps = 0L
}

class PetData {
    var lvl = 0
    var eggs = 0
}

class Deal {
    var pet = ""
    var eggs = 0
    var gems = 0
    var bought = false
}

class Deals {
    var block = -1L
    var list = mutableListOf<Deal>()
}

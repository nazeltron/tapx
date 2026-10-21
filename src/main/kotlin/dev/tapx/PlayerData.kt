package dev.tapx

/** Everything saved for one player. Plain fields so Gson can (de)serialize it. */
class PlayerData {
    var name = ""
    var gold = 0.0
    var gems = 25L
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
    /** Cursor speed multiplier (mouse sensitivity inside the game). */
    var cursorSpeed = 1.0

    // --- added in 2.2
    var skillTree = mutableMapOf<String, Int>()
    var bonusSp = 0
    /** One-time gem purchases (vip_gold, double_dmg, offline_master, mana_well, starter). */
    var owned = mutableSetOf<String>()
    /** Levelled gem purchases (auto_tap, sp_pack). */
    var shopLevels = mutableMapOf<String, Int>()
    var skins = mutableSetOf<String>()
    var skin: String? = null
    var login = LoginData()
    var quests = QuestData()

    // --- added in 3.1
    /** Store rank bought with gems (vip, pro, mvp, elite, titan, legend). Paid: kept on resets. */
    var rank: String? = null
    /** Tutorial step; -1 when finished or skipped. */
    var tutorial = 0
    /** Day number of the last daily wheel spin. */
    var wheelDay = -1L
    var discordLinked = false
    var discordRewarded = false
    /** Promo codes already redeemed. */
    var promos = mutableSetOf<String>()

    // --- added in 3.2
    /** Training levels (gold upgrades, reset on prestige). */
    var training = mutableMapOf<String, Int>()
    /** Daily wheel spins used today (ranks give more). */
    var wheelSpins = 0

    // --- added in 3.3
    /** Staff rank given by command (owner, admin, mod, helper); kept on resets. */
    var staff: String? = null
}

class LoginData {
    var streak = 0
    var lastClaimDay = -1L
}

class Quest {
    var id = ""
    var target = 0
    var start = 0L
    var claimed = false
}

class QuestData {
    var day = -1L
    var list = mutableListOf<Quest>()
    var bonusClaimed = false
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
    var stagesCleared = 0L
    var upgrades = 0L
    var questsDone = 0L
    var gemsBought = 0L
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

/**
 * A fresh start that keeps everything paid for with gems (gems, gamepasses, levelled purchases,
 * skins, rank), the Discord link, used promo codes and settings. Used by the admin resets.
 */
fun PlayerData.resetKeepingPaid(): PlayerData {
    val old = this
    return PlayerData().apply {
        name = old.name
        gems = old.gems
        owned = old.owned
        shopLevels = old.shopLevels
        bonusSp = 3 * (old.shopLevels["sp_pack"] ?: 0)
        skins = old.skins
        skin = old.skin
        rank = old.rank
        staff = old.staff
        stats.gemsBought = old.stats.gemsBought
        discordLinked = old.discordLinked
        discordRewarded = old.discordRewarded
        promos = old.promos
        sound = old.sound
        mult = old.mult
        screenZoom = old.screenZoom
        cursorSpeed = old.cursorSpeed
        returnTo = old.returnTo
        returnMode = old.returnMode
        tutorial = -1
    }
}

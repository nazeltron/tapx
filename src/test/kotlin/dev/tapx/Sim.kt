package dev.tapx

/**
 * Headless balance check: plays the game with the same greedy strategy as web/tools/sim.js, so the
 * pacing of both versions can be compared. ./gradlew sim -Pargs="hours tapsPerSecond"
 */
fun main(args: Array<String>) {
    val hours = args.getOrNull(0)?.toDouble() ?: 3.0
    val tps = args.getOrNull(1)?.toDouble() ?: 5.0
    var failT = 0.0
    val d = PlayerData().apply { mult = 1; gems = 0 }
    val g = Game(d, object : GameEvents { override fun onBossFail() { failT = 45.0 } })
    val log = ArrayList<String>()
    var marks = HashSet<Int>()
    var prestiges = 0
    var t = 0.0
    var tapAcc = 0.0
    val dt = 0.1

    fun shop() {
        repeat(50) {
            var best: String? = null
            var bestV = 0.0
            val tc = g.tapCost(g.d.tapLevel)
            if (g.d.gold >= tc) { best = "tap"; bestV = (g.tapBase(g.d.tapLevel + 1) - g.tapBase(g.d.tapLevel)) * tps / tc }
            HEROES.forEachIndexed { i, h ->
                if (!g.heroUnlocked(i)) return@forEachIndexed
                val l = g.heroLvl(h.id); val c = g.heroCost(h, l)
                if (g.d.gold < c) return@forEachIndexed
                val v = (g.heroBaseDps(h, l + 1) - g.heroBaseDps(h, l)) / c
                if (v > bestV) { bestV = v; best = h.id }
            }
            for (sk in SKILLS) if (g.d.tapLevel >= sk.req && g.skillLvl(sk.id) < 3 && g.d.gold >= g.skillCost(sk, g.skillLvl(sk.id)) * 3) g.buySkill(sk.id)
            when (best) { null -> return; "tap" -> g.buyTap(); else -> g.buyHero(best!!) }
        }
        // spend skill points round-robin over the damage nodes
        val nodes = listOf("k_edge", "w_rally", "w_vets", "k_bane", "r_greed", "w_drill")
        while (g.spFree() > 0 && nodes.any { g.buyNode(it) }) Unit
    }

    while (t < hours * 3600) {
        t += dt
        tapAcc += tps * dt
        while (tapAcc >= 1) { tapAcc--; g.tap(false) }
        g.update(dt)
        for (sk in SKILLS) g.castSkill(sk.id)
        if ((t * 10).toLong() % 10 == 0L) { shop(); g.checkAchievements() }
        if (failT > 0) { failT -= dt; if (failT <= 0) g.fightBoss() }
        for (m in listOf(10, 25, 50, 75, 100, 150, 200)) if (g.d.stage >= m && marks.add(m))
            log += "stage $m at ${"%.1f".format(t / 60)} min, tapLv ${g.d.tapLevel}, dps ${fmt(g.heroDps(false))}, relics now ${g.relicsFor(g.d.maxStage)}"
        if (g.d.stage >= 60 && g.d.farming && prestiges < 3) {
            val gained = g.prestige(); prestiges++; marks = HashSet()
            log += "PRESTIGE +$gained at ${"%.1f".format(t / 60)}"
            for (a in ARTIFACTS) while (g.buyArtifact(a.id)) Unit
        }
    }
    log += "final stage ${g.d.stage} max ${g.d.maxStage} gold ${fmt(g.d.gold)} gems ${g.d.gems}"
    println(log.joinToString("\n"))
}

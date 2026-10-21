package dev.tapx

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.server.packs.metadata.pack.PackMetadataSection

/** Parses pack.mcmeta candidates with the game's own codec (./gradlew packCheck). */
fun main() {
    net.minecraft.SharedConstants.tryDetectVersion()
    net.minecraft.server.Bootstrap.bootStrap()
    val codec = PackMetadataSection.CLIENT_TYPE.codec()
    for (json in listOf(
        """{"description":"x","pack_format":75,"min_format":75,"max_format":99}""",
        """{"description":"x","min_format":75,"max_format":99}""",
        """{"description":"x","min_format":[75,0],"max_format":[99,0]}""",
        """{"description":"x","pack_format":75}""",
    )) {
        val r = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
        println("$json -> ${r.result().map { it.supportedFormats() }.orElse(null)} ${r.error().map { it.message() }.orElse("")}")
    }
}

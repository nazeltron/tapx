package dev.tapx

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.TooltipDisplay
import org.bukkit.Color
import org.bukkit.Material
import com.destroystokyo.paper.profile.PlayerProfile
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.SkullMeta

/** Builders for the vanilla items that make up every menu. */
object Items {
    // Hide vanilla tooltip lines (attack damage, dye colour, horn sound...) so only our text shows.
    @Suppress("UnstableApiUsage")
    private val CLEAN_TOOLTIP = TooltipDisplay.tooltipDisplay().addHiddenComponents(
        DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.DYED_COLOR, DataComponentTypes.ENCHANTMENTS,
        DataComponentTypes.STORED_ENCHANTMENTS, DataComponentTypes.INSTRUMENT, DataComponentTypes.POTION_CONTENTS,
        DataComponentTypes.UNBREAKABLE, DataComponentTypes.TRIM, DataComponentTypes.BANNER_PATTERNS,
        DataComponentTypes.JUKEBOX_PLAYABLE, DataComponentTypes.FIREWORKS, DataComponentTypes.CONTAINER,
        DataComponentTypes.BUNDLE_CONTENTS, DataComponentTypes.WEAPON, DataComponentTypes.BLOCKS_ATTACKS,
    ).build()

    @Suppress("UnstableApiUsage")
    private val NO_TOOLTIP = TooltipDisplay.tooltipDisplay().hideTooltip(true).build()

    @Suppress("UnstableApiUsage")
    fun item(
        mat: Material,
        name: String,
        lore: List<String> = emptyList(),
        amount: Int = 1,
        glint: Boolean? = null,
        dye: Color? = null,
        head: PlayerProfile? = null,
    ): ItemStack {
        val stack = ItemStack(mat, amount.coerceIn(1, mat.maxStackSize))
        stack.editMeta { meta ->
            meta.displayName(mm(name))
            if (lore.isNotEmpty()) meta.lore(lore.map(::mm))
            if (glint != null) meta.setEnchantmentGlintOverride(glint)
            if (dye != null && meta is LeatherArmorMeta) meta.setColor(dye)
            if (head != null && meta is SkullMeta) meta.playerProfile = head
        }
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, CLEAN_TOOLTIP)
        return stack
    }

    /** Background tile with no tooltip at all. */
    @Suppress("UnstableApiUsage")
    fun pane(mat: Material): ItemStack {
        val stack = ItemStack(mat)
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, NO_TOOLTIP)
        return stack
    }
}

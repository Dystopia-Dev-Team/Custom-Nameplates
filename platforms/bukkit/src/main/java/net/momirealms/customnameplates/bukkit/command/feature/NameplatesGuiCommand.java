/*
 *  Copyright (C) <2024> <XiaoMoMi>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.momirealms.customnameplates.bukkit.command.feature;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.momirealms.customnameplates.api.CNPlayer;
import net.momirealms.customnameplates.api.ConfigManager;
import net.momirealms.customnameplates.api.feature.nameplate.Nameplate;
import net.momirealms.customnameplates.bukkit.BukkitCustomNameplates;
import net.momirealms.customnameplates.bukkit.command.BukkitCommandFeature;
import net.momirealms.customnameplates.common.command.CustomNameplatesCommandManager;
import net.momirealms.customnameplates.common.locale.MessageConstants;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;

import static net.momirealms.customnameplates.api.helper.AdventureHelper.miniMessage;

public class NameplatesGuiCommand extends BukkitCommandFeature<CommandSender> {

    public NameplatesGuiCommand(CustomNameplatesCommandManager<CommandSender> commandManager, BukkitCustomNameplates plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .senderType(Player.class)
                .handler(context -> {
                    if (!ConfigManager.nameplateModule()) return;
                    Player bukkitPlayer = context.sender();
                    CNPlayer player = plugin.getPlayer(bukkitPlayer.getUniqueId());
                    if (player == null) {
                        throw new RuntimeException("Player should not be null");
                    }
                    Collection<Nameplate> available = plugin.getNameplateManager().availableNameplates(player);
                    if (available.isEmpty()) {
                        handleFeedback(context, MessageConstants.COMMAND_NAMEPLATES_LIST_FAILURE_NONE);
                    } else {
                        PaginatedGui paginatedGui = Gui.paginated()
                                .title(miniMessage(ConfigManager.nameplatesGuiTitle()))
                                .rows(ConfigManager.nameplatesGuiRows())
                                .create();

                        paginatedGui.setDefaultClickAction(event -> event.setCancelled(true));

                        paginatedGui.setItem(ConfigManager.nameplatesGuiFillerItemSlots(), ItemBuilder.from(new ItemStack(Material.valueOf(ConfigManager.nameplatesGuiFillerItemMaterial())))
                                .model(ConfigManager.nameplatesGuiFillerItemModelData())
                                .name(Component.space())
                                .flags(
                                        ItemFlag.HIDE_ATTRIBUTES,
                                        ItemFlag.HIDE_ENCHANTS,
                                        ItemFlag.HIDE_UNBREAKABLE,
                                        ItemFlag.HIDE_DESTROYS,
                                        ItemFlag.HIDE_PLACED_ON,
                                        ItemFlag.HIDE_POTION_EFFECTS
                                )
                                .asGuiItem());

                        paginatedGui.setItem(ConfigManager.nameplatesGuiPreviousPageItemSlot(), ItemBuilder.from(Material.valueOf(ConfigManager.nameplatesGuiPreviousPageItemMaterial()))
                                .name(miniMessage(ConfigManager.nameplatesGuiPreviousPageItemName()))
                                .model(ConfigManager.nameplatesGuiPreviousPageItemModelData())
                                .asGuiItem(event -> paginatedGui.previous()));

                        paginatedGui.setItem(ConfigManager.nameplatesGuiCloseItemSlot(), ItemBuilder.from(Material.valueOf(ConfigManager.nameplatesGuiCloseItemMaterial()))
                                .name(miniMessage(ConfigManager.nameplatesGuiCloseItemName()))
                                .model(ConfigManager.nameplatesGuiCloseItemModelData())
                                .asGuiItem(event -> paginatedGui.close(bukkitPlayer)));

                        paginatedGui.setItem(ConfigManager.nameplatesGuiNextPageItemSlot(), ItemBuilder.from(Material.valueOf(ConfigManager.nameplatesGuiNextPageItemMaterial()))
                                .name(miniMessage(ConfigManager.nameplatesGuiNextPageItemName()))
                                .model(ConfigManager.nameplatesGuiNextPageItemModelData())
                                .asGuiItem(event -> paginatedGui.next()));

                        Collator collator = ConfigManager.nameplatesGuiPreferredCollatorLanguage().equals("default") ? Collator.getInstance() : Collator.getInstance(Locale.forLanguageTag(ConfigManager.nameplatesGuiPreferredCollatorLanguage()));
                        collator.setStrength(Collator.PRIMARY);
                        Collection<Nameplate> sortedAvailable = available.stream()
                                .sorted(Comparator.comparing(Nameplate::id, collator))
                                .toList();

                        for (Nameplate nameplate : sortedAvailable) {
                            ItemBuilder itemBuilder = ItemBuilder.from(Material.valueOf(ConfigManager.nameplatesGuiNameplateItemMaterial()))
                                    .name(miniMessage(ConfigManager.nameplatesGuiNameplateItemName().replace("%nameplate%", nameplate.id())))
                                    .lore(ConfigManager.nameplatesGuiNameplateItemLore().stream().map(MiniMessage.miniMessage()::deserialize).collect(Collectors.toList()))
                                    .model(ConfigManager.nameplatesGuiNameplateItemModelData())
                                    .flags(ItemFlag.HIDE_ENCHANTS);

                            if (player.currentNameplate().equals(nameplate.id())) {
                                itemBuilder.enchant(Enchantment.MENDING, 1);
                            }

                            paginatedGui.addItem(itemBuilder.asGuiItem(event -> {
                                ItemStack clickedItem = event.getCurrentItem();
                                if (clickedItem == null) return;

                                if (player.currentNameplate().equals(nameplate.id())) {
                                    player.setNameplateData("none");
                                    player.save();
                                    handleFeedback(context, MessageConstants.COMMAND_NAMEPLATES_UNEQUIP_SUCCESS);
                                    clickedItem.removeEnchantments();
                                    paginatedGui.updatePageItem(event.getSlot(), clickedItem);
                                    event.setCancelled(true);
                                    return;
                                }

                                if (!plugin.getNameplateManager().hasNameplate(player, nameplate.id())) {
                                    handleFeedback(context, MessageConstants.COMMAND_NAMEPLATES_EQUIP_FAILURE_PERMISSION);
                                    event.setCancelled(true);
                                    paginatedGui.close(bukkitPlayer);
                                    return;
                                }

                                player.setNameplateData(nameplate.id());
                                player.save();
                                handleFeedback(context, MessageConstants.COMMAND_NAMEPLATES_EQUIP_SUCCESS, miniMessage(nameplate.displayName()));

                                for (GuiItem guiItem : paginatedGui.getPageItems()) {
                                    ItemStack guiItemStack = guiItem.getItemStack();
                                    if (guiItemStack.getType() == Material.valueOf(ConfigManager.nameplatesGuiNameplateItemMaterial())
                                            && !guiItemStack.getEnchantments().isEmpty()) {
                                        guiItemStack.removeEnchantments();
                                        guiItem.setItemStack(guiItemStack);
                                    }
                                }
                                paginatedGui.update();

                                clickedItem.addUnsafeEnchantment(Enchantment.MENDING, 1);
                                paginatedGui.updatePageItem(event.getSlot(), clickedItem);
                                event.setCancelled(true);
                            }));
                        }
                        paginatedGui.open(bukkitPlayer);
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "nameplates_gui";
    }
}

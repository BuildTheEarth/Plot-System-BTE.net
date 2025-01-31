package com.alpsbte.plotsystem.core.menus;

import com.alpsbte.plotsystem.PlotSystem;
import com.alpsbte.plotsystem.core.system.Builder;
import com.alpsbte.plotsystem.core.system.plot.Plot;
import com.alpsbte.plotsystem.utils.Invitation;
import com.alpsbte.plotsystem.utils.io.language.LangPaths;
import com.alpsbte.plotsystem.utils.io.language.LangUtil;
import com.alpsbte.plotsystem.utils.items.MenuItems;
import com.alpsbte.plotsystem.utils.items.builder.ItemBuilder;
import com.alpsbte.plotsystem.utils.items.builder.LoreBuilder;
import com.alpsbte.plotsystem.utils.Utils;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ipvp.canvas.mask.BinaryMask;
import org.ipvp.canvas.mask.Mask;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public class PlotMemberMenu extends AbstractMenu {

    private final Plot plot;

    private final ItemStack emptyMemberSlotItem = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (byte) 13).setName("§2§l" + LangUtil.get(getMenuPlayer(), LangPaths.Plot.GroupSystem.EMPTY_MEMBER_SLOTS)).build();
    private List<Builder> builders;

    public PlotMemberMenu(Plot plot, Player menuPlayer) {
        super(3,  LangUtil.get(menuPlayer, LangPaths.MenuTitle.MANAGE_MEMBERS) + " | " + LangUtil.get(menuPlayer, LangPaths.Plot.PLOT_NAME) + " #" + plot.getID(), menuPlayer);
        this.plot = plot;
    }

    @Override
    protected void setPreviewItems() {
        // Set loading item for plot owner item
        getMenu().getSlot(10).setItem(MenuItems.loadingItem(Material.SKULL_ITEM, (byte) 3, getMenuPlayer()));

        // Set loading item for plot member items
        Bukkit.getScheduler().runTask(PlotSystem.getPlugin(), () -> {
            try {
                List<Builder> plotMembers = plot.getPlotMembers();
                for (int i = 1; i <= 3; i++) {
                    if (plotMembers.size() >= i) {
                        getMenu().getSlot(11 + i).setItem(MenuItems.loadingItem(Material.SKULL_ITEM, (byte) 3, getMenuPlayer()));
                    } else {
                        getMenu().getSlot(11 + i).setItem(emptyMemberSlotItem);
                    }
                }
            } catch (SQLException ex) {
                Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
            }
        });

        super.setPreviewItems();
    }

    @Override
    protected void setMenuItemsAsync() {
        // Set plot owner item
        try {
            getMenu().getSlot(10)
                    .setItem(new ItemBuilder(Utils.getPlayerHead(plot.getPlotOwner().getUUID()))
                            .setName("§6§l" + LangUtil.get(getMenuPlayer(), LangPaths.Plot.OWNER)).setLore(new LoreBuilder()
                                    .addLine(plot.getPlotOwner().getName()).build())
                            .build());
        } catch (SQLException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
        }

        // Set plot member items
        try {
            builders = plot.getPlotMembers();
            for (int i = 12; i < 15; i++) {
                if (builders.size() >= (i - 11)) {
                    Builder builder = builders.get(i - 12);
                    getMenu().getSlot(i)
                            .setItem(new ItemBuilder(Utils.getPlayerHead(builder.getUUID()))
                                    .setName("§b§l" + LangUtil.get(getMenuPlayer(), LangPaths.Plot.MEMBER))
                                    .setLore(new LoreBuilder()
                                            .addLines(builder.getName(),
                                                    "",
                                                    Utils.getActionFormat(LangUtil.get(getMenuPlayer(), LangPaths.Note.Action.CLICK_TO_REMOVE_PLOT_MEMBER)))
                                            .build())
                                    .build());
                }
            }
        } catch (SQLException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
        }

        // Set add plot member item
        ItemStack whitePlus = Utils.getItemHead(Utils.CustomHead.ADD_BUTTON);
        getMenu().getSlot(16)
                .setItem(new ItemBuilder(whitePlus)
                        .setName("§6§l" + LangUtil.get(getMenuPlayer(), LangPaths.MenuTitle.ADD_MEMBER_TO_PLOT)).setLore(new LoreBuilder()
                                .addLines(LangUtil.get(getMenuPlayer(), LangPaths.MenuDescription.ADD_MEMBER_TO_PLOT),
                                        "",
                                        Utils.getNoteFormat(LangUtil.get(getMenuPlayer(), LangPaths.Note.PLAYER_HAS_TO_BE_ONLINE))).build())
                        .build());

        // Set back item
        getMenu().getSlot(22).setItem(MenuItems.backMenuItem(getMenuPlayer()));
    }

    @Override
    protected void setItemClickEventsAsync() {
        // Set click event for member slots
        for (int i = 12; i < 15; i++) {
            int itemSlot = i;
            getMenu().getSlot(i).setClickHandler((clickPlayer, clickInformation) -> {
                if (!getMenu().getSlot(itemSlot).getItem(clickPlayer).equals(emptyMemberSlotItem)) {
                    Builder builder = builders.get(itemSlot-12);

                    try {
                        plot.removePlotMember(builder);
                        clickPlayer.sendMessage(Utils.getInfoMessageFormat(LangUtil.get(getMenuPlayer(), LangPaths.Message.Info.REMOVED_PLOT_MEMBER,builder.getName(), Integer.toString(plot.getID()))));
                    } catch (SQLException ex) {
                        Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
                    }

                    reloadMenuAsync();
                }
            });
        }

        // Set click event for add plot member item
        getMenu().getSlot(16).setClickHandler((clickPlayer, clickInformation) -> {
            clickPlayer.closeInventory();
            new AnvilGUI.Builder()
                    .onComplete((player, text) -> {
                        try {
                            if (Builder.getBuilderByName(text) != null) {
                                Builder builder = Builder.getBuilderByName(text);
                                if (builder.isOnline()) {
                                    // Check if player is owner of plot
                                    if (builder.getPlayer() == plot.getPlotOwner().getPlayer()) {
                                        player.sendMessage(Utils.getErrorMessageFormat(LangUtil.get(getMenuPlayer(), LangPaths.Message.Error.PLAYER_IS_PLOT_OWNER)));
                                        return AnvilGUI.Response.text(LangUtil.get(getMenuPlayer(), LangPaths.Note.Anvil.PLAYER_IS_OWNER));
                                    }

                                    // Check if player is already a member of the plot
                                    for (Builder item : plot.getPlotMembers()) {
                                        if (builder.getPlayer() == item.getPlayer()) {
                                            player.sendMessage(Utils.getErrorMessageFormat(LangUtil.get(getMenuPlayer(), LangPaths.Message.Error.PLAYER_IS_PLOT_MEMBER)));
                                            return AnvilGUI.Response.text(LangUtil.get(getMenuPlayer(), LangPaths.Note.Anvil.PLAYER_ALREADY_ADDED));
                                        }
                                    }

                                    new Invitation(builder.getPlayer(), plot);
                                    return AnvilGUI.Response.close();
                                } else {
                                    // Builder isn't online, thus can't be asked if he/she wants to be added
                                    player.sendMessage(Utils.getErrorMessageFormat(LangUtil.get(getMenuPlayer(), LangPaths.Message.Error.PLAYER_IS_NOT_ONLINE)));
                                    return AnvilGUI.Response.text(LangUtil.get(getMenuPlayer(), LangPaths.Note.Anvil.PLAYER_NOT_ONLINE));
                                }
                            }
                        } catch (SQLException ex) {
                            Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
                        }
                        // Input was invalid or Player hasn't joined the server yet
                        player.sendMessage(Utils.getErrorMessageFormat(LangUtil.get(getMenuPlayer(), LangPaths.Message.Error.PLAYER_NOT_FOUND)));
                        return AnvilGUI.Response.text(LangUtil.get(getMenuPlayer(), LangPaths.Note.Anvil.INVALID_INPUT));
                    })
                    .text(LangUtil.get(getMenuPlayer(), LangPaths.Note.Anvil.ENTER_PLAYER_NAME))
                    .itemLeft(new ItemStack(Material.NAME_TAG))
                    .itemRight(new ItemStack(Material.SKULL))
                    .title(LangUtil.get(getMenuPlayer(), LangPaths.MenuTitle.ENTER_PLAYER_NAME))
                    .plugin(PlotSystem.getPlugin())
                    .open(clickPlayer);
        });

        // Set click event for back item
        getMenu().getSlot(22).setClickHandler((clickPlayer, clickInformation) -> {
            try {
                new PlotActionsMenu(clickPlayer, plot);
            } catch (SQLException ex) {
                Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
            }
        });
    }

    @Override
    protected Mask getMask() {
        return BinaryMask.builder(getMenu())
                .item(new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (byte) 7).setName(" ").build())
                .pattern("111111111")
                .pattern("000000000")
                .pattern("111111111")
                .build();
    }
}
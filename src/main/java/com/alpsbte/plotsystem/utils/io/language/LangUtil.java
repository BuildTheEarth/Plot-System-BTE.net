package com.alpsbte.plotsystem.utils.io.language;

import com.alpsbte.plotsystem.PlotSystem;
import com.alpsbte.plotsystem.core.system.Builder;
import com.alpsbte.plotsystem.utils.Utils;
import com.alpsbte.plotsystem.utils.io.YamlFile;
import com.alpsbte.plotsystem.utils.io.YamlFileFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;

public class LangUtil extends YamlFileFactory {

    private final static LanguageFile[] languages = new LanguageFile[] {
        new LanguageFile("en_GB", 1.0),
        new LanguageFile("de_DE", 1.0, "de_AT", "de_CH")
    };

    public LangUtil() {
        super(languages);

        Arrays.stream(languages).forEach(lang -> {
            if (!lang.getFile().exists()) {
                createFile(lang);
            }
            reloadFile(lang);

            if (lang.getDouble(LangPaths.CONFIG_VERSION) != lang.getVersion()) {
                updateFile(lang);
                reloadFile(lang);
            }
        });
    }

    public static String get(CommandSender sender, String key) {
        return getLanguageFileByLocale(sender instanceof Player ? getLocaleTagByPlayer((Player) sender) : languages[0].tag).getTranslation(key);
    }

    public static String get(CommandSender sender, String key, String... args) {
        return getLanguageFileByLocale(sender instanceof Player ? getLocaleTagByPlayer((Player) sender) : languages[0].tag).getTranslation(key, args);
    }

    private static LanguageFile getLanguageFileByLocale(String locale) {
        return Arrays.stream(languages)
                .filter(lang -> lang.tag.equalsIgnoreCase(locale))
                .findFirst()
                .orElseGet(() -> Arrays.stream(languages)
                        .filter(lang -> lang.additionalLang != null && Arrays.stream(lang.additionalLang).anyMatch(l -> l.equalsIgnoreCase(locale)))
                        .findFirst()
                        .orElse(languages[0]));
    }

    private static String getLocaleTagByPlayer(Player player) {
        Builder builder = new Builder(player.getUniqueId());
        if (builder.getLanguageTag() != null) {
            return builder.getLanguageTag();
        } else return player.getPlayer().getLocale();
    }

    public static void broadcast(String key) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Utils.getInfoMessageFormat(get(player,key)));
        }
    }

    public static void broadcast(String key, String... strings) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Utils.getInfoMessageFormat(get(player,key,strings)));
        }
    }

    private static class LanguageFile extends YamlFile {
        private final String tag;
        private final String name;
        private final String headID;
        private String[] additionalLang;

        public LanguageFile(String lang, double version) {
            super(Paths.get("lang", lang + ".yml"), version);

            this.tag = lang;
            this.name = getString("lang.name");
            this.headID = getString("lang.head-id");
        }

        public LanguageFile(String lang, double version, String... additionalLang) {
            this(lang, version);
            this.additionalLang = additionalLang;
        }

        public String getTranslation(String key) {
            return getString(key);
        }

        public String getTranslation(String key, String... args) {
            String translation = getString(key);
            for (int i = 0; i < args.length; i++) {
                translation = translation.replace("{" + i + "}", args[i]);
            }
            return translation;
        }

        public String getTag() {
            return tag;
        }

        public String getName() {
            return name;
        }

        public ItemStack getHead() {
            return Utils.getItemHead(new Utils.CustomHead(headID));
        }

        @Override
        public InputStream getDefaultFileStream() {
            return PlotSystem.getPlugin().getResource("lang" + File.separator + getFile().getName());
        }

        @Override
        public int getMaxConfigWidth() {
            return 400;
        }
    }
}

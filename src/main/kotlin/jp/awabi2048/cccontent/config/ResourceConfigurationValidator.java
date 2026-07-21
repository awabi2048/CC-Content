package jp.awabi2048.cccontent.config;

import org.bukkit.Material;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ResourceConfigurationValidator {
    private static final Set<String> ALLOWED_EMPTY_CONFIGS = Set.of(
        "config/arena/mob_type.yml"
    );
    private static final Set<String> REQUIRED_ARENA_TOKEN_EXCHANGE_RATES = Set.of(
        "skeleton", "zombie", "drowned", "spider", "husk", "bogged", "guardian", "wither_skeleton",
        "blaze", "slime", "silverfish", "spirit", "magma_cube", "witch", "iron_golem",
        "elder_guardian", "frog", "enderman", "shulker", "endermite", "bat", "creeper", "boomerang"
    );

    private ResourceConfigurationValidator() {
    }

    public static List<String> validateConfigDirectory(Path configRoot) throws IOException {
        List<String> errors = new ArrayList<>();
        Map<Path, Object> configs = loadYamlConfigs(configRoot, errors);
        errors.addAll(validate(configRoot, configs));
        return errors;
    }

    public static List<String> validate(Path configRoot, Map<Path, Object> configs) throws IOException {
        List<String> errors = new ArrayList<>();
        validateNonEmptyConfigs(configRoot, configs, errors);
        validateCoreConfig(configRoot, configs, errors);
        validateArenaSettingsConfig(configRoot, configs, errors);
        validateArenaMissionConfig(configRoot, configs, errors);
        validateArenaOverEnchanterConfig(configRoot, configs, errors);
        validateIngredientDefinitions(configRoot, configs, errors);
        validateMobDefinitions(configRoot, configs, errors);
        validateArenaDropConfig(configRoot, configs, errors);
        validateArenaRewardConfig(configRoot, configs, errors);
        validateArenaTokenExchangeConfig(configRoot, configs, errors);
        validateArenaThemes(configRoot, configs, errors);
        validateRankSettingsConfig(configRoot, configs, errors);
        validateRankJobExp(configRoot, configs, errors);
        validateNpcMenu(configRoot, configs, errors);
        validateCustomItemConfigs(configRoot, configs, errors);
        validateBreweryConfigs(configRoot, configs, errors);
        validateCookingConfigs(configRoot, configs, errors);
        validatePartyConfigs(configRoot, configs, errors);
        validateFishingConfigs(configRoot, configs, errors);
        validateMiniGameConfigs(configRoot, configs, errors);
        validateResourceCollectionConfigs(configRoot, configs, errors);
        validateSukimaDungeonConfigs(configRoot, configs, errors);
        return errors;
    }

    private static Map<Path, Object> loadYamlConfigs(Path configRoot, List<String> errors) throws IOException {
        Map<Path, Object> configs = new HashMap<>();
        if (!Files.isDirectory(configRoot)) {
            errors.add(format("missing config directory", configRoot, "<root>", "config directory is required"));
            return configs;
        }

        try (Stream<Path> paths = Files.walk(configRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(ResourceConfigurationValidator::isYaml).sorted().toList()) {
                try {
                    configs.put(file, parseYaml(file));
                } catch (Exception error) {
                    errors.add(format("invalid yaml", file, "<root>", error.getMessage()));
                }
            }
        }
        return configs;
    }

    private static Object parseYaml(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        return new Yaml(new SafeConstructor(options)).load(Files.readString(file));
    }

    private static void validateNonEmptyConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        for (Map.Entry<Path, Object> entry : configs.entrySet()) {
            String resourcePath = resourcePath(configRoot, entry.getKey());
            if (entry.getValue() == null && !ALLOWED_EMPTY_CONFIGS.contains(resourcePath)) {
                errors.add(format("empty config", entry.getKey(), "<root>", "config must contain at least one value"));
            }
        }
    }

    private static void validateCoreConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("core.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }

        Map<String, Object> contentEnabled = requireMap(root, "content_enabled", file, errors);
        if (contentEnabled != null) {
            Set<String> knownFeatures = Set.of(
                "arena", "rank", "brewery", "cooking", "fishing", "resource_collection",
                "seasonal", "sukima_dungeon", "party", "minigame"
            );
            contentEnabled.keySet().stream()
                .filter(key -> !knownFeatures.contains(key))
                .sorted()
                .forEach(key -> errors.add(format(
                    "unknown config key", file, "content_enabled." + key, "unknown content feature"
                )));
            requireBoolean(contentEnabled, "arena", file, "content_enabled.arena", errors);
            requireBoolean(contentEnabled, "rank", file, "content_enabled.rank", errors);
            requireBoolean(contentEnabled, "brewery", file, "content_enabled.brewery", errors);
            requireBoolean(contentEnabled, "cooking", file, "content_enabled.cooking", errors);
            requireBoolean(contentEnabled, "fishing", file, "content_enabled.fishing", errors);
            requireBoolean(contentEnabled, "resource_collection", file, "content_enabled.resource_collection", errors);
            requireBoolean(contentEnabled, "seasonal", file, "content_enabled.seasonal", errors);
            requireBoolean(contentEnabled, "sukima_dungeon", file, "content_enabled.sukima_dungeon", errors);
            requireBoolean(contentEnabled, "party", file, "content_enabled.party", errors);
            requireBoolean(contentEnabled, "minigame", file, "content_enabled.minigame", errors);
        }
        Map<String, Object> persistence = requireMap(root, "persistence", file, errors);
        if (persistence != null) {
            requirePositiveNumber(persistence, "flush_interval_minutes", file, "persistence.flush_interval_minutes", errors);
        }
    }

    private static void validateArenaSettingsConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("arena/settings.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            errors.add(format("missing arena settings config", file, "<root>", "settings config is required"));
            return;
        }
        requireMap(root, "bgm", file, errors);
        requireMap(root, "multiplayer", file, errors);
        requireMap(root, "entrance_lift", file, errors);
        requireMap(root, "down", file, errors);
        requireMap(root, "mob_spawn", file, errors);
        requireMap(root, "door_animation", file, errors);
        requireMap(root, "world_settings", file, errors);
        Map<String, Object> session = requireMap(root, "session", file, errors);
        if (session != null) {
            requirePositiveNumber(session, "max_concurrent", file, "session.max_concurrent", errors);
        }
        Map<String, Object> worldPool = requireMap(root, "world_pool", file, errors);
        if (worldPool != null) {
            requirePositiveNumber(worldPool, "size", file, "world_pool.size", errors);
            requirePositiveNumber(worldPool, "cleanup_blocks_per_tick", file, "world_pool.cleanup_blocks_per_tick", errors);
        }
    }

    private static void validateArenaMissionConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("arena/mission.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            errors.add(format("missing arena mission config", file, "<root>", "mission config is required"));
            return;
        }
        Map<String, Object> mission = requireMap(root, "mission", file, errors);
        if (mission != null) {
            requirePositiveNumber(mission, "generate_count", file, "mission.generate_count", errors);
        }
        requireMap(root, "license", file, errors);
        Map<String, Object> barrierRestart = requireMap(root, "barrier_restart", file, errors);
        if (barrierRestart != null) {
            requirePositiveNumber(barrierRestart, "default_duration_seconds", file, "barrier_restart.default_duration_seconds", errors);
            requireNonNegativeNumber(barrierRestart, "corruption_ratio_base", file, "barrier_restart.corruption_ratio_base", errors);
        }
        requireMap(root, "mission_charactor_config", file, errors);
    }

    private static void validateArenaOverEnchanterConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("arena/over_enchanter.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            errors.add(format("missing arena over enchanter config", file, "<root>", "over enchanter config is required"));
            return;
        }
        requireMap(root, "slot_unlocks", file, errors);
        requireMap(root, "limit_breaking", file, errors);
        requireMap(root, "incompatible_combination", file, errors);
        requireMap(root, "invalid_target_attach", file, errors);
    }

    private static void validateRankSettingsConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("rank/settings.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            errors.add(format("missing rank settings config", file, "<root>", "rank settings config is required"));
            return;
        }
        Map<String, Object> combatExp = requireMap(root, "combat_exp", file, errors);
        if (combatExp != null) {
            requireNonNegativeNumber(combatExp, "health_multiplier", file, "combat_exp.health_multiplier", errors);
        }
    }

    private static void validateIngredientDefinitions(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("ingredient_definition.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        Map<String, Object> ingredients = requireMap(root, "ingredients", file, errors);
        if (ingredients == null) {
            return;
        }
        requireNonEmpty(ingredients, file, "ingredients", errors);
        for (Map.Entry<String, Object> entry : ingredients.entrySet()) {
            Map<String, Object> ingredient = asMap(entry.getValue());
            String path = "ingredients." + entry.getKey();
            if (ingredient == null) {
                errors.add(format("invalid ingredient", file, path, "ingredient must be a section"));
                continue;
            }
            boolean hasMaterial = isNonBlankString(ingredient.get("material"));
            boolean hasMaterials = ingredient.get("materials") instanceof List<?> list && !list.isEmpty();
            boolean hasCustomItem = isNonBlankString(ingredient.get("custom_item_id"));
            boolean hasCustomItems = ingredient.get("custom_item_ids") instanceof List<?> list && !list.isEmpty();
            if (!hasMaterial && !hasMaterials && !hasCustomItem && !hasCustomItems) {
                errors.add(format("missing ingredient matcher", file, path, "material, materials, custom_item_id, or custom_item_ids is required"));
            }
            if (hasMaterial) {
                requireMaterial(ingredient.get("material"), file, path + ".material", errors);
            }
            if (ingredient.get("materials") instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    requireMaterial(list.get(i), file, path + ".materials[" + i + "]", errors);
                }
            }
            if (hasCustomItem && !isCustomItemId((String) ingredient.get("custom_item_id"))) {
                errors.add(format("invalid custom item id", file, path + ".custom_item_id", "feature.id format is required"));
            }
            if (ingredient.get("custom_item_ids") instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object value = list.get(i);
                    if (!(value instanceof String id) || !isCustomItemId(id)) {
                        errors.add(format("invalid custom item id", file, path + ".custom_item_ids[" + i + "]", "feature.id format is required"));
                    }
                }
            }
        }
    }

    private static void validateMobDefinitions(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("mob_definition.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        requireNonEmpty(root, file, "<root>", errors);
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            Map<String, Object> mob = asMap(entry.getValue());
            if (mob == null) {
                errors.add(format("invalid mob definition", file, entry.getKey(), "mob definition must be a section"));
                continue;
            }
            requireString(mob, "type", file, entry.getKey() + ".type", errors);
            requirePositiveNumber(mob, "health", file, entry.getKey() + ".health", errors);
            requireNonNegativeNumber(mob, "attack", file, entry.getKey() + ".attack", errors);
            requireNonNegativeNumber(mob, "movement_speed", file, entry.getKey() + ".movement_speed", errors);
            requireNonNegativeNumber(mob, "armor", file, entry.getKey() + ".armor", errors);
        }
    }

    private static void validateArenaDropConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("arena/drop.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        requireNonEmpty(root, file, "<root>", errors);
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            Map<String, Object> drop = asMap(entry.getValue());
            if (drop == null) {
                errors.add(format("invalid arena drop", file, entry.getKey(), "drop entry must be a section"));
                continue;
            }
            requireNonNegativeNumber(drop, "base_exp", file, entry.getKey() + ".base_exp", errors);
            Object itemsValue = drop.get("items");
            if (!(itemsValue instanceof List<?> items)) {
                errors.add(format("missing arena drop items", file, entry.getKey() + ".items", "items list is required"));
                continue;
            }
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = asMap(items.get(i));
                String path = entry.getKey() + ".items[" + i + "]";
                if (item == null) {
                    errors.add(format("invalid arena drop item", file, path, "item must be a section"));
                    continue;
                }
                requireMaterial(item.get("item"), file, path + ".item", errors);
                requireAmount(item.get("amount"), file, path + ".amount", errors);
                if (item.containsKey("chance")) {
                    requireRatio(item.get("chance"), file, path + ".chance", errors);
                }
            }
        }
    }

    private static void validateArenaRewardConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("arena/reward.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            errors.add(format("missing arena reward config", file, "<root>", "reward config is required"));
            return;
        }
        Map<String, Object> mobToken = requireMap(root, "mob_token", file, errors);
        if (mobToken != null) {
            requireRatio(mobToken.get("drop_chance"), file, "mob_token.drop_chance", errors);
            requireNonNegativeNumber(mobToken, "looting_bonus_per_level", file, "mob_token.looting_bonus_per_level", errors);
        }
        Map<String, Object> enchantShard = requireMap(root, "enchant_shard", file, errors);
        if (enchantShard != null) {
            requireNonNegativeNumber(enchantShard, "drop_rate_multiplier", file, "enchant_shard.drop_rate_multiplier", errors);
            requireNonNegativeNumber(enchantShard, "looting_multiplier_per_level", file, "enchant_shard.looting_multiplier_per_level", errors);
        }
    }

    private static void validateArenaTokenExchangeConfig(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("arena/token_exchange.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            errors.add(format("missing arena token exchange config", file, "<root>", "token exchange config is required"));
            return;
        }
        Map<String, Object> rates = requireMap(root, "rates", file, errors, "rates");
        if (rates == null) {
            return;
        }
        requireNonEmpty(rates, file, "rates", errors);
        for (Map.Entry<String, Object> entry : rates.entrySet()) {
            requirePositiveNumber(entry.getValue(), file, "rates." + entry.getKey(), errors);
        }
        for (String categoryId : REQUIRED_ARENA_TOKEN_EXCHANGE_RATES) {
            if (!rates.containsKey(categoryId)) {
                errors.add(format("missing arena token exchange rate", file, "rates." + categoryId, "rate is required for generated token category"));
            }
        }
    }

    private static void validateArenaThemes(Path configRoot, Map<Path, Object> configs, List<String> errors) throws IOException {
        Path themeDir = configRoot.resolve("arena/themes");
        try (Stream<Path> paths = Files.list(themeDir)) {
            for (Path file : paths.filter(ResourceConfigurationValidator::isYaml).sorted().toList()) {
                Map<String, Object> root = rootMap(configs, file, errors);
                if (root == null) {
                    continue;
                }
                requireRatio(root.get("promotion_probability"), file, "promotion_probability", errors);
                validateArenaThemeVariant(file, root, "normal", true, errors);
                validateArenaThemeVariant(file, root, "promoted", false, errors);
            }
        }
    }

    private static void validateArenaThemeVariant(Path file, Map<String, Object> root, String variantName, boolean required, List<String> errors) {
        Object value = root.get(variantName);
        if (value == null && !required) {
            return;
        }
        Map<String, Object> variant = asMap(value);
        if (variant == null) {
            errors.add(format("invalid arena theme", file, variantName, "variant must be a section"));
            return;
        }
        if ("normal".equals(variantName)) {
            requireMaterial(variant.get("icon"), file, variantName + ".icon", errors);
            requirePositiveNumber(variant, "weight", file, variantName + ".weight", errors);
        }
        requirePositiveNumber(variant, "difficulty_star", file, variantName + ".difficulty_star", errors);
        requirePositiveNumber(variant, "max_participants", file, variantName + ".max_participants", errors);
        Object wavesValue = variant.get("waves");
        if (!(wavesValue instanceof List<?> waves) || waves.isEmpty()) {
            errors.add(format("missing arena theme waves", file, variantName + ".waves", "at least one wave is required"));
            return;
        }
        for (int i = 0; i < waves.size(); i++) {
            Map<String, Object> wave = asMap(waves.get(i));
            String path = variantName + ".waves[" + i + "]";
            if (wave == null) {
                errors.add(format("invalid arena theme wave", file, path, "wave must be a section"));
                continue;
            }
            requirePositiveNumber(wave, "wave", file, path + ".wave", errors);
            requirePositiveNumber(wave, "spawn_interval", file, path + ".spawn_interval", errors);
            requirePositiveNumber(wave, "clear_mob_count", file, path + ".clear_mob_count", errors);
            requirePositiveNumber(wave, "max_alive", file, path + ".max_alive", errors);
            Map<String, Object> mobs = requireMap(wave, "mobs", file, errors, path + ".mobs");
            if (mobs == null) {
                continue;
            }
            requireNonEmpty(mobs, file, path + ".mobs", errors);
            for (Map.Entry<String, Object> mobEntry : mobs.entrySet()) {
                Map<String, Object> mob = asMap(mobEntry.getValue());
                String mobPath = path + ".mobs." + mobEntry.getKey();
                if (mob == null) {
                    errors.add(format("invalid arena theme mob", file, mobPath, "mob entry must be a section"));
                    continue;
                }
                requirePositiveNumber(mob, "weight", file, mobPath + ".weight", errors);
                requirePositiveNumber(mob, "stat_multiplier", file, mobPath + ".stat_multiplier", errors);
            }
        }
    }

    private static void validateRankJobExp(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("rank/job_exp.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        requireNonEmpty(root, file, "<root>", errors);
        for (Map.Entry<String, Object> profession : root.entrySet()) {
            Map<String, Object> entries = asMap(profession.getValue());
            if (entries == null) {
                errors.add(format("invalid job exp table", file, profession.getKey(), "profession exp table must be a section"));
                continue;
            }
            requireNonEmpty(entries, file, profession.getKey(), errors);
            for (Map.Entry<String, Object> entry : entries.entrySet()) {
                requirePositiveNumber(entry.getValue(), file, profession.getKey() + "." + entry.getKey(), errors);
            }
        }
    }

    private static void validateNpcMenu(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("npc/menu.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        Map<String, Object> oageBox = requireMap(root, "oage_box", file, errors);
        if (oageBox == null) {
            return;
        }
        Map<String, Object> rewards = requireMap(oageBox, "rewards", file, errors, "oage_box.rewards");
        if (rewards == null) {
            return;
        }
        Map<String, Object> itemRewards = requireMap(rewards, "item", file, errors, "oage_box.rewards.item");
        if (itemRewards != null) {
            requireNonEmpty(itemRewards, file, "oage_box.rewards.item", errors);
            for (Map.Entry<String, Object> entry : itemRewards.entrySet()) {
                String path = "oage_box.rewards.item." + entry.getKey();
                if (!isCustomItemId(entry.getKey())) {
                    errors.add(format("invalid custom item id", file, path, "custom item id must use the /ccc give feature.id format"));
                }
                Map<String, Object> reward = asMap(entry.getValue());
                if (reward == null) {
                    errors.add(format("invalid oage box item reward", file, path, "reward must be a section"));
                    continue;
                }
                requirePositiveNumber(reward, "amount", file, path + ".amount", errors);
                requirePositiveNumber(reward, "weight", file, path + ".weight", errors);
            }
        }
        validateWeightedAmountRewards(rewards, "dg", file, "oage_box.rewards.dg", errors);
        validateWeightedAmountRewards(rewards, "world_point", file, "oage_box.rewards.world_point", errors);
    }

    private static void validateWeightedAmountRewards(Map<String, Object> rewards, String key, Path file, String path, List<String> errors) {
        Object rewardsValue = rewards.get(key);
        if (!(rewardsValue instanceof List<?> rewardList) || rewardList.isEmpty()) {
            errors.add(format("missing oage box reward", file, path, "at least one reward is required"));
            return;
        }
        for (int i = 0; i < rewardList.size(); i++) {
            Map<String, Object> reward = asMap(rewardList.get(i));
            String rewardPath = path + "[" + i + "]";
            if (reward == null) {
                errors.add(format("invalid oage box reward", file, rewardPath, "reward must be a section"));
                continue;
            }
            requirePositiveNumber(reward, "amount", file, rewardPath + ".amount", errors);
            requirePositiveNumber(reward, "weight", file, rewardPath + ".weight", errors);
        }
    }

    private static void validateCustomItemConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        requirePositiveNumber(rootMap(configs, configRoot.resolve("custom_item/air_cannon.yml"), errors), "cooldown_ticks", configRoot.resolve("custom_item/air_cannon.yml"), "cooldown_ticks", errors);
        validateCustomHead(configRoot, configs, errors);
    }

    private static void validateCustomHead(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("custom_item/custom_head.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        Map<String, Object> variants = requireMap(root, "variants", file, errors);
        if (variants == null) {
            return;
        }
        requireNonEmpty(variants, file, "variants", errors);
        for (Map.Entry<String, Object> entry : variants.entrySet()) {
            Map<String, Object> variant = asMap(entry.getValue());
            String path = "variants." + entry.getKey();
            if (variant == null) {
                errors.add(format("invalid custom head variant", file, path, "variant must be a section"));
                continue;
            }
            Map<String, Object> item = requireMap(variant, "item", file, errors, path + ".item");
            if (item != null) {
                requireMaterial(item.get("material"), file, path + ".item.material", errors);
                requireString(item, "name", file, path + ".item.name", errors);
            }
            Object headsValue = variant.get("heads");
            if (!(headsValue instanceof List<?> heads) || heads.isEmpty()) {
                errors.add(format("missing custom heads", file, path + ".heads", "at least one head is required"));
            }
        }
    }

    private static void validateBreweryConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path configFile = configRoot.resolve("brewery/config.yml");
        Map<String, Object> config = rootMap(configs, configFile, errors);
        if (config != null) {
            requireMap(config, "brewery", configFile, errors);
        }
        Path recipeFile = configRoot.resolve("brewery/recipes.yml");
        Map<String, Object> recipeRoot = rootMap(configs, recipeFile, errors);
        if (recipeRoot != null) {
            Map<String, Object> preparations = requireMap(recipeRoot, "preparations", recipeFile, errors);
            Map<String, Object> recipes = requireMap(recipeRoot, "brew_families", recipeFile, errors);
            if (preparations == null || recipes == null) {
                return;
            }
            requireNonEmpty(preparations, recipeFile, "preparations", errors);
            requireNonEmpty(recipes, recipeFile, "brew_families", errors);
            for (Map.Entry<String, Object> entry : recipes.entrySet()) {
                Map<String, Object> recipe = asMap(entry.getValue());
                String path = "brew_families." + entry.getKey();
                if (recipe == null) {
                    errors.add(format("invalid brewery recipe", recipeFile, path, "recipe must be a section"));
                    continue;
                }
                requireMap(recipe, "fermentation", recipeFile, errors, path + ".fermentation");
                requireMap(recipe, "distillation", recipeFile, errors, path + ".distillation");
                requireMap(recipe, "aging", recipeFile, errors, path + ".aging");
                requireMap(recipe, "outputs", recipeFile, errors, path + ".outputs");
            }
        }
    }

    private static void validatePartyConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("party/config.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) return;
        Map<String, Object> party = requireMap(root, "party", file, errors);
        if (party == null) return;
        requireIntegerRange(party.get("default_capacity"), 1, 99, file, "party.default_capacity", errors);
        requirePositiveInteger(party.get("invite_expiration_seconds"), file, "party.invite_expiration_seconds", errors);
        requirePositiveInteger(party.get("offline_grace_seconds"), file, "party.offline_grace_seconds", errors);
    }

    private static void validateCookingConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path configFile = configRoot.resolve("cooking/config.yml");
        Map<String, Object> config = rootMap(configs, configFile, errors);
        if (config != null) {
            requireBoolean(config, "enabled", configFile, "enabled", errors);
            Map<String, Object> matching = requireMap(config, "matching", configFile, errors);
            if (matching != null) {
                for (String key : List.of("maximum_excess_ratio_per_ingredient", "maximum_unknown_ratio",
                    "maximum_total_error", "ambiguity_margin")) {
                    requireRatio(matching.get(key), configFile, "matching." + key, errors);
                }
            }
            Map<String, Object> equipment = requireMap(config, "equipment", configFile, errors);
            if (equipment != null) {
                requireIntegerRange(equipment.get("pan_max_scale"), 5, 5, configFile, "equipment.pan_max_scale", errors);
                requireIntegerRange(equipment.get("cauldron_max_scale"), 3, 3, configFile, "equipment.cauldron_max_scale", errors);
            }
            Map<String, Object> state = requireMap(config, "state", configFile, errors);
            if (state != null) requirePositiveInteger(state.get("flush_interval_ticks"), configFile, "state.flush_interval_ticks", errors);
        }
        for (String name : List.of("ingredients.yml", "cutting.yml", "recipe.yml")) {
            Path file = configRoot.resolve("cooking/" + name);
            Map<String, Object> root = rootMap(configs, file, errors);
            if (root != null) {
                requireMap(root, name.equals("ingredients.yml") ? "ingredients" : "recipes", file, errors);
            }
        }
    }

    private static void validateFishingConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("fishing/fish.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) return;
        requireBoolean(root, "enabled", file, "enabled", errors);
        Map<String, Object> waitTime = requireMap(root, "wait_time", file, errors);
        if (waitTime != null) {
            requirePositiveInteger(waitTime.get("min_ticks"), file, "wait_time.min_ticks", errors);
            requirePositiveInteger(waitTime.get("max_ticks"), file, "wait_time.max_ticks", errors);
        }
        Map<String, Object> minigame = requireMap(root, "minigame", file, errors);
        if (minigame != null) {
            for (String key : List.of("hook_window_ticks", "fight_duration_ticks", "fight_interval_ticks",
                "status_message_ticks",
                "green_width", "input_step", "visual_forward_range", "visual_lateral_range",
                "facing_bonus_multiplier")) {
                requirePositiveNumber(minigame, key, file, "minigame." + key, errors);
            }
            requireNonNegativeNumber(minigame, "yellow_margin", file, "minigame.yellow_margin", errors);
            requireFiniteNumberRange(minigame.get("initial_effectiveness"), 0.0, 100.0, file, "minigame.initial_effectiveness", errors);
            requireFiniteNumberRange(minigame.get("lure_reduction_per_level"), 0.0, 0.9, file, "minigame.lure_reduction_per_level", errors);
            requireFiniteNumberRange(minigame.get("resistance_smoothing"), 0.01, 1.0, file, "minigame.resistance_smoothing", errors);
            requireFiniteNumberRange(minigame.get("lateral_smoothing"), 0.01, 1.0, file, "minigame.lateral_smoothing", errors);
        }
        Map<String, Object> rods = requireMap(root, "rod", file, errors);
        if (rods != null) {
            requireNonEmpty(rods, file, "rod", errors);
            for (Map.Entry<String, Object> entry : rods.entrySet()) {
                String path = "rod." + entry.getKey();
                Map<String, Object> rod = asMap(entry.getValue());
                if (rod == null) {
                    errors.add(format("invalid fishing rod", file, path, "rod must be a section"));
                    continue;
                }
                for (String key : List.of("power_multiplier", "finesse_multiplier")) {
                    requirePositiveNumber(rod, key, file, path + "." + key, errors);
                }
                requirePositiveInteger(rod.get("max_durability"), file, path + ".max_durability", errors);
            }
        }
        Map<String, Object> bait = requireMap(root, "bait", file, errors);
        if (bait != null) {
            requireBoolean(bait, "consume_on_valid_session", file, "bait.consume_on_valid_session", errors);
            Map<String, Object> definitions = requireMap(bait, "definitions", file, errors, "bait.definitions");
            if (definitions == null) return;
            requireNonEmpty(definitions, file, "bait.definitions", errors);
            for (Map.Entry<String, Object> entry : definitions.entrySet()) {
                String path = "bait.definitions." + entry.getKey();
                Map<String, Object> definition = asMap(entry.getValue());
                if (definition == null) {
                    errors.add(format("invalid fishing bait", file, path, "bait must be a section"));
                    continue;
                }
                requireMaterial(definition.get("material"), file, path + ".material", errors);
                for (String key : List.of("wait_time_multiplier", "rare_catch_multiplier", "quality_multiplier")) {
                    requirePositiveNumber(definition, key, file, path + "." + key, errors);
                }
            }
        }
        Map<String, Object> fish = requireMap(root, "fish", file, errors);
        if (fish != null) {
            requireNonEmpty(fish, file, "fish", errors);
            for (Map.Entry<String, Object> entry : fish.entrySet()) {
                String path = "fish." + entry.getKey();
                Map<String, Object> definition = asMap(entry.getValue());
                if (definition == null) {
                    errors.add(format("invalid fishing definition", file, path, "fish must be a section"));
                    continue;
                }
                requireMaterial(definition.get("material"), file, path + ".material", errors);
                requirePositiveNumber(definition, "weight", file, path + ".weight", errors);
                requirePositiveNumber(definition, "min_level", file, path + ".min_level", errors);
                requireString(definition, "rarity", file, path + ".rarity", errors);
                Map<String, Object> quality = requireMap(definition, "quality", file, errors, path + ".quality");
                if (quality != null) {
                    requireNonEmpty(quality, file, path + ".quality", errors);
                    Set<String> requiredQualityIds = Set.of("common", "rare", "legendary");
                    if (!quality.keySet().equals(requiredQualityIds)) {
                        errors.add(format("invalid fishing quality tiers", file, path + ".quality",
                            "exactly common, rare and legendary are required"));
                    }
                    for (String qualityId : requiredQualityIds) {
                        requirePositiveNumber(quality, qualityId, file, path + ".quality." + qualityId, errors);
                    }
                }
                Map<String, Object> water = requireMap(definition, "water", file, errors, path + ".water");
                if (water != null) {
                    requireString(water, "type", file, path + ".water.type", errors);
                    Object waterType = water.get("type");
                    if (waterType instanceof String value &&
                        !Set.of("any", "shore", "narrow", "open", "deep_open").contains(value)) {
                        errors.add(format("invalid fishing water type", file, path + ".water.type",
                            "one of any, shore, narrow, open or deep_open is required"));
                    }
                    for (String dimension : List.of("depth", "width")) {
                        Map<String, Object> range = requireMap(water, dimension, file, errors, path + ".water." + dimension);
                        if (range != null) {
                            requirePositiveInteger(range.get("min"), file, path + ".water." + dimension + ".min", errors);
                            requirePositiveInteger(range.get("max"), file, path + ".water." + dimension + ".max", errors);
                        }
                    }
                }
                Map<String, Object> fight = requireMap(definition, "fight", file, errors, path + ".fight");
                if (fight != null) {
                    requireFiniteNumberRange(fight.get("target_center"), 0.0, 100.0, file, path + ".fight.target_center", errors);
                    requireFiniteNumberRange(fight.get("drift_per_step"), 0.0, 100.0, file, path + ".fight.drift_per_step", errors);
                    requireFiniteNumberRange(fight.get("direction_persistence"), 0.0, 1.0, file, path + ".fight.direction_persistence", errors);
                    requirePositiveNumber(fight, "duration_multiplier", file, path + ".fight.duration_multiplier", errors);
                }
            }
        }
    }

    private static void validateMiniGameConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path settingsFile = configRoot.resolve("minigame/settings.yml");
        Map<String, Object> settings = rootMap(configs, settingsFile, errors);
        if (settings == null) {
            errors.add(format("missing minigame settings config", settingsFile, "<root>", "settings config is required"));
        } else {
            requireAllowedKeys(settings, Set.of("default_game_id", "defaults"), settingsFile, errors);
            Object defaultGameId = settings.get("default_game_id");
            if (!(defaultGameId instanceof String gameId) || gameId.isBlank() || !Set.of("race", "hideandseek", "chase", "colosseum", "endergolf").contains(gameId)) {
                errors.add(format("invalid minigame default", settingsFile, "default_game_id", "supported non-blank game id is required"));
            }
            Map<String, Object> defaults = requireMap(settings, "defaults", settingsFile, errors);
            if (defaults != null) {
                requireAllowedKeys(defaults, Set.of("time_limit_seconds", "marker_radius"), settingsFile, errors);
                requireIntegerRange(defaults.get("time_limit_seconds"), 30, 3600, settingsFile, "defaults.time_limit_seconds", errors);
                requireFiniteNumberRange(defaults.get("marker_radius"), 0.5, 4.0, settingsFile, "defaults.marker_radius", errors);
            }
        }

        validateMiniGameFile(configRoot, configs, errors, "hideandseek", Set.of("time_limit_seconds", "hunter_count", "preparation_seconds"), true, false);
        Map<String, Object> hideAndSeek = rootMap(configs, configRoot.resolve("minigame/hideandseek.yml"), errors);
        if (hideAndSeek != null) {
            requireIntegerRange(hideAndSeek.get("preparation_seconds"), 0, 600,
                configRoot.resolve("minigame/hideandseek.yml"), "preparation_seconds", errors);
        }
        validateMiniGameFile(configRoot, configs, errors, "chase", Set.of("time_limit_seconds", "hunter_count"), true, false);
        validateMiniGameFile(configRoot, configs, errors, "colosseum", Set.of("time_limit_seconds", "first_to"), false, true);
        validateMiniGameFile(configRoot, configs, errors, "endergolf", Set.of("time_limit_seconds"), false, false);
    }

    private static void validateMiniGameFile(Path configRoot, Map<Path, Object> configs, List<String> errors,
                                             String gameId, Set<String> allowed, boolean hunter, boolean firstTo) {
        Path file = configRoot.resolve("minigame/" + gameId + ".yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            errors.add(format("missing minigame config", file, "<root>", "game config is required"));
            return;
        }
        requireAllowedKeys(root, allowed, file, errors);
        requireIntegerRange(root.get("time_limit_seconds"), 30, 3600, file, "time_limit_seconds", errors);
        if (hunter) requireIntegerRange(root.get("hunter_count"), 1, 99, file, "hunter_count", errors);
        if (firstTo) requireIntegerRange(root.get("first_to"), 1, 99, file, "first_to", errors);
    }

    private static void validateResourceCollectionConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path configFile = configRoot.resolve("resource_collection/config.yml");
        Map<String, Object> config = rootMap(configs, configFile, errors);
        if (config != null) {
            requireBoolean(config, "enabled", configFile, "enabled", errors);
            Map<String, Object> chisel = requireMap(config, "chisel", configFile, errors);
            if (chisel != null) {
                requireAllowedKeys(
                    chisel,
                    Set.of("target_count", "target_timeout_ticks", "particle_count"),
                    configFile,
                    errors
                );
                requirePositiveInteger(chisel.get("target_count"), configFile, "chisel.target_count", errors);
                requirePositiveInteger(
                    chisel.get("target_timeout_ticks"),
                    configFile,
                    "chisel.target_timeout_ticks",
                    errors
                );
                requirePositiveInteger(chisel.get("particle_count"), configFile, "chisel.particle_count", errors);
            }
            validateResourceFeatureSection(
                config, "mineral",
                Set.of("enabled", "normal_bonus_drop", "work_speed", "inspection", "chisel_game"),
                configFile, errors
            );
            validateResourceFeatureSection(
                config, "forest",
                Set.of(
                    "enabled", "normal_bonus_drop", "work_speed", "heartwood", "bark",
                    "timber_processing", "forest_products", "leaf_cleanup", "automatic_replant"
                ),
                configFile, errors
            );
            validateResourceFeatureSection(
                config, "crop",
                Set.of(
                    "enabled", "normal_bonus_drop", "work_speed", "wild_gathering", "surface_gathering",
                    "area_tilling", "area_harvest", "automatic_replant"
                ),
                configFile, errors
            );
        }
        Path forestProductsFile = configRoot.resolve("resource_collection/forest_products.yml");
        Map<String, Object> forestProducts = rootMap(configs, forestProductsFile, errors);
        if (forestProducts != null) {
            requireAllowedKeys(
                forestProducts,
                Set.of(
                    "schema_version", "enabled", "base_discovery_chance",
                    "maximum_discovery_bonus", "burl_override_chance"
                ),
                forestProductsFile,
                errors
            );
            requireIntegerRange(
                forestProducts.get("schema_version"),
                2,
                2,
                forestProductsFile,
                "schema_version",
                errors
            );
            requireBoolean(forestProducts, "enabled", forestProductsFile, "enabled", errors);
            requireFiniteNumberRange(
                forestProducts.get("base_discovery_chance"),
                0.0,
                1.0,
                forestProductsFile,
                "base_discovery_chance",
                errors
            );
            requireFiniteNumberRange(
                forestProducts.get("maximum_discovery_bonus"),
                0.0,
                1.0,
                forestProductsFile,
                "maximum_discovery_bonus",
                errors
            );
            requireFiniteNumberRange(
                forestProducts.get("burl_override_chance"),
                0.0,
                1.0,
                forestProductsFile,
                "burl_override_chance",
                errors
            );
        }
    }

    private static void validateResourceFeatureSection(
        Map<String, Object> config,
        String sectionName,
        Set<String> allowedKeys,
        Path configFile,
        List<String> errors
    ) {
        Map<String, Object> section = requireMap(config, sectionName, configFile, errors);
        if (section == null) return;
        requireAllowedKeys(section, allowedKeys, configFile, errors);
        for (String key : allowedKeys) {
            requireBoolean(section, key, configFile, sectionName + "." + key, errors);
        }
    }

    private static void validateSukimaDungeonConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path settingsFile = configRoot.resolve("sukima_dungeon/settings.yml");
        Map<String, Object> settingsRoot = rootMap(configs, settingsFile, errors);
        if (settingsRoot == null) {
            errors.add(format("missing sukima dungeon settings config", settingsFile, "<root>", "settings config is required"));
        } else {
            Map<String, Object> sizes = requireMap(settingsRoot, "sizes", settingsFile, errors);
            if (sizes != null) {
                requireNonEmpty(sizes, settingsFile, "sizes", errors);
                for (Map.Entry<String, Object> entry : sizes.entrySet()) {
                    Map<String, Object> size = asMap(entry.getValue());
                    if (size == null) {
                        errors.add(format("invalid config value", settingsFile, "sizes." + entry.getKey(), "size entry must be a section"));
                        continue;
                    }
                    String path = "sizes." + entry.getKey();
                    requirePositiveNumber(size, "tiles", settingsFile, path + ".tiles", errors);
                    requirePositiveNumber(size, "duration", settingsFile, path + ".duration", errors);
                    requireNonNegativeNumber(size, "npc_base_count", settingsFile, path + ".npc_base_count", errors);
                    requireNonNegativeNumber(size, "sprout_base_count", settingsFile, path + ".sprout_base_count", errors);
                }
            }
        }
        Path themeFile = configRoot.resolve("sukima_dungeon/theme.yml");
        Map<String, Object> themeRoot = rootMap(configs, themeFile, errors);
        if (themeRoot != null) {
            Map<String, Object> themes = requireMap(themeRoot, "themes", themeFile, errors);
            if (themes != null) {
                requireNonEmpty(themes, themeFile, "themes", errors);
            }
        }
        Path lootFile = configRoot.resolve("sukima_dungeon/loot.yml");
        Map<String, Object> lootRoot = rootMap(configs, lootFile, errors);
        if (lootRoot != null) {
            Map<String, Object> items = requireMap(lootRoot, "items", lootFile, errors);
            if (items != null) {
                requireNonEmpty(items, lootFile, "items", errors);
                for (Map.Entry<String, Object> entry : items.entrySet()) {
                    Map<String, Object> item = asMap(entry.getValue());
                    String path = "items." + entry.getKey();
                    if (item == null) {
                        errors.add(format("invalid sukima loot item", lootFile, path, "item must be a section"));
                        continue;
                    }
                    requireMaterial(item.get("material"), lootFile, path + ".material", errors);
                    requirePositiveNumber(item, "weight", lootFile, path + ".weight", errors);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rootMap(Map<Path, Object> configs, Path file, List<String> errors) {
        Object root = configs.get(file);
        if (root == null) {
            return null;
        }
        if (root instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
            copy.remove("config_version");
            return copy;
        }
        errors.add(format("invalid config root", file, "<root>", "root must be a section"));
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static Map<String, Object> requireMap(Map<String, Object> parent, String key, Path file, List<String> errors) {
        return requireMap(parent, key, file, errors, key);
    }

    private static Map<String, Object> requireMap(Map<String, Object> parent, String key, Path file, List<String> errors, String path) {
        if (parent == null) {
            return null;
        }
        Map<String, Object> map = asMap(parent.get(key));
        if (map == null) {
            errors.add(format("missing config section", file, path, "section is required"));
        }
        return map;
    }

    private static void requireNonEmpty(Map<String, Object> map, Path file, String path, List<String> errors) {
        if (map.isEmpty()) {
            errors.add(format("empty config section", file, path, "section must contain at least one entry"));
        }
    }

    private static void requireAllowedKeys(Map<String, Object> map, Set<String> allowed, Path file, List<String> errors) {
        map.keySet().stream()
            .filter(key -> !allowed.contains(key))
            .sorted()
            .forEach(key -> errors.add(format("unknown config key", file, key, "unknown minigame config key")));
    }

    private static void requireIntegerRange(Object value, int minimum, int maximum, Path file, String path, List<String> errors) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) ||
            number.doubleValue() != number.longValue()) {
            errors.add(format("invalid integer", file, path, "integer value is required"));
            return;
        }
        long integer = number.longValue();
        if (integer < minimum || integer > maximum) {
            errors.add(format("invalid integer range", file, path, "value must be between " + minimum + " and " + maximum));
        }
    }

    private static void requireFiniteNumberRange(Object value, double minimum, double maximum, Path file, String path, List<String> errors) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            errors.add(format("invalid finite number", file, path, "finite number is required"));
            return;
        }
        double numberValue = number.doubleValue();
        if (numberValue < minimum || numberValue > maximum) {
            errors.add(format("invalid number range", file, path, "value must be between " + minimum + " and " + maximum));
        }
    }

    private static void requireBoolean(Map<String, Object> map, String key, Path file, String path, List<String> errors) {
        if (!(map.get(key) instanceof Boolean)) {
            errors.add(format("invalid boolean", file, path, "boolean value is required"));
        }
    }

    private static void requireString(Map<String, Object> map, String key, Path file, String path, List<String> errors) {
        if (!isNonBlankString(map.get(key))) {
            errors.add(format("invalid string", file, path, "non-blank string is required"));
        }
    }

    private static void requirePositiveNumber(Map<String, Object> map, String key, Path file, String path, List<String> errors) {
        if (map == null) {
            return;
        }
        requirePositiveNumber(map.get(key), file, path, errors);
    }

    private static void requirePositiveNumber(Object value, Path file, String path, List<String> errors) {
        if (!(value instanceof Number number) || number.doubleValue() <= 0) {
            errors.add(format("invalid positive number", file, path, "positive number is required"));
        }
    }

    private static void requirePositiveInteger(Object value, Path file, String path, List<String> errors) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            errors.add(format("invalid integer", file, path, "positive integer value is required"));
            return;
        }
        if (((Number) value).longValue() <= 0) {
            errors.add(format("invalid positive integer", file, path, "positive integer is required"));
        }
    }

    private static void requireNonNegativeNumber(Map<String, Object> map, String key, Path file, String path, List<String> errors) {
        if (map == null) {
            return;
        }
        Object value = map.get(key);
        if (!(value instanceof Number number) || number.doubleValue() < 0) {
            errors.add(format("invalid non-negative number", file, path, "non-negative number is required"));
        }
    }

    private static void requireRatio(Object value, Path file, String path, List<String> errors) {
        if (!(value instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() > 1) {
            errors.add(format("invalid ratio", file, path, "number between 0 and 1 is required"));
        }
    }

    private static void requireMaterial(Object value, Path file, String path, List<String> errors) {
        if (!isNonBlankString(value) || Material.matchMaterial(value.toString()) == null) {
            errors.add(format("invalid material", file, path, "valid Bukkit material name is required"));
        }
    }

    private static void requireAmount(Object value, Path file, String path, List<String> errors) {
        if (value instanceof Number number && number.intValue() >= 0) {
            return;
        }
        if (value instanceof String text && text.matches("\\d+(-\\d+)?")) {
            return;
        }
        errors.add(format("invalid amount", file, path, "non-negative integer or min-max range is required"));
    }

    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static boolean isCustomItemId(String value) {
        return value != null && value.matches("[a-z0-9_]+(\\.[a-z0-9_]+)+");
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String resourcePath(Path configRoot, Path file) {
        return "config/" + configRoot.relativize(file).toString().replace('\\', '/');
    }

    private static String format(String type, Path file, String key, String detail) {
        return "[resource config validation] " + type + "\n"
            + "  file: " + file + "\n"
            + "  key: " + key + "\n"
            + "  detail: " + detail;
    }
}

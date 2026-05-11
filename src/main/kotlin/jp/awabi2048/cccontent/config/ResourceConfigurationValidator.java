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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ResourceConfigurationValidator {
    private static final Set<String> ALLOWED_EMPTY_CONFIGS = Set.of(
        "config/arena/mob_type.yml"
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
        validateIngredientDefinitions(configRoot, configs, errors);
        validateMobDefinitions(configRoot, configs, errors);
        validateArenaDropConfig(configRoot, configs, errors);
        validateArenaThemes(configRoot, configs, errors);
        validateRankJobExp(configRoot, configs, errors);
        validateRankJobs(configRoot, configs, errors);
        validateTutorialTasks(configRoot, configs, errors);
        validateNpcMenu(configRoot, configs, errors);
        validateCustomItemConfigs(configRoot, configs, errors);
        validateBreweryConfigs(configRoot, configs, errors);
        validateCookingConfigs(configRoot, configs, errors);
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
            requireBoolean(contentEnabled, "arena", file, "content_enabled.arena", errors);
            requireBoolean(contentEnabled, "rank", file, "content_enabled.rank", errors);
            requireBoolean(contentEnabled, "brewery", file, "content_enabled.brewery", errors);
            requireBoolean(contentEnabled, "cooking", file, "content_enabled.cooking", errors);
            requireBoolean(contentEnabled, "sukima_dungeon", file, "content_enabled.sukima_dungeon", errors);
        }
        Map<String, Object> persistence = requireMap(root, "persistence", file, errors);
        if (persistence != null) {
            requirePositiveNumber(persistence, "flush_interval_minutes", file, "persistence.flush_interval_minutes", errors);
        }
        Map<String, Object> sukima = requireMap(root, "sukima_dungeon", file, errors);
        if (sukima != null) {
            Map<String, Object> sizes = requireMap(sukima, "sizes", file, errors);
            if (sizes != null) {
                requireNonEmpty(sizes, file, "sukima_dungeon.sizes", errors);
                for (Map.Entry<String, Object> entry : sizes.entrySet()) {
                    Map<String, Object> size = asMap(entry.getValue());
                    if (size == null) {
                        errors.add(format("invalid config value", file, "sukima_dungeon.sizes." + entry.getKey(), "size entry must be a section"));
                        continue;
                    }
                    String path = "sukima_dungeon.sizes." + entry.getKey();
                    requirePositiveNumber(size, "tiles", file, path + ".tiles", errors);
                    requirePositiveNumber(size, "duration", file, path + ".duration", errors);
                    requireNonNegativeNumber(size, "npc_base_count", file, path + ".npc_base_count", errors);
                    requireNonNegativeNumber(size, "sprout_base_count", file, path + ".sprout_base_count", errors);
                }
            }
        }
        Map<String, Object> arena = requireMap(root, "arena", file, errors);
        if (arena != null) {
            requireMap(arena, "bgm", file, errors);
            requireMap(arena, "mission", file, errors);
            Map<String, Object> session = requireMap(arena, "session", file, errors);
            if (session != null) {
                requirePositiveNumber(session, "max_concurrent", file, "arena.session.max_concurrent", errors);
            }
            Map<String, Object> worldPool = requireMap(arena, "world_pool", file, errors);
            if (worldPool != null) {
                requirePositiveNumber(worldPool, "size", file, "arena.world_pool.size", errors);
                requirePositiveNumber(worldPool, "cleanup_blocks_per_tick", file, "arena.world_pool.cleanup_blocks_per_tick", errors);
            }
        }
        Map<String, Object> rank = requireMap(root, "rank", file, errors);
        if (rank != null) {
            requireMap(rank, "combat_exp", file, errors);
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
            if (!hasMaterial && !hasMaterials) {
                errors.add(format("missing ingredient materials", file, path, "material or materials is required"));
            }
            if (hasMaterial) {
                requireMaterial(ingredient.get("material"), file, path + ".material", errors);
            }
            if (ingredient.get("materials") instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    requireMaterial(list.get(i), file, path + ".materials[" + i + "]", errors);
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

    private static void validateArenaThemes(Path configRoot, Map<Path, Object> configs, List<String> errors) throws IOException {
        Path themeDir = configRoot.resolve("arena/themes");
        try (Stream<Path> paths = Files.list(themeDir)) {
            for (Path file : paths.filter(ResourceConfigurationValidator::isYaml).sorted().toList()) {
                Map<String, Object> root = rootMap(configs, file, errors);
                if (root == null) {
                    continue;
                }
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

    private static void validateRankJobs(Path configRoot, Map<Path, Object> configs, List<String> errors) throws IOException {
        Path jobDir = configRoot.resolve("rank/job");
        try (Stream<Path> paths = Files.list(jobDir)) {
            for (Path file : paths.filter(ResourceConfigurationValidator::isYaml).sorted().toList()) {
                Map<String, Object> root = rootMap(configs, file, errors);
                if (root == null) {
                    continue;
                }
                Map<String, Object> skills = requireMap(root, "skills", file, errors);
                if (skills == null) {
                    continue;
                }
                Map<String, Object> settings = requireMap(skills, "settings", file, errors, "skills.settings");
                if (settings != null) {
                    Map<String, Object> level = requireMap(settings, "level", file, errors, "skills.settings.level");
                    if (level != null) {
                        requirePositiveNumber(level, "initialExp", file, "skills.settings.level.initialExp", errors);
                        requirePositiveNumber(level, "base", file, "skills.settings.level.base", errors);
                        requirePositiveNumber(level, "maxLevel", file, "skills.settings.level.maxLevel", errors);
                    }
                    requireMaterial(settings.get("overviewIcon"), file, "skills.settings.overviewIcon", errors);
                }
                for (Map.Entry<String, Object> entry : skills.entrySet()) {
                    if ("settings".equals(entry.getKey())) {
                        continue;
                    }
                    Map<String, Object> skill = asMap(entry.getValue());
                    String path = "skills." + entry.getKey();
                    if (skill == null) {
                        errors.add(format("invalid skill node", file, path, "skill must be a section"));
                        continue;
                    }
                    requireNonNegativeNumber(skill, "requiredLevel", file, path + ".requiredLevel", errors);
                    requireMaterial(skill.get("icon"), file, path + ".icon", errors);
                    if (!(skill.get("children") instanceof List<?>)) {
                        errors.add(format("missing skill children", file, path + ".children", "children list is required"));
                    }
                }
            }
        }
    }

    private static void validateTutorialTasks(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("rank/tutorial_tasks.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        requireNonEmpty(root, file, "<root>", errors);
    }

    private static void validateNpcMenu(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("npc/menu.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        Map<String, Object> garbageBox = requireMap(root, "garbage_box", file, errors);
        if (garbageBox == null) {
            return;
        }
        Object rewardsValue = garbageBox.get("rewards");
        if (!(rewardsValue instanceof List<?> rewards) || rewards.isEmpty()) {
            errors.add(format("missing garbage box rewards", file, "garbage_box.rewards", "at least one reward is required"));
            return;
        }
        for (int i = 0; i < rewards.size(); i++) {
            Map<String, Object> reward = asMap(rewards.get(i));
            String path = "garbage_box.rewards[" + i + "]";
            if (reward == null) {
                errors.add(format("invalid garbage box reward", file, path, "reward must be a section"));
                continue;
            }
            requireMaterial(reward.get("material"), file, path + ".material", errors);
            requirePositiveNumber(reward, "amount", file, path + ".amount", errors);
            requirePositiveNumber(reward, "weight", file, path + ".weight", errors);
        }
    }

    private static void validateCustomItemConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        requirePositiveNumber(rootMap(configs, configRoot.resolve("custom_item/air_cannon.yml"), errors), "cooldown_ticks", configRoot.resolve("custom_item/air_cannon.yml"), "cooldown_ticks", errors);
        validateCustomHead(configRoot, configs, errors);
        validateRadioCassette(configRoot, configs, errors);
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

    private static void validateRadioCassette(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path file = configRoot.resolve("custom_item/radio_cassette.yml");
        Map<String, Object> root = rootMap(configs, file, errors);
        if (root == null) {
            return;
        }
        Map<String, Object> cassettes = requireMap(root, "cassettes", file, errors);
        if (cassettes == null) {
            return;
        }
        requireNonEmpty(cassettes, file, "cassettes", errors);
        for (Map.Entry<String, Object> entry : cassettes.entrySet()) {
            Map<String, Object> cassette = asMap(entry.getValue());
            String path = "cassettes." + entry.getKey();
            if (cassette == null) {
                errors.add(format("invalid cassette", file, path, "cassette must be a section"));
                continue;
            }
            requireString(cassette, "sound", file, path + ".sound", errors);
        }
    }

    private static void validateBreweryConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path configFile = configRoot.resolve("brewery/config.yml");
        Map<String, Object> config = rootMap(configs, configFile, errors);
        if (config != null) {
            requireBoolean(config, "enabled", configFile, "enabled", errors);
            requireMap(config, "settings", configFile, errors);
        }
        Path recipeFile = configRoot.resolve("brewery/recipe.yml");
        Map<String, Object> recipes = rootMap(configs, recipeFile, errors);
        if (recipes != null) {
            requireNonEmpty(recipes, recipeFile, "<root>", errors);
            for (Map.Entry<String, Object> entry : recipes.entrySet()) {
                Map<String, Object> recipe = asMap(entry.getValue());
                String path = entry.getKey();
                if (recipe == null) {
                    errors.add(format("invalid brewery recipe", recipeFile, path, "recipe must be a section"));
                    continue;
                }
                requireString(recipe, "name", recipeFile, path + ".name", errors);
                requirePositiveNumber(recipe, "required_skill_level", recipeFile, path + ".required_skill_level", errors);
                requireMap(recipe, "fermentation", recipeFile, errors, path + ".fermentation");
                requireMap(recipe, "final_output", recipeFile, errors, path + ".final_output");
            }
        }
    }

    private static void validateCookingConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
        Path configFile = configRoot.resolve("cooking/config.yml");
        Map<String, Object> config = rootMap(configs, configFile, errors);
        if (config != null) {
            requireBoolean(config, "enabled", configFile, "enabled", errors);
            requireMap(config, "settings", configFile, errors);
        }
        Path recipeFile = configRoot.resolve("cooking/recipe.yml");
        Map<String, Object> root = rootMap(configs, recipeFile, errors);
        if (root != null) {
            requireMap(root, "recipes", recipeFile, errors);
        }
    }

    private static void validateSukimaDungeonConfigs(Path configRoot, Map<Path, Object> configs, List<String> errors) {
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
            return (Map<String, Object>) map;
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

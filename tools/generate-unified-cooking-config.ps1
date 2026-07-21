param(
    [string]$Spec = "../.docs/planning/active-review/cc_content_unified_complete_implementation_spec_2026-07-21.md",
    [string]$OutputDirectory = "src/main/resources/config/cooking"
)

$ErrorActionPreference = 'Stop'
$lines = Get-Content -LiteralPath $Spec -Encoding utf8

function Section-Lines([string]$start, [string]$end) {
    $startIndex = [Array]::IndexOf($lines, $start)
    $endIndex = [Array]::IndexOf($lines, $end)
    if ($startIndex -lt 0 -or $endIndex -le $startIndex) { throw "Section not found: $start -> $end" }
    return $lines[($startIndex + 1)..($endIndex - 1)]
}

function Table-Rows($section, [int]$minimumColumns) {
    return $section | Where-Object { $_ -match '^\| `' } | ForEach-Object {
        $parts = $_.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
        if ($parts.Count -ge $minimumColumns) { ,$parts }
    }
}

function Ticks([string]$value) {
    return [regex]::Matches($value, '`([^`]+)`') | ForEach-Object { $_.Groups[1].Value }
}

function Alias([string]$canonical) {
    if ($canonical.StartsWith('resource.')) { return 'resource_' + $canonical.Substring(9) }
    if ($canonical.StartsWith('fishing.fish_')) { return 'fish_' + $canonical.Substring(13) }
    if ($canonical.StartsWith('minecraft:')) { return $canonical.Substring(10) }
    if ($canonical.StartsWith('cooking.')) { return $canonical.Substring(8) }
    throw "Unsupported ingredient: $canonical"
}

function Parse-Ingredients([string]$cell) {
    $result = [ordered]@{}
    foreach ($match in [regex]::Matches($cell, '`([^`]+)`×(\d+)')) {
        $canonical = $match.Groups[1].Value
        $amount = [int]$match.Groups[2].Value
        $result[(Alias $canonical)] = $amount
        $script:canonicalByAlias[(Alias $canonical)] = $canonical
    }
    if ($result.Count -eq 0) { throw "No ingredients parsed: $cell" }
    return $result
}

function Append-Result($builder, [string]$id, [string]$kind, [string]$container, [string]$pane, [string]$tier, [string]$heat, [int]$amount, [int]$stack, $effects) {
    $base = if ($kind -eq 'BOWL') { 'RABBIT_STEW' } elseif ($kind -eq 'BOTTLE') { 'POTION' } else { 'POISONOUS_POTATO' }
    $nutrition = if ($kind -eq 'BOTTLE') { 0 } elseif ($tier -eq 'TOP') { 12 } elseif ($tier -eq 'ADVANCED') { 9 } elseif ($tier -eq 'INTERMEDIATE') { 7 } elseif ($tier -eq 'BASIC') { 5 } else { 0 }
    $saturation = if ($kind -eq 'BOTTLE') { '0.0' } elseif ($tier -eq 'TOP') { '1.2' } elseif ($tier -eq 'ADVANCED') { '1.0' } elseif ($tier -eq 'INTERMEDIATE') { '0.8' } elseif ($tier -eq 'BASIC') { '0.6' } else { '0.0' }
    [void]$builder.AppendLine('    result:')
    [void]$builder.AppendLine("      kind: $kind")
    [void]$builder.AppendLine("      custom_item_id: cooking.$id")
    [void]$builder.AppendLine("      base_material: $base")
    [void]$builder.AppendLine("      item_model: kota_server:custom_item/cooking/$id")
    if ($container -and $container -ne '-') { [void]$builder.AppendLine("      container: $container") }
    if ($pane -and $pane -ne '-') { [void]$builder.AppendLine("      liquid_pane: $pane") }
    [void]$builder.AppendLine("      nutrition: $nutrition")
    [void]$builder.AppendLine("      saturation_modifier: $saturation")
    if ($kind -eq 'BOTTLE') { [void]$builder.AppendLine('      always_eat: true') }
    [void]$builder.AppendLine("      max_stack_size: $stack")
    [void]$builder.AppendLine("      amount_per_scale: $amount")
    if ($effects.Count -gt 0) {
        [void]$builder.AppendLine('      effects:')
        foreach ($effect in $effects) { [void]$builder.AppendLine("        - $effect") }
    }
    $failureId = if ($heat -eq 'HIGH') {
        if ($kind -eq 'BOWL') { 'undercooked_bowl_food' } elseif ($kind -eq 'BOTTLE') { 'underprepared_bottle_liquid' } else { 'undercooked_solid_food' }
    } else {
        if ($kind -eq 'BOWL') { 'burnt_bowl_food' } elseif ($kind -eq 'BOTTLE') { 'burnt_bottle_liquid' } else { 'burnt_solid_food' }
    }
    [void]$builder.AppendLine('    failure_result:')
    [void]$builder.AppendLine("      custom_item_id: cooking.$failureId")
    [void]$builder.AppendLine('      base_material: POISONOUS_POTATO')
    [void]$builder.AppendLine("      item_model: kota_server:custom_item/cooking/$failureId")
    if ($pane -and $pane -ne '-') { [void]$builder.AppendLine('      liquid_pane: GRAY_STAINED_GLASS_PANE') }
    [void]$builder.AppendLine('      max_stack_size: 1')
    [void]$builder.AppendLine('      amount_per_scale: 1')
}

$canonicalByAlias = [ordered]@{}
$cutting = [System.Text.StringBuilder]::new()
[void]$cutting.AppendLine('config_version: 2')
[void]$cutting.AppendLine('')
[void]$cutting.AppendLine('recipes:')

$vegetableRows = Table-Rows (Section-Lines '## 20.1 野菜・果実・肉' '## 20.2 魚') 6
foreach ($row in $vegetableRows) {
    $id = @(Ticks $row[0])[0]
    $canonical = @(Ticks $row[1])[0]
    if ($row[1] -notmatch '×(\d+)$') { throw "Invalid cutting input: $($row[1])" }
    $alias = Alias $canonical
    $canonicalByAlias[$alias] = $canonical
    $outputId = @(Ticks $row[2])[0]
    $amount = [int]$row[3].Trim('`')
    $foodClass = $row[4].Trim('`')
    $durability = [int]$row[5].Trim('`')
    [void]$cutting.AppendLine("  ${id}:")
    [void]$cutting.AppendLine("    input: $alias")
    [void]$cutting.AppendLine('    output:')
    [void]$cutting.AppendLine("      custom_item_id: $outputId")
    [void]$cutting.AppendLine("      amount: $amount")
    [void]$cutting.AppendLine("    food_class: $foodClass")
    [void]$cutting.AppendLine("    base_durability: $durability")
    [void]$cutting.AppendLine('    stage:')
    [void]$cutting.AppendLine('      type: PRIMARY')
    [void]$cutting.AppendLine('      depth: 1')
}

$fishRows = Table-Rows (Section-Lines '## 20.2 魚' '# 21. 中間材料完全定義') 3
foreach ($row in $fishRows) {
    $fishId = $row[0].Trim('`')
    $amount = [int]$row[1].Trim('`')
    $foodClass = $row[2].Trim('`')
    $alias = "fish_$fishId"
    $canonicalByAlias[$alias] = "fishing.fish_$fishId"
    [void]$cutting.AppendLine("  fillet_${fishId}:")
    [void]$cutting.AppendLine("    input: $alias")
    [void]$cutting.AppendLine('    output:')
    [void]$cutting.AppendLine("      custom_item_id: cooking.fillet_$fishId")
    [void]$cutting.AppendLine("      amount: $amount")
    [void]$cutting.AppendLine("    food_class: $foodClass")
    [void]$cutting.AppendLine('    base_durability: 1')
    [void]$cutting.AppendLine('    stage:')
    [void]$cutting.AppendLine('      type: PRIMARY')
    [void]$cutting.AppendLine('      depth: 1')
}

$recipeBuilder = [System.Text.StringBuilder]::new()
[void]$recipeBuilder.AppendLine('config_version: 3')
[void]$recipeBuilder.AppendLine('')
[void]$recipeBuilder.AppendLine('recipes:')

$intermediateRows = Table-Rows (Section-Lines '# 21. 中間材料完全定義' '# 22. 完成料理完全定義') 11
foreach ($row in $intermediateRows) {
    $id = @(Ticks $row[0])[0].Replace('cooking.', '')
    $stage = @(Ticks $row[2])[0].Split('/')[0].Trim()
    $equipmentHeat = @(Ticks $row[3])
    $equipment = $equipmentHeat[0]
    if ($equipment -notin @('PAN', 'CAULDRON')) { continue }
    $heat = $equipmentHeat[1]
    $ingredients = Parse-Ingredients $row[4]
    $water = [int]$row[5]
    $resultTokens = @(Ticks $row[6])
    $kind = $resultTokens[0]
    $container = $resultTokens[1]
    $pane = $resultTokens[2]
    $amount = [int]$row[7]
    $seconds = [int]$row[8]
    $stack = [int]$row[9]
    [void]$recipeBuilder.AppendLine("  ${id}:")
    [void]$recipeBuilder.AppendLine("    equipment: $equipment")
    [void]$recipeBuilder.AppendLine("    group: INTERMEDIATE_MATERIAL")
    [void]$recipeBuilder.AppendLine("    tier: BASIC")
    [void]$recipeBuilder.AppendLine("    heat: $heat")
    [void]$recipeBuilder.AppendLine('    ingredients:')
    foreach ($pair in $ingredients.GetEnumerator()) { [void]$recipeBuilder.AppendLine("      $($pair.Key): $($pair.Value)") }
    [void]$recipeBuilder.AppendLine("    water_units: $water")
    [void]$recipeBuilder.AppendLine("    duration_seconds: $seconds")
    [void]$recipeBuilder.AppendLine('    exp: 0')
    Append-Result $recipeBuilder $id $kind $container $pane 'NONE' $heat $amount $stack @()
}

foreach ($tier in @('BASIC', 'INTERMEDIATE', 'ADVANCED', 'TOP')) {
    $start = "## 22.$(@{BASIC=2;INTERMEDIATE=3;ADVANCED=4;TOP=5}[$tier]) $tier"
    $end = if ($tier -eq 'BASIC') { '## 22.3 INTERMEDIATE' } elseif ($tier -eq 'INTERMEDIATE') { '## 22.4 ADVANCED' } elseif ($tier -eq 'ADVANCED') { '## 22.5 TOP' } else { '## 22.6 説明文' }
    $rows = Table-Rows (Section-Lines $start $end) 10
    foreach ($row in $rows) {
        $id = @(Ticks $row[0])[0]
        $groupTier = @(Ticks $row[2])
        $equipmentHeat = @(Ticks $row[3])
        $ingredients = Parse-Ingredients $row[4]
        $water = [int]$row[5]
        $resultTokens = @(Ticks $row[6])
        $effects = @(Ticks $row[9])
        [void]$recipeBuilder.AppendLine("  ${id}:")
        [void]$recipeBuilder.AppendLine("    equipment: $($equipmentHeat[0])")
        [void]$recipeBuilder.AppendLine("    group: $($groupTier[0])")
        [void]$recipeBuilder.AppendLine("    tier: $($groupTier[1])")
        [void]$recipeBuilder.AppendLine("    heat: $($equipmentHeat[1])")
        [void]$recipeBuilder.AppendLine('    ingredients:')
        foreach ($pair in $ingredients.GetEnumerator()) { [void]$recipeBuilder.AppendLine("      $($pair.Key): $($pair.Value)") }
        [void]$recipeBuilder.AppendLine("    water_units: $water")
        [void]$recipeBuilder.AppendLine("    duration_seconds: $([int]$row[7])")
        [void]$recipeBuilder.AppendLine("    exp: $([int]$row[8])")
        $stack = if ($resultTokens[0] -eq 'ITEM') { 16 } else { 1 }
        Append-Result $recipeBuilder $id $resultTokens[0] $resultTokens[1] $resultTokens[2] $groupTier[1] $equipmentHeat[1] 1 $stack $effects
    }
}

# CRAFTING/FURNACE/SMOKERとBrewery原液も同じ正規材料辞書を使う。
foreach ($line in $lines) {
    foreach ($match in [regex]::Matches($line, '`((?:minecraft:|resource\.|cooking\.|fishing\.)[^`]+)`×\d+')) {
        $canonical = $match.Groups[1].Value
        $canonicalByAlias[(Alias $canonical)] = $canonical
    }
}

$ingredientsBuilder = [System.Text.StringBuilder]::new()
[void]$ingredientsBuilder.AppendLine('config_version: 2')
[void]$ingredientsBuilder.AppendLine('')
[void]$ingredientsBuilder.AppendLine('ingredients:')
foreach ($pair in $canonicalByAlias.GetEnumerator() | Sort-Object Key) {
    $alias = $pair.Key
    $canonical = $pair.Value
    [void]$ingredientsBuilder.AppendLine("  ${alias}:")
    [void]$ingredientsBuilder.AppendLine('    matcher:')
    if ($canonical.StartsWith('resource.')) {
        $value = $canonical.Substring(9)
        [void]$ingredientsBuilder.AppendLine("      resource_id: $value")
        $display = "custom_items.resource.$value.name"
    } elseif ($canonical.StartsWith('fishing.fish_')) {
        $value = $canonical.Substring(13)
        [void]$ingredientsBuilder.AppendLine("      fish_id: $value")
        $display = "fishing.catalog.item.$value"
    } elseif ($canonical.StartsWith('minecraft:')) {
        $value = $canonical.Substring(10)
        [void]$ingredientsBuilder.AppendLine("      material: $($value.ToUpperInvariant())")
        $display = "item.minecraft.$value"
    } else {
        [void]$ingredientsBuilder.AppendLine("      custom_item_id: $canonical")
        $display = "custom_items.$canonical.name"
    }
    [void]$ingredientsBuilder.AppendLine("    display_name_key: $display")
    if ($canonical -eq 'minecraft:milk_bucket') {
        [void]$ingredientsBuilder.AppendLine('    container_remainder:')
        [void]$ingredientsBuilder.AppendLine('      material: BUCKET')
        [void]$ingredientsBuilder.AppendLine('      amount: 1')
    } elseif ($canonical -eq 'minecraft:honey_bottle') {
        [void]$ingredientsBuilder.AppendLine('    container_remainder:')
        [void]$ingredientsBuilder.AppendLine('      material: GLASS_BOTTLE')
        [void]$ingredientsBuilder.AppendLine('      amount: 1')
    } else {
        [void]$ingredientsBuilder.AppendLine('    container_remainder: null')
    }
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'ingredients.yml'), $ingredientsBuilder.ToString(), [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'cutting.yml'), $cutting.ToString(), [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'recipe.yml'), $recipeBuilder.ToString(), [Text.UTF8Encoding]::new($false))

$recipeCount = ([regex]::Matches($recipeBuilder.ToString(), '(?m)^  [a-z0-9_]+:\r?$')).Count
Write-Output "ingredients=$($canonicalByAlias.Count) cutting=$($vegetableRows.Count + $fishRows.Count) recipes=$recipeCount"

param(
    [string]$Spec = "../.docs/planning/active-review/cc_content_unified_complete_implementation_spec_2026-07-21.md",
    [string]$Output = "src/main/resources/config/brewery/recipes.yml"
)

$ErrorActionPreference = 'Stop'
$lines = Get-Content -LiteralPath $Spec -Encoding utf8

function Section([string]$start, [string]$end) {
    $a = [Array]::IndexOf($lines, $start)
    $b = [Array]::IndexOf($lines, $end)
    if ($a -lt 0 -or $b -le $a) { throw "Section not found: $start" }
    $lines[($a + 1)..($b - 1)]
}

function Rows($section, [int]$columns) {
    $section | Where-Object { $_ -match '^\| `' } | ForEach-Object {
        $parts = $_.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
        if ($parts.Count -ge $columns) { ,$parts }
    }
}

function Token([string]$text) { ([regex]::Match($text, '`([^`]+)`')).Groups[1].Value }

$effects = @{}
foreach ($row in Rows (Section '# 35. 醸造PotionEffect' '# 36. 赤ワイン分岐') 2) {
    $effects[(Token $row[0])] = @([regex]::Matches($row[1], '`([^`]+)`') |
        ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ -ne '-' })
}

$consumption = @{}
foreach ($row in Rows (Section '## 37.2 完成酒27件' '## 37.3 Cooking非酒類') 5) {
    $consumption[(Token $row[0])] = @([double]$row[2], [double]$row[3], [double]$row[4])
}

$families = Rows (Section '# 33. 醸造family完全定義' '# 34. 完成酒名・モデル') 10
$builder = [Text.StringBuilder]::new()
[void]$builder.AppendLine('config_version: 3')
[void]$builder.AppendLine('')
[void]$builder.AppendLine('preparations:')
foreach ($row in $families) {
    $family = Token $row[0]
    $preparation = Token $row[1]
    $group = Token $row[3]
    $prep = [regex]::Match($row[5], '(\d+) / `([^`]+)` / `([^`]+)`')
    [void]$builder.AppendLine("  ${preparation}:")
    [void]$builder.AppendLine("    family: $family")
    [void]$builder.AppendLine("    group: $group")
    [void]$builder.AppendLine('    ingredients:')
    foreach ($match in [regex]::Matches($row[4], '`([^`]+)`×(\d+)')) {
        [void]$builder.AppendLine("      `"$($match.Groups[1].Value)`": $($match.Groups[2].Value)")
    }
    [void]$builder.AppendLine('    water_units: 3')
    [void]$builder.AppendLine('    max_scale: 1')
    [void]$builder.AppendLine("    duration_seconds: $($prep.Groups[1].Value)")
    [void]$builder.AppendLine("    heat: $($prep.Groups[2].Value)")
    [void]$builder.AppendLine("    liquid_pane: $($prep.Groups[3].Value)")
    [void]$builder.AppendLine("    failure_result: brewery.failed_$preparation")
}

[void]$builder.AppendLine('')
[void]$builder.AppendLine('brew_families:')
foreach ($row in $families) {
    $family = Token $row[0]
    $preparation = Token $row[1]
    $group = Token $row[3]
    $outputs = @([regex]::Matches($row[2], '`([^`]+)`') | ForEach-Object { $_.Groups[1].Value })
    $distill = [regex]::Match($row[7], '(\d+)回 × (\d+)秒')
    [void]$builder.AppendLine("  ${family}:")
    [void]$builder.AppendLine("    group: $group")
    [void]$builder.AppendLine("    preparation: $preparation")
    [void]$builder.AppendLine('    fermentation:')
    [void]$builder.AppendLine("      duration_seconds: $([int]$row[6])")
    [void]$builder.AppendLine('      yeast: brewery.cultured_yeast')
    [void]$builder.AppendLine('    distillation:')
    [void]$builder.AppendLine("      required_runs: $($distill.Groups[1].Value)")
    [void]$builder.AppendLine("      duration_seconds_per_run: $($distill.Groups[2].Value)")
    [void]$builder.AppendLine('      filter_consumption_per_run: 1')
    [void]$builder.AppendLine('    aging:')
    if ($row[8] -eq '`なし`') {
        [void]$builder.AppendLine('      variants: []')
    } else {
        [void]$builder.AppendLine('      variants:')
        foreach ($variant in $row[8].Split([string[]]@('<br>'), [StringSplitOptions]::None)) {
            $parsed = [regex]::Match($variant, '`([^`]+)`: (\d+)単位 / ([A-Z_]+)')
            if (-not $parsed.Success) { throw "Invalid aging variant: $variant" }
            [void]$builder.AppendLine("        - output_id: $($parsed.Groups[1].Value)")
            [void]$builder.AppendLine("          target_units: $($parsed.Groups[2].Value)")
            [void]$builder.AppendLine("          barrel_types: [$($parsed.Groups[3].Value)]")
        }
    }
    [void]$builder.AppendLine('    outputs:')
    foreach ($outputId in $outputs) {
        $values = $consumption[$outputId]
        if ($null -eq $values) { throw "Missing consumption: $outputId" }
        $glintParts = $row[9].Split([string[]]@('<br>'), [StringSplitOptions]::None)
        $specificGlint = $glintParts | Where-Object { $_ -match "``$([regex]::Escape($outputId))``:" } | Select-Object -First 1
        $glint = if ($specificGlint) {
            $specificGlint.Replace('`','').Split(':')[-1].Trim()
        } else {
            $glintParts[0].Replace('`','').Split(':')[-1].Trim()
        }
        [void]$builder.AppendLine("      ${outputId}:")
        [void]$builder.AppendLine("        glint: $glint")
        if ($effects[$outputId].Count -eq 0) { [void]$builder.AppendLine('        effects: []') }
        else {
            [void]$builder.AppendLine('        effects:')
            foreach ($effect in $effects[$outputId]) { [void]$builder.AppendLine("          - $effect") }
        }
        [void]$builder.AppendLine("        item_model: kota_server:custom_item/brewery/$outputId")
        [void]$builder.AppendLine('        consumption:')
        [void]$builder.AppendLine("          alcohol_percent: $($values[0].ToString('0.0',[Globalization.CultureInfo]::InvariantCulture))")
        [void]$builder.AppendLine("          intoxication_points: $($values[1].ToString('0.0',[Globalization.CultureInfo]::InvariantCulture))")
        [void]$builder.AppendLine("          sobering_points: $($values[2].ToString('0.0',[Globalization.CultureInfo]::InvariantCulture))")
    }
}

[IO.File]::WriteAllText((Join-Path (Get-Location) $Output), $builder.ToString(), [Text.UTF8Encoding]::new($false))
Write-Output "families=$($families.Count) outputs=$($consumption.Count)"

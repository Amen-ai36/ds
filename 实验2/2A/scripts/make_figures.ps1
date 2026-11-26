param([string]$Root = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$Root = [IO.Path]::GetFullPath($Root)
$outputDir = Join-Path $Root 'results\figures'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$data = Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $Root 'results\tables\chart_data.json') | ConvertFrom-Json
$fontFamily = 'Microsoft YaHei'
$script:ink = [Drawing.ColorTranslator]::FromHtml('#17243A')
$script:muted = [Drawing.ColorTranslator]::FromHtml('#5A6980')
$colors = @('#315EAD', '#008B78', '#D88C2C')

function Text($value, $x, $y, $size = 22, $color = $script:ink, $bold = $false, $width = 1350, $height = 65) {
    $style = if ($bold) { [Drawing.FontStyle]::Bold } else { [Drawing.FontStyle]::Regular }
    $font = [Drawing.Font]::new($fontFamily, [single]$size, $style, [Drawing.GraphicsUnit]::Pixel)
    $brush = [Drawing.SolidBrush]::new($color)
    $script:g.DrawString([string]$value, $font, $brush, [Drawing.RectangleF]::new([single]$x,[single]$y,[single]$width,[single]$height))
    $brush.Dispose(); $font.Dispose()
}
function Canvas($title, $subtitle) {
    $script:bitmap = [Drawing.Bitmap]::new(1600,900)
    $script:g = [Drawing.Graphics]::FromImage($script:bitmap)
    $script:g.Clear([Drawing.Color]::White)
    $script:g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $script:g.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    Text $title 85 45 40 $script:ink $true
    Text $subtitle 88 108 23 $script:muted
}
function Save($name) {
    $script:bitmap.Save((Join-Path $outputDir $name), [Drawing.Imaging.ImageFormat]::Png)
    $script:g.Dispose(); $script:bitmap.Dispose()
}
function Draw-Comparison($kind, $title, $unit, $filename) {
    Canvas $title '5 个节点 · 相同故障类型和网络分布 · 断开当前 Leader 后由剩余多数派重选'
    $observations = @()
    foreach ($d in $data) {
        if ($kind -eq 'latency') { $observations += @($d.reelection_samples_ms) }
        elseif ($kind -eq 'rounds') { $observations += $d.election_rounds_mean }
        else { $observations += $d.unexpected_leader_changes_total }
    }
    $maxValue = ($observations | Measure-Object -Maximum).Maximum
    if ($null -eq $maxValue -or $maxValue -le 0) { $maxValue = 1 }
    $limit = [math]::Ceiling(($maxValue*1.2)/4)*4
    if ($kind -eq 'latency') { $limit = [math]::Ceiling(($maxValue*1.15)/400)*400 }
    $baseY = 690; $plotH = 390
    $scenarios = @('reliable','lossy_reordered')
    $scenarioTitles = @('可靠网络','丢包与回复延迟网络')
    $profiles = @('baseline','fast','stable')
    for ($panel=0; $panel -lt 2; $panel++) {
        $left = 145+$panel*755
        Text $scenarioTitles[$panel] ($left+40) 205 29 $script:ink $true 570 50
        for ($tick=0; $tick -le 4; $tick++) {
            $value = $limit*$tick/4
            $y = $baseY-$plotH*$tick/4
            $pen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#E2E8F0'),1)
            $script:g.DrawLine($pen,[single]$left,[single]$y,[single]($left+545),[single]$y)
            $pen.Dispose()
            Text ('{0:g}' -f $value) ($left-85) ($y-15) 21 $script:muted $false 80 40
        }
        Text $unit ($left-83) 260 21 $script:muted $false 160 40
        for ($i=0; $i -lt 3; $i++) {
            $d = $data | Where-Object { $_.scenario -eq $scenarios[$panel] -and $_.profile -eq $profiles[$i] }
            $x = $left+55+170*$i
            $v = if ($kind -eq 'latency') { $d.reelection_mean_ms } elseif ($kind -eq 'rounds') { $d.election_rounds_mean } else { $d.unexpected_leader_changes_total }
            if ($null -eq $v) {
                Text '无有效样本' ($x-20) 640 20 $script:muted $false 160 45
                continue
            }
            $h = [double]$v/$limit*$plotH
            $color = [Drawing.ColorTranslator]::FromHtml($colors[$i])
            $brush = [Drawing.SolidBrush]::new($color)
            $script:g.FillRectangle($brush,[single]$x,[single]($baseY-$h),95,[single][math]::Max(2,$h))
            $brush.Dispose()
            if ($kind -eq 'latency') {
                $meanY = $baseY-$h
                $delta = [double]$d.reelection_std_ms/$limit*$plotH
                $pen = [Drawing.Pen]::new($script:ink,2)
                $script:g.DrawLine($pen,[single]($x+47.5),[single]($meanY-$delta),[single]($x+47.5),[single][math]::Min($baseY,$meanY+$delta))
                $script:g.DrawLine($pen,[single]($x+33),[single]($meanY-$delta),[single]($x+62),[single]($meanY-$delta))
                $pen.Dispose()
                $sampleIndex=0
                foreach ($sample in $d.reelection_samples_ms) {
                    $px = $x+18+(($sampleIndex*17)%61)
                    $py = $baseY-[double]$sample/$limit*$plotH
                    $fill = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(185,255,255,255))
                    $script:g.FillEllipse($fill,[single]($px-4),[single]($py-4),8,8)
                    $outline = [Drawing.Pen]::new($script:ink,1)
                    $script:g.DrawEllipse($outline,[single]($px-4),[single]($py-4),8,8)
                    $outline.Dispose()
                    $fill.Dispose(); $sampleIndex++
                }
            }
            $number = if ($kind -eq 'changes') { '{0:0}' -f $v } else { '{0:0.0}' -f $v }
            $labelY = $baseY-$h-48
            if ($kind -eq 'latency') {
                $largestSample = ($d.reelection_samples_ms | Measure-Object -Maximum).Maximum
                $highestValue = [math]::Max([double]$v+[double]$d.reelection_std_ms,[double]$largestSample)
                $labelY = $baseY-$highestValue/$limit*$plotH-48
            }
            Text $number ($x-10) $labelY 28 $color $true 155 48
            Text $profiles[$i] ($x-10) 709 24 $script:ink $true 160 45
            Text ('成功 {0}/{1}' -f $d.successful_trials,$d.trials) ($x-15) 752 20 $script:muted $false 170 40
        }
    }
    $note = if ($kind -eq 'latency') { '柱高=平均重选耗时；误差线=样本标准差；圆点=单次试验。失败试验单独统计，未填 0。' } elseif ($kind -eq 'rounds') { '轮数=断连至新 Leader 当选间出现的不同竞选任期数；同任期多个候选人只计一轮。' } else { '累计每轮 2 秒无主动故障窗口中的 Leader 节点变化次数；0 仅表示本批窗口内未观察到。' }
    Text $note 88 824 21 $script:muted $false 1430 55
    Save $filename
}

Draw-Comparison 'latency' '重选耗时与参数策略' '毫秒' '03_reelection_latency.png'
Draw-Comparison 'rounds' '重选轮数与参数策略' '轮' '04_election_rounds.png'
Draw-Comparison 'changes' '无主动故障窗口中的换主次数' '次' '05_unexpected_leader_changes.png'

$tracePath = Join-Path $Root 'results\raw\traces\reliable_baseline_trial_01.json'
$trace = Get-Content -Raw -Encoding UTF8 -LiteralPath $tracePath | ConvertFrom-Json
function Timeline($reelection, $filename) {
    $origin = if ($reelection) { [long]$trace.fault_unix_nano } else { [long]$trace.start_unix_nano }
    $promotion = $trace.events | Where-Object { $_.kind -eq 'leader_elected' -and $_.at_unix_nano -ge $origin -and (-not $reelection -or $_.node -ne $trace.old_leader) } | Select-Object -First 1
    if ($null -eq $promotion) { return }
    $duration = ([long]$promotion.at_unix_nano-$origin)/1e6
    $span = [math]::Max(150,[math]::Ceiling(($duration+120)/100)*100)
    $title = if ($reelection) { 'Leader 断连后的重选时间线' } else { '集群启动后的初始选举时间线' }
    Canvas $title ('可靠网络 · baseline · 第 1 轮 · S{0} 在任期 {1} 当选 · 耗时 {2:0.000} ms' -f $promotion.node,$promotion.term,$duration)
    $pen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#DDE5EF'),2)
    for ($i=0;$i -lt 5;$i++) {
        $y = 270+$i*100
        Text ('S{0}' -f $i) 85 ($y-20) 27 $script:ink $true 150 45
        $script:g.DrawLine($pen,220,$y,1495,$y)
        if ($reelection -and $i -eq $trace.old_leader) { Text '已断开' 95 ($y+15) 17 ([Drawing.Color]::Gray) $false 120 40 }
    }
    $pen.Dispose()
    if ($reelection) {
        $faultPen = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml('#B84949'),2)
        $faultPen.DashStyle = [Drawing.Drawing2D.DashStyle]::Dash
        $script:g.DrawLine($faultPen,220,235,220,710)
        $faultPen.Dispose()
        Text ('t=0：断开 S{0}' -f $trace.old_leader) 220 190 22 ([Drawing.ColorTranslator]::FromHtml('#B84949')) $true 400 50
    }
    for ($tick=0;$tick -le 5;$tick++) {
        $x=220+1275*$tick/5
        Text ('{0:0} ms' -f ($span*$tick/5)) ($x-35) 755 19 $script:muted $false 140 40
    }
    foreach ($e in $trace.events) {
        $ms=([long]$e.at_unix_nano-$origin)/1e6
        if ($ms -lt 0 -or $ms -gt $duration+1 -or $e.kind -notin @('election_started','vote_granted','leader_elected')) { continue }
        $x=220+1275*$ms/$span; $y=270+[int]$e.node*100
        $label = switch ($e.kind) {
            'election_started' { 'Candidate T{0}' -f $e.term }
            'vote_granted' { '投给 S{0} · T{1}' -f $e.peer,$e.term }
            'leader_elected' { 'Leader T{0} · {1}/5票' -f $e.term,$e.votes }
        }
        $color = if ($e.kind -eq 'leader_elected') { [Drawing.ColorTranslator]::FromHtml('#008B78') } else { [Drawing.ColorTranslator]::FromHtml('#315EAD') }
        $brush = [Drawing.SolidBrush]::new($color)
        $script:g.FillEllipse($brush,[single]($x-7),[single]($y-7),14,14)
        $brush.Dispose()
        # Candidate 与同节点的当选通常很接近，分别放在线上、线下。
        $labelY = if ($e.kind -eq 'election_started') { $y-46 } else { $y+14 }
        $labelX = [math]::Min(1250,$x-40)
        Text $label $labelX $labelY 21 $color ($e.kind -eq 'leader_elected') 320 50
    }
    Text '事件来自 reliable_baseline_trial_01.json；横坐标为相对时间，节点编号从 0 开始。' 88 831 21 $script:muted $false 1400 50
    Save $filename
}
Timeline $false '01_initial_election_timeline.png'
Timeline $true '02_leader_failure_reelection_timeline.png'
Write-Output $outputDir

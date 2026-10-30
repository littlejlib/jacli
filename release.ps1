# Publish a jacli release that the two-line install and `jacli update` pick up. Run from this folder:
#
#     .\release.ps1                  build, checksum and publish the version in pom.xml
#     .\release.ps1 -DryRun          build and checksum only; show what would be published
#     .\release.ps1 -Changes f.md    put f.md (what changed in this release) at the top of the release notes
#
# One release carries everything an installed jacli needs: jacli.exe, jacli-daemon.exe, the shaded
# jar they all fetch (jacli.jar, from the same release), one update file per exe (jacli.update.json,
# jacli-daemon.update.json, each read by that exe's -Xjr:update at releases/latest/download/),
# version.txt and SHA256SUMS. The exes are built by jr-maven-plugin (the jrexe profile in pom.xml) with the sha256 of
# exactly that jar baked in; each exe writes its own folder target\jr\release-<name>, gathered here into
# target\jr\release. The asset NAMES never change between releases, only their contents.
#
# $env:GH names the GitHub CLI to use (default: gh). Requires a clean, pushed working tree: a release must match a
# public commit. After publishing, every asset URL is fetched and checked against SHA256SUMS (GitHub has served an
# asset with HTTP 500 while the API listed it).

param([switch]$DryRun, [string]$Changes)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$gh = if ($env:GH) { $env:GH } else { 'gh' }
$version = ([xml](Get-Content pom.xml)).project.version
if ($version -match '-') { throw "version $version is a pre-release; this script publishes stable releases only" }
$tag = "v$version"
$repo = 'littlejlib/jacli'
$out = Join-Path $PSScriptRoot 'target\jr\release'

# Only lines shaped like git's own output are trusted: a wrapper may print a banner on stdout.
function Sha([string]$ref) { @(git rev-parse $ref) | Where-Object { $_ -match '^[0-9a-f]{40}$' } | Select-Object -Last 1 }

if (-not $DryRun) {
    $dirty = @(git status --porcelain) | Where-Object { $_ -match '^[ MADRCU?!]{2} ' }
    if ($dirty) { throw "the working tree is not clean - commit first, so the release matches a public commit:`n$($dirty -join "`n")" }
    git fetch -q origin
    if ((Sha HEAD) -ne (Sha '@{u}')) { throw 'HEAD is not pushed - push first' }
}

Write-Host "building $tag"
for ($i = 1; $i -le 4; $i++) {
    # quoted: PowerShell 5.1 splits an unquoted -Dname.with.dots=value into two arguments
    & mvn -B -q '-Djrexe' '-Djr.source=url' clean package
    if ($LASTEXITCODE -eq 0) { break }
    if ($i -eq 4) { throw 'mvn package failed' }
    Write-Host "build attempt $i failed, retrying"
}

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null
foreach ($d in Get-ChildItem (Join-Path $PSScriptRoot 'target\jr') -Directory -Filter 'release-*') { Copy-Item (Join-Path $d.FullName '*') $out -Force }
# SHA256SUMS over everything gathered (each exe's run wrote one for its own files only); update files are not assets to verify against it.
$sums = Get-ChildItem $out -File | Where-Object { $_.Name -ne 'SHA256SUMS' -and $_.Name -notlike '*.update.json' } | Sort-Object Name |
    ForEach-Object { "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower())  $($_.Name)" }
[IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), (($sums -join "`n") + "`n"))

foreach ($f in @('jacli.exe', 'jacli-daemon.exe', 'jacli.jar', 'version.txt', 'SHA256SUMS',
                 'jacli.update.json', 'jacli-daemon.update.json')) {
    if (-not (Test-Path (Join-Path $out $f))) { throw "the release folder has no $f - see the jr:exe output above" }
}
Get-ChildItem $out | ForEach-Object { Write-Host ('  {0,-28} {1,7:N1} MB' -f $_.Name, ($_.Length / 1MB)) }
if ($DryRun) { Write-Host "dry run - nothing published. Assets are in $out"; return }

$download = "https://github.com/$repo/releases/latest/download"
$notes = "Install on Windows, in cmd or PowerShell (curl.exe ships with Windows 10 and 11; no installer script):`n`n    curl.exe -fLo jacli.exe $download/jacli.exe`n    .\jacli.exe install`n`n" +
         "Then, once, to put jacli in front of Claude Code:`n`n    jacli claude install`n`nUpdate later with ``jacli update``.`n"
if ($Changes) { $notes = (Get-Content $Changes -Raw) + "`n`n" + $notes }
$notesFile = Join-Path $out '..\release-notes.md'
[IO.File]::WriteAllText($notesFile, $notes)

$assets = Get-ChildItem $out -File | ForEach-Object { $_.FullName }
$ErrorActionPreference = 'Continue'   # gh prints its account notice on stderr; only its exit code counts
& $gh release create $tag @assets --repo $repo --title "jacli $version" --notes-file $notesFile --target (Sha HEAD) --latest
if ($LASTEXITCODE -ne 0) { throw 'gh release create failed' }

Write-Host "verifying every asset URL"
$bad = 0
foreach ($line in $sums) {
    $hash, $name = $line -split '  ', 2
    $tmp = Join-Path $env:TEMP "jacli-verify-$name"
    & curl.exe -fsSL --retry 5 --retry-all-errors -o $tmp "https://github.com/$repo/releases/download/$tag/$name"
    $got = if ($LASTEXITCODE -eq 0) { (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower() } else { 'download failed' }
    Remove-Item $tmp -ErrorAction SilentlyContinue
    if ($got -ne $hash) { Write-Host "  BAD  $name ($got)"; $bad++ } else { Write-Host "  ok   $name" }
}
foreach ($name in @('jacli.update.json', 'jacli-daemon.update.json', 'SHA256SUMS')) {
    & curl.exe -fsSL --retry 5 --retry-all-errors -o NUL "$download/$name"
    if ($LASTEXITCODE -ne 0) { Write-Host "  BAD  latest/download/$name"; $bad++ } else { Write-Host "  ok   latest/download/$name" }
}
if ($bad) { throw "$bad asset(s) did not verify - re-upload with: $gh release upload $tag <file> --clobber --repo $repo" }
Write-Host "published ${tag}: https://github.com/$repo/releases/tag/$tag"

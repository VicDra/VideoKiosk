param()
$json = [Console]::In.ReadToEnd() | ConvertFrom-Json
$filePath = $json.tool_input.file_path
if (-not ($filePath -like 'C:\ClaudeCodes\VideoKiosk*')) { exit 0 }
$repoPath = 'C:\ClaudeCodes\VideoKiosk'
$env:PATH = "$env:PATH;C:\Program Files\Git\cmd;C:\Program Files\GitHub CLI"
git -C $repoPath add -A 2>$null
$staged = git -C $repoPath diff --cached --name-only 2>$null
if (-not $staged) { exit 0 }
$short = $filePath.Replace($repoPath + '\', '').Replace('\', '/')
git -C $repoPath commit -m "auto: update $short" 2>&1 | Out-Null
git -C $repoPath push origin master 2>&1 | Out-Null
Write-Output "Pushed: $short"

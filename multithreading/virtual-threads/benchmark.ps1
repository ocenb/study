param(
    [Parameter(Mandatory)] [string] $Classes,
    [string] $Java = 'java.exe',
    [ValidateRange(1, [int]::MaxValue)] [int[]] $TaskCounts = @(100, 1000, 10000),
    [ValidateRange(0, [int]::MaxValue)] [int] $SleepMillis = 100,
    [ValidateRange(1, [int]::MaxValue)] [int] $Repeats = 3
)

$ErrorActionPreference = 'Stop'
$javaPath = (Get-Command $Java -CommandType Application).Source
$classPath = (Resolve-Path -LiteralPath $Classes).Path
$modes = @('virtual', 'platform', 'scope')

foreach ($count in $TaskCounts) {
    for ($run = 0; $run -lt $Repeats; $run++) {
        for ($offset = 0; $offset -lt $modes.Count; $offset++) {
            $mode = $modes[($run + $offset) % $modes.Count]
            $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
            $startInfo.FileName = $javaPath
            $startInfo.WorkingDirectory = $classPath
            $startInfo.Arguments = "--enable-preview -cp . VirtualThreadsDemo $mode $count $SleepMillis --quiet"
            $startInfo.UseShellExecute = $false
            $startInfo.CreateNoWindow = $true
            $startInfo.RedirectStandardOutput = $true
            $startInfo.RedirectStandardError = $true

            $process = [System.Diagnostics.Process]::Start($startInfo)
            try {
                $stdout = $process.StandardOutput.ReadToEndAsync()
                $stderr = $process.StandardError.ReadToEndAsync()
                $peakBytes = 0L
                while (-not $process.WaitForExit(20)) {
                    try {
                        $process.Refresh()
                        $peakBytes = [Math]::Max($peakBytes, $process.PeakWorkingSet64)
                    } catch {
                        if (-not $process.HasExited) { throw }
                    }
                }
                $output = $stdout.GetAwaiter().GetResult().Trim()
                $errors = $stderr.GetAwaiter().GetResult()
                if ($process.ExitCode -ne 0) {
                    throw "$mode ($count tasks) failed with exit code $($process.ExitCode).`n$output`n$errors"
                }
                if ($output -notmatch '^mode=(\w+) tasks=(\d+) completed=(\d+) sleep_ms=(\d+) wall_ms=([0-9.]+) cpu_ms=([0-9.]+|n/a)$' -or
                    $Matches[1] -ne $mode -or [int] $Matches[2] -ne $count -or
                    [int] $Matches[3] -ne $count -or [int] $Matches[4] -ne $SleepMillis) {
                    throw "Unexpected benchmark output: $output"
                }
                if ($peakBytes -le 0) {
                    throw "No working-set reading for $mode ($count tasks)"
                }

                [pscustomobject]@{
                    Run = $run + 1
                    Mode = $mode
                    Tasks = $count
                    Completed = [int] $Matches[3]
                    SleepMs = $SleepMillis
                    WallMs = [double] $Matches[5]
                    CpuMs = if ($Matches[6] -ne 'n/a') { [double] $Matches[6] } else { $null }
                    PeakWorkingSetMiB = [Math]::Round($peakBytes / 1MB, 2)
                }
            } finally {
                $process.Dispose()
            }
        }
    }
}

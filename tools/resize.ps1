Add-Type -AssemblyName System.Drawing
$src = 'D:\vit\apkdemo\GoldieSettingsAndroid\screen_main3.png'
$dst = 'D:\vit\apkdemo\GoldieSettingsAndroid\screen_main3_small.png'
$img = [System.Drawing.Image]::FromFile($src)
$newW = 576
$newH = [int]($img.Height * $newW / $img.Width)
$bmp = New-Object System.Drawing.Bitmap($newW, $newH)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = 'HighQualityBicubic'
$g.DrawImage($img, 0, 0, $newW, $newH)
$bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()
$bmp.Dispose()
$g.Dispose()
Write-Host "saved $dst ${newW}x${newH}"

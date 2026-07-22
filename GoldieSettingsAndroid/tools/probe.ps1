Add-Type -AssemblyName System.Drawing
$path = 'D:\vit\apkdemo\GoldieSettingsAndroid\screen_main2.png'
$bmp = New-Object System.Drawing.Bitmap($path)
Write-Host ("WxH: {0}x{1}" -f $bmp.Width, $bmp.Height)
for ($y = 20; $y -lt $bmp.Height; $y += 300) {
    for ($x = 50; $x -lt $bmp.Width; $x += 400) {
        $c = $bmp.GetPixel($x, $y)
        Write-Host ("y{0,4} x{1,4}: #{2:X2}{3:X2}{4:X2}" -f $y, $x, $c.R, $c.G, $c.B)
    }
}
$bmp.Dispose()

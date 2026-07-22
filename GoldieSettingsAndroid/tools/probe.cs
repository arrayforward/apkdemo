using System;
using System.Drawing;
using System.Drawing.Imaging;

class P {
    static void Main(string[] args) {
        if (args.Length < 1) { Console.WriteLine("usage: probe <png>"); return; }
        using (var bmp = new Bitmap(args[0])) {
            Console.WriteLine($"WxH: {bmp.Width}x{bmp.Height}");
            for (int y = 20; y < bmp.Height; y += 200) {
                for (int x = 50; x < bmp.Width; x += 300) {
                    var c = bmp.GetPixel(x, y);
                    Console.WriteLine($"y{y,4} x{x,4}: #{c.R:X2}{c.G:X2}{c.B:X2}");
                }
            }
        }
    }
}

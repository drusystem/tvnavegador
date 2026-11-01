using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class CutChest {
    public static void Run(string srcPath, string outPath, string previewPath) {
        using (var src = new Bitmap(srcPath)) {
            int x0 = 70, y0 = 440, w = 300, h = 130;
            bool[,] keep = new bool[w, h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    keep[x, y] = IsChest(src.GetPixel(x + x0, y + y0));
                }
            }
            for (int pass = 0; pass < 2; pass++) {
                bool[,] next = new bool[w, h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        bool on = keep[x, y];
                        if (!on) {
                            Color p = src.GetPixel(x + x0, y + y0);
                            if (!IsBlue(p) && !IsGrass(p)) {
                                if (x > 0 && keep[x - 1, y]) on = true;
                                else if (x + 1 < w && keep[x + 1, y]) on = true;
                                else if (y > 0 && keep[x, y - 1]) on = true;
                                else if (y + 1 < h && keep[x, y + 1]) on = true;
                            }
                        }
                        next[x, y] = on;
                    }
                }
                keep = next;
            }

            int[] seen = new int[w * h];
            int bestId = 0, bestCount = 0, id = 0;
            var stack = new Stack<int>();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (!keep[x, y] || seen[i] != 0) continue;
                    id++;
                    int count = 0;
                    stack.Push(i);
                    seen[i] = id;
                    while (stack.Count > 0) {
                        int cur = stack.Pop();
                        count++;
                        int cx = cur % w;
                        int cy = cur / w;
                        Try(keep, seen, stack, w, h, cx - 1, cy, id);
                        Try(keep, seen, stack, w, h, cx + 1, cy, id);
                        Try(keep, seen, stack, w, h, cx, cy - 1, id);
                        Try(keep, seen, stack, w, h, cx, cy + 1, id);
                    }
                    if (count > bestCount) { bestCount = count; bestId = id; }
                }
            }

            int minX = w, minY = h, maxX = 0, maxY = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (seen[y * w + x] != bestId) { keep[x, y] = false; continue; }
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }

            int cw = maxX - minX + 1;
            int ch = maxY - minY + 1;
            using (var output = new Bitmap(cw, ch, PixelFormat.Format32bppArgb)) {
                for (int y = 0; y < ch; y++) {
                    for (int x = 0; x < cw; x++) {
                        if (keep[x + minX, y + minY]) {
                            Color p = src.GetPixel(x + minX + x0, y + minY + y0);
                            output.SetPixel(x, y, Color.FromArgb(255, p.R, p.G, p.B));
                        } else {
                            output.SetPixel(x, y, Color.FromArgb(0, 0, 0, 0));
                        }
                    }
                }
                output.Save(outPath, ImageFormat.Png);
                using (var preview = new Bitmap(cw + 16, ch + 16))
                using (var g = Graphics.FromImage(preview)) {
                    g.Clear(Color.Magenta);
                    g.DrawImage(output, 8, 8);
                    preview.Save(previewPath, ImageFormat.Png);
                }
                Console.WriteLine("pixels " + bestCount + " size " + cw + "x" + ch + " origin " + (minX + x0) + "," + (minY + y0));
            }
        }
    }

    static void Try(bool[,] keep, int[] seen, Stack<int> stack, int w, int h, int x, int y, int id) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        if (!keep[x, y] || seen[i] != 0) return;
        seen[i] = id;
        stack.Push(i);
    }

    static bool IsBlue(Color p) {
        return p.B > p.R + 28 && p.B > 45;
    }

    static bool IsGrass(Color p) {
        return p.G >= p.R - 8 && p.B >= p.R - 18 && p.R < 150 && (p.R - p.G) < 16;
    }

    static bool IsChest(Color p) {
        if (IsBlue(p) || IsGrass(p)) return false;
        if (p.R > p.G + 8 && p.R >= p.B - 8) return true;
        if (p.R > 80 && p.G > 55 && p.R >= p.B - 10) return true;
        return false;
    }
}

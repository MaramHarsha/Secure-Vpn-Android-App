package com.technoupdate.securevpn.utils

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.pow


object ImageUtils {

    fun createContrast(src: Bitmap, value: Double): Bitmap? { // image size
        val width: Int = src.width
        val height: Int = src.height
        // create output bitmap
        val bmOut: Bitmap = Bitmap.createBitmap(width, height, src.config)
        // color information
        var A: Int
        var R: Int
        var G: Int
        var B: Int
        var pixel: Int
        // get contrast value
        val contrast = ((100 + value) / 100).pow(2.0)
        // scan through all pixels
        for (x in 0 until width) {
            for (y in 0 until height) { // get pixel color
                pixel = src.getPixel(x, y)
                A = Color.alpha(pixel)
                // apply filter contrast for every channel R, G, B
                R = Color.red(pixel)
                R = (((R / 255.0 - 0.5) * contrast + 0.5) * 255.0).toInt()
                if (R < 0) {
                    R = 0
                } else if (R > 255) {
                    R = 255
                }
                G = Color.red(pixel)
                G = (((G / 255.0 - 0.5) * contrast + 0.5) * 255.0).toInt()
                if (G < 0) {
                    G = 0
                } else if (G > 255) {
                    G = 255
                }
                B = Color.red(pixel)
                B = (((B / 255.0 - 0.5) * contrast + 0.5) * 255.0).toInt()
                if (B < 0) {
                    B = 0
                } else if (B > 255) {
                    B = 255
                }
                // set new pixel color to output bitmap
                bmOut.setPixel(x, y, Color.argb(A, R, G, B))
            }
        }
        return bmOut
    }

}
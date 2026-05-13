package com.an0obis.comuginator.ui.family

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

fun createQrBitmap(content: String, sizePx: Int = 512): Bitmap {
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap[x, y] = if (bits[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return bitmap
}

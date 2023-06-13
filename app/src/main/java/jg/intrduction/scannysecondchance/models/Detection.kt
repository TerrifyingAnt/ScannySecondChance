package jg.intrduction.scannysecondchance.models

import android.graphics.RectF

data class Detection (
    val bbox: RectF,
    val score: Float
)
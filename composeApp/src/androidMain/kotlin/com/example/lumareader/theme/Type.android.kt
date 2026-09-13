package com.example.lumareader.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.lumareader.R

actual val GoogleSans: FontFamily = FontFamily(
    Font(resId = R.font.googlesans_regular, weight = FontWeight.Normal),
    Font(resId = R.font.googlesans_bold, weight = FontWeight.Bold)
)

actual val LiterataFont: FontFamily = FontFamily(
    Font(resId = R.font.literata, weight = FontWeight.Normal)
)

actual val InterFont: FontFamily = FontFamily(
    Font(resId = R.font.inter, weight = FontWeight.Normal)
)

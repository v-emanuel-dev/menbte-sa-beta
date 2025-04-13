package com.example.mentesa.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.mentesa.R

// Definição das cores para a nova paleta suave com maior contraste
val BackgroundColor = Color(0xFFF0F7F4) // Fundo claro suave
val PrimaryColor = Color(0xFF408E5F) // Verde folha mais escuro para melhor contraste
val SecondaryColor = Color(0xFF75B0A1) // Verde menta um pouco mais escuro
val TextColorDark = Color(0xFF121212) // Preto mais puro para texto
val TextColorLight = Color(0xFFF7F7F7) // Mantendo o branco para texto em fundos escuros
val SurfaceColor = Color(0xFFFFFFFF) // Mantendo o branco para superfícies
val UserBubbleColor = Color(0xFFDCEBE3) // Bolhas de mensagem do usuário um pouco mais escuras
val BotBubbleColor = Color(0xFF408E5F) // Bolhas do bot com a cor primária mais escura
val UserTextColor = TextColorDark // Mantendo o texto do usuário escuro
val BotTextColor = Color(0xFFFFFFFF) // Texto branco para melhor contraste no fundo verde

// Em vez de usar Google Fonts, usamos fontes do sistema para evitar problemas de configuração
val appFontFamily = FontFamily.SansSerif

// Esquema de cores claro (padrão)
private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    background = BackgroundColor,
    surface = SurfaceColor,
    onPrimary = TextColorLight,
    onSecondary = TextColorDark,
    onBackground = TextColorDark,
    onSurface = TextColorDark
)

// Esquema de cores escuro
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    background = Color(0xFF1A1C19),
    surface = Color(0xFF2A2C29),
    onPrimary = TextColorLight,
    onSecondary = TextColorLight,
    onBackground = TextColorLight,
    onSurface = TextColorLight
)

// Definição da tipografia com pesos aumentados para melhor legibilidade
val MenteSaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    displayMedium = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Bold, // Aumentando de SemiBold para Bold
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    displaySmall = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.SemiBold, // Aumentando de Medium para SemiBold
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Bold, // Aumentando de SemiBold para Bold
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Bold, // Aumentando de SemiBold para Bold
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.SemiBold, // Aumentando de Medium para SemiBold
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Medium, // Aumentando de Normal para Medium
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.Medium, // Aumentando de Normal para Medium
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = appFontFamily,
        fontWeight = FontWeight.SemiBold, // Aumentando de Medium para SemiBold
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun MenteSaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MenteSaTypography,
        content = content
    )
}
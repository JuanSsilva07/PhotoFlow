package com.example.aestheticcamera.ui.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.ui.camera.GuideType

data class Lesson(
    val title: String,
    val guideType: GuideType,
    val description: String,
    val tip: String
)

val lessonsList = listOf(
    Lesson(
        title = "Regra dos Terços",
        guideType = GuideType.THIRDS,
        description = "Divida o quadro em uma grade de 3x3 e posicione os elementos mais importantes nas interseções das linhas ou ao longo delas. Isso cria mais equilíbrio e interesse do que simplesmente centralizar o sujeito.",
        tip = "Dica: Ao tirar fotos de pessoas, tente posicionar os olhos do sujeito em uma das duas interseções superiores."
    ),
    Lesson(
        title = "Espiral Áurea",
        guideType = GuideType.GOLDEN,
        description = "Baseada na Proporção Áurea (Fibonacci), esta espiral guia o olhar do espectador de forma fluida e natural através do quadro. Ideal para fotos de paisagens, arquitetura ou retratos artísticos com espaço negativo.",
        tip = "Dica: Posicione o ponto focal da sua foto (como o assunto principal) no menor círculo da espiral."
    ),
    Lesson(
        title = "Simetria Central",
        guideType = GuideType.SYMMETRY,
        description = "Divida a imagem exatamente ao meio (vertical ou horizontalmente). A simetria cria imagens marcantes, formais e organizadas. Funciona muito bem com reflexos na água, estradas vazias e fachadas de prédios.",
        tip = "Dica: Certifique-se de manter o celular perfeitamente alinhado. Até mesmo pequenos desvios podem estragar o efeito simétrico."
    ),
    Lesson(
        title = "Silhueta & Enquadramento de Retrato",
        guideType = GuideType.SILHOUETTE,
        description = "Use o contorno do corpo humano para criar profundidade e destaque. Posicionar o sujeito alinhado com a silhueta permite ajustar as luzes de fundo e obter retratos estéticos sem esforço.",
        tip = "Dica: Prefira fotos contra a luz (backlight) no pôr do sol para destacar as silhuetas de forma dramática."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonsScreen(
    onSelectGuideAndShoot: (GuideType) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Lições de Fotografia", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Aprenda a enquadrar momentos comuns como cenas de um filme utilizando nossos guias interativos.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(lessonsList) { lesson ->
                LessonCard(
                    lesson = lesson,
                    onStartCapture = { onSelectGuideAndShoot(lesson.guideType) }
                )
            }
        }
    }
}

@Composable
fun LessonCard(
    lesson: Lesson,
    onStartCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lesson.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFD54F).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Fase 1",
                        color = Color(0xFFFFD54F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = lesson.description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Text(
                    text = lesson.tip,
                    color = Color(0xFFFFD54F).copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartCapture,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Usar este Guia e Fotografar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

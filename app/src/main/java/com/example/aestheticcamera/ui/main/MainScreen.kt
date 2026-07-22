package com.example.aestheticcamera.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.R
import com.example.aestheticcamera.theme.AestheticCameraTheme
import com.example.aestheticcamera.ui.components.GlassCard
import com.example.aestheticcamera.ui.components.GlassPillBar
import com.example.aestheticcamera.ui.components.SpringButton
import com.example.aestheticcamera.ui.album.AlbumScreen

@Composable
fun MainScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToLessons: () -> Unit,
    onNavigateToCollage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Início, 2: Álbum (1: Câmera abre tela cheia)
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                onNavigateToEditor(Uri.encode(uri.toString()))
            }
        }
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0A0A),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF121214),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_home),
                            contentDescription = "Início",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Início", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFFD54F),
                        selectedTextColor = Color(0xFFFFD54F),
                        unselectedIconColor = Color.White.copy(alpha = 0.4f),
                        unselectedTextColor = Color.White.copy(alpha = 0.4f),
                        indicatorColor = Color.White.copy(alpha = 0.05f)
                    )
                )
                
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = {
                        SpringButton(
                            onClick = onNavigateToCamera,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFFFD54F),
                                                Color(0xFFFFB300)
                                            )
                                        )
                                    )
                                    .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_camera),
                                    contentDescription = "Câmera",
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
                
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_album),
                            contentDescription = "Álbum",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Álbum", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFFD54F),
                        selectedTextColor = Color(0xFFFFD54F),
                        unselectedIconColor = Color.White.copy(alpha = 0.4f),
                        unselectedTextColor = Color.White.copy(alpha = 0.4f),
                        indicatorColor = Color.White.copy(alpha = 0.05f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeTabContent(
                    onNavigateToCamera = onNavigateToCamera,
                    onNavigateToLessons = onNavigateToLessons,
                    onNavigateToCollage = onNavigateToCollage,
                    onOpenGallery = { galleryLauncher.launch("*/*") }
                )
                2 -> AlbumScreen(
                    onNavigateToEditor = onNavigateToEditor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun HomeTabContent(
    onNavigateToCamera: () -> Unit,
    onNavigateToLessons: () -> Unit,
    onNavigateToCollage: () -> Unit,
    onOpenGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Cabeçalho Premium
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Olá, Fotógrafo",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pronto para enquadrar o dia?",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
            Text(
                text = "YoungPhoto",
                color = Color(0xFFFFD54F),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        // Card Dica do Dia
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.tip_of_the_day),
                    contentDescription = "Dica do Dia",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Dark Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                // Conteúdo do Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD54F), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "DICA DE COMPOSIÇÃO",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = "A Regra dos Terços",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Alinhe os objetos importantes ao longo das linhas e cruzamentos da grade 3x3 para dar mais equilíbrio e força visual à sua foto.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Seção Ações Rápidas
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Ações Rápidas",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // Card Principal Câmera
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCamera() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD54F))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tirar Foto com Guia",
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Abra a câmera com sobreposição de enquadramentos e filtros retrô em tempo real.",
                            color = Color.Black.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = "Câmera",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(start = 8.dp)
                    )
                }
            }

            // Card Criador de Colagens
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCollage() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎞️ Criador de Colagens",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Crie montagens e tiras de filme 35mm com suas fotos favoritas.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Text(
                        text = "✨",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Duas Colunas para Ações Secundárias
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Aprender Composição
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .clickable { onNavigateToLessons() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161618)),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💡", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "Aprender",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Guia completo de enquadramentos",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                // Importar da Galeria
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .clickable { onOpenGallery() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161618)),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_album),
                                contentDescription = "Galeria",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Editar Galeria",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Importe e edite fotos do dispositivo",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Rodapé Decorativo
        Text(
            text = "\"Fotografar é colocar na mesma linha de mira a cabeça, o olho e o coração.\"\n— Henri Cartier-Bresson",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
    }
}



@Preview
@Composable
fun MainScreenPreview() {
    AestheticCameraTheme {
        MainScreen(
            onNavigateToCamera = {},
            onNavigateToEditor = {},
            onNavigateToLessons = {},
            onNavigateToCollage = {}
        )
    }
}

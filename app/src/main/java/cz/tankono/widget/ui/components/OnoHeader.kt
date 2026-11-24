package cz.tankono.widget.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tankono.widget.R
import cz.tankono.widget.ui.OnoRed
import cz.tankono.widget.ui.OnoYellow

@Composable
fun OnoHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .height(40.dp)
            .background(OnoYellow)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomStart)
                .offset(y = (-8).dp)
                .background(OnoRed)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomStart)
                .offset(y = (-5).dp)
                .background(OnoRed)
        )
        Image(
            painter = painterResource(id = R.drawable.logo_text),
            contentDescription = "Tank ONO",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart
        )
        Text(
            text = "Nastavení",
            color = OnoRed,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 4.dp)
        )
    }
}
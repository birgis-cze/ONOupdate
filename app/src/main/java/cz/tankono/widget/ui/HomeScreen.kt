package cz.tankono.widget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tankono.widget.data.model.Currency
import cz.tankono.widget.data.model.PriceFormatter
import cz.tankono.widget.data.model.PriceState
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.repo.PriceRepository

@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PriceState?>(null) }

    LaunchedEffect(Unit) {
        state = PriceRepository(context).loadState()
    }

    val s = state
    if (s == null) {
        Box(
            Modifier.fillMaxSize().background(OnoYellow),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = OnoRed)
        }
        return
    }

    Surface(color = OnoYellow, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnoRed,
                    contentColor = OnoYellow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nastavení", fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCard {
                    SectionTitle("Aktuální ceny (všechny produkty)")

                    Product.entries.forEach { product ->
                        val cur = s.current?.entries?.get(product)
                        val old = s.previous?.entries?.get(product)
                        if (cur != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    product.displayName,
                                    color = OnoRed,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    PriceFormatter.format(old, Currency.CZK),
                                    color = OnoRed.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                                Text(
                                    PriceFormatter.format(cur, Currency.CZK),
                                    color = OnoRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
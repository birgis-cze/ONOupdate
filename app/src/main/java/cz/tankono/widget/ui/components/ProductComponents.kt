package cz.tankono.widget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.tankono.widget.data.model.Product
import cz.tankono.widget.data.prefs.WidgetSettings
import cz.tankono.widget.ui.OnoRed
import cz.tankono.widget.ui.OnoYellow

@Composable
fun CurrencyButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) OnoRed else Color.White
    val fg = if (selected) OnoYellow else OnoRed
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(44.dp)
            .border(2.dp, OnoRed, RoundedCornerShape(8.dp))
            .background(bg, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProductGroup(
    title: String,
    products: List<Product>,
    settings: WidgetSettings,
    state: MutableState<WidgetSettings?>
) {
    Column {
        Text(title, color = OnoRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .padding(top = 2.dp, bottom = 6.dp)
                .fillMaxWidth(0.4f)
                .height(1.dp)
                .background(OnoRed)
        )
        products.forEach { product ->
            val checked = product in settings.visibleProducts
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(
                    product.displayName + " (" + product.shortName + ")",
                    color = OnoRed,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        val newSet = if (isChecked) settings.visibleProducts + product
                                     else settings.visibleProducts - product
                        state.value = settings.copy(visibleProducts = newSet)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnoYellow,
                        checkedTrackColor = OnoRed,
                        checkedBorderColor = OnoRed,
                        uncheckedThumbColor = OnoRed,
                        uncheckedTrackColor = Color.White,
                        uncheckedBorderColor = OnoRed
                    )
                )
            }
        }
    }
}
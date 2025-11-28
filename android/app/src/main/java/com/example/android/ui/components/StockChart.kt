package com.example.android.ui.components

import kotlin.math.min
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.android.data.model.CandleResponse
import com.example.android.data.model.Currency
import com.example.android.util.toFormattedCurrency
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.text.SimpleDateFormat
import java.util.*

/**
 * MPAndroidChart 기반 주식 차트 컴포저블
 *
 * 기능:
 * - 날짜 기준 오름차순 정렬 (과거 → 최근)
 * - X축에 날짜 표시
 * - 확대/축소(Pinch Zoom)
 * - 좌우 드래그 스크롤
 * - 터치 시 해당 지점의 날짜와 가격 표시
 */
@Composable
fun StockChart(
    candleData: CandleResponse,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    var selectedPrice by remember { mutableStateOf<Double?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        // 터치된 지점 정보 표시
        if (selectedPrice != null && selectedDate != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDate ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = (selectedPrice ?: 0.0).toFormattedCurrency(currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 차트
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    LineChart(context).apply {
                        setupChart(
                            candleData = candleData,
                            onValueSelected = { price, date ->
                                selectedPrice = price
                                selectedDate = date
                            }
                        )
                    }
                },
                update = { chart ->
                    chart.setupChart(
                        candleData = candleData,
                        onValueSelected = { price, date ->
                            selectedPrice = price
                            selectedDate = date
                        }
                    )
                }
            )
        }
    }
}

/**
 * LineChart 설정
 */
private fun LineChart.setupChart(
    candleData: CandleResponse,
    onValueSelected: (Double, String) -> Unit
) {
    // 데이터 정렬 (과거 → 최근)
    val sortedData = candleData.timestamps
        .zip(candleData.close)
        .sortedBy { it.first } // timestamp 기준 오름차순

    val entries = sortedData.mapIndexed { index, (timestamp, closePrice) ->
        Entry(index.toFloat(), closePrice.toFloat())
    }

    val dates = sortedData.map { (timestamp, _) ->
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp * 1000))
    }

    // LineDataSet 생성
    val dataSet = LineDataSet(entries, "Price").apply {
        color = Color.parseColor("#3B82F6")
        lineWidth = 2.5f
        setDrawCircles(false)
        setDrawValues(false)
        setDrawFilled(true)
        fillColor = Color.parseColor("#3B82F6")
        fillAlpha = 50
        mode = LineDataSet.Mode.CUBIC_BEZIER
        cubicIntensity = 0.2f

        // 하이라이트 설정
        highLightColor = Color.parseColor("#FF3B30")
        setDrawHighlightIndicators(true)
        highlightLineWidth = 1.5f
    }

    val lineData = LineData(dataSet)
    this.data = lineData

    // X축 설정
    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawGridLines(true)
        gridColor = Color.LTGRAY
        gridLineWidth = 0.5f
        textColor = Color.DKGRAY
        textSize = 10f
        granularity = 1f
        labelRotationAngle = -45f

        // 날짜 포맷터
        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < dates.size) {
                    dates[index]
                } else {
                    ""
                }
            }
        }

        // 라벨 개수 조절
        setLabelCount(min(6, dates.size), false)
    }

    // 왼쪽 Y축 설정
    axisLeft.apply {
        setDrawGridLines(true)
        gridColor = Color.LTGRAY
        gridLineWidth = 0.5f
        textColor = Color.DKGRAY
        textSize = 10f

        valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "%.0f".format(value)
            }
        }
    }

    // 오른쪽 Y축 비활성화
    axisRight.isEnabled = false

    // 차트 설정
    description.isEnabled = false
    legend.isEnabled = false
    setTouchEnabled(true)
    isDragEnabled = true
    setScaleEnabled(true)
    setPinchZoom(true)
    setDrawGridBackground(false)

    // 배경색
    setBackgroundColor(Color.WHITE)

    // 터치 리스너
    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
        override fun onValueSelected(e: Entry?, h: Highlight?) {
            if (e != null) {
                val index = e.x.toInt()
                if (index >= 0 && index < dates.size) {
                    onValueSelected(e.y.toDouble(), dates[index])
                }
            }
        }

        override fun onNothingSelected() {
            // 선택 해제 시
        }
    })

    // 애니메이션
    animateX(1000)

    // 차트 갱신
    invalidate()
}

/**
 * 간단한 라인 차트 (대체용)
 */
@Composable
fun SimpleLineChart(
    candleData: CandleResponse,
    currency: Currency,
    modifier: Modifier = Modifier
) {
    var selectedPrice by remember { mutableStateOf<Double?>(null) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        if (selectedPrice != null && selectedDate != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedDate ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = (selectedPrice ?: 0.0).toFormattedCurrency(currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "차트를 표시하려면 MPAndroidChart 라이브러리가 필요합니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
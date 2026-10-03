package com.jaime.teleprompter.domain

/** Todo lo necesario para exportar una grabación; es lo que se entrega al trabajo en segundo plano. */
data class ExportJob(
    val sourcePath: String,
    val filters: FilterTimeline,
    val horizontalFrameCenter: Float,
    val verticalOutput: VerticalOutput,
)

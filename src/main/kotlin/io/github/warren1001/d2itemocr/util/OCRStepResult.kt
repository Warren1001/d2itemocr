package io.github.warren1001.d2itemocr.util

import io.github.warren1001.d2itemocr.processing.OCRStep
import java.awt.image.BufferedImage
import java.util.*

data class OCRStepResult(val step: OCRStep, val image: BufferedImage, val timeTaken: Long, val visualizedImage: Optional<BufferedImage>)
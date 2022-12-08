package io.github.warren1001.d2itemocr.processing

import java.awt.image.BufferedImage

interface OCRStep {
	
	fun stepNameDifferentiator(): String
	
	fun canVisualize(): Boolean
	
	fun process(image: BufferedImage, visualize: Boolean): BufferedImage
	
}
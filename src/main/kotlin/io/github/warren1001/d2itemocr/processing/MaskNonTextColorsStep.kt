package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.getColor
import io.github.warren1001.d2itemocr.setColor
import io.github.warren1001.d2itemocr.withinRange
import java.awt.Color
import java.awt.image.BufferedImage

class MaskNonTextColorsStep(private val textColorList: List<Color>): OCRStep {
	
	override fun stepNameDifferentiator() = "maskNonTextColors"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		for (y in 0 until image.height) {
			for (x in 0 until image.width) {
				val color = image.getColor(x, y)
				var found = false
				for (textColor in textColorList) {
					if (color.withinRange(textColor, 2)) {
						found = true
						break
					}
				}
				if (!found) {
					image.setColor(x, y, Color(0, 0, 0))
				}
			}
		}
		return image
	}
	
}
package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.setColor
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BoldTextStep(private val textColorList: List<Color>): OCRStep {
	
	override fun stepNameDifferentiator() = "boldText"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		for (y in 0 until image.height) {
			for (x in 0 until image.width) {
				var foundNeighbor = false
				val color = image.getRGB(x, y)
				val r = color shr 16 and 0xFF
				val g = color shr 8 and 0xFF
				val b = color and 0xFF
				for (textColor in textColorList) {
					for (i in -1..1) {
						for (j in -1..1) {
							val nx = x + i
							val ny = y + j
							if ((i == 0 && j == 0) || nx >= image.width || nx < 0 || ny >= image.height || ny < 0) continue
							val neighborColor = image.getRGB(nx, ny)
							val nr = neighborColor shr 16 and 0xFF
							val ng = neighborColor shr 8 and 0xFF
							val nb = neighborColor and 0xFF
							if (withinRange(nr, ng, nb, textColor.red, textColor.green, textColor.blue, 3)) {
								if (isShadeOf(r, g, b, textColor.red, textColor.green, textColor.blue)) {
									foundNeighbor = true
									image.setColor(x, y, textColor)
								}
							}
							if (foundNeighbor) break
						}
						if (foundNeighbor) break
					}
					if (foundNeighbor) break
				}
			}
		}
		return image
	}
	
	fun isShadeOf(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int, error: Double = 0.1): Boolean {
		val rg1 = r1 / g1.toDouble()
		val rg2 = r2 / g2.toDouble()
		val gb1 = g1 / b1.toDouble()
		val gb2 = g2 / b2.toDouble()
		return withinMargin(rg1, rg2, error) && withinMargin(gb1, gb2, error)
				&& min(r1, r2) / max(r1, r2).toDouble() >= 0.3
				&& min(g1, g2) / max(g1, g2).toDouble() >= 0.3
				&& min(b1, b2) / max(b1, b2).toDouble() >= 0.3
	}
	
	fun withinRange(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int, range: Int): Boolean {
		return abs(r1 - r2) <= range && abs(g1 - g2) <= range && abs(b1 - b2) <= range
	}
	
	fun withinMargin(first: Double, second: Double, error: Double): Boolean {
		val f = max(first, 1.0)
		val s = max(second, 1.0)
		val top = abs(f - s)
		val bot = max(f, s)
		val div = top / bot
		return div <= error
	}
	
}
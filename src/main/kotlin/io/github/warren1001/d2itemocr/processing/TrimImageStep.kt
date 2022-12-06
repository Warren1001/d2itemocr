package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.countConnected
import io.github.warren1001.d2itemocr.pad
import io.github.warren1001.d2itemocr.util.ColorPoint
import java.awt.image.BufferedImage

class TrimImageStep: OCRStep {
	
	private val padding = 20
	
	override fun stepNameDifferentiator() = "trimImage"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		val discovered = mutableSetOf<ColorPoint>()
		val paragraphs = mutableSetOf<Set<ColorPoint>>()
		for (x in 0 until image.width) {
			for (y in 0 until image.height) {
				val connected = countConnected(image, x, y, discovered, 20)
				if (connected.isEmpty()) continue
				paragraphs.add(connected)
			}
		}
		if (paragraphs.isNotEmpty()) {
			val paragraph = paragraphs.maxBy { it.size }
			var minX = Int.MAX_VALUE
			var minY = Int.MAX_VALUE
			var maxX = Int.MIN_VALUE
			var maxY = Int.MIN_VALUE
			for (point in paragraph) {
				if (point.x < minX) minX = point.x
				if (point.y < minY) minY = point.y
				if (point.x > maxX) maxX = point.x
				if (point.y > maxY) maxY = point.y
			}
			return image.getSubimage(minX, minY, maxX - minX, maxY - minY).pad(padding)
		}
		return image
	}
	
}
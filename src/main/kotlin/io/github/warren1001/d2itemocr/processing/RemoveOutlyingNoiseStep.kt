package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.countConnected
import io.github.warren1001.d2itemocr.getColor
import io.github.warren1001.d2itemocr.setColor
import io.github.warren1001.d2itemocr.util.ColorPoint
import io.github.warren1001.d2itemocr.withinRange
import java.awt.Color
import java.awt.image.BufferedImage

class RemoveOutlyingNoiseStep: OCRStep {
	
	private val requiredNeighbors = 15
	private val radiusBounds = 15
	
	override fun stepNameDifferentiator() = "removeOutlyingNoise"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		val discovered = mutableSetOf<ColorPoint>()
		for (x in 0 until image.width) {
			for (y in 0 until image.height) {
				val connected = countConnected(image, x, y, discovered, 3)
				if (connected.isEmpty()) continue
				var minX = Int.MAX_VALUE
				var minY = Int.MAX_VALUE
				var maxX = Int.MIN_VALUE
				var maxY = Int.MIN_VALUE
				var ax = 0
				var ay = 0
				for (point in connected) {
					if (point.x < minX) minX = point.x
					if (point.y < minY) minY = point.y
					if (point.x > maxX) maxX = point.x
					if (point.y > maxY) maxY = point.y
					ax += point.x
					ay += point.y
				}
				ax /= connected.size
				ay /= connected.size
				val rx = (maxX - minX) / 2 + 1
				val ry = (maxY - minY) / 2 + 1
				var foundNeighbors = 0
				for (i in -radiusBounds-rx..radiusBounds+rx) {
					for (j in -radiusBounds-ry..radiusBounds+ry) {
						if ((i > -rx && i < rx && j > -ry && j < ry) || ax + i < 0 || ax + i >= image.width || ay + j < 0 || ay + j >= image.height) continue
						val point = image.getColor(ax + i, ay + j)
						if (!point.withinRange(Color.BLACK, 1)) {
							foundNeighbors++
							if (foundNeighbors >= requiredNeighbors) break
						}
					}
					if (foundNeighbors >= requiredNeighbors) break
				}
				if (foundNeighbors < requiredNeighbors) {
					connected.forEach { image.setColor(it, Color.BLACK) }
				}
			}
		}
		return image
	}
	
}
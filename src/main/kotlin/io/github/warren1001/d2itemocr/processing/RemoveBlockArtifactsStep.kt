package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.countConnected
import io.github.warren1001.d2itemocr.getColor
import io.github.warren1001.d2itemocr.setColor
import io.github.warren1001.d2itemocr.util.ColorPoint
import java.awt.Color
import java.awt.image.BufferedImage

class RemoveBlockArtifactsStep: OCRStep {
	
	override fun stepNameDifferentiator() = "removeBlockArtifacts"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		for (m in 1 downTo 1) {
			val discovered = mutableSetOf<ColorPoint>()
			for (x in 0 until image.width) {
				if (x >= image.width) break
				for (y in 0 until image.height) {
					if (y >= image.height) break
					val connected = countConnected(image, x, y, discovered, m)
					if (connected.isEmpty()) continue
					var minX = Int.MAX_VALUE
					var minY = Int.MAX_VALUE
					var maxX = Int.MIN_VALUE
					var maxY = Int.MIN_VALUE
					for (point in connected) {
						if (point.x < minX) minX = point.x
						if (point.y < minY) minY = point.y
						if (point.x > maxX) maxX = point.x
						if (point.y > maxY) maxY = point.y
					}
					val somePoint = connected.first()
					val color = image.getColor(somePoint)
					val width = maxX - minX
					val height = maxY - minY
					if (connected.size >= 150 || height > 20 || width > 20
						|| ((color.red == 132 && color.green == 132 && color.blue == 132) && (height < 6 || width < 1 || connected.size <= 10))
					) {
						connected.forEach { image.setColor(it, Color.BLACK) }
					}
				}
			}
		}
		return image
	}
	
}
package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.toLong
import java.awt.Color
import java.awt.image.BufferedImage

class MaskDarkBlocksStep: OCRStep {
	
	private val blockSize = 3
	
	override fun stepNameDifferentiator() = "maskDarkBlocks"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		val sections = mutableListOf<Long>()
		for (x in 0 until image.width step blockSize) {
			for (y in 0 until image.height step blockSize) {
				if (x + blockSize > image.width || y + blockSize > image.height) continue
				var count = 0
				for (i in 0 until blockSize) {
					for (j in 0 until blockSize) {
						val c = image.getRGB(x + i, y + j)
						val r = c shr 16 and 0xFF
						val g = c shr 8 and 0xFF
						val b = c and 0xFF
						if (((r == 2 && g <= 2 && b <= 2) || (r <= 2 && g == 2 && b <= 2) || (r <= 2 && g <= 2 && b == 2))
							|| (r in 3..4 && g in 3..4 && b in 3..4)) {
							count++
						}
					}
				}
				if (count == blockSize * blockSize) {
					sections.add(x.toLong(y))
				}
			}
		}
		for (section in sections) {
			val x = (section shr 32).toInt()
			val y = section.toInt()
			for (i in 0 until blockSize) {
				for (j in 0 until blockSize) {
					image.setRGB(x + i, y + j, Color.WHITE.rgb)
				}
			}
		}
		return image
	}
}
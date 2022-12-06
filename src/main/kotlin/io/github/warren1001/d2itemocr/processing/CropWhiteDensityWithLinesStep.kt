package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.sumRGB
import io.github.warren1001.d2itemocr.util.BoundingBox
import io.github.warren1001.d2itemocr.util.BoundingLine
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Crops the image to the bounding box created by a cluster of horizontal lines containing the most white pixels.
 */
class CropWhiteDensityWithLinesStep: OCRStep {
	
	private val WHITE_RGB = Color.WHITE.rgb
	
	override fun stepNameDifferentiator() = "cropWhiteDensityWithLines"
	
	override fun canVisualize() = true
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		val boxes = mutableListOf(BoundingBox())
		val lines = mutableListOf<BoundingLine>()
		val list = mutableListOf<Int>()
		var lineSkips = 0
		var consecutiveSkips = 0
		for (y in 1 until image.height - 2) {
			for (x in 0 until image.width - 1) {
				val colorAbove = image.sumRGB(x, y - 1)
				val colorSum = image.sumRGB(x, y)
				val colorNext = image.sumRGB(x + 1, y)
				val colorBelow = image.sumRGB(x, y + 2)
				if (colorSum < 200) {
					if (colorSum / colorNext.toDouble() > 1.5 && colorSum > 30 && colorNext > 60) {
						if (list.size > 300) {
							for (i in 0 until consecutiveSkips) {
								list.removeLast()
							}
							if (visualize) visualizeLine(image, list, y)
							lines.add(BoundingLine(list.first(), list.last(), y))
							list.clear()
							lineSkips = 0
							consecutiveSkips = 0
						} else if (lineSkips < 5) {
							lineSkips++
							consecutiveSkips++
							list.add(x)
						} else {
							list.clear()
							lineSkips = 0
							consecutiveSkips = 0
						}
					} else if ((colorAbove < 765 && colorAbove / colorSum.toDouble() > 1.5)
						|| (colorBelow < 765 && colorBelow / colorSum.toDouble() > 1.5)) {
						list.add(x)
						consecutiveSkips = 0
					} else if (lineSkips < 5) {
						lineSkips++
						consecutiveSkips++
						list.add(x)
					} else if (list.size > 300) {
						for (i in 0 until consecutiveSkips) {
							list.removeLast()
						}
						if (visualize) visualizeLine(image, list, y)
						lines.add(BoundingLine(list.first(), list.last(), y))
						list.clear()
						lineSkips = 0
						consecutiveSkips = 0
					} else {
						list.clear()
						lineSkips = 0
						consecutiveSkips = 0
					}
				} else if (list.size > 300) {
					for (i in 0 until consecutiveSkips) {
						list.removeLast()
					}
					if (visualize) visualizeLine(image, list, y)
					lines.add(BoundingLine(list.first(), list.last(), y))
					list.clear()
					lineSkips = 0
					consecutiveSkips = 0
				} else {
					list.clear()
					lineSkips = 0
					consecutiveSkips = 0
				}
			}
			list.clear()
			lineSkips = 0
		}
		for (line in lines) {
			var found = false
			for (box in boxes) {
				if (box.grow(line)) {
					found = true
					break
				}
			}
			if (!found) {
				val bb = BoundingBox()
				bb.grow(line)
				boxes.add(bb)
			}
		}
		return boxes.map { image.getSubimage(it.minX, it.minY, it.width, it.height) }.maxBy {
			var count = 0
			for (x in 0 until it.width) {
				for (y in 0 until it.height) {
					val color = it.sumRGB(x, y)
					if (color == 765) {
						count++
					}
				}
			}
			count
		}
	}
	
	fun visualizeLine(image: BufferedImage, list: List<Int>, y: Int) {
		for (x in list) {
			image.setRGB(x, y, WHITE_RGB)
		}
	}
	
}
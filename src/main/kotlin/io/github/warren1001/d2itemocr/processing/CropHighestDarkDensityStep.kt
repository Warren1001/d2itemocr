package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.sumRGB
import io.github.warren1001.d2itemocr.toLong
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Crops the image to the bounding box created by a cluster of boxes containing the most dark pixels.
 * Crop pads the block size difference away from the bounding box to compensate for margin of error.
 */
class CropHighestDarkDensityStep: OCRStep {
	
	private val blockSize = 45
	private val WHITE_RGB = Color.WHITE.rgb
	
	override fun stepNameDifferentiator() = "cropHighestDarkDensity"
	
	override fun canVisualize() = true
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		val sections = mutableListOf<Pair<Long, Int>>()
		for (x in 0 until image.width step blockSize) {
			for (y in 0 until image.height step blockSize) {
				var sum = 0
				var count = 0
				val endX = if (x + blockSize > image.width) image.width - x else blockSize
				val endY = if (y + blockSize > image.height) image.height - y else blockSize
				if (endX < 2 || endY < 2) {
					sections.add(Pair(x.toLong(y), 765 / 2))
					continue
				}
				var forceEnd = false
				for (i in 0 until endX) {
					for (j in 0 until endY) {
						if (x + i >= image.width || y + j >= image.height) continue
						val c = image.getRGB(x + i, y + j)
						val r = c shr 16 and 0xFF
						val g = c shr 8 and 0xFF
						val b = c and 0xFF
						if (r > 60 || g > 60 || b > 60) { // if we find any pixel that is significantly brighter than the rest, we can skip this section
							forceEnd = true
							sections.add(Pair(x.toLong(y), 765 / 2))
							break
						}
						sum += image.sumRGB(x + i, y + j)
						count++
					}
					if (forceEnd) break
				}
				if (forceEnd) continue
				val final = sum / count
				sections.add(Pair(x.toLong(y), final))
			}
		}
		val max = sections.maxBy { it.second }.second
		val min = sections.minBy { it.second }.second
		val med = (max + min) / 2
		val dist = max - med
		val lower = med - (dist / 1.2)
		
		var minX = Int.MAX_VALUE
		var minY = Int.MAX_VALUE
		var maxX = Int.MIN_VALUE
		var maxY = Int.MIN_VALUE
		
		for (i in 0 until sections.size) {
			val section = sections[i]
			if (section.second <= lower) {
				val x = (section.first shr 32).toInt()
				val y = section.first.toInt()
				val ex = (x + blockSize).coerceAtMost(image.width)
				val ey = (y + blockSize).coerceAtMost(image.height)
				val width = ex - x
				val height = ey - y
				if (width < 3 || height < 3) continue
				if (x < minX) minX = x
				if (y < minY) minY = y
				if (ex > maxX) maxX = ex
				if (ey > maxY) maxY = ey
				if (visualize) visualizeBlock(image, x, y, ex, ey)
			}
		}
		
		minX -= (blockSize / 2).toInt()
		minX = minX.coerceAtLeast(0)
		minY -= (blockSize / 2).toInt()
		minY = minY.coerceAtLeast(0)
		maxX += (blockSize / 2).toInt()
		maxX = maxX.coerceAtMost(image.width - 1)
		maxY += (blockSize / 2).toInt()
		maxY = maxY.coerceAtMost(image.height - 1)
		var width = maxX - minX
		var height = maxY - minY
		width = width.coerceAtMost(image.width - minX - 1)
		height = height.coerceAtMost(image.height - minY - 1)
		return image.getSubimage(minX, minY, width, height)
	}
	
	fun visualizeBlock(image: BufferedImage, startX: Int, startY: Int, endX: Int, endY: Int) {
		for (x in startX until endX) {
			for (y in startY until endY) {
				image.setRGB(x, y, WHITE_RGB)
			}
		}
	}
	
}
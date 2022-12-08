package io.github.warren1001.d2itemocr.processing

import io.github.warren1001.d2itemocr.util.BoundingBox
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LocateTooltipBoxStep: OCRStep {
	
	private val minLineWidth = 250
	private val minLineHeight = 100
	private val darkThreshold = 144
	private val shadeVariationAllowance = 0.1
	private val minWhitePixelsInTooltip = 10
	private val colorDifferenceFactor = 1.5
	private val gracePixels = 24
	private val consecutiveGracePixels = 12
	
	override fun stepNameDifferentiator() = "locateTooltipBox"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		var lineStart = -1
		var lineWidth = 0
		var gracePixelsConsumed = 0
		var consecutiveGracePixelsConsumed = 0
		for (y in 1 until image.height - minLineHeight) {
			for (x in 0 until image.width - 1) {
				val colorCurrent = image.getRGB(x, y)
				val rc = colorCurrent shr 16 and 0xFF
				val gc = colorCurrent shr 8 and 0xFF
				val bc = colorCurrent and 0xFF
				val sumc = rc + gc + bc
				val colorNext = image.getRGB(x + 1, y)
				val rn = colorNext shr 16 and 0xFF
				val gn = colorNext shr 8 and 0xFF
				val bn = colorNext and 0xFF
				val sumn = rn + gn + bn
				val colorAdjacent = image.getRGB(x, y - 1)
				val ra = colorAdjacent shr 16 and 0xFF
				val ga = colorAdjacent shr 8 and 0xFF
				val ba = colorAdjacent and 0xFF
				val suma = ra + ga + ba
				val isProbablyBorder = sumn / sumc.toDouble() < colorDifferenceFactor && flatDiff(suma, sumn) >= 10
				if (isDark(rc, gc, bc) && (isProbablyBorder || (gracePixelsConsumed < gracePixels && consecutiveGracePixelsConsumed < consecutiveGracePixels))) {
					if (!isProbablyBorder) {
						gracePixelsConsumed++
						consecutiveGracePixelsConsumed++
					} else {
						consecutiveGracePixelsConsumed = 0
					}
					if (lineStart == -1) {
						lineStart = x
						//image.setRGB(x, y, Color.WHITE.rgb)
						debug("dark found, starting line at $x, $y with width $lineWidth", x, y)
					} else {
						lineWidth++
						//image.setRGB(x, y, Color.WHITE.rgb)
						debug("dark found, expanding line started at $x, $y by 1 to width $lineWidth", x, y)
					}
					if (x == image.width - 1 && lineWidth >= minLineWidth) {
						val boxO = locatePossibleBoundingBox(image, lineStart, y, lineWidth)
						if (boxO.isPresent) {
							debug("we reached the end of the image first, and found a box", x, y)
							val box = boxO.get()
							debug("found a box at ${box.minX}, ${box.minY}, of width ${box.width} and height ${box.height}", x, y)
							return image//image.getSubimage(boxO.get().minX, boxO.get().minY, boxO.get().width, boxO.get().height)
						} else {
							debug("we reached the end of the image first, but didn't find a box", x, y)
							lineStart = -1
							lineWidth = 0
							gracePixelsConsumed = 0
							consecutiveGracePixelsConsumed = 0
						}
					}
				} else {
					if (lineWidth >= minLineWidth) {
						debug("the line is long enough, checking for a box", x, y)
						val boxO = locatePossibleBoundingBox(image, lineStart, y, lineWidth)
						if (boxO.isPresent) {
							debug("found a box", x, y)
							val box = boxO.get()
							debug("found a box at ${box.minX}, ${box.minY}, of width ${box.width} and height ${box.height}", x, y)
							return image//image.getSubimage(boxO.get().minX, boxO.get().minY, boxO.get().width, boxO.get().height)
						}
						lineStart = -1
						lineWidth = 0
						gracePixelsConsumed = 0
						consecutiveGracePixelsConsumed = 0
					} else {
						debug("line of width $lineWidth starting at $lineStart, $y is not long enough", x, y)
						lineStart = -1
						lineWidth = 0
						gracePixelsConsumed = 0
						consecutiveGracePixelsConsumed = 0
					}
				}
			}
		}
		println("found nothing")
		return image
	}
	
	private fun debug(msg: String, x: Int, y: Int) {
		if (x in 1088..1549 && y in 297..551) {
			println(msg)
		}
	}
	
	private fun locatePossibleBoundingBox(image: BufferedImage, leftX: Int, topY: Int, width: Int): Optional<BoundingBox> {
		debug("Looking for box with TL at ($leftX, $topY) and width $width", leftX, topY)
		var leftX = leftX
		val rightX = leftX + width
		val rightHeight = locateRightVerticalLine(image, rightX, topY)
		if (rightHeight < minLineHeight) return Optional.empty()
		debug("Found right height of $rightHeight", leftX, topY)
		drawLine(image, rightX, rightX, topY, topY + rightHeight)
		val bottomY = topY + rightHeight
		val bottomWidth = locateBottomHorizontalLine(image, rightX, bottomY)
		if (bottomWidth < minLineWidth) return Optional.empty()
		debug("Found bottom width of $bottomWidth", leftX, topY)
		debug("widths: $width, $bottomWidth", leftX, topY)
		if (bottomWidth < width) leftX = rightX - bottomWidth
		drawLine(image, leftX, rightX, bottomY, bottomY)
		val leftHeight = locateLeftVerticalLine(image, leftX, bottomY)
		if (leftHeight < minLineHeight) return Optional.empty()
		debug("Found left height of $leftHeight", leftX, topY)
		debug("heights: $rightHeight, $leftHeight", leftX, topY)
		if (leftHeight < rightHeight) return Optional.empty()
		drawLine(image, leftX, leftX, topY, bottomY)
		return if (ensureBoxIsTooltip(image, leftX, topY, rightX, bottomY)) {
			Optional.of(BoundingBox(leftX, topY, rightX, bottomY))
		} else {
			Optional.empty()
		}
	}
	
	private fun drawLine(image: BufferedImage, x1: Int, y1: Int, x2: Int, y2: Int) {
		for (x in x1..x2) {
			for (y in y1..y2) {
				image.setRGB(x, y, Color.WHITE.rgb)
			}
		}
	}
	
	private fun locateRightVerticalLine(image: BufferedImage, x: Int, startY: Int): Int {
		var lineHeight = 1
		for (y in startY + 1 until image.height - 1) {
			debug("($x, $y) of (${image.width}, ${image.height})", x, y)
			val colorCurrent = image.getRGB(x, y)
			val rc = colorCurrent shr 16 and 0xFF
			val gc = colorCurrent shr 8 and 0xFF
			val bc = colorCurrent and 0xFF
			val sumc = rc + gc + bc
			val colorNext = image.getRGB(x + 1, y)
			val rn = colorNext shr 16 and 0xFF
			val gn = colorNext shr 8 and 0xFF
			val bn = colorNext and 0xFF
			val sumn = rn + gn + bn
			if (sumn / sumc.toDouble() < colorDifferenceFactor) {
				lineHeight++
			} else {
				break
			}
		}
		return lineHeight
	}
	
	private fun locateBottomHorizontalLine(image: BufferedImage, startX: Int, y: Int): Int {
		var lineWidth = 0
		for (x in startX + 1 downTo 0) {
			val colorCurrent = image.getRGB(x, y)
			val rc = colorCurrent shr 16 and 0xFF
			val gc = colorCurrent shr 8 and 0xFF
			val bc = colorCurrent and 0xFF
			val sumc = rc + gc + bc
			val colorNext = image.getRGB(x + 1, y)
			val rn = colorNext shr 16 and 0xFF
			val gn = colorNext shr 8 and 0xFF
			val bn = colorNext and 0xFF
			val sumn = rn + gn + bn
			if (sumn / sumc.toDouble() < colorDifferenceFactor) {
				lineWidth++
			} else {
				break
			}
		}
		return lineWidth
	}
	
	private fun locateLeftVerticalLine(image: BufferedImage, x: Int, startY: Int): Int {
		var lineHeight = 1
		for (y in startY - 1 downTo 1) {
			val colorCurrent = image.getRGB(x, y)
			val rc = colorCurrent shr 16 and 0xFF
			val gc = colorCurrent shr 8 and 0xFF
			val bc = colorCurrent and 0xFF
			val sumc = rc + gc + bc
			val colorNext = image.getRGB(x + 1, y)
			val rn = colorNext shr 16 and 0xFF
			val gn = colorNext shr 8 and 0xFF
			val bn = colorNext and 0xFF
			val sumn = rn + gn + bn
			if (sumn / sumc.toDouble() < colorDifferenceFactor) {
				lineHeight++
			} else {
				break
			}
		}
		return lineHeight
	}
	
	private fun ensureBoxIsTooltip(image: BufferedImage, minX: Int, minY: Int, maxX: Int, maxY: Int): Boolean {
		var count = 0
		for (x in minX..maxX) {
			for (y in minY..maxY) {
				val color = image.getRGB(x, y)
				val r = color shr 16 and 0xFF
				val g = color shr 8 and 0xFF
				val b = color and 0xFF
				if (r == 255 && g == 255 && b == 255) {
					count++
				}
			}
		}
		return count >= minWhitePixelsInTooltip
	}
	
	private fun isDark(r: Int, g: Int, b: Int) = r <= darkThreshold && g <= darkThreshold && b <= darkThreshold// && areShades(r, g, b, 20, 20, 20)
	
	private fun areShades(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Boolean {
		val rg1 = diff(r1, g1)
		val rg2 = diff(r2, g2)
		val rgDiff = abs(rg1 - rg2)
		if (rgDiff > shadeVariationAllowance) return false
		val gb1 = diff(g1, b1)
		val gb2 = diff(g2, b2)
		val gbDiff = abs(gb1 - gb2)
		return gbDiff <= shadeVariationAllowance
		
	}
	
	private fun diff(first: Int, second: Int) = min(first, second) / max(first, second).toDouble()
	
	private fun flatDiff(first: Int, second: Int) = abs(first - second)
	
}
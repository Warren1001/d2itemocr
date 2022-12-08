package io.github.warren1001.d2itemocr.processing

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LocateTooltipBox2Step: OCRStep {
	
	private val shadeVariationAllowance = 0.5
	private val WHITE = Color.WHITE.rgb
	private val gracePixels = 2
	
	override fun stepNameDifferentiator() = "locateTooltipBox2"
	
	override fun canVisualize() = false
	
	override fun process(image: BufferedImage, visualize: Boolean): BufferedImage {
		for (y in 1 until image.height - 1) {
			var tsumColorsLineTop = 0
			var tsumColorsLineCurr = 0
			var tstartX = -1
			var twidth = 0
			var tgracePixelsConsumed = 0
			
			var bsumColorsLineBot = 0
			var bsumColorsLineCurr = 0
			var bstartX = -1
			var bwidth = 0
			var bgracePixelsConsumed = 0
			for (x in 0 until image.width) {
				val currColor = image.getRGB(x, y)
				val cr = currColor shr 16 and 0xFF
				val cg = currColor shr 8 and 0xFF
				val cb = currColor and 0xFF
				val csum = cr + cg + cb
				val topColor = image.getRGB(x, y - 1)
				val tr = topColor shr 16 and 0xFF
				val tg = topColor shr 8 and 0xFF
				val tb = topColor and 0xFF
				val tsum = tr + tg + tb
				val tareShades = areShades(cr, cg, cb, tr, tg, tb) || tsum + csum < 20
				val tisDark = csum <= 125
				val tisDarker = (((tsumColorsLineTop + tsum) / (twidth + 1)) / ((tsumColorsLineCurr + csum) / (twidth + 1)).toDouble() >= 1.5)// || tsum + csum < 20
				val tbelowIsDark = pixelsBelowAreDark(image, x, y, 5)
				val tisSpecificDark = cr in 1..2 && cg in 1..2 && cb in 1..2
				//if ((y == 297 || y == 1) && x in 1088..1549)
				//	println("x: $x, y: $y, areShades: $areShades, isDark: $isDark, isDarker: $isDarker, belowIsDark: $belowIsDark, isNotSpecificDark: ${!isSpecificDark}")
				val tbool = tareShades && tisDark && tisDarker && tbelowIsDark && !tisSpecificDark
				if (tbool || tgracePixelsConsumed < gracePixels) {
					if (!tbool) tgracePixelsConsumed++
					else tgracePixelsConsumed = 0
					if (tstartX == -1) {
						tsumColorsLineCurr += csum
						tsumColorsLineTop += tsum
						tstartX = x
					} else {
						tsumColorsLineCurr += csum
						tsumColorsLineTop += tsum
						twidth++
					}
				} else {
					if (twidth > 300) {
						if (tgracePixelsConsumed > 0) twidth -= tgracePixelsConsumed
						for (i in tstartX..tstartX + twidth) {
							image.setRGB(i, y, WHITE)
						}
					}
					tsumColorsLineCurr = 0
					tsumColorsLineTop = 0
					tstartX = -1
					twidth = 0
					tgracePixelsConsumed = 0
				}
				
				val botColor = image.getRGB(x, y + 1)
				val br = botColor shr 16 and 0xFF
				val bg = botColor shr 8 and 0xFF
				val bb = botColor and 0xFF
				val bsum = br + bg + bb
				val bareShades = areShades(cr, cg, cb, br, bg, bb) || bsum + csum < 20
				val bisDark = csum <= 125
				val bisDarker = (((bsumColorsLineBot + bsum) / (bwidth + 1)) / ((bsumColorsLineCurr + csum) / (bwidth + 1)).toDouble() >= 1.5)// || tsum + csum < 20
				val baboveIsDark = pixelsAboveAreDark(image, x, y, 5)
				val bisSpecificDark = cr in 1..2 && cg in 1..2 && cb in 1..2
				if ((y == 551) && x in 1088..1549)
					println("x: $x, y: $y, bareShades: $bareShades, bisDark: $bisDark, bisDarker: $bisDarker, bbelowIsDark: $baboveIsDark, bisNotSpecificDark: ${!bisSpecificDark}")
				val bbool = bareShades && bisDark && bisDarker && baboveIsDark && !bisSpecificDark
				if (bbool || bgracePixelsConsumed < gracePixels) {
					if (!bbool) bgracePixelsConsumed++
					else bgracePixelsConsumed = 0
					if (bstartX == -1) {
						bsumColorsLineCurr += csum
						bsumColorsLineBot += bsum
						bstartX = x
					} else {
						bsumColorsLineCurr += csum
						bsumColorsLineBot += bsum
						bwidth++
					}
				} else {
					if (bwidth > 300) {
						if (bgracePixelsConsumed > 0) bwidth -= bgracePixelsConsumed
						for (i in bstartX..bstartX + bwidth) {
							image.setRGB(i, y, WHITE)
						}
					}
					bsumColorsLineCurr = 0
					bsumColorsLineBot = 0
					bstartX = -1
					bwidth = 0
					bgracePixelsConsumed = 0
				}
			}
		}
		return image
	}
	
	private fun pixelsBelowAreDark(image: BufferedImage, x: Int, y: Int, range: Int): Boolean {
		for (i in 1..range) {
			if (y + i >= image.height) break
			val currColor = image.getRGB(x, y + i)
			val cr = currColor shr 16 and 0xFF
			val cg = currColor shr 8 and 0xFF
			val cb = currColor and 0xFF
			val csum = cr + cg + cb
			if (csum > 125) return false
		}
		return true
	}
	
	private fun pixelsAboveAreDark(image: BufferedImage, x: Int, y: Int, range: Int): Boolean {
		for (i in 1..range) {
			if (y - i < 0) break
			val currColor = image.getRGB(x, y - i)
			val cr = currColor shr 16 and 0xFF
			val cg = currColor shr 8 and 0xFF
			val cb = currColor and 0xFF
			val csum = cr + cg + cb
			if (csum > 125) return false
		}
		return true
	}
	
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
	
}
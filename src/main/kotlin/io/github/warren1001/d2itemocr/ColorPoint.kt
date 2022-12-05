package io.github.warren1001.d2itemocr

import java.awt.image.BufferedImage

class ColorPoint(private val image: BufferedImage, val x: Int, val y: Int) {
	
	var jumpCount: Int = 0
	
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		
		other as ColorPoint
		
		if (x != other.x) return false
		if (y != other.y) return false
		
		return true
		
	}
	
	override fun hashCode(): Int {
		var result = x
		result = 31 * result + y
		return result
	}
	
	override fun toString(): String {
		return "($x, $y)"
	}
	
	fun add(x: Int, y: Int): ColorPoint {
		val p = ColorPoint(image, this.x + x, this.y + y)
		p.jumpCount = this.jumpCount
		return p
	}
	
	fun isBlack(): Boolean {
		val color = image.getColor(this)
		return color.red == 0 && color.green == 0 && color.blue == 0
	}
	
}

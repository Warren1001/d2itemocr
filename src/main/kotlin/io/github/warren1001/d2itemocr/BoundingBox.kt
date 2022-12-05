package io.github.warren1001.d2itemocr

class BoundingBox(var minX: Int = -1, var minY: Int = -1, var maxX: Int = -1, var maxY: Int = -1) {
	
	var width: Int = 0
	var height: Int = 0
	
	fun grow(line: BoundingLine): Boolean {
		return if (minX == -1) {
			minX = line.startX
			maxX = line.endX
			minY = line.y
			maxY = line.y
			width = maxX - minX + 1
			height = 1
			true
		} else if ((line.startX < minX && line.endX < minX) || (line.startX > maxX && line.endX > maxX)) {
			false
		} else {
			minX = minX.coerceAtMost(line.startX)
			maxX = maxX.coerceAtLeast(line.endX)
			minY = minY.coerceAtMost(line.y)
			maxY = maxY.coerceAtLeast(line.y)
			width = maxX - minX + 1
			height = maxY - minY + 1
			true
		}
	}
	
}
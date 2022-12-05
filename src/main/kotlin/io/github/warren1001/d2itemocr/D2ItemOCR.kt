package io.github.warren1001.d2itemocr

import com.twelvemonkeys.image.ResampleOp
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp
import java.io.File
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class D2ItemOCR {
	
	// gold, white, blue, gray
	private val colors = mutableListOf(Color(255, 255, 255), Color(212, 196, 145), Color(136, 136, 255), Color(132, 132, 132))
	
	init {
		
		val instance = Tesseract()
		instance.setDatapath(File("tessdata").absolutePath)
		instance.setLanguage("ExocetD2")
		//instance.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY)
		
		val imagesFolder = File("images")
		//val images = imagesFolder.listFiles()
		val file = File(imagesFolder, "test4.bmp")
		var image = file.toImage()
		
		val service = Executors.newFixedThreadPool(16)
		
		var total = 0L
		var start: Long = System.currentTimeMillis()
		var last: Long
		
		val images = subSection(image)
		last = System.currentTimeMillis() - start
		total += last
		println("subSection: $last")
		images.forEachIndexed { index, img ->
			img.saveToFile("images/process/subsection/$index.png")
		}
		//start = System.currentTimeMillis()
		image = images.maxBy {
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
		last = System.currentTimeMillis() - start
		total += last
		println("chosen: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_01_chosen.png")
		start = System.currentTimeMillis()
		image = markDarkestSections(image)
		last = System.currentTimeMillis() - start
		total += last
		println("markDarkestSections: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_02_marked.png")
		start = System.currentTimeMillis()
		boldenText(image)
		last = System.currentTimeMillis() - start
		total += last
		println("boldenText: $last")
		image.saveToFile("images/${file.nameWithoutExtension}_03_bolded.png")
		start = System.currentTimeMillis()
		maskNonTextColors(image)
		last = System.currentTimeMillis() - start
		total += last
		println("maskNonTextColors: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_04_masked.png")
		start = System.currentTimeMillis()
		removeBlocks(image)
		last = System.currentTimeMillis() - start
		total += last
		println("removeBlocks: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_05_removed.png")
		start = System.currentTimeMillis()
		removeOutlyingPatches(image)
		last = System.currentTimeMillis() - start
		total += last
		println("removeOutlyingPatches: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_06_outlying.png")
		start = System.currentTimeMillis()
		image = cropToLargestParagraph(image)
		last = System.currentTimeMillis() - start
		total += last
		println("cropToLargestParagraph: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_07_cropped.png")
		//image = filterLanczos(image, 1280, 720)
		//println("filterLanczos: $last")
		//image.saveToFile("images/process/${file.nameWithoutExtension}_08_filtered.png")
		/*boldenText(image, service)
		println("boldenText: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_09_bolded.png")
		start = System.currentTimeMillis()
		maskNonTextColors(image)
		println("maskNonTextColors: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_10_masked.png")
		start = System.currentTimeMillis()
		removeBlocks(image)
		println("removeBlocks: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_11_removed.png")
		start = System.currentTimeMillis()
		removeOutlyingPatches(image)
		println("removeOutlyingPatches: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_12_outlying.png")
		start = System.currentTimeMillis()
		image = cropToLargestParagraph(image)
		println("cropToLargestParagraph: $last")
		image.saveToFile("images/process/${file.nameWithoutExtension}_12_cropped.png")
		//images.forEach {*/
		try {
			start = System.currentTimeMillis()
			//val result = instance.doOCR(it)
			val result = instance.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)
			last = System.currentTimeMillis() - start
			total += last
			println("OCR: $last")
			println(result.joinToString(separator = "\n", transform = { it.toString() }))
			println()
		} catch (e: TesseractException) {
			println(e.message)
		}
		//}
		println("total time taken: $total")
	}
	
	fun markDarkestSections(image: BufferedImage): BufferedImage {
		val step = 60
		val sections = mutableListOf<Pair<Long, Int>>()
		for (x in 0 until image.width - step step step) {
			for (y in 0 until image.height - step step step) {
				var sum = 0
				var count = 0
				for (i in 0 until step) {
					for (j in 0 until step) {
						if (x + i >= image.width || y + j >= image.height) continue
						var c = image.sumRGB(x + i, y + j)
						//c = if (c > 60) 765 else 0
						sum += c
						count++
					}
				}
				val final = sum / count
				//if (final == 0) print("${sections.size}: $x,$y, ")
				sections.add(Pair(x.toLong(y), final))
			}
		}
		//println()
		val lowerFourth = (sections.sumOf { it.second } / sections.size) / 2
		
		var minX = Int.MAX_VALUE
		var minY = Int.MAX_VALUE
		var maxX = Int.MIN_VALUE
		var maxY = Int.MIN_VALUE
		
		//println("smallest: $smallest")
		for (i in 0 until sections.size) {
			val section = sections[i]
			if (section.second <= lowerFourth) {
				val x = (section.first shr 32).toInt()
				val y = section.first.toInt()
				val ex = (x + step).coerceAtMost(image.width - 1)
				val ey = (y + step).coerceAtMost(image.height - 1)
				if (x < minX) minX = x
				if (y < minY) minY = y
				if (ex > maxX) maxX = ex
				if (ey > maxY) maxY = ey
				//print("$i: $x,$y, ")
				for (m in 0 until step) {
					for (n in 0 until step) {
						if (x + m >= image.width || y + n >= image.height) continue
						//println("marking..")
						image.setRGB(x + m, y + n, Color.WHITE.rgb)
					}
				}
			}
		}
		
		minX -= step / 2
		minX = minX.coerceAtLeast(0)
		minY -= step / 2
		minY = minY.coerceAtLeast(0)
		maxX += step / 2
		maxX = maxX.coerceAtMost(image.width - 1)
		maxY += step / 2
		maxY = maxY.coerceAtMost(image.height - 1)
		
		return image.getSubimage(minX, minY, maxX - minX, maxY - minY)
		//println()
	}
	
	fun subSection(image: BufferedImage): MutableList<BufferedImage> {
		val boxes = mutableListOf(BoundingBox())
		val lines = mutableListOf<BoundingLine>()
		val list = mutableListOf<Int>()
		val whiteRGB = Color.WHITE.rgb
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
							//if (y == 309 || y == 563) println("found line of length (force): ${list.size}")
							for (i in 0 until consecutiveSkips) {
								list.removeLast()
							}
							/*for (i in list) {
								image.setRGB(i, y, whiteRGB)
							}*/
							lines.add(BoundingLine(list.first(), list.last(), y))
							list.clear()
							lineSkips = 0
							consecutiveSkips = 0
						} else if (lineSkips < 5) {
							lineSkips++
							consecutiveSkips++
							list.add(x)
							//if (y == 309 || y == 563) println("marked ($x, $y)")
						} else {
							//if (y == 309 || y == 563) println("line of length ${list.size} was not long enough (force)")
							list.clear()
							lineSkips = 0
							consecutiveSkips = 0
						}
					} else if ((colorAbove < 765 && colorAbove / colorSum.toDouble() > 1.5)
						|| (colorBelow < 765 && colorBelow / colorSum.toDouble() > 1.5)) {
						list.add(x)
						//if (y == 309 || y == 563) println("marked ($x, $y)")
						consecutiveSkips = 0
					} else if (lineSkips < 5) {
						lineSkips++
						consecutiveSkips++
						list.add(x)
						//if (y == 309 || y == 563) println("marked ($x, $y)")
					} else if (list.size > 300) {
						//if (y == 309 || y == 563) println("found line of length (a): ${list.size}")
						for (i in 0 until consecutiveSkips) {
							list.removeLast()
						}
						/*for (i in list) {
							image.setRGB(i, y, whiteRGB)
						}*/
						lines.add(BoundingLine(list.first(), list.last(), y))
						list.clear()
						lineSkips = 0
						consecutiveSkips = 0
					} else {
						//if (y == 309 || y == 563) println("line of length ${list.size} was not long enough (a)")
						list.clear()
						lineSkips = 0
						consecutiveSkips = 0
					}
				} else if (list.size > 300) {
					//if (y == 309 || y == 563) println("found line of length (b): ${list.size}")
					for (i in 0 until consecutiveSkips) {
						list.removeLast()
					}
					/*for (i in list) {
						image.setRGB(i, y, whiteRGB)
					}*/
					lines.add(BoundingLine(list.first(), list.last(), y))
					list.clear()
					lineSkips = 0
					consecutiveSkips = 0
				} else {
					//if (y == 309 || y == 563) println("line of length ${list.size} was not long enough (b)")
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
		return boxes.map { image.getSubimage(it.minX, it.minY, it.width, it.height) }.toMutableList()
	}
	
	fun Int.toLong(other: Int) = (this.toLong() shl 32) or other.toLong()
	
	fun boldenText(image: BufferedImage) {
		//val explored = mutableSetOf<Long>()
		for (y in 0 until image.height) {
			for (x in 0 until image.width) {
				var foundNeighbor = false
				val color = image.getRGB(x, y)
				val r = color shr 16 and 0xFF
				val g = color shr 8 and 0xFF
				val b = color and 0xFF
				for (textColor in colors) {
					for (i in -1..1) {
						for (j in -1..1) {
							val nx = x + i
							val ny = y + j
							//val key = nx.toLong(ny)
							if ((i == 0 && j == 0) /*|| explored.contains(key)*/ || nx >= image.width || nx < 0 || ny >= image.height || ny < 0) continue
							//explored.add(key)
							val neighborColor = image.getRGB(nx, ny)
							val nr = neighborColor shr 16 and 0xFF
							val ng = neighborColor shr 8 and 0xFF
							val nb = neighborColor and 0xFF
							if (withinRange(nr, ng, nb, textColor.red, textColor.green, textColor.blue, 3)) {
								if (isShadeOf(r, g, b, textColor.red, textColor.green, textColor.blue)) {
									foundNeighbor = true
									//println("($x,$y): $color -> $textColor because of ($nx,$ny): $neighbor")
									image.setColor(x, y, textColor)
								}
							}
							if (foundNeighbor) break
						}
						if (foundNeighbor) break
					}
					if (foundNeighbor) break
				}
			}
		}
	}
	
	fun isShadeOf(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int, error: Double = 0.1): Boolean {
		val rg1 = r1 / g1.toDouble()
		val rg2 = r2 / g2.toDouble()
		val gb1 = g1 / b1.toDouble()
		val gb2 = g2 / b2.toDouble()
		return withinMargin(rg1, rg2, error) && withinMargin(gb1, gb2, error)
				&& min(r1, r2) / max(r1, r2).toDouble() >= 0.3 // 0.3
				&& min(g1, g2) / max(g1, g2).toDouble() >= 0.3
				&& min(b1, b2) / max(b1, b2).toDouble() >= 0.3
	}
	
	fun withinRange(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int, range: Int): Boolean {
		return abs(r1 - r2) <= range && abs(g1 - g2) <= range && abs(b1 - b2) <= range
	}
	
	fun maskNonTextColors(bufferedImage: BufferedImage) {
		for (y in 0 until bufferedImage.height) {
			for (x in 0 until bufferedImage.width) {
				val color = bufferedImage.getColor(x, y)
				var found = false
				for (textColor in colors) {
					if (color.withinRange(textColor, 2)) {
						found = true
						break
					}
				}
				if (!found) {
					bufferedImage.setColor(x, y, Color(0, 0, 0))
				}
			}
		}
	}
	
	fun removeBlocks(image: BufferedImage) {
		for (m in 1 downTo 1) {
			val discovered = mutableSetOf<ColorPoint>()
			for (x in 0 until image.width) {
				if (x >= image.width) break
				for (y in 0 until image.height) {
					if (y >= image.height) break
					val connected = countConnected(image, x, y, discovered, m)
					if (connected.isEmpty()) continue
					//println("size: ${connected.size}")
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
		//ColorPoint.resetColorPoints()
	}
	
	fun removeOutlyingPatches(image: BufferedImage) {
		val requiredNeighbors = 15
		val radiusBounds = 15
		val discovered = mutableSetOf<ColorPoint>()
		for (x in 0 until image.width) {
			for (y in 0 until image.height) {
				val connected = countConnected(image, x, y, discovered, 3)
				if (connected.isEmpty()) continue
				//println("size: ${connected.size} of color: ${image.getColor(connected.first())}")
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
							//println("found neighbor! ($foundNeighbors)")
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
		//ColorPoint.resetColorPoints()
	}
	
	fun cropToLargestParagraph(image: BufferedImage): BufferedImage {
		val discovered = mutableSetOf<ColorPoint>()
		val paragraphs = mutableSetOf<Set<ColorPoint>>()
		for (x in 0 until image.width) {
			for (y in 0 until image.height) {
				val connected = countConnected(image, x, y, discovered, 20)
				if (connected.isEmpty()) continue
				//println("size: ${connected.size}")
				paragraphs.add(connected)
			}
		}
		//ColorPoint.resetColorPoints()
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
			return image.getSubimage(minX, minY, maxX - minX, maxY - minY)
		}
		return image
	}
	
	fun Color.withinRange(other: Color, range: Int): Boolean {
		return abs(this.red - other.red) <= range && abs(this.green - other.green) <= range && abs(this.blue - other.blue) <= range
	}
	
	fun Color.isShadeOf(other: Color, error: Double = 0.1): Boolean {
		val rg1 = this.red / this.green.toDouble()
		val rg2 = other.red / other.green.toDouble()
		val gb1 = this.green / this.blue.toDouble()
		val gb2 = other.green / other.blue.toDouble()
		return withinMargin(rg1, rg2, error) && withinMargin(gb1, gb2, error)
				&& min(this.red, other.red) / max(this.red, other.red).toDouble() >= 0.3 // 0.3
				&& min(this.green, other.green) / max(this.green, other.green).toDouble() >= 0.3
				&& min(this.blue, other.blue) / max(this.blue, other.blue).toDouble() >= 0.3
	}
	
	fun countConnected(image: BufferedImage, x: Int, y: Int, discovered: MutableSet<ColorPoint> = mutableSetOf(), maxJumpCount: Int): Set<ColorPoint> {
		
		val point = ColorPoint(image, x, y)
		if (!discovered.add(point)) return emptySet()
		
		val connected = mutableSetOf<ColorPoint>()
		
		//println("starting at pixel $x, $y: $r, $g, $b")
		if (!point.isBlack()) {
			
			var explore = mutableSetOf<ColorPoint>()
			
			//println("it passes, collect it and branch out")
			connected.add(point)
			explore(image, point, discovered, explore, maxJumpCount)
			
			//println("begin explore")
			
			while (explore.isNotEmpty()) {
				//println("explore size: ${explore.size}, maxJump: $maxJumpCount")
				val next = explore.first()
				explore.remove(next)
				//println("removed: $next")
				if (!next.isBlack()) connected.add(next)
				explore(image, next, discovered, explore, maxJumpCount)
			}
			
			//println("end explore")
			
		}
		return connected
	}
	
	fun explore(image: BufferedImage, start: ColorPoint, discovered: MutableSet<ColorPoint>, explore: MutableSet<ColorPoint>, maxJumpCount: Int) {
		for (i in -1..1) {
			if (start.x + i >= image.width || start.x + i < 0) continue
			for (j in -1..1) {
				if ((i == 0 && j == 0) || start.y + j >= image.height || start.y + j < 0) continue
				val next = start.add(i, j)
				if (!discovered.add(next)) continue
				//println("discovered: ${discovered.size}")
				//println("checking pixel ${next.x}, ${next.y}: ${next.jumpCount} < $maxJumpCount")
				if (next.isBlack() && next.jumpCount < maxJumpCount) {
					next.jumpCount++
					explore.add(next)
					//println("black added: $next")
				} else if (!next.isBlack()) {
					next.jumpCount = 0
					explore.add(next)
					//println("not black added: $next")
				}
			}
		}
	}
	
	fun withinMargin(first: Int, second: Int, error: Double): Boolean {
		val f = max(first, 1)
		val s = max(second, 1)
		val top = abs(f - s)
		val bot = max(f, s)
		val div = top / bot.toDouble()
		//println("$f, $s, $div")
		return div <= error
	}
	
	fun withinMargin(first: Double, second: Double, error: Double): Boolean {
		val f = max(first, 1.0)
		val s = max(second, 1.0)
		val top = abs(f - s)
		val bot = max(f, s)
		val div = top / bot
		//println("$f, $s, $div")
		return div <= error
	}
	
}

private fun progressiveScaling(before: BufferedImage, longestSideLength: Int): BufferedImage {
	var before = before
	var w = before.width
	var h = before.height
	var ratio = if (h > w) longestSideLength.toDouble() / h else longestSideLength.toDouble() / w
	
	//Multi Step Rescale operation
	//This technique is describen in Chris Campbell’s blog The Perils of Image.getScaledInstance(). As Chris mentions, when downscaling to something less than factor 0.5, you get the best result by doing multiple downscaling with a minimum factor of 0.5 (in other words: each scaling operation should scale to maximum half the size).
	while (ratio < 0.5) {
		val tmp = scale(before, 0.5)
		before = tmp
		w = before.width
		h = before.height
		ratio = if (h > w) longestSideLength.toDouble() / h else longestSideLength.toDouble() / w
	}
	return scale(before, ratio)
}

private fun scale(imageToScale: BufferedImage, ratio: Double): BufferedImage {
	val dWidth = (imageToScale.width * ratio).toInt()
	val dHeight = (imageToScale.height * ratio).toInt()
	val scaledImage = BufferedImage(dWidth, dHeight, BufferedImage.TYPE_INT_RGB)
	val graphics2D = scaledImage.createGraphics()
	graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
	graphics2D.drawImage(imageToScale, 0, 0, dWidth, dHeight, null)
	graphics2D.dispose()
	return scaledImage
}

fun BufferedImage.sumRGB(x: Int, y: Int): Int {
	val rgb = getRGB(x, y)
	val r = (rgb shr 16) and 0xFF
	val g = (rgb shr 8) and 0xFF
	val b = rgb and 0xFF
	return r + g + b
}

fun BufferedImage.setColor(x: Int, y: Int, color: Color) = setRGB(x, y, color.rgb)

fun BufferedImage.setColor(point: ColorPoint, color: Color) = setColor(point.x, point.y, color)

fun BufferedImage.getColor(x: Int, y: Int) = Color(getRGB(x, y), true)

fun BufferedImage.getColor(point: ColorPoint) = getColor(point.x, point.y)

fun BufferedImage.saveToFile(fileName: String) = ImageIO.write(this, "png", File(fileName))

fun filterLanczos(image: BufferedImage, width: Int, height: Int): BufferedImage {
	val resampler: BufferedImageOp = ResampleOp(width, height, ResampleOp.FILTER_LANCZOS)
	return resampler.filter(image, null)
}

fun BufferedImage.resize(width: Int, height: Int): BufferedImage {
	val resizedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
	val g = resizedImage.createGraphics()
	g.drawImage(this, 0, 0, width, height, null)
	g.dispose()
	return resizedImage
}

fun BufferedImage.scaleToHeight(height: Int): BufferedImage {
	val ratio = height.toDouble() / this.height
	val width = (this.width * ratio).toInt()
	return this.resize(width, height)
}

fun BufferedImage.scaleToWidth(width: Int): BufferedImage {
	val ratio = width.toDouble() / this.width
	val height = (this.height * ratio).toInt()
	return this.resize(width, height)
}

fun File.toImage() = ImageIO.read(this)

fun main(args: Array<String>) {
	D2ItemOCR()
}
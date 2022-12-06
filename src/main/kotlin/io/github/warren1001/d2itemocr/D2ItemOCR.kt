package io.github.warren1001.d2itemocr

import io.github.warren1001.d2itemocr.processing.*
import io.github.warren1001.d2itemocr.util.ColorPoint
import io.github.warren1001.d2itemocr.util.OCRStepResult
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.WritableRaster
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

class D2ItemOCR {
	
	private val instance = Tesseract()
	
	// gold, white, blue, gray
	private val textColors = mutableListOf(Color(255, 255, 255), Color(212, 196, 145), Color(136, 136, 255), Color(132, 132, 132))
	
	private val ocrSteps = mutableListOf(
		CropWhiteDensityWithLinesStep(),
		MaskDarkBlocksStep(),
		CropHighestDarkDensityStep(),
		BoldTextStep(textColors),
		MaskNonTextColorsStep(textColors),
		RemoveBlockArtifactsStep(),
		RemoveOutlyingNoiseStep(),
		TrimImageStep()
	)
	
	init {
		
		instance.setDatapath(File("tessdata").absolutePath)
		instance.setLanguage("ExocetD2")
		
		var run = true
		
		File("images", "process").mkdirs()
		
		Executors.newSingleThreadExecutor().execute {
			while (run) {
				print("Input the image path to OCR: ")
				val input = readLine()!!
				if (input == "exit") {
					run = false
				} else {
					val file = File("images", input)
					if (!file.exists()) {
						println("File '$input' does not exist, try again. Must be located in images/ folder.")
					} else {
						println()
						println("Processing image...")
						doOCR(file)
					}
				}
			}
		}
		
	}
	
	fun doOCR(file: File) {
		
		val name = file.nameWithoutExtension
		var image = file.toImage()
		
		var totalTimeTaken = 0L
		
		for ((i, step) in ocrSteps.withIndex()) {
			val result = executeOCRStep(image, step, true)
			totalTimeTaken += result.timeTaken
			image = result.image
			image.saveToFile("images/process/${name}_${i}_${step.stepNameDifferentiator()}.png")
			val visualOptional = result.visualizedImage
			if (visualOptional.isPresent) {
				visualOptional.get().saveToFile("images/process/${name}_${i}_${step.stepNameDifferentiator()}_visualized.png")
			}
			println("Step ${i + 1} of ${ocrSteps.size} (${step.stepNameDifferentiator()}) took ${result.timeTaken}ms")
		}
		
		try {
			
			val start = System.currentTimeMillis()
			val result = instance.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)
			val time = System.currentTimeMillis() - start
			totalTimeTaken += time
			
			println("OCR took ${time}ms")
			println()
			println(result.joinToString(separator = "\n", transform = { "${it.text}\t${(it.confidence * 10).roundToInt() / 10.0}% Confidence" }))
			
		} catch (e: TesseractException) {
			println(e.message)
		}
		
		println()
		println("Total time taken: $totalTimeTaken")
		println()
		println("----------------------------------------")
		println()
	}
	
	fun executeOCRStep(image: BufferedImage, step: OCRStep, visualize: Boolean = false): OCRStepResult {
		val start = System.currentTimeMillis()
		val visualizedImage = if (visualize && step.canVisualize()) Optional.of(step.process(image.clone(), true)) else Optional.empty()
		val result = step.process(image, false)
		val time = System.currentTimeMillis() - start
		return OCRStepResult(step, result, time, visualizedImage)
	}
	
}

fun BufferedImage.clone(): BufferedImage {
	val cm: ColorModel = this.colorModel
	val isAlphaPremultiplied: Boolean = cm.isAlphaPremultiplied
	val raster: WritableRaster = this.copyData(this.raster.createCompatibleWritableRaster())
	return BufferedImage(cm, raster, isAlphaPremultiplied, null)
}

fun countConnected(image: BufferedImage, x: Int, y: Int, discovered: MutableSet<ColorPoint> = mutableSetOf(), maxJumpCount: Int): Set<ColorPoint> {
	
	val point = ColorPoint(image, x, y)
	if (!discovered.add(point)) return emptySet()
	
	val connected = mutableSetOf<ColorPoint>()
	
	if (!point.isBlack()) {
		
		val explore = mutableSetOf<ColorPoint>()
		
		connected.add(point)
		explore(image, point, discovered, explore, maxJumpCount)
		
		while (explore.isNotEmpty()) {
			val next = explore.first()
			explore.remove(next)
			if (!next.isBlack()) connected.add(next)
			explore(image, next, discovered, explore, maxJumpCount)
		}
		
	}
	return connected
}

private fun explore(image: BufferedImage, start: ColorPoint, discovered: MutableSet<ColorPoint>, explore: MutableSet<ColorPoint>, maxJumpCount: Int) {
	for (i in -1..1) {
		if (start.x + i >= image.width || start.x + i < 0) continue
		for (j in -1..1) {
			if ((i == 0 && j == 0) || start.y + j >= image.height || start.y + j < 0) continue
			val next = start.add(i, j)
			if (!discovered.add(next)) continue
			if (next.isBlack() && next.jumpCount < maxJumpCount) {
				next.jumpCount++
				explore.add(next)
			} else if (!next.isBlack()) {
				next.jumpCount = 0
				explore.add(next)
			}
		}
	}
}

fun Color.withinRange(other: Color, range: Int): Boolean {
	return abs(this.red - other.red) <= range && abs(this.green - other.green) <= range && abs(this.blue - other.blue) <= range
}

fun Int.toLong(other: Int) = (this.toLong() shl 32) or other.toLong()

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

fun BufferedImage.pad(length: Int) = this.pad(length, length, Color.BLACK)

fun BufferedImage.pad(width: Int, height: Int, color: Color): BufferedImage {
	val paddedImage = BufferedImage(this.width + width * 2, this.height + height * 2, BufferedImage.TYPE_INT_ARGB)
	val g = paddedImage.createGraphics()
	g.color = color
	g.fillRect(0, 0, paddedImage.width, paddedImage.height)
	g.drawImage(this, width, height, null)
	g.dispose()
	return paddedImage
}

fun File.toImage() = ImageIO.read(this)

fun main(args: Array<String>) {
	D2ItemOCR()
}
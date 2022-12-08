package io.github.warren1001.d2itemocr

import io.github.warren1001.d2itemocr.processing.*
import io.github.warren1001.d2itemocr.util.ColorPoint
import io.github.warren1001.d2itemocr.util.OCRStepResult
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.ITessAPI.TessPageSegMode
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.WritableRaster
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.system.exitProcess

class D2ItemOCR {
	
	private val instance = Tesseract()
	
	// gold, white, blue, gray, red
	private val textColors = mutableListOf(Color(255, 255, 255), Color(212, 196, 145), Color(136, 136, 255), Color(132, 132, 132), Color(255, 105, 105))
	
	private val ocrSteps1 = mutableListOf(
		CropWhiteDensityWithLinesStep(),
		MaskDarkBlocksStep(),
		CropHighestDarkDensityStep()
	)
	
	private val ocrSteps2 = mutableListOf(
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
		instance.setVariable("tessedit_char_whitelist", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789%*()+-/,:' ")
		instance.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY)
		instance.setPageSegMode(TessPageSegMode.PSM_SINGLE_BLOCK)
		
		File("images", "process").mkdirs()
		
		while (true) {
			print("Input the image and mode to OCR: ")
			val input = readLine()!!
			if (input.startsWith("exit")) {
				exitProcess(0)
			}
			val args = input.split(" ")
			if (args.size != 2) {
				println("You must provide the image file name and the mode (1 or 2): 'test.bmp 1'")
				continue
			}
			val fileName = args[0]
			val mode = args[1].toInt()
			if (mode != 1 && mode != 2) {
				println("Mode must be 1 or 2")
				continue
			}
			val file = File("images", fileName)
			if (!file.exists()) {
				println("File '$fileName' does not exist, try again. Must be located in images/ folder.")
			} else {
				println()
				println("Processing image...")
				doOCR(file, mode)
			}
		}
		
	}
	
	fun doOCR(file: File, mode: Int) {
		
		val name = file.nameWithoutExtension
		var image = file.toImage()
		val ocrSteps = if (mode == 1) ocrSteps1 else ocrSteps2
		
		var totalTimeTaken = 0L
		
		val path = "images/process/${name}/"
		File(path).listFiles()?.forEach { it.delete() }
		
		for ((i, step) in ocrSteps.withIndex()) {
			val result = executeOCRStep(image, step, true)
			totalTimeTaken += result.timeTaken
			image = result.image
			image.saveToFile("$path${i}_${step.stepNameDifferentiator()}.png")
			result.visualizedImage.ifPresent { it.saveToFile("images/process/${name}/${i}_v_${step.stepNameDifferentiator()}.png") }
			val stepInfo = "Step ${i + 1}/${ocrSteps.size} - ${step.stepNameDifferentiator()}:"
			val spaces = "\t".repeat(7 - stepInfo.length / 8)
			println("$stepInfo$spaces${result.timeTaken} ms")
		}
		
		try {
			
			val start = System.currentTimeMillis()
			val result = instance.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)
			val time = System.currentTimeMillis() - start
			totalTimeTaken += time
			
			val stepInfo = "Step Final - OCR:"
			val spaces = "\t".repeat(7 - stepInfo.length / 8)
			
			println("$stepInfo$spaces${time} ms")
			println()
			println("Total time taken: $totalTimeTaken ms")
			println()
			println(result.filter { it.confidence > 5 }.joinToString(separator = "\n", transform = {
				val text = it.text.replace("\n", "")
				val spacing = "\t".repeat(7 - text.length / 8)
				"$text$spacing(${(it.confidence * 10).roundToInt() / 10.0}%)"
			}))
			
		} catch (e: TesseractException) {
			println(e.message)
		}
		
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

fun BufferedImage.saveToFile(fileName: String): Boolean {
	val file = File(fileName)
	file.parentFile.mkdirs()
	return ImageIO.write(this, "png", file)
}

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
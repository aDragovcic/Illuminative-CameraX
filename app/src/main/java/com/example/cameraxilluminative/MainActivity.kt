package com.example.cameraxilluminative

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.cameraxilluminative.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors

private const val CAMERA_PERMISSIONS_REQUEST_CODE = 10
private const val PHOTO_EXTENSION = ".jpg"

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    // CameraX
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    // Usecases
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // Image capture
    private var lastImageUri: Uri? = null

    // Image analysis
    private var analysisMode = AnalysisMode.TEXT_RECOGNITION
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private enum class AnalysisMode {
        TEXT_RECOGNITION, IMAGE_LABELING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCamera(CameraSelector.LENS_FACING_BACK)

        binding.thumbnail.setOnClickListener {
            val fullScreenPhotoFragment = FullScreenPhotoFragment().apply { uri = lastImageUri }
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, fullScreenPhotoFragment)
                .addToBackStack("FullScreenFragment")
                .commit()
        }

        binding.flipCameraButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            setupCamera(lensFacing)
        }

        binding.imageRecognitionButton.setOnClickListener { analysisMode = AnalysisMode.IMAGE_LABELING }
        binding.textRecognitionButton.setOnClickListener { analysisMode = AnalysisMode.TEXT_RECOGNITION }

        binding.shutterButton.setOnClickListener {
            val file = File(getOutputDirectory(this), System.currentTimeMillis().toString() + PHOTO_EXTENSION)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

            imageCapture?.takePicture(
                outputOptions, cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        lastImageUri = outputFileResults.savedUri ?: Uri.fromFile(file)
                        binding.thumbnail.post {
                            // Set thumbnail image
                            Glide.with(binding.thumbnail)
                                .load(lastImageUri)
                                .apply(RequestOptions.circleCropTransform())
                                .into(binding.thumbnail)

                            // Animate thumbnail
                            binding.thumbnail.animate()
                                .scaleX(1.3f)
                                .scaleY(1.3f)
                                .setInterpolator(OvershootInterpolator())
                                .withEndAction {
                                    binding.thumbnail.animate().scaleY(1f).scaleX(1f).start()
                                }
                                .start()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(this@MainActivity, "Image Capture failed!", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun setupCamera(lensFacing: Int) {
        this.lensFacing = lensFacing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview usecase definition
        preview = Preview.Builder().build()

        // ImageCapture usecase definition
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()

        // Image analyzer usecase definition
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 700))
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    imageProxy.image?.let {
                        when (analysisMode) {
                            AnalysisMode.IMAGE_LABELING -> {
                                processImageLabeling(imageProxy)
                            }
                            AnalysisMode.TEXT_RECOGNITION -> {
                                processTextRecognition(imageProxy)
                            }
                        }
                    }
                }
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        // Bind usecases to lifecycle
        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)
            preview?.setSurfaceProvider(binding.previewView.surfaceProvider)
        } catch (exc: Exception) {
            Log.e("MainActivity", "Use case binding failed", exc)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processTextRecognition(imageProxy: ImageProxy) {
        imageProxy.image?.let { image ->
            binding.imageRecognitionResult.visibility = View.INVISIBLE

            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(inputImage)
                .addOnSuccessListener { textList ->
                    if (textList.text.isEmpty()) {
                        binding.textRecognitionContainer.visibility = View.INVISIBLE
                    } else {
                        binding.textRecognitionContainer.visibility = View.VISIBLE
                        binding.textRecognitionResult.text = textList.text
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Image analysis failed", Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageLabeling(imageProxy: ImageProxy) {
        binding.imageRecognitionResult.visibility = View.VISIBLE
        binding.textRecognitionContainer.visibility = View.INVISIBLE

        imageProxy.image?.let { image ->
            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
            labeler.process(inputImage)
                .addOnSuccessListener { labelList ->
                    labelList.firstOrNull()?.let { label ->
                        binding.imageRecognitionResult.text = label.text
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Image analysis failed", Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSIONS_REQUEST_CODE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults.firstOrNull()) {
                // Permission granted
                setupCamera(lensFacing)
            } else {
                // Permission denied
                Toast.makeText(this, "Permission request denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    override fun onResume() {
        super.onResume()

        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}

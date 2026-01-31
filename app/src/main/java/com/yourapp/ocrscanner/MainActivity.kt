package com.yourapp.ocrscanner

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yourapp.ocrscanner.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    
    companion object {
        private const val TAG = "OCRScanner"
        private const val REQUEST_CAMERA_PERMISSION = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupUI()
        checkCameraPermission()
    }
    
    private fun setupUI() {
        binding.apply {
            btnCapture.setOnClickListener {
                captureAndScan()
            }
            
            btnGallery.setOnClickListener {
                // Simuler import galerie
                simulateGalleryImport()
            }
            
            btnSave.setOnClickListener {
                saveResults()
            }
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA) -> {
                showPermissionDialog()
            }
            else -> {
                requestCameraPermission()
            }
        }
    }
    
    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission caméra")
            .setMessage("Cette app a besoin de la caméra pour scanner des documents.")
            .setPositiveButton("OK") { _, _ ->
                requestCameraPermission()
            }
            .setNegativeButton("Annuler") { _, _ ->
                Toast.makeText(this, "Fonctionnalité limitée", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION
        )
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Erreur caméra", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun captureAndScan() {
        val imageCapture = imageCapture ?: return
        
        val photoFile = File(
            externalCacheDir,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".jpg"
        )
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    processImageWithOCR(savedUri)
                }
                
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Erreur: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun processImageWithOCR(imageUri: Uri) {
        try {
            binding.progressBar.visibility = android.view.View.VISIBLE
            
            val image = InputImage.fromFilePath(this, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    displayResults(visionText.text)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Erreur OCR: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    binding.progressBar.visibility = android.view.View.GONE
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun simulateGalleryImport() {
        // Simulation pour GitHub
        val fakeText = """
            FACTURE #INV-2024-001
            Client: Entreprise ABC
            Date: 15 Janvier 2024
            Détails:
            - Service Développement: 1500€
            - Hébergement: 200€
            - Maintenance: 300€
            TOTAL: 2000€
            TVA (20%): 400€
            TOTAL TTC: 2400€
        """.trimIndent()
        
        displayResults(fakeText)
        Toast.makeText(this, "Simulation galerie - Texte factice", Toast.LENGTH_SHORT).show()
    }
    
    private fun displayResults(text: String) {
        binding.apply {
            tvResult.text = text
            tvStats.text = "${text.length} caractères • ${text.split("\\s+".toRegex()).size} mots"
        }
    }
    
    private fun saveResults() {
        val text = binding.tvResult.text.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, "Aucun texte à sauvegarder", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Sauvegarder")
            .setItems(arrayOf("Copier", "Partager", "Fichier")) { _, which ->
                when (which) {
                    0 -> copyToClipboard(text)
                    1 -> shareText(text)
                    2 -> saveToFile(text)
                }
            }
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("OCR Result", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Texte copié!", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareText(text: String) {
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Partager le texte"))
    }
    
    private fun saveToFile(text: String) {
        val fileName = "scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())}.txt"
        val file = File(getExternalFilesDir(null), fileName)
        
        try {
            file.writeText(text)
            Toast.makeText(this, "Sauvegardé: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

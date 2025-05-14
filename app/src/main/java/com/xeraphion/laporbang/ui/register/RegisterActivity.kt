package com.xeraphion.laporbang.ui.register

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.databinding.ActivityRegisterBinding
import com.xeraphion.laporbang.helper.getImageUri
import com.xeraphion.laporbang.helper.reduceFileImage
import com.xeraphion.laporbang.helper.uriToFile
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var launcherGallery: ActivityResultLauncher<String>
    private lateinit var launcherIntentCamera: ActivityResultLauncher<Uri>
    private var currentPhotoPath: String? = null
    private var selectedImageFile: File? = null

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    private val viewModel: RegisterViewModel by viewModels {
        val apiService = ApiConfig.getApiService()
        val repository = RegisterRepository(apiService)
        RegisterViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        launcherGallery =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    val imageFile = uriToFile(it, this).reduceFileImage()
                    binding.ivShowImage.setImageURI(Uri.fromFile(imageFile))
                    selectedImageFile = imageFile
                } ?: Toast.makeText(this, "TIdak ada gambar yang dipilih", Toast.LENGTH_SHORT).show()
            }

        launcherIntentCamera =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
                if (isSuccess) {
                    currentPhotoPath?.let { path ->
                        val imageFile = uriToFile(path.toUri(), this).reduceFileImage()
                        binding.ivShowImage.setImageBitmap(BitmapFactory.decodeFile(imageFile.path))
                        selectedImageFile = imageFile
                    }
                }
            }

        binding.btnGallery.setOnClickListener {
            startGallery()
        }

        binding.btnCamera.setOnClickListener {
            checkAndRequestCameraPermission()
        }

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsernameRegister.text.toString()
            val email = binding.etEmailRegister.text.toString()
            val password = binding.etPasswordRegister.text.toString()
            val confirmPassword = binding.etConfirmPasswordRegister.text.toString()

            if (!isValidEmail(email)) {
                binding.textInputEmailLogin.error = "Please enter a valid email address."
                return@setOnClickListener
            }

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Semua kolom wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Password tidak cocok!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val usernamePart = username.toRequestBody("text/plain".toMediaTypeOrNull())
            val emailPart = email.toRequestBody("text/plain".toMediaTypeOrNull())
            val passwordPart = password.toRequestBody("text/plain".toMediaTypeOrNull())
            val confirmPasswordPart =
                confirmPassword.toRequestBody("text/plain".toMediaTypeOrNull())

            val imagePart = selectedImageFile?.let {
                val reqFile = it.asRequestBody("image/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("profileImage", it.name, reqFile)
            }

            viewModel.registerUser(
                usernamePart,
                emailPart,
                passwordPart,
                confirmPasswordPart,
                imagePart,
                onSuccess = {
                    Toast.makeText(this, "Registrasi akun berhasil!", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onError = { errorMessage ->
                    Toast.makeText(this, "Registrasi gagal: $errorMessage", Toast.LENGTH_SHORT)
                        .show()
                }
            )
        }

        binding.tvSignIn.setOnClickListener {
            finish()
        }

        binding.etEmailRegister.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                validateEmail(s.toString())
            }
        })
    }

    private fun validateEmail(email: String): Boolean {
        return if (email.isEmpty()) {
            binding.textInputEmailLogin.error = "Email tidak boleh kosong"
            false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputEmailLogin.error = "Format email tidak valid"
            false
        } else {
            binding.textInputEmailLogin.error = null
            true
        }
    }


    private fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required to use this feature",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun startGallery() {
        launcherGallery.launch("image/*")
    }

    private fun startCamera() {
        val photoURI: Uri = getImageUri(this)
        currentPhotoPath = photoURI.toString()
        launcherIntentCamera.launch(photoURI)
    }
}
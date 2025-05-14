package com.xeraphion.laporbang.ui.account

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.databinding.FragmentEditAccountBinding
import com.xeraphion.laporbang.helper.getImageUri
import com.xeraphion.laporbang.helper.reduceFileImage
import com.xeraphion.laporbang.helper.uriToFile
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import kotlin.collections.get

class EditAccountFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentEditAccountBinding? = null
    private val binding get() = _binding!!
    private var currentPhotoPath: String? = null
    private var selectedImageFile: File? = null

    private lateinit var userPreference: UserPreference
    private lateinit var viewModel: EditAccountViewModel

    private val launcherGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    val imageFile = uriToFile(it, requireContext()).reduceFileImage()
                    if (imageFile.exists()) {
                        binding.ivShowImage.setImageURI(Uri.fromFile(imageFile))
                        selectedImageFile = imageFile
                    } else {
                        Toast.makeText(requireContext(), "Gagal memuat gambar", Toast.LENGTH_SHORT)
                            .show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Kesalahan pemrosesan gambar: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } ?: Toast.makeText(
                requireContext(),
                "TIdak ada gambar yang dipilih",
                Toast.LENGTH_SHORT
            ).show()
        }

    private val launcherIntentCamera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                currentPhotoPath?.let { path ->
                    try {
                        val imageFile = uriToFile(path.toUri(), requireContext()).reduceFileImage()
                        if (imageFile.exists()) {
                            binding.ivShowImage.setImageURI(Uri.fromFile(imageFile))
                            selectedImageFile = imageFile
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Gagal memuat gambar dari kamera!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Kesalahan pemrosesan gambar: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEditAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = arguments?.getString("username")
        val profileImagePath = arguments?.getString("profileImagePath")

        binding.edEditUsername.setText(username)

        profileImagePath?.let {
            Glide.with(this)
                .load(it)
                .into(binding.ivShowImage)
        }

        binding.btnCamera.setOnClickListener { startCamera() }
        binding.btnGallery.setOnClickListener { startGallery() }
        binding.btnSaveProfile.setOnClickListener { saveProfile() }

        binding.edEditPasswordCurrent.doOnTextChanged { current, _, _, _ ->
            val newPassword = binding.edEditPasswordNew.text?.toString() ?: ""

            // Validate current password field
            binding.tiEditPasswordCurrent.error = when {
                newPassword.isNotEmpty() && current.isNullOrEmpty() ->
                    "Kata sandi saat ini diperlukan untuk mengubah kata sandi!"

                else -> null
            }

            // Also update new password field validation
            binding.tiEditPasswordNew.error = when {
                current?.isNotEmpty() == true && newPassword.isEmpty() ->
                    "Kata sandi baru tidak boleh kosong!"

                else -> null
            }
        }

        binding.edEditPasswordNew.doOnTextChanged { newPass, _, _, _ ->
            val currentPassword = binding.edEditPasswordCurrent.text?.toString() ?: ""

            // Validate new password field - this should clear when appropriate
            binding.tiEditPasswordNew.error = when {
                currentPassword.isNotEmpty() && newPass.isNullOrEmpty() ->
                    "Kata sandi baru tidak boleh kosong!"

                else -> null
            }

            // Validate current password field
            binding.tiEditPasswordCurrent.error = when {
                newPass?.isNotEmpty() == true && currentPassword.isEmpty() ->
                    "Kata sandi saat ini diperlukan untuk mengubah kata sandi!"

                else -> null
            }
        }
        binding.edEditUsername.doOnTextChanged { text, _, _, _ ->
            binding.tiEditUsername.error =
                if (text.isNullOrEmpty()) "Username tidak boleh kosong!" else null
        }

//        userPreference = UserPreference.getInstance(requireContext())
//
//        lifecycleScope.launch {
//            val token = userPreference.getToken()
//            if (!token.isNullOrEmpty()) {
//                val factory = EditAccountViewModelFactory(token)
//                viewModel = ViewModelProvider(
//                    this@EditAccountFragment,
//                    factory
//                )[EditAccountViewModel::class.java]
//            } else {
//                Toast.makeText(requireContext(), "Anda tidak memiliki akses!", Toast.LENGTH_SHORT)
//                    .show()
//            }
//        }
        userPreference = UserPreference.getInstance(requireContext())

        lifecycleScope.launch {
            val token = userPreference.getToken()
            if (!token.isNullOrEmpty()) {
                val factory = EditAccountViewModelFactory(token)
                viewModel = ViewModelProvider(
                    this@EditAccountFragment,
                    factory
                )[EditAccountViewModel::class.java]

                // Set up the observer once here instead of in saveProfile()
                viewModel.editProfileResponse.observe(viewLifecycleOwner) { response ->
                    if (response.isSuccessful) {
                        val successMessage = response.body()?.message ?: "Update successful"
                        Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
                        setFragmentResult("editProfileKey", bundleOf("isProfileUpdated" to true))
                        dismiss()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = try {
                            JSONObject(errorBody ?: "").optString("error", response.message())
                        } catch (e: Exception) {
                            response.message()
                        }
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Anda tidak memiliki akses!", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun startGallery() {
        launcherGallery.launch("image/*")
    }

    private fun startCamera() {
        val photoURI: Uri = getImageUri(requireContext())
        currentPhotoPath = photoURI.toString()
        launcherIntentCamera.launch(photoURI)
    }

    private fun saveProfile() {
        val name = binding.edEditUsername.text.toString().trim()
        val currentPassword = binding.edEditPasswordCurrent.text.toString().trim()
        val newPassword = binding.edEditPasswordNew.text.toString().trim()

        binding.tiEditPasswordCurrent.error = null
        binding.tiEditPasswordNew.error = null

        if (name.isEmpty() && currentPassword.isEmpty() && newPassword.isEmpty() && selectedImageFile == null) {
            Toast.makeText(requireContext(), "Tidak ada yang perlu diperbarui!", Toast.LENGTH_SHORT)
                .show()
            return
        }

//        if (currentPassword.isNotEmpty() && newPassword.isEmpty()) {
//            binding.tiEditPasswordNew.error = "Kata sandi baru tidak boleh kosong!"
//            return
//        }
//
//        if (newPassword.isNotEmpty() && currentPassword.isEmpty()) {
//            binding.tiEditPasswordCurrent.error = "Kata sandi saat ini diperlukan untuk mengubah kata sandi!"
//            return
//        }

        val nameRequestBody =
            name.takeIf { it.isNotEmpty() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val currentPasswordRequestBody = currentPassword.takeIf { it.isNotEmpty() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val newPasswordRequestBody = newPassword.takeIf { it.isNotEmpty() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        var profileImagePart: MultipartBody.Part? = null
        selectedImageFile?.let {
            val imageRequestBody = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
            profileImagePart =
                MultipartBody.Part.createFormData("profileImage", it.name, imageRequestBody)
        }

        viewModel.editProfile(
            nameRequestBody,
            newPasswordRequestBody,
            currentPasswordRequestBody,
            profileImagePart
        )

//        viewModel.editProfileResponse.observe(viewLifecycleOwner) { response ->
//            if (response.isSuccessful) {
//                val successMessage = response.body()?.message ?: "Update successful"
//                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
//                setFragmentResult("editProfileKey", bundleOf("isProfileUpdated" to true))
////                findNavController().navigate(R.id.action_nav_update_account_to_nav_account)
//                dismiss()
//            } else {
//                val errorBody = response.errorBody()?.string()
//                val errorMessage = try {
//                    JSONObject(errorBody).getString("error")
//                } catch (_: Exception) {
//                    null
//                }
//                when {
//                    errorMessage?.contains("Kata sandi saat ini diperlukan untuk mengubah kata sandi!", true) == true -> {
//                        binding.tiEditPasswordCurrent.error = "Kata sandi saat ini diperlukan untuk mengubah kata sandi!"
//                    }
//                    errorMessage?.contains("Kata sandi baru tidak boleh sama dengan kata sandi saat ini!", true) == true -> {
//                        binding.tiEditPasswordNew.error = "Kata sandi baru tidak boleh sama dengan kata sandi saat ini!"
//                    }
//                    errorMessage?.contains("Tidak ada data yang perlu diperbarui!", true) == true -> {
//                        Toast.makeText(requireContext(), "Tidak ada data yang perlu diperbarui!", Toast.LENGTH_SHORT).show()
//                    }
//                    errorMessage?.contains("Unauthorized", true) == true -> {
//                        Toast.makeText(requireContext(), "Anda tidak memiliki akses. Silakan periksa kredensial Anda.", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//        }
//        viewModel.editProfileResponse.observe(viewLifecycleOwner) { response ->
//            if (response.isSuccessful) {
//                val successMessage = response.body()?.message ?: "Update successful"
//                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
//                setFragmentResult("editProfileKey", bundleOf("isProfileUpdated" to true))
//                dismiss()
//            } else {
//                val errorBody = response.errorBody()?.string()
//                val errorMessage = try {
//                    JSONObject(errorBody ?: "").optString("error", response.message())
//                } catch (e: Exception) {
//                    response.message()
//                }
//                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
//            }
//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
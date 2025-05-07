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
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.xeraphion.laporbang.R
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
                        Toast.makeText(requireContext(), "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Kesalahan pemrosesan gambar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(requireContext(), "TIdak ada gambar yang dipilih", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(requireContext(), "Failed to load image from camera", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
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

        binding.edEditUsername.doOnTextChanged { text, _, _, _ ->
            binding.tiEditUsername.error = if (text.isNullOrEmpty()) "Please enter a username" else null
        }

        userPreference = UserPreference.getInstance(requireContext())

        lifecycleScope.launch {
            val token = userPreference.getToken()
            if (!token.isNullOrEmpty()) {
                val factory = EditAccountViewModelFactory(token)
                viewModel = ViewModelProvider(this@EditAccountFragment, factory)[EditAccountViewModel::class.java]
            } else {
                Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
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

        // Clear previous errors
        binding.tiEditPasswordCurrent.error = null
        binding.tiEditPasswordNew.error = null

        if (name.isEmpty() && currentPassword.isEmpty() && newPassword.isEmpty() && selectedImageFile == null) {
            Toast.makeText(requireContext(), "Nothing to update", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate new password if current password is provided
        if (currentPassword.isNotEmpty() && newPassword.isEmpty()) {
            binding.tiEditPasswordNew.error = "New password cannot be empty"
            return
        }

        // Validate current password if new password is provided
        if (newPassword.isNotEmpty() && currentPassword.isEmpty()) {
            binding.tiEditPasswordCurrent.error = "Current password is required to change password"
            return
        }

        val nameRequestBody = name.takeIf { it.isNotEmpty() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val currentPasswordRequestBody = currentPassword.takeIf { it.isNotEmpty() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val newPasswordRequestBody = newPassword.takeIf { it.isNotEmpty() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        var profileImagePart: MultipartBody.Part? = null
        selectedImageFile?.let {
            val imageRequestBody = it.asRequestBody("image/jpeg".toMediaTypeOrNull())
            profileImagePart = MultipartBody.Part.createFormData("profileImage", it.name, imageRequestBody)
        }

        viewModel.editProfile(nameRequestBody, newPasswordRequestBody, currentPasswordRequestBody, profileImagePart)

        viewModel.editProfileResponse.observe(viewLifecycleOwner) { response ->
            if (response.isSuccessful) {
                val successMessage = response.body()?.message ?: "Update successful"
                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
                setFragmentResult("editProfileKey", bundleOf("isProfileUpdated" to true))
//                findNavController().navigate(R.id.action_nav_update_account_to_nav_account)
                dismiss()
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    JSONObject(errorBody).getString("error")
                } catch (_: Exception) {
                    null
                }

                when {
                    errorMessage?.contains("Current password is incorrect", true) == true -> {
                        binding.tiEditPasswordCurrent.error = "Current password is incorrect"
                    }
                    errorMessage?.contains("New password cannot be the same", true) == true -> {
                        binding.tiEditPasswordNew.error = "New password cannot be the same as the current password"
                    }
                    errorMessage?.contains("No data to update", true) == true -> {
                        Toast.makeText(requireContext(), "Nothing to update", Toast.LENGTH_SHORT).show()
                    }
                    errorMessage?.contains("Unauthorized", true) == true -> {
                        Toast.makeText(requireContext(), "Unauthorized access. Please check your credentials.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
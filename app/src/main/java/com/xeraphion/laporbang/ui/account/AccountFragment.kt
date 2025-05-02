package com.xeraphion.laporbang.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.xeraphion.laporbang.R
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.databinding.FragmentAccountBinding
import com.xeraphion.laporbang.ui.login.LoginActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AccountViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(requireActivity().application)
        )[AccountViewModel::class.java]

        viewModel.fetchUserData()

        viewModel.user.observe(viewLifecycleOwner) { user ->
            binding.textName.text = user.username
            binding.textEmail.text = user.email
            binding.textDateJoined.text = formatDate(user.createdAt)
            Glide.with(this)
                .load(user.profileImage)
                .into(binding.imageAvatar)

            binding.btnEditProfile.setOnClickListener {
                navigateToEditProfile(user.username.toString(), user.profileImage)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                val userPreference = UserPreference.getInstance(requireContext())
                userPreference.clearToken()
                Toast.makeText(requireContext(), "Logout berhasil!", Toast.LENGTH_SHORT).show()

                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

                requireActivity().finish()
            }
        }

        parentFragmentManager.setFragmentResultListener(
            "editProfileKey", viewLifecycleOwner
        ) { _, resultBundle ->
            val isProfileUpdated = resultBundle.getBoolean("isProfileUpdated", false)
            if (isProfileUpdated) {
                viewModel.fetchUserData()
            }
        }
    }

    private fun navigateToEditProfile(username: String, profileImage: String?) {
        val bundle = Bundle().apply {
            putString("username", username)
            putString("profileImagePath", profileImage)
        }

        findNavController().navigate(R.id.action_nav_account_to_nav_update_account, bundle)
    }

    private fun formatDate(dateString: String?): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val date = dateString?.let { inputFormat.parse(it) }
        return date?.let {
            "Joined ${outputFormat.format(it)}"
        } ?: "Bergabung Tidak Diketahui"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

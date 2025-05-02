package com.xeraphion.laporbang.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.xeraphion.laporbang.R
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels {
        val userPreference = UserPreference.getInstance(requireContext())
        HomeViewModelFactory(userPreference)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvReport.layoutManager = LinearLayoutManager(requireContext())

        var isFilteredById = false

        viewModel.fetchReports()

        // Observe reports and update the RecyclerView
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reports.collect { reportList ->
                    val adapter = HomeAdapter(reportList) { selectedReport ->
                        val bundle = Bundle().apply {
                            putParcelable("report", selectedReport)
                        }
                        findNavController().navigate(R.id.nav_detail, bundle)
                    }
                    binding.rvReport.adapter = adapter
                }
            }
        }

        // Listen for data change events and refresh reports
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dataChangedEvent.collect {
                    viewModel.fetchReports() // Refresh data when notified
                }
            }
        }

        binding.fabReport.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_nav_report)
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_filter) {
                val popupMenu = PopupMenu(requireContext(), requireActivity().findViewById(R.id.action_filter))
                popupMenu.menu.add("Filter berdasarkan Pengguna")
                popupMenu.menu.add("Tampilkan Semua Laporan")

                popupMenu.setOnMenuItemClickListener { popupItem ->
                    when (popupItem.title) {
                        "Filter berdasarkan Pengguna" -> {
                            viewModel.fetchReportsByUserId()
                            menuItem.title = "Tampilkan Semua Laporan"
                            isFilteredById = true
                        }
                        "Tampilkan Semua Laporan" -> {
                            viewModel.fetchReports()
                            menuItem.title = "Filter berdasarkan Pengguna"
                            isFilteredById = false
                        }
                    }
                    true
                }
                popupMenu.show()
                true
            } else {
                false
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

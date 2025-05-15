package com.xeraphion.laporbang.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
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
import com.xeraphion.laporbang.response.ReportsResponseItem
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels {
        val userPreference = UserPreference.getInstance(requireContext())
        HomeViewModelFactory(userPreference)
    }

    private var latestReportList: List<ReportsResponseItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvReport.layoutManager = LinearLayoutManager(requireContext())

        if (viewModel.isFilteredById.value) {
            viewModel.fetchReportsByUserId()
        } else {
            viewModel.fetchReports()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFilteredById.collect { /* Update UI if needed */ }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentSort.collect { /* Update UI if needed */ }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAscending.collect {
                updateSortIcon()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reports.collect { reportList ->
                    latestReportList = reportList
                    updateReportList()
                }
            }
        }

        // Update the dataChangedEvent collection to use the filter state
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dataChangedEvent.collect {
                    // No need to fetch reports directly here
                    // The ViewModel's notifyDataChanged method will handle
                    // choosing the right fetch method based on filter state
                }
            }
        }

        binding.fabReport.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_nav_report)
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_filter -> {
                    showFilterPopup(menuItem)
                    true
                }
                R.id.action_sorted_by -> {
                    // Fix: use ViewModel to update sort direction instead of local variable
                    viewModel.setSortDirection(!viewModel.isAscending.value)
                    updateReportList()
                    true
                }
                else -> false
            }
        }
        updateSortIcon()
    }

    private fun showFilterPopup(menuItem: MenuItem) {
        val popupMenu = PopupMenu(requireContext(), requireActivity().findViewById(R.id.action_filter))
        popupMenu.menu.add(if (viewModel.isFilteredById.value) "Tampilkan Semua Laporan" else "Filter berdasarkan Pengguna")
        popupMenu.menu.add("Urutkan berdasarkan Tanggal")
        popupMenu.menu.add("Urutkan berdasarkan Keparahan")
        popupMenu.menu.add("Urutkan berdasarkan Jumlah Lubang")

        popupMenu.setOnMenuItemClickListener { popupItem ->
            when (popupItem.title) {
                "Filter berdasarkan Pengguna" -> {
                    viewModel.setFilterById(true)
                }
                "Tampilkan Semua Laporan" -> {
                    viewModel.setFilterById(false)
                }
                "Urutkan berdasarkan Tanggal" -> {
                    viewModel.setSortType(HomeViewModel.SortType.DATE)
                    viewModel.setSortDirection(true)
                    updateReportList()
                }
                "Urutkan berdasarkan Keparahan" -> {
                    viewModel.setSortType(HomeViewModel.SortType.SEVERITY)
                    viewModel.setSortDirection(false)
                    updateReportList()
                }
                "Urutkan berdasarkan Jumlah Lubang" -> {
                    viewModel.setSortType(HomeViewModel.SortType.HOLES)
                    viewModel.setSortDirection(true)
                    updateReportList()
                }
            }
            true
        }
        popupMenu.show()
    }

    private fun updateSortIcon() {
        val iconRes = if (viewModel.isAscending.value) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
        binding.toolbar.menu.findItem(R.id.action_sorted_by)?.icon =
            ContextCompat.getDrawable(requireContext(), iconRes)
    }

    private fun updateReportList() {
        val sortedList = when (viewModel.currentSort.value) {
            HomeViewModel.SortType.DATE -> {
                if (viewModel.isAscending.value) {
                    latestReportList.sortedBy { it.updatedAt ?: it.createdAt }
                } else {
                    latestReportList.sortedByDescending { it.updatedAt ?: it.createdAt }
                }
            }
            HomeViewModel.SortType.SEVERITY -> {
                if (viewModel.isAscending.value) latestReportList.sortedBy { severityOrder(it.severity) }
                else latestReportList.sortedByDescending { severityOrder(it.severity) }
            }
            HomeViewModel.SortType.HOLES -> {
                if (viewModel.isAscending.value) latestReportList.sortedBy { it.holesCount ?: 0 }
                else latestReportList.sortedByDescending { it.holesCount ?: 0 }
            }
        }
        binding.rvReport.adapter = HomeAdapter(sortedList) { selectedReport ->
            val bundle = Bundle().apply { putParcelable("report", selectedReport) }
            findNavController().navigate(R.id.nav_detail, bundle)
        }
    }

    private fun severityOrder(severity: String?): Int = when (severity?.lowercase()) {
        "tinggi" -> 3
        "sedang" -> 2
        "rendah" -> 1
        else -> 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
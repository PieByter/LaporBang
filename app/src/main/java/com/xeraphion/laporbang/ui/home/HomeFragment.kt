package com.xeraphion.laporbang.ui.home

import android.os.Bundle
import android.view.*
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels {
        val userPreference = UserPreference.getInstance(requireContext())
        HomeViewModelFactory(userPreference)
    }

    private var isFilteredById = false
    private var currentSort = SortType.DATE
    private var isAscending = false
    private var latestReportList: List<ReportsResponseItem> = emptyList()

    enum class SortType { DATE, SEVERITY, HOLES }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvReport.layoutManager = LinearLayoutManager(requireContext())

        viewModel.fetchReports()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reports.collect { reportList ->
                    latestReportList = reportList
                    updateReportList()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dataChangedEvent.collect {
                    viewModel.fetchReports()
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
                    isAscending = !isAscending
                    updateSortIcon()
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
        popupMenu.menu.add(if (isFilteredById) "Tampilkan Semua Laporan" else "Filter berdasarkan Pengguna")
        popupMenu.menu.add("Urutkan berdasarkan Tanggal")
        popupMenu.menu.add("Urutkan berdasarkan Keparahan")
        popupMenu.menu.add("Urutkan berdasarkan Jumlah Lubang")

        popupMenu.setOnMenuItemClickListener { popupItem ->
            when (popupItem.title) {
                "Filter berdasarkan Pengguna" -> {
                    isFilteredById = true
                    viewModel.fetchReportsByUserId()
                }
                "Tampilkan Semua Laporan" -> {
                    isFilteredById = false
                    viewModel.fetchReports()
                }
                "Urutkan berdasarkan Tanggal" -> {
                    currentSort = SortType.DATE
                    isAscending = true
                    updateSortIcon()
                    updateReportList()
                }
                "Urutkan berdasarkan Keparahan" -> {
                    currentSort = SortType.SEVERITY
                    isAscending = false
                    updateSortIcon()
                    updateReportList()
                }
                "Urutkan berdasarkan Jumlah Lubang" -> {
                    currentSort = SortType.HOLES
                    isAscending = true
                    updateSortIcon()
                    updateReportList()
                }
            }
            true
        }
        popupMenu.show()
    }

    private fun updateSortIcon() {
        val iconRes = if (isAscending) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
        binding.toolbar.menu.findItem(R.id.action_sorted_by)?.icon =
            ContextCompat.getDrawable(requireContext(), iconRes)
    }

    private fun updateReportList() {
        val sortedList = when (currentSort) {
            SortType.DATE -> {
                if (isAscending) {
                    latestReportList.sortedBy { it.updatedAt ?: it.createdAt }
                } else {
                    latestReportList.sortedByDescending { it.updatedAt ?: it.createdAt }
                }
            }
            SortType.SEVERITY -> {
                if (isAscending) latestReportList.sortedBy { severityOrder(it.severity) }
                else latestReportList.sortedByDescending { severityOrder(it.severity) }
            }
            SortType.HOLES -> {
                if (isAscending) latestReportList.sortedBy { it.holesCount ?: 0 }
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
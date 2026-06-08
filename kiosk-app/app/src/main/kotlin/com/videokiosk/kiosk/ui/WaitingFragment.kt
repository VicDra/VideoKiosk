package com.videokiosk.kiosk.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.videokiosk.kiosk.R
import com.videokiosk.kiosk.model.CallState
import com.videokiosk.kiosk.settings.SettingsActivity
import com.videokiosk.kiosk.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Idle / home screen.
 * Implements the ЕИРЦ ЛО brand chrome (topbar + accent line) with
 * a horizontal body split: welcome copy (left) + Видеосвязь CTA tile (right).
 *
 * View IDs:
 *   btn_call_operator  — LinearLayout acting as the primary CTA tile
 *   tv_status          — topbar status badge updated with call state
 *   btn_settings       — topbar gear icon → SettingsActivity
 */
class WaitingFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var callButton: View
    private lateinit var statusTextView: TextView

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_waiting, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        callButton = view.findViewById(R.id.btn_call_operator)
        statusTextView = view.findViewById(R.id.tv_status)

        // CTA tile → start a call
        callButton.setOnClickListener {
            viewModel.startCall()
        }

        // Gear icon → settings
        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // Observe call state — update topbar badge
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.callState.collect { state ->
                when (state) {
                    is CallState.Idle -> {
                        callButton.isEnabled = true
                        callButton.alpha = 1f
                        statusTextView.text = getString(R.string.status_ready)
                    }
                    is CallState.Calling -> {
                        callButton.isEnabled = false
                        callButton.alpha = 0.7f
                        statusTextView.text = getString(R.string.status_calling)
                    }
                    is CallState.Error -> {
                        callButton.isEnabled = true
                        callButton.alpha = 1f
                        statusTextView.text = state.message
                    }
                    else -> { /* navigation handled by MainActivity */ }
                }
            }
        }
    }
}

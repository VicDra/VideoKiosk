package com.videokiosk.kiosk.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.videokiosk.kiosk.R
import com.videokiosk.kiosk.model.CallState
import com.videokiosk.kiosk.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Idle/waiting screen shown when no call is active.
 * The user presses the call button to initiate a call to the operator.
 */
class WaitingFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var callButton: Button
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

        // Initiate a call when the button is pressed
        callButton.setOnClickListener {
            viewModel.startCall()
        }

        // Observe call state to update UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.callState.collect { state ->
                when (state) {
                    is CallState.Idle -> {
                        callButton.isEnabled = true
                        statusTextView.text = getString(R.string.status_ready)
                    }
                    is CallState.Calling -> {
                        callButton.isEnabled = false
                        statusTextView.text = getString(R.string.status_calling)
                    }
                    is CallState.Error -> {
                        callButton.isEnabled = true
                        statusTextView.text = state.message
                    }
                    else -> { /* handled by navigation in MainActivity */ }
                }
            }
        }
    }
}

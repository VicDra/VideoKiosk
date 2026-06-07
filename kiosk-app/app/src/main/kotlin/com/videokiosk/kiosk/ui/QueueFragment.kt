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
 * Queue screen shown when the operator is busy and the kiosk is waiting.
 * Displays the current queue position and an estimated wait time.
 */
class QueueFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tvQueuePosition: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var btnCancelCall: Button

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_queue, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvQueuePosition = view.findViewById(R.id.tv_queue_position)
        tvEstimatedTime = view.findViewById(R.id.tv_estimated_time)
        btnCancelCall = view.findViewById(R.id.btn_cancel_call)

        btnCancelCall.setOnClickListener {
            viewModel.endCall()
        }

        // Observe call state — update position label when Queued updates arrive
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.callState.collect { state ->
                when (state) {
                    is CallState.Queued -> {
                        tvQueuePosition.text = state.position.toString()
                        // TODO: calculate estimated wait time from position (e.g. position * avg call duration)
                        tvEstimatedTime.text = getString(
                            R.string.queue_estimated_minutes,
                            state.position * 3   // rough estimate: 3 min per client ahead
                        )
                    }
                    is CallState.Calling -> {
                        tvQueuePosition.text = "..."
                        tvEstimatedTime.text = getString(R.string.queue_connecting)
                    }
                    else -> { /* navigation handled by MainActivity */ }
                }
            }
        }
    }
}
